"""FastAPI realtime gateway.

This service is the Python orchestration boundary described in the solution:
the browser connects here only after Spring Boot has authenticated it and
issued a short-lived ticket.  Model adapters are intentionally interfaces at
this stage; no fake LLM or streaming-TTS result is produced.
"""

from __future__ import annotations

import asyncio
import hashlib
import hmac
import json
import os
import time
from collections import defaultdict
from dataclasses import dataclass
from typing import AsyncIterator, Protocol
from uuid import UUID, uuid4

import httpx
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.responses import JSONResponse

try:  # Supports both `uvicorn gateway:app` and package imports.
    from .protocol import RealtimeEvent, SessionEventStream, TurnState
    from .semantic_segmenter import AdaptiveSemanticChunker
except ImportError:
    from protocol import RealtimeEvent, SessionEventStream, TurnState
    from semantic_segmenter import AdaptiveSemanticChunker


class GatewayBackpressure(Exception):
    """The client cannot consume events quickly enough."""


class LlmConfigurationError(Exception):
    """The gateway does not have credentials for the selected LLM."""


class LlmProviderError(Exception):
    """The selected LLM returned an unusable response."""


class LlmAdapter(Protocol):
    async def stream_reply(
        self, prompt: str, cancel: asyncio.Event, *, user_id: int | None = None
    ) -> AsyncIterator[str]: ...


class TtsStreamingAdapter(Protocol):
    async def stream_audio(self, text: str, cancel: asyncio.Event) -> AsyncIterator[bytes]: ...


class DeepSeekLlmAdapter:
    """Convert DeepSeek Chat Completions SSE chunks into plain text deltas.

    The API key is deliberately read from the process environment by
    ``GatewaySettings``. It must never reach the browser or repository.
    """

    def __init__(self, settings: "GatewaySettings", transport: httpx.AsyncBaseTransport | None = None):
        self._settings = settings
        self._transport = transport

    async def stream_reply(
        self, prompt: str, cancel: asyncio.Event, *, user_id: int | None = None
    ) -> AsyncIterator[str]:
        if not self._settings.deepseek_api_key:
            raise LlmConfigurationError("DEEPSEEK_API_KEY is not configured")

        request: dict[str, object] = {
            "model": self._settings.deepseek_model,
            "stream": True,
            "stream_options": {"include_usage": True},
            "thinking": {"type": "disabled"},
            "max_tokens": self._settings.deepseek_max_tokens,
            "temperature": 0.6,
            "messages": [
                {
                    "role": "system",
                    "content": "你是智能语音交互平台的助手。回答自然、简洁，适合直接朗读。",
                },
                {"role": "user", "content": prompt},
            ],
        }
        # Send an application-scoped pseudonymous value instead of exposing
        # the database user ID to the external model provider.
        if user_id is not None:
            request["user_id"] = hmac.new(
                self._settings.internal_key.encode("utf-8"),
                f"zhisheng:{user_id}".encode("utf-8"),
                hashlib.sha256,
            ).hexdigest()

        timeout = httpx.Timeout(connect=10.0, read=60.0, write=10.0, pool=10.0)
        headers = {
            "Authorization": f"Bearer {self._settings.deepseek_api_key}",
            "Content-Type": "application/json",
            "Accept": "text/event-stream",
        }
        async with httpx.AsyncClient(timeout=timeout, transport=self._transport) as client:
            try:
                async with client.stream(
                    "POST",
                    f"{self._settings.deepseek_base_url}/chat/completions",
                    headers=headers,
                    json=request,
                ) as response:
                    if response.status_code >= 400:
                        raise LlmProviderError(f"DeepSeek request failed with HTTP {response.status_code}")
                    async for line in response.aiter_lines():
                        if cancel.is_set():
                            return
                        if not line.startswith("data:"):
                            continue  # SSE keep-alive comments and blank lines.
                        data = line[5:].strip()
                        if data == "[DONE]":
                            return
                        try:
                            payload = json.loads(data)
                            choices = payload.get("choices", [])
                            delta = choices[0].get("delta", {}) if choices else {}
                            content = delta.get("content")
                        except (AttributeError, IndexError, json.JSONDecodeError) as error:
                            raise LlmProviderError("DeepSeek returned an invalid SSE chunk") from error
                        if isinstance(content, str) and content:
                            yield content
            except httpx.HTTPError as error:
                raise LlmProviderError("DeepSeek connection failed") from error


@dataclass(frozen=True)
class GatewaySettings:
    spring_boot_url: str
    internal_key: str
    allowed_origins: frozenset[str]
    max_outbound_events: int
    deepseek_api_key: str
    deepseek_model: str
    deepseek_base_url: str
    deepseek_max_tokens: int

    @classmethod
    def from_environment(cls) -> "GatewaySettings":
        origins = os.getenv("REALTIME_ALLOWED_ORIGINS", "http://localhost:8081,http://127.0.0.1:8081")
        return cls(
            spring_boot_url=os.getenv("SPRING_BOOT_URL", "http://127.0.0.1:18080").rstrip("/"),
            internal_key=os.getenv("REALTIME_GATEWAY_INTERNAL_KEY", ""),
            allowed_origins=frozenset(item.strip() for item in origins.split(",") if item.strip()),
            max_outbound_events=_bounded_env_int("REALTIME_MAX_OUTBOUND_EVENTS", 128, 8, 2_048),
            deepseek_api_key=os.getenv("DEEPSEEK_API_KEY", ""),
            # DeepSeek's current recommended Flash identifier. A developer
            # can override this only through their local environment.
            deepseek_model=os.getenv("DEEPSEEK_MODEL", "deepseek-v4-flash"),
            deepseek_base_url=os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com").rstrip("/"),
            deepseek_max_tokens=_bounded_env_int("DEEPSEEK_MAX_TOKENS", 1_024, 1, 8_192),
        )


def _bounded_env_int(name: str, default: int, minimum: int, maximum: int) -> int:
    try:
        value = int(os.getenv(name, str(default)))
    except ValueError as error:
        raise ValueError(f"{name} must be an integer") from error
    if value < minimum or value > maximum:
        raise ValueError(f"{name} must be between {minimum} and {maximum}")
    return value


class GatewaySession:
    """Owns event ordering, bounded delivery and cancellation for one socket."""

    def __init__(self, session_id: str, max_outbound_events: int):
        self.events = SessionEventStream(session_id)
        self.outbound: asyncio.Queue[RealtimeEvent] = asyncio.Queue(maxsize=max_outbound_events)
        self.chunkers: dict[str, AdaptiveSemanticChunker] = {}
        self.cancel_signals: dict[str, asyncio.Event] = {}
        self.tasks: dict[str, set[asyncio.Task[object]]] = defaultdict(set)
        self.llm_requested_turns: set[str] = set()

    async def start_turn(self, turn_id: str) -> None:
        event = self.events.start_turn(turn_id)
        self.chunkers[turn_id] = AdaptiveSemanticChunker()
        self.cancel_signals[turn_id] = asyncio.Event()
        self._enqueue(event)

    async def interrupt_turn(self, turn_id: str) -> None:
        if self.events.state_of(turn_id) is not TurnState.ACTIVE:
            raise ValueError("cannot interrupt a non-active turn")
        event = self.events.interrupt(turn_id)
        self.cancel_signals[turn_id].set()
        for task in self.tasks.pop(turn_id, set()):
            task.cancel()
        self._enqueue(event)

    def register_task(self, turn_id: str, task: asyncio.Task[object]) -> None:
        self.tasks[turn_id].add(task)
        task.add_done_callback(lambda completed: self.tasks[turn_id].discard(completed))

    def emit(self, turn_id: str, event: str, **payload: object) -> None:
        self._enqueue(self.events.emit(turn_id, event, **payload))

    async def send_loop(self, websocket: WebSocket) -> None:
        while True:
            event = await self.outbound.get()
            await websocket.send_json(event.to_dict())

    async def close(self) -> None:
        for cancel in self.cancel_signals.values():
            cancel.set()
        pending = [task for turn_tasks in self.tasks.values() for task in turn_tasks]
        for task in pending:
            task.cancel()
        if pending:
            await asyncio.gather(*pending, return_exceptions=True)

    def _enqueue(self, event: RealtimeEvent) -> None:
        try:
            self.outbound.put_nowait(event)
        except asyncio.QueueFull as error:
            raise GatewayBackpressure("outbound event queue is full") from error


async def consume_ticket(ticket: str, origin: str, settings: GatewaySettings) -> int | None:
    """Ask Spring Boot to atomically consume a one-time browser ticket."""
    if not ticket or not settings.internal_key:
        return None
    try:
        async with httpx.AsyncClient(timeout=3.0) as client:
            response = await client.post(
                f"{settings.spring_boot_url}/api/realtime/internal/tickets/consume",
                headers={"X-Realtime-Gateway-Key": settings.internal_key},
                json={"ticket": ticket, "origin": origin},
            )
        if response.status_code != 200:
            return None
        return int(response.json()["userId"])
    except (httpx.HTTPError, KeyError, TypeError, ValueError):
        return None


settings = GatewaySettings.from_environment()
app = FastAPI(title="Zhisheng Realtime Gateway", version="0.1.0")

try:
    from .voice import install_voice_route
except ImportError:
    from voice import install_voice_route

install_voice_route(app, settings, consume_ticket, DeepSeekLlmAdapter)


@app.get("/realtime/health")
async def health() -> JSONResponse:
    return JSONResponse({
        "status": "UP",
        "capabilities": [
            "ticket-validation", "ordered-events", "backpressure", "turn-cancellation", "llm-streaming"
        ],
        "llmAdapter": "deepseek" if settings.deepseek_api_key else "not-configured",
        "llmModel": settings.deepseek_model if settings.deepseek_api_key else None,
        "ttsAdapter": "xtts" if os.path.isfile(os.getenv("REALTIME_TTS_REFERENCE", "")) else "not-configured",
        "voicePath": "/realtime/voice",
        "voiceSynthesisMode": os.getenv("REALTIME_TTS_MODE", "segmented"),
        "voiceConfigured": bool(settings.deepseek_api_key and os.path.isfile(os.getenv("REALTIME_TTS_REFERENCE", ""))),
        "knowledgeVoiceConfigured": bool(settings.internal_key and os.path.isfile(os.getenv("REALTIME_TTS_REFERENCE", ""))),
        "voiceInferenceCancellation": False,
    })


@app.websocket("/realtime/ws")
async def realtime_socket(websocket: WebSocket) -> None:
    origin = websocket.headers.get("origin")
    if origin not in settings.allowed_origins:
        await websocket.close(code=1008, reason="origin not allowed")
        return

    user_id = await consume_ticket(websocket.query_params.get("ticket", ""), origin, settings)
    if user_id is None:
        await websocket.close(code=1008, reason="invalid ticket")
        return

    await websocket.accept()
    session = GatewaySession(str(uuid4()), settings.max_outbound_events)
    llm = DeepSeekLlmAdapter(settings)
    sender = asyncio.create_task(session.send_loop(websocket))
    try:
        while True:
            message = await websocket.receive_json()
            event = message.get("event")
            turn_id = message.get("turnId")
            if event == "turn.start":
                session_id = message.get("sessionId")
                if session_id is not None and session_id != session.events.session_id:
                    await websocket.close(code=1008, reason="session id mismatch")
                    return
                _validate_turn_id(turn_id)
                await session.start_turn(turn_id)
                prompt = message.get("prompt")
                if prompt is not None:
                    _start_llm_task(session, llm, turn_id, prompt, user_id)
            elif event == "llm.request":
                _validate_turn_id(turn_id)
                _start_llm_task(session, llm, turn_id, message.get("prompt"), user_id)
            elif event == "turn.interrupt":
                _validate_turn_id(turn_id)
                await session.interrupt_turn(turn_id)
            else:
                await websocket.close(code=1003, reason="unsupported client event")
                return
    except GatewayBackpressure:
        await websocket.close(code=1013, reason="client is too slow")
    except (ValueError, TypeError):
        await websocket.close(code=1007, reason="invalid realtime event")
    except WebSocketDisconnect:
        pass
    finally:
        sender.cancel()
        await session.close()
        await asyncio.gather(sender, return_exceptions=True)


def _validate_turn_id(value: object) -> None:
    if not isinstance(value, str):
        raise ValueError("turnId must be a UUID")
    UUID(value)


def _start_llm_task(
    session: GatewaySession, llm: LlmAdapter, turn_id: str, prompt: object, user_id: int
) -> None:
    if not isinstance(prompt, str) or not prompt.strip() or len(prompt) > 4_000:
        raise ValueError("prompt must be 1 to 4000 characters")
    if session.events.state_of(turn_id) is not TurnState.ACTIVE:
        raise ValueError("cannot request a reply for a non-active turn")
    if turn_id in session.llm_requested_turns:
        raise ValueError("turn already has an LLM request")
    session.llm_requested_turns.add(turn_id)
    task = asyncio.create_task(_stream_llm_turn(session, llm, turn_id, prompt.strip(), user_id))
    session.register_task(turn_id, task)


async def _stream_llm_turn(
    session: GatewaySession, llm: LlmAdapter, turn_id: str, prompt: str, user_id: int
) -> None:
    """Forward provider deltas and semantic TTS-ready segments in order."""
    cancel = session.cancel_signals[turn_id]
    try:
        session.emit(turn_id, "llm.started")
        async for delta in llm.stream_reply(prompt, cancel, user_id=user_id):
            if cancel.is_set():
                return
            session.emit(turn_id, "llm.delta", text=delta)
            for segment in session.chunkers[turn_id].feed(delta, int(time.monotonic() * 1000)):
                session.emit(turn_id, "tts.segment_ready", text=segment.text, reason=segment.reason)
        if cancel.is_set():
            return
        for segment in session.chunkers[turn_id].finish():
            session.emit(turn_id, "tts.segment_ready", text=segment.text, reason=segment.reason)
        session.emit(turn_id, "llm.completed")
        session.emit(turn_id, "turn.completed")
    except asyncio.CancelledError:
        raise
    except (LlmConfigurationError, LlmProviderError) as error:
        if not cancel.is_set():
            session.emit(turn_id, "turn.failed", code="llm_unavailable", message=str(error))

"""FastAPI realtime gateway.

This service is the Python orchestration boundary described in the solution:
the browser connects here only after Spring Boot has authenticated it and
issued a short-lived ticket. DeepSeek and iFlytek are provider adapters; their
credentials are environment variables and are never exposed to the browser.
"""

from __future__ import annotations

import asyncio
import base64
import hashlib
import hmac
import io
import json
import os
import re
import time
import wave
from collections import defaultdict
from dataclasses import dataclass
from email.utils import formatdate
from typing import AsyncIterator, Protocol
from urllib.parse import urlencode, urlsplit
from uuid import UUID, uuid4

import httpx
import websockets
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse


_CITATION_ID = re.compile(r"C[0-9]{3}")
_CITATION_TAG = re.compile(r"[【\[](?P<citation>C[0-9]{3})[】\]]")

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


class TtsConfigurationError(Exception):
    """The gateway does not have credentials for the selected TTS provider."""


class TtsProviderError(Exception):
    """The selected TTS provider returned an unusable response."""


class AsrProviderError(Exception):
    """The realtime ASR provider returned an unusable response."""


class KnowledgeRetrievalError(Exception):
    """The authorized knowledge service returned an unusable result."""


class LlmAdapter(Protocol):
    async def stream_reply(
        self, prompt: str, cancel: asyncio.Event, *, user_id: int | None = None,
        knowledge_context: str = ""
    ) -> AsyncIterator[str]: ...


class TtsStreamingAdapter(Protocol):
    async def stream_audio(self, text: str, cancel: asyncio.Event) -> AsyncIterator[bytes]: ...


@dataclass(frozen=True)
class AsrResult:
    text: str
    mode: str
    is_final: bool


class AsrStreamingAdapter(Protocol):
    async def stream_transcript(
        self, audio: asyncio.Queue[bytes | None], cancel: asyncio.Event, config: dict[str, object]
    ) -> AsyncIterator[AsrResult]: ...


class AsrFallbackAdapter(Protocol):
    async def transcribe_pcm(self, pcm: bytes, cancel: asyncio.Event) -> str: ...


@dataclass(frozen=True)
class RetrievedKnowledge:
    context: str
    citations: dict[str, dict[str, object]]


class KnowledgeRetriever(Protocol):
    async def retrieve(self, query: str, user_id: int) -> RetrievedKnowledge: ...


class DeepSeekLlmAdapter:
    """Convert DeepSeek Chat Completions SSE chunks into plain text deltas.

    The API key is deliberately read from the process environment by
    ``GatewaySettings``. It must never reach the browser or repository.
    """

    def __init__(self, settings: "GatewaySettings", transport: httpx.AsyncBaseTransport | None = None):
        self._settings = settings
        self._transport = transport

    async def stream_reply(
        self, prompt: str, cancel: asyncio.Event, *, user_id: int | None = None,
        knowledge_context: str = ""
    ) -> AsyncIterator[str]:
        if not self._settings.deepseek_api_key:
            raise LlmConfigurationError("DEEPSEEK_API_KEY is not configured")

        system_prompt = "你是智能语音交互平台的助手。回答自然、简洁，适合直接朗读。"
        if knowledge_context:
            system_prompt += (
                "\n以下是服务端已授权且仍有效的资料。涉及资料中的事实时，只能依据这些资料回答；"
                "每个事实句末尾必须附上对应引用标记，例如【C001】。"
                "不得编造未提供的引用。\n\n资料：\n" + knowledge_context
            )
        request: dict[str, object] = {
            "model": self._settings.deepseek_model,
            "stream": True,
            "stream_options": {"include_usage": True},
            "thinking": {"type": "disabled"},
            "temperature": 0.6,
            "messages": [
                {
                    "role": "system",
                    "content": system_prompt,
                },
                {"role": "user", "content": prompt},
            ],
        }
        # A numeric internal ID is not personal information and lets the
        # provider isolate rate limiting/cache data by application user.
        if user_id is not None:
            request["user_id"] = str(user_id)

        timeout = httpx.Timeout(connect=10.0, read=None, write=10.0, pool=10.0)
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


class XfyunTtsStreamingAdapter:
    """Stream one semantic segment through iFlytek's online TTS WebSocket API.

    iFlytek accepts a completed short text segment and returns PCM data in
    several WebSocket messages. The semantic segmenter supplies those short
    segments while the gateway converts every returned chunk into an ordered
    browser event. Credentials remain process environment variables only.
    """

    def __init__(self, settings: "GatewaySettings", connect_factory=websockets.connect):
        self._settings = settings
        self._connect = connect_factory

    async def stream_audio(self, text: str, cancel: asyncio.Event) -> AsyncIterator[bytes]:
        if not self._settings.has_xfyun_tts_credentials:
            raise TtsConfigurationError("iFlytek TTS credentials are not configured")
        if len(text.encode("utf-8")) > 8_000:
            raise TtsProviderError("TTS segment exceeds iFlytek's 8000-byte limit")

        try:
            url, connection_options = self._connection_target()
            async with self._connect(url, open_timeout=10, close_timeout=3, **connection_options) as socket:
                await socket.send(json.dumps(self._request(text), ensure_ascii=False))
                async for message in socket:
                    if cancel.is_set():
                        return
                    if isinstance(message, bytes):
                        # The default JSON protocol returns base64 in text
                        # frames, but accepting binary here keeps the adapter
                        # compatible with iFlytek's binary-response option.
                        if message:
                            yield message
                        continue
                    try:
                        response = json.loads(message)
                        code = response.get("code")
                        data = response.get("data") or {}
                    except (AttributeError, json.JSONDecodeError) as error:
                        raise TtsProviderError("iFlytek returned invalid TTS JSON") from error
                    if code != 0:
                        raise TtsProviderError(f"iFlytek TTS failed with code {code}")
                    audio = data.get("audio")
                    if audio:
                        try:
                            chunk = base64.b64decode(audio, validate=True)
                        except (ValueError, TypeError) as error:
                            raise TtsProviderError("iFlytek returned invalid audio data") from error
                        if chunk:
                            yield chunk
                    if data.get("status") == 2:
                        return
        except TtsProviderError:
            raise
        except Exception as error:
            # Do not include ``str(error)`` here. WebSocket-library exceptions
            # can contain the fully signed URL, whose query must never reach a
            # browser response, log file, or terminal screenshot.
            status_code = getattr(error, "status_code", None)
            detail = f"HTTP {status_code}" if isinstance(status_code, int) else type(error).__name__
            raise TtsProviderError(f"iFlytek TTS connection failed ({detail})") from error

    def _authorized_url(self) -> str:
        endpoint = urlsplit(self._settings.xfyun_tts_endpoint)
        host = endpoint.netloc
        request_path = endpoint.path or "/v2/tts"
        date = formatdate(usegmt=True)
        signature_origin = f"host: {host}\ndate: {date}\nGET {request_path} HTTP/1.1"
        signature = base64.b64encode(
            hmac.new(
                self._settings.xfyun_tts_api_secret.encode("utf-8"),
                signature_origin.encode("utf-8"),
                hashlib.sha256,
            ).digest()
        ).decode("ascii")
        authorization_origin = (
            f'api_key="{self._settings.xfyun_tts_api_key}", algorithm="hmac-sha256", '
            f'headers="host date request-line", signature="{signature}"'
        )
        query = urlencode({
            "authorization": base64.b64encode(authorization_origin.encode("utf-8")).decode("ascii"),
            "date": date,
            "host": host,
        })
        return f"{self._settings.xfyun_tts_endpoint}?{query}"

    def _connection_target(self) -> tuple[str, dict[str, object]]:
        """Prefer the official APIPassword header when a developer provides it.

        APIPassword is kept in an HTTPS/WSS header, rather than URL query
        parameters that are more likely to end up in access logs. The documented
        HMAC option remains available for existing local configuration.
        """
        if self._settings.xfyun_tts_api_password:
            return self._settings.xfyun_tts_endpoint, {
                "extra_headers": {"x-api-key": self._settings.xfyun_tts_api_password},
            }
        return self._authorized_url(), {}

    def _request(self, text: str) -> dict[str, object]:
        return {
            "common": {"app_id": self._settings.xfyun_tts_app_id},
            "business": {
                "aue": "raw",
                "auf": "audio/L16;rate=16000",
                "tte": "UTF8",
                "vcn": self._settings.xfyun_tts_voice,
                "speed": 50,
                "volume": 50,
                "pitch": 50,
            },
            "data": {
                "status": 2,
                "text": base64.b64encode(text.encode("utf-8")).decode("ascii"),
            },
        }


class FunasrRealtimeAdapter:
    """Bridge browser PCM frames to the local FunASR WebSocket service."""

    def __init__(self, settings: "GatewaySettings", connect_factory=websockets.connect):
        self._settings = settings
        self._connect = connect_factory

    async def stream_transcript(
        self, audio: asyncio.Queue[bytes | None], cancel: asyncio.Event, config: dict[str, object]
    ) -> AsyncIterator[AsrResult]:
        sender: asyncio.Task[object] | None = None
        try:
            async with self._connect(self._settings.funasr_realtime_url, open_timeout=5, close_timeout=3) as socket:
                await socket.send(json.dumps(self._request_config(config)))
                sender = asyncio.create_task(self._send_audio(socket, audio, cancel))
                async for message in socket:
                    if cancel.is_set():
                        return
                    if not isinstance(message, str):
                        continue
                    try:
                        payload = json.loads(message)
                        text = payload.get("text", "")
                        mode = payload.get("mode", "")
                    except (AttributeError, json.JSONDecodeError) as error:
                        raise AsrProviderError("FunASR returned invalid JSON") from error
                    if not isinstance(text, str) or not isinstance(mode, str):
                        raise AsrProviderError("FunASR returned an invalid transcript")
                    # FunASR 2-pass server uses its offline result after the
                    # browser's audio.end marker as the stable final text.
                    is_final = mode.endswith("offline")
                    if text:
                        yield AsrResult(text=text, mode=mode, is_final=is_final)
                    if is_final:
                        return
        except AsrProviderError:
            raise
        except Exception as error:
            raise AsrProviderError(f"FunASR connection failed ({type(error).__name__})") from error
        finally:
            if sender is not None:
                sender.cancel()
                await asyncio.gather(sender, return_exceptions=True)

    async def _send_audio(self, socket: object, audio: asyncio.Queue[bytes | None], cancel: asyncio.Event) -> None:
        while not cancel.is_set():
            frame = await audio.get()
            try:
                if frame is None:
                    await socket.send(json.dumps({"is_speaking": False}))  # type: ignore[attr-defined]
                    return
                await socket.send(frame)  # type: ignore[attr-defined]
            finally:
                audio.task_done()

    @staticmethod
    def _request_config(config: dict[str, object]) -> dict[str, object]:
        chunk_size = config.get("chunk_size", [5, 10, 5])
        if not isinstance(chunk_size, list) or len(chunk_size) != 3 or not all(isinstance(item, int) for item in chunk_size):
            raise AsrProviderError("chunk_size must contain three integers")
        mode = config.get("mode", "2pass")
        if mode not in {"online", "offline", "2pass"}:
            raise AsrProviderError("unsupported FunASR mode")
        chunk_interval = config.get("chunk_interval", 5)
        if not isinstance(chunk_interval, int) or not 1 <= chunk_interval <= 20:
            raise AsrProviderError("chunk_interval must be between 1 and 20")
        return {
            "mode": mode,
            "wav_name": str(config.get("wav_name", "microphone"))[:128],
            "is_speaking": True,
            "chunk_size": chunk_size,
            "chunk_interval": chunk_interval,
            "encoder_chunk_look_back": config.get("encoder_chunk_look_back", 4),
            "decoder_chunk_look_back": config.get("decoder_chunk_look_back", 1),
        }


class FunasrHttpFallbackAdapter:
    """Retry one completed microphone turn through the local HTTP ASR API.

    The realtime server uses VAD before producing a final transcript. On short
    or quiet turns it can legitimately return no final text even though the
    browser sent valid PCM. Keeping the already-authorized PCM in memory for
    the current turn gives us a reliable one-shot fallback without changing
    the browser protocol.
    """

    def __init__(self, settings: "GatewaySettings", transport: httpx.AsyncBaseTransport | None = None):
        self._settings = settings
        self._transport = transport

    async def transcribe_pcm(self, pcm: bytes, cancel: asyncio.Event) -> str:
        if cancel.is_set() or len(pcm) < 3_200:  # less than 100ms at 16k PCM16 mono
            return ""
        wav = self._to_wav(pcm)
        timeout = httpx.Timeout(connect=5.0, read=90.0, write=15.0, pool=5.0)
        try:
            async with httpx.AsyncClient(timeout=timeout, transport=self._transport) as client:
                response = await client.post(
                    self._settings.funasr_http_url,
                    files={"file": ("realtime-fallback.wav", wav, "audio/wav")},
                )
            if response.status_code != 200:
                raise AsrProviderError(f"FunASR fallback failed with HTTP {response.status_code}")
            payload = response.json()
            transcription = payload.get("transcription") if isinstance(payload, dict) else None
            if not isinstance(transcription, list) or not transcription or not isinstance(transcription[0], dict):
                return ""
            text = transcription[0].get("text", "")
            return text.strip() if isinstance(text, str) else ""
        except AsrProviderError:
            raise
        except (httpx.HTTPError, ValueError, TypeError) as error:
            raise AsrProviderError("FunASR fallback connection failed") from error

    @staticmethod
    def _to_wav(pcm: bytes) -> bytes:
        buffer = io.BytesIO()
        with wave.open(buffer, "wb") as wav:
            wav.setnchannels(1)
            wav.setsampwidth(2)
            wav.setframerate(16_000)
            wav.writeframes(pcm)
        return buffer.getvalue()


class SpringKnowledgeRetriever:
    """Retrieve only Spring-authorized, version-valid document chunks.

    The gateway never opens MinIO objects or performs its own permission check.
    Spring Boot derives the permitted document IDs from the authenticated user,
    then returns compact context plus a server-owned citations dictionary.
    """

    def __init__(self, settings: "GatewaySettings", transport: httpx.AsyncBaseTransport | None = None):
        self._settings = settings
        self._transport = transport

    async def retrieve(self, query: str, user_id: int) -> RetrievedKnowledge:
        if not self._settings.internal_key:
            raise KnowledgeRetrievalError("realtime gateway key is not configured")
        timeout = httpx.Timeout(connect=3.0, read=30.0, write=5.0, pool=3.0)
        try:
            async with httpx.AsyncClient(timeout=timeout, transport=self._transport) as client:
                response = await client.post(
                    f"{self._settings.spring_boot_url}/api/knowledge/internal/retrieve",
                    headers={"X-Realtime-Gateway-Key": self._settings.internal_key},
                    json={"userId": user_id, "query": query},
                )
            if response.status_code != 200:
                raise KnowledgeRetrievalError(f"knowledge retrieval failed with HTTP {response.status_code}")
            payload = response.json()
            context = payload.get("context", "")
            citations = payload.get("citations", {})
            if not isinstance(context, str) or not isinstance(citations, dict):
                raise KnowledgeRetrievalError("knowledge retrieval returned an invalid payload")
            safe_citations: dict[str, dict[str, object]] = {}
            for citation_id, citation in citations.items():
                if isinstance(citation_id, str) and isinstance(citation, dict) and _CITATION_ID.fullmatch(citation_id):
                    safe_citations[citation_id] = citation
            return RetrievedKnowledge(context=context, citations=safe_citations)
        except KnowledgeRetrievalError:
            raise
        except (httpx.HTTPError, ValueError, TypeError) as error:
            raise KnowledgeRetrievalError("knowledge retrieval connection failed") from error


@dataclass(frozen=True)
class GatewaySettings:
    spring_boot_url: str
    internal_key: str
    allowed_origins: frozenset[str]
    max_outbound_events: int
    deepseek_api_key: str
    deepseek_model: str
    deepseek_base_url: str
    xfyun_tts_app_id: str
    xfyun_tts_api_key: str
    xfyun_tts_api_secret: str
    xfyun_tts_api_password: str
    xfyun_tts_voice: str
    xfyun_tts_endpoint: str
    funasr_realtime_url: str
    funasr_http_url: str = "http://127.0.0.1:8002/asr"

    @property
    def has_xfyun_tts_credentials(self) -> bool:
        return bool(
            self.xfyun_tts_app_id
            and (self.xfyun_tts_api_password or (self.xfyun_tts_api_key and self.xfyun_tts_api_secret))
        )

    @classmethod
    def from_environment(cls) -> "GatewaySettings":
        origins = os.getenv("REALTIME_ALLOWED_ORIGINS", "http://localhost:8081,http://127.0.0.1:8081")
        return cls(
            spring_boot_url=os.getenv("SPRING_BOOT_URL", "http://127.0.0.1:18080").rstrip("/"),
            internal_key=os.getenv("REALTIME_GATEWAY_INTERNAL_KEY", ""),
            allowed_origins=frozenset(item.strip() for item in origins.split(",") if item.strip()),
            max_outbound_events=int(os.getenv("REALTIME_MAX_OUTBOUND_EVENTS", "128")),
            deepseek_api_key=os.getenv("DEEPSEEK_API_KEY", ""),
            # DeepSeek's current recommended Flash identifier. A developer
            # can override this only through their local environment.
            deepseek_model=os.getenv("DEEPSEEK_MODEL", "deepseek-flash"),
            deepseek_base_url=os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com").rstrip("/"),
            xfyun_tts_app_id=os.getenv("XFYUN_TTS_APP_ID", ""),
            xfyun_tts_api_key=os.getenv("XFYUN_TTS_API_KEY", ""),
            xfyun_tts_api_secret=os.getenv("XFYUN_TTS_API_SECRET", ""),
            xfyun_tts_api_password=os.getenv("XFYUN_TTS_API_PASSWORD", ""),
            xfyun_tts_voice=os.getenv("XFYUN_TTS_VOICE", "x4_xiaoyan"),
            xfyun_tts_endpoint=os.getenv("XFYUN_TTS_ENDPOINT", "wss://tts-api.xfyun.cn/v2/tts"),
            funasr_realtime_url=os.getenv("FUNASR_REALTIME_URL", "ws://127.0.0.1:10095"),
            funasr_http_url=os.getenv("FUNASR_HTTP_URL", "http://127.0.0.1:8002/asr"),
        )


class GatewaySession:
    """Owns event ordering, bounded delivery and cancellation for one socket."""

    def __init__(self, session_id: str, max_outbound_events: int):
        self.events = SessionEventStream(session_id)
        self.outbound: asyncio.Queue[RealtimeEvent] = asyncio.Queue(maxsize=max_outbound_events)
        self.chunkers: dict[str, AdaptiveSemanticChunker] = {}
        self.cancel_signals: dict[str, asyncio.Event] = {}
        self.asr_inputs: dict[str, asyncio.Queue[bytes | None]] = {}
        self.asr_pcm: dict[str, bytearray] = {}
        self.tasks: dict[str, set[asyncio.Task[object]]] = defaultdict(set)
        # Per-WebSocket context only. It is intentionally capped and is never
        # persisted to disk, so a fresh browser session starts a fresh dialog.
        self.conversation_history: list[tuple[str, str]] = []
        self.turn_started_at: dict[str, float] = {}

    async def start_turn(self, turn_id: str) -> None:
        self.turn_started_at[turn_id] = time.monotonic()
        gateway_metrics.started += 1
        event = self.events.start_turn(turn_id)
        self.chunkers[turn_id] = AdaptiveSemanticChunker()
        self.cancel_signals[turn_id] = asyncio.Event()
        self._enqueue(event)

    async def interrupt_turn(self, turn_id: str) -> None:
        # The interruption itself and its terminal outcome are separate
        # protocol facts. Consumers may clear playback on turn.interrupt and
        # must discard every later event after turn.cancelled.
        self.emit(turn_id, "turn.interrupt")
        event = self.events.interrupt(turn_id)
        self.cancel_signals[turn_id].set()
        audio = self.asr_inputs.get(turn_id)
        if audio is not None:
            _offer_audio_end(audio)
        for task in self.tasks.pop(turn_id, set()):
            task.cancel()
        self._enqueue(event)
        gateway_metrics.cancelled += 1

    def register_task(self, turn_id: str, task: asyncio.Task[object]) -> None:
        self.tasks[turn_id].add(task)
        task.add_done_callback(lambda completed: self.tasks[turn_id].discard(completed))

    def emit(self, turn_id: str, event: str, **payload: object) -> None:
        elapsed_ms = round((time.monotonic() - self.turn_started_at.get(turn_id, time.monotonic())) * 1000)
        if event == "llm.first_token":
            gateway_metrics.record_first_token(elapsed_ms)
            payload.setdefault("latencyMs", elapsed_ms)
        elif event == "tts.first_audio":
            gateway_metrics.record_first_audio(elapsed_ms)
            payload.setdefault("latencyMs", elapsed_ms)
        elif event == "turn.completed":
            gateway_metrics.completed += 1
        elif event == "turn.failed":
            gateway_metrics.failed += 1
        elif event == "playback.buffer_underrun":
            gateway_metrics.buffer_underruns += 1
        self._enqueue(self.events.emit(turn_id, event, **payload))

    def open_asr_input(self, turn_id: str) -> asyncio.Queue[bytes | None]:
        queue: asyncio.Queue[bytes | None] = asyncio.Queue(maxsize=64)
        self.asr_inputs[turn_id] = queue
        self.asr_pcm[turn_id] = bytearray()
        return queue

    def append_asr_pcm(self, turn_id: str, chunk: bytes) -> None:
        # Keep at most 20 MB per active turn to avoid unbounded memory use.
        pcm = self.asr_pcm.get(turn_id)
        if pcm is not None and len(pcm) < 20 * 1024 * 1024:
            pcm.extend(chunk[:20 * 1024 * 1024 - len(pcm)])

    def asr_pcm_for(self, turn_id: str) -> bytes:
        return bytes(self.asr_pcm.get(turn_id, b""))

    def prompt_with_history(self, prompt: str) -> str:
        if not self.conversation_history:
            return prompt
        turns = "\n".join(
            f"{role}：{text[:600]}"
            for role, text in self.conversation_history[-12:]
        )
        return f"以下是本次语音会话的最近上下文，请据此延续对话：\n{turns}\n\n当前用户问题：{prompt}"

    def remember_dialogue(self, role: str, text: str) -> None:
        clean = text.strip()
        if not clean:
            return
        self.conversation_history.append((role, clean[:1_200]))
        self.conversation_history = self.conversation_history[-12:]

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


async def consume_ticket(ticket: str, settings: GatewaySettings) -> int | None:
    """Ask Spring Boot to atomically consume a one-time browser ticket."""
    if not ticket or not settings.internal_key:
        return None
    try:
        async with httpx.AsyncClient(timeout=3.0) as client:
            response = await client.post(
                f"{settings.spring_boot_url}/api/realtime/internal/tickets/consume",
                headers={"X-Realtime-Gateway-Key": settings.internal_key},
                json={"ticket": ticket},
            )
        if response.status_code != 200:
            return None
        return int(response.json()["userId"])
    except (httpx.HTTPError, KeyError, TypeError, ValueError):
        return None


class GatewayMetrics:
    """Process-local operational counters; never stores audio, prompts or identities."""
    def __init__(self) -> None:
        self.started = 0
        self.completed = 0
        self.cancelled = 0
        self.failed = 0
        self.buffer_underruns = 0
        self.first_token_samples: list[int] = []
        self.first_audio_samples: list[int] = []

    @staticmethod
    def _average(values: list[int]) -> int | None:
        return round(sum(values) / len(values)) if values else None

    @staticmethod
    def _append(values: list[int], value: int) -> None:
        values.append(value)
        del values[:-200]

    def record_first_token(self, value: int) -> None:
        self._append(self.first_token_samples, value)

    def record_first_audio(self, value: int) -> None:
        self._append(self.first_audio_samples, value)

    def view(self) -> dict[str, object]:
        return {
            "turnsStarted": self.started,
            "turnsCompleted": self.completed,
            "turnsCancelled": self.cancelled,
            "turnsFailed": self.failed,
            "bufferUnderruns": self.buffer_underruns,
            "averageFirstTokenMs": self._average(self.first_token_samples),
            "averageFirstAudioMs": self._average(self.first_audio_samples),
            "sampleWindow": len(self.first_token_samples),
        }


settings = GatewaySettings.from_environment()
gateway_metrics = GatewayMetrics()
app = FastAPI(title="Zhisheng Realtime Gateway", version="0.1.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=list(settings.allowed_origins),
    allow_credentials=False,
    allow_methods=["GET"],
    allow_headers=["*"],
)


@app.get("/realtime/health")
async def health() -> JSONResponse:
    return JSONResponse({
        "status": "UP",
        "capabilities": [
            "ticket-validation", "ordered-events", "backpressure", "turn-cancellation", "llm-streaming",
            "tts-streaming", "asr-to-llm", "rag-citations", "segment-sequences",
            "audio-chunk-sequences", "browser-audioworklet"
        ],
        "llmAdapter": "deepseek" if settings.deepseek_api_key else "not-configured",
        "llmModel": settings.deepseek_model if settings.deepseek_api_key else None,
        "ttsAdapter": "xfyun" if settings.has_xfyun_tts_credentials else "not-configured",
        "ttsVoice": settings.xfyun_tts_voice if settings.has_xfyun_tts_credentials else None,
        "knowledgeRetriever": "spring-faiss" if settings.internal_key else "not-configured",
    })


@app.get("/realtime/metrics")
async def metrics() -> JSONResponse:
    """Aggregate, de-identified data for the system observation UI."""
    return JSONResponse({"status": "UP", "metrics": gateway_metrics.view()})


@app.websocket("/realtime/ws")
async def realtime_socket(websocket: WebSocket) -> None:
    origin = websocket.headers.get("origin")
    if origin not in settings.allowed_origins:
        await websocket.close(code=1008, reason="origin not allowed")
        return

    user_id = await consume_ticket(websocket.query_params.get("ticket", ""), settings)
    if user_id is None:
        await websocket.close(code=1008, reason="invalid ticket")
        return

    await websocket.accept()
    session = GatewaySession(str(uuid4()), settings.max_outbound_events)
    llm = DeepSeekLlmAdapter(settings)
    tts = XfyunTtsStreamingAdapter(settings) if settings.has_xfyun_tts_credentials else None
    asr = FunasrRealtimeAdapter(settings)
    asr_fallback = FunasrHttpFallbackAdapter(settings)
    knowledge = SpringKnowledgeRetriever(settings)
    active_audio_turn: str | None = None
    sender = asyncio.create_task(session.send_loop(websocket))
    try:
        while True:
            frame = await websocket.receive()
            if frame.get("type") == "websocket.disconnect":
                raise WebSocketDisconnect
            binary = frame.get("bytes")
            if binary is not None:
                if active_audio_turn is None:
                    await websocket.close(code=1007, reason="audio received before turn.start")
                    return
                if not session.cancel_signals[active_audio_turn].is_set():
                    try:
                        session.append_asr_pcm(active_audio_turn, binary)
                        session.asr_inputs[active_audio_turn].put_nowait(binary)
                    except asyncio.QueueFull:
                        await websocket.close(code=1013, reason="audio input queue is full")
                        return
                continue
            raw_message = frame.get("text")
            if not isinstance(raw_message, str):
                await websocket.close(code=1007, reason="invalid websocket frame")
                return
            message = json.loads(raw_message)
            if not isinstance(message, dict):
                raise ValueError("realtime event must be an object")
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
                    _start_llm_task(session, llm, tts, knowledge, turn_id, prompt, user_id)
                else:
                    if (
                        active_audio_turn is not None
                        and session.events.state_of(active_audio_turn) == TurnState.ACTIVE
                    ):
                        raise ValueError("only one active audio turn is allowed per session")
                    active_audio_turn = turn_id
                    _start_asr_task(session, asr, asr_fallback, llm, tts, knowledge, turn_id, message.get("asr"), user_id)
            elif event == "llm.request":
                _validate_turn_id(turn_id)
                _start_llm_task(session, llm, tts, knowledge, turn_id, message.get("prompt"), user_id)
            elif event in {"audio.end", "speech.end"}:
                _validate_turn_id(turn_id)
                if active_audio_turn != turn_id:
                    raise ValueError("speech.end does not match the active turn")
                session.emit(turn_id, "speech.end")
                session.emit(turn_id, "endpoint.detected", mode="browser-silence")
                _offer_audio_end(session.asr_inputs[turn_id])
            elif event == "turn.interrupt":
                _validate_turn_id(turn_id)
                await session.interrupt_turn(turn_id)
                if active_audio_turn == turn_id:
                    active_audio_turn = None
            elif event in {"playback.started", "playback.stopped", "playback.buffer_underrun"}:
                # Browser playback telemetry is intentionally best-effort.
                # It is displayed locally and must never revive a completed
                # turn or delay cancellation on a slow client.
                _validate_turn_id(turn_id)
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
    session: GatewaySession, llm: LlmAdapter, tts: TtsStreamingAdapter | None, knowledge: KnowledgeRetriever | None,
    turn_id: str, prompt: object, user_id: int
) -> None:
    if not isinstance(prompt, str) or not prompt.strip() or len(prompt) > 4_000:
        raise ValueError("prompt must be 1 to 4000 characters")
    task = asyncio.create_task(_retrieve_and_stream_llm_turn(
        session, llm, tts, knowledge, turn_id, prompt.strip(), user_id
    ))
    session.register_task(turn_id, task)


def _start_asr_task(
    session: GatewaySession, asr: AsrStreamingAdapter, fallback: AsrFallbackAdapter,
    llm: LlmAdapter, tts: TtsStreamingAdapter | None,
    knowledge: KnowledgeRetriever | None,
    turn_id: str, config: object, user_id: int
) -> None:
    if config is None:
        config = {}
    if not isinstance(config, dict):
        raise ValueError("asr config must be an object")
    audio = session.open_asr_input(turn_id)
    task = asyncio.create_task(_stream_asr_turn(
        session, asr, fallback, llm, tts, knowledge, turn_id, audio, config, user_id
    ))
    session.register_task(turn_id, task)


async def _retrieve_and_stream_llm_turn(
    session: GatewaySession, llm: LlmAdapter, tts: TtsStreamingAdapter | None,
    knowledge: KnowledgeRetriever | None, turn_id: str, prompt: str, user_id: int
) -> None:
    cancel = session.cancel_signals[turn_id]
    retrieved = RetrievedKnowledge(context="", citations={})
    try:
        if knowledge is not None:
            retrieved = await knowledge.retrieve(prompt, user_id)
            if cancel.is_set():
                return
            session.emit(turn_id, "retrieval.completed", citations=retrieved.citations)
            # Retrieval is additive for ordinary conversation.  If there is
            # no relevant authorized document, answer naturally without a
            # citation rather than blocking greetings and general chat.
            # When context exists, the LLM still receives the strict
            # citation instruction in _stream_llm_turn.
        await _stream_llm_turn(session, llm, tts, turn_id, prompt, user_id, retrieved)
    except asyncio.CancelledError:
        raise
    except KnowledgeRetrievalError as error:
        if not cancel.is_set():
            session.emit(turn_id, "turn.failed", code="knowledge_unavailable", message=str(error))


async def _stream_no_citation_answer(
    session: GatewaySession, tts: TtsStreamingAdapter | None, turn_id: str
) -> None:
    """Fail closed when authorization/effective-date filtering finds no source.

    A fluent answer without a server-owned citation would look convincing but
    would violate the knowledge-base contract. This explicit response remains
    speakable while making the lack of reliable source clear to the user.
    """
    cancel = session.cancel_signals[turn_id]
    text = "当前没有检索到仍在有效期内且你有权限查看的可靠资料，因此我无法确认这个问题。"
    queue: asyncio.Queue[tuple[int, str, list[str]] | None] | None = None
    worker: asyncio.Task[object] | None = None
    try:
        if tts is not None:
            queue = asyncio.Queue()
            worker = asyncio.create_task(_stream_tts_segments(session, tts, turn_id, queue))
            session.register_task(turn_id, worker)
        session.emit(turn_id, "llm.started")
        session.emit(turn_id, "llm.first_token")
        session.emit(turn_id, "llm.delta", text=text)
        session.emit(turn_id, "segment.ready", text=text, reason="knowledge-empty", segmentSequence=0, citationIds=[])
        if queue is not None and worker is not None:
            await queue.put((0, text, []))
        session.emit(turn_id, "llm.completed")
        if queue is not None and worker is not None:
            await queue.put(None)
            await worker
        if not cancel.is_set():
            session.remember_dialogue("助手", text)
            session.emit(turn_id, "turn.completed")
    except asyncio.CancelledError:
        raise


async def _stream_llm_turn(
    session: GatewaySession, llm: LlmAdapter, tts: TtsStreamingAdapter | None,
    turn_id: str, prompt: str, user_id: int, retrieved: RetrievedKnowledge | None = None
) -> None:
    """Forward provider deltas and semantic TTS-ready segments in order."""
    cancel = session.cancel_signals[turn_id]
    segment_queue: asyncio.Queue[tuple[int, str, list[str]] | None] | None = None
    tts_worker: asyncio.Task[object] | None = None
    citations = retrieved.citations if retrieved is not None else {}
    provider_prompt = session.prompt_with_history(prompt)
    session.remember_dialogue("用户", prompt)
    assistant_fragments: list[str] = []
    try:
        if tts is not None:
            segment_queue = asyncio.Queue()
            tts_worker = asyncio.create_task(_stream_tts_segments(session, tts, turn_id, segment_queue))
            session.register_task(turn_id, tts_worker)
        session.emit(turn_id, "llm.started")
        segment_index = 0
        first_token = True
        if retrieved is not None and retrieved.context:
            reply = llm.stream_reply(provider_prompt, cancel, user_id=user_id, knowledge_context=retrieved.context)
        else:
            reply = llm.stream_reply(provider_prompt, cancel, user_id=user_id)
        async for delta in reply:
            if cancel.is_set():
                return
            assistant_fragments.append(delta)
            if first_token:
                session.emit(turn_id, "llm.first_token")
                first_token = False
            session.emit(turn_id, "llm.delta", text=delta)
            for segment in session.chunkers[turn_id].feed(delta, int(time.monotonic() * 1000)):
                text, citation_ids = _extract_segment_citations(segment.text, citations)
                if not text:
                    continue
                session.emit(
                    turn_id, "segment.ready", text=text, reason=segment.reason,
                    segmentSequence=segment_index, citationIds=citation_ids,
                )
                if segment_queue is not None:
                    await segment_queue.put((segment_index, text, citation_ids))
                    segment_index += 1
        if cancel.is_set():
            return
        for segment in session.chunkers[turn_id].finish():
            text, citation_ids = _extract_segment_citations(segment.text, citations)
            if not text:
                continue
            session.emit(
                turn_id, "segment.ready", text=text, reason=segment.reason,
                segmentSequence=segment_index, citationIds=citation_ids,
            )
            if segment_queue is not None:
                await segment_queue.put((segment_index, text, citation_ids))
                segment_index += 1
        session.emit(turn_id, "llm.completed")
        if segment_queue is not None and tts_worker is not None:
            await segment_queue.put(None)
            await tts_worker
        if cancel.is_set():
            return
        session.remember_dialogue("助手", "".join(assistant_fragments))
        session.emit(turn_id, "turn.completed")
    except asyncio.CancelledError:
        raise
    except (LlmConfigurationError, LlmProviderError) as error:
        if not cancel.is_set():
            session.emit(turn_id, "turn.failed", code="llm_unavailable", message=str(error))


async def _stream_asr_turn(
    session: GatewaySession, asr: AsrStreamingAdapter, fallback: AsrFallbackAdapter,
    llm: LlmAdapter, tts: TtsStreamingAdapter | None,
    knowledge: KnowledgeRetriever | None, turn_id: str, audio: asyncio.Queue[bytes | None],
    config: dict[str, object], user_id: int
) -> None:
    """Send final FunASR text directly into the LLM for the same turn."""
    cancel = session.cancel_signals[turn_id]
    try:
        session.emit(turn_id, "asr.started")
        async for result in asr.stream_transcript(audio, cancel, config):
            if cancel.is_set():
                return
            session.emit(turn_id, "asr.final" if result.is_final else "asr.partial", text=result.text, mode=result.mode)
            if result.is_final:
                _start_llm_task(session, llm, tts, knowledge, turn_id, result.text, user_id)
                return
        if not cancel.is_set():
            fallback_text = await fallback.transcribe_pcm(session.asr_pcm_for(turn_id), cancel)
            if fallback_text:
                session.emit(turn_id, "asr.final", text=fallback_text, mode="http-fallback")
                _start_llm_task(session, llm, tts, knowledge, turn_id, fallback_text, user_id)
                return
            session.emit(turn_id, "turn.failed", code="asr_empty", message="未识别到有效语音，请说话更久后重试")
    except asyncio.CancelledError:
        raise
    except AsrProviderError as error:
        if not cancel.is_set():
            session.emit(turn_id, "turn.failed", code="asr_unavailable", message=str(error))


async def _stream_tts_segments(
    session: GatewaySession, tts: TtsStreamingAdapter, turn_id: str,
    segment_queue: asyncio.Queue[tuple[int, str, list[str]] | None]
) -> None:
    """Pre-synthesize nearby segments while preserving ordered PCM metadata.

    Keep one following segment in flight while the preceding segment is still
    playing.  This removes the provider connection gap at sentence boundaries.
    Future packets are safe: the browser stores both future PCM and its
    completion marker, then releases them only after every earlier
    ``segmentSequence`` has been drained.
    """
    cancel = session.cancel_signals[turn_id]
    max_parallel_segments = 2
    active_workers: set[asyncio.Task[None]] = set()

    async def synthesize_segment(segment_index: int, text: str, citation_ids: list[str]) -> None:
        first_audio = True
        chunk_sequence = 0
        segment_started = time.monotonic()
        try:
            async for chunk in tts.stream_audio(text, cancel):
                if cancel.is_set():
                    return
                encoded = base64.b64encode(chunk).decode("ascii")
                if first_audio:
                    session.emit(
                        turn_id, "tts.first_audio", segmentIndex=segment_index, segmentSequence=segment_index,
                        codec="pcm_s16le", format="pcm_s16le", sampleRate=16000, channels=1,
                        synthesisMode="streaming", citationIds=citation_ids,
                    )
                    first_audio = False
                session.emit(
                    turn_id, "audio.chunk", segmentIndex=segment_index,
                    segmentSequence=segment_index, chunkSequence=chunk_sequence,
                    codec="pcm_s16le", format="pcm_s16le", sampleRate=16000, channels=1,
                    synthesisMode="streaming", citationIds=citation_ids, audioBase64=encoded,
                )
                chunk_sequence += 1
            if not cancel.is_set():
                session.emit(
                    turn_id, "tts.segment_completed", segmentIndex=segment_index,
                    segmentSequence=segment_index, chunkCount=chunk_sequence,
                    synthesisMode="streaming", citationIds=citation_ids,
                    elapsedMs=round((time.monotonic() - segment_started) * 1000),
                )
        except (TtsConfigurationError, TtsProviderError) as error:
            if not cancel.is_set():
                session.emit(turn_id, "tts.failed", code="tts_unavailable", message=str(error))

    try:
        while True:
            # Bound provider connections.  When both slots are in use, wait
            # for one segment to finish rather than growing unbounded tasks.
            if len(active_workers) >= max_parallel_segments:
                _done, active_workers = await asyncio.wait(
                    active_workers, return_when=asyncio.FIRST_COMPLETED
                )
                continue
            item = await segment_queue.get()
            try:
                if item is None or cancel.is_set():
                    break
                segment_index, text, citation_ids = item
                active_workers.add(asyncio.create_task(synthesize_segment(segment_index, text, citation_ids)))
            finally:
                segment_queue.task_done()
        if active_workers:
            await asyncio.gather(*active_workers)
    except asyncio.CancelledError:
        raise
    finally:
        for worker in active_workers:
            if not worker.done():
                worker.cancel()
        if active_workers:
            await asyncio.gather(*active_workers, return_exceptions=True)


def _offer_audio_end(audio: asyncio.Queue[bytes | None]) -> None:
    """Signal the end exactly once without waiting behind a slow browser."""
    try:
        audio.put_nowait(None)
    except asyncio.QueueFull:
        # The ASR task is cancelled on interrupt; on a normal end it will
        # consume one queued frame and the client may repeat audio.end.
        pass


def _extract_segment_citations(text: str, citations: dict[str, dict[str, object]]) -> tuple[str, list[str]]:
    """Remove model citation tags from spoken text and keep only server-known IDs."""
    matched = [match.group("citation") for match in _CITATION_TAG.finditer(text)]
    citation_ids = list(dict.fromkeys(item for item in matched if item in citations))
    return _CITATION_TAG.sub("", text).strip(), citation_ids

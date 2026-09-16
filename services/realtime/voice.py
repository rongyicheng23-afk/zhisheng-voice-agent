"""Authenticated segmented-speech transport using the existing turn core.

XTTS's HTTP fallback cannot cancel an in-flight inference. Disconnecting stops
consumption and subsequent synthesis; stale PCM never reaches a new turn.
"""
import asyncio
import base64
import io
import json
import os
import time
import wave
from pathlib import Path

import httpx
from fastapi import WebSocket, WebSocketDisconnect

try:
    from .turns import AudioChunk, RealtimeSession
except ImportError:
    from turns import AudioChunk, RealtimeSession


class AudioDelivery:
    """Only one PCM chunk may await acknowledgement from the playback buffer."""
    def __init__(self):
        self.sequence = None
        self.ready = asyncio.Event()
        self.ready.set()

    def arm(self, sequence):
        self.sequence = sequence
        self.ready.clear()

    def ack(self, sequence):
        if type(sequence) is int and sequence == self.sequence:
            self.ready.set()

    async def wait(self):
        await asyncio.wait_for(self.ready.wait(), 30)


class SegmentedTts:
    synthesis_mode = "segmented"

    def __init__(self, url, reference, delivery, transport=None):
        self.url, self.reference, self.delivery = url, reference, delivery
        self.transport = transport

    async def stream(self, text):
        if not text.strip() or len(text) > 500:
            raise ValueError("invalid TTS segment")
        async with httpx.AsyncClient(timeout=90, transport=self.transport) as client:
            async with client.stream("POST", self.url,
                    data={"text": text, "language": "zh-cn", "emotion": "neutral"},
                    files={"audio": ("reference.wav", self.reference, "audio/wav")}) as response:
                response.raise_for_status()
                data = bytearray()
                async for block in response.aiter_bytes():
                    data.extend(block)
                    if len(data) > 16 * 1024 * 1024:
                        raise ValueError("TTS output exceeds limit")
        with wave.open(io.BytesIO(data), "rb") as wav:
            if (wav.getnchannels(), wav.getsampwidth(), wav.getframerate(), wav.getcomptype()) != (1, 2, 24000, "NONE"):
                raise ValueError("expected mono PCM16 WAV at 24000 Hz")
            while True:
                pcm = wav.readframes(2400)
                if not pcm:
                    break
                yield AudioChunk(pcm, sample_rate=24000)
                await self.delivery.wait()


class VoiceLlm:
    def __init__(self, adapter, user_id):
        self.adapter, self.user_id = adapter, user_id

    async def stream(self, prompt):
        cancel = asyncio.Event()
        stream = self.adapter.stream_reply(prompt, cancel, user_id=self.user_id)
        try:
            async for delta in stream:
                yield delta
        finally:
            cancel.set()
            await stream.aclose()


def reference_audio():
    path = os.getenv("REALTIME_TTS_REFERENCE", "")
    if not path:
        return None
    file = Path(path)
    if not file.is_file() or not 44 < file.stat().st_size <= 10 * 1024 * 1024:
        return None
    return file.read_bytes()


def install_voice_route(app, settings, consume_ticket, llm_factory):
    active_users = set()

    @app.websocket("/realtime/voice")
    async def voice_socket(websocket: WebSocket):
        origin = websocket.headers.get("origin")
        if origin not in settings.allowed_origins:
            await websocket.close(code=1008)
            return
        user_id = await consume_ticket(websocket.query_params.get("ticket", ""), origin, settings)
        if user_id is None:
            await websocket.close(code=1008)
            return
        # Per-process admission; use a shared limiter before deploying multiple workers.
        if user_id in active_users or len(active_users) >= 4:
            await websocket.close(code=1013)
            return
        active_users.add(user_id)
        session = None
        try:
            await websocket.accept()
            reference = reference_audio()
            if not settings.deepseek_api_key or reference is None:
                await websocket.send_json({"event": "session.unavailable",
                    "message": "语音回答未配置：需要服务器 DeepSeek 密钥及获授权的 TTS 参考音频"})
                await websocket.close(code=1013)
                return
            delivery = AudioDelivery()

            async def send(event):
                if event["event"] == "audio.chunk":
                    delivery.arm(event["sequence"])
                    event = dict(event, pcm=base64.b64encode(event["pcm"]).decode("ascii"))
                await websocket.send_json(event)

            tts = SegmentedTts(os.getenv("REALTIME_TTS_URL", "http://127.0.0.1:8003/synthesize"),
                    reference, delivery)
            session = RealtimeSession(VoiceLlm(llm_factory(settings), user_id), tts, send)
            await websocket.send_json({"event": "session.ready", "sessionId": session.session_id,
                "sampleRate": 24000, "synthesisMode": "segmented",
                "inferenceCancellation": False})
            started = False
            deadline = time.monotonic() + 600
            while True:
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    raise TimeoutError("session limit")
                raw = await asyncio.wait_for(websocket.receive_text(), min(120, remaining))
                if len(raw) > 16000:
                    raise ValueError("event too large")
                message = json.loads(raw)
                if not isinstance(message, dict):
                    raise ValueError("invalid event")
                event = message.get("event")
                if event == "turn.start" and not started:
                    prompt = message.get("prompt")
                    if not isinstance(prompt, str) or not 1 <= len(prompt.strip()) <= 4000:
                        raise ValueError("invalid prompt")
                    started = True
                    await session.start(prompt)
                elif session.active and message.get("turnId") == session.active["id"]:
                    if event == "turn.interrupt":
                        await session.interrupt(message["turnId"])
                    elif event == "audio.ack":
                        delivery.ack(message.get("sequence"))
                    elif event == "playback.completed":
                        await session.playback_finished(message["turnId"])
                    else:
                        raise ValueError("unsupported event")
                else:
                    raise ValueError("invalid turn")
        except WebSocketDisconnect:
            pass
        except (ValueError, TimeoutError, asyncio.TimeoutError):
            await websocket.close(code=1007)
        except Exception:
            # Never expose provider payloads, tickets, filesystem paths or credentials.
            try:
                await websocket.close(code=1011)
            except Exception:
                pass
        finally:
            try:
                if session:
                    await session.close()
            except Exception:
                pass
            finally:
                active_users.discard(user_id)

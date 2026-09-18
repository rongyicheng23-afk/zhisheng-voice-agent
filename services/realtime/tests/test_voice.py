import asyncio
import base64
import io
import unittest
import wave
from types import SimpleNamespace
from unittest.mock import patch

import httpx
from fastapi import FastAPI
from fastapi.testclient import TestClient
from services.realtime.voice import AudioDelivery, SegmentedTts, install_voice_route


def wav_bytes(rate=24000):
    output = io.BytesIO()
    with wave.open(output, "wb") as wav:
        wav.setnchannels(1); wav.setsampwidth(2); wav.setframerate(rate)
        wav.writeframes(b"\x01\x00" * 4800)
    return output.getvalue()


class TtsTests(unittest.IsolatedAsyncioTestCase):
    async def test_wav_is_decoded_and_delivery_waits_for_correct_ack(self):
        delivery = AudioDelivery()
        adapter = SegmentedTts("http://tts/synthesize", b"reference", delivery,
                httpx.MockTransport(lambda request: httpx.Response(200, content=wav_bytes())))
        stream = adapter.stream("测试文本")
        chunk = await anext(stream)
        self.assertEqual(b"\x01\x00" * 2400, chunk.pcm)
        delivery.arm(12)
        next_chunk = asyncio.create_task(anext(stream))
        await asyncio.sleep(0)
        delivery.ack(11)
        await asyncio.sleep(0)
        self.assertFalse(next_chunk.done())
        delivery.ack(12)
        self.assertEqual(chunk.pcm, (await next_chunk).pcm)
        await stream.aclose()

    async def test_wrong_sample_rate_and_provider_failure_do_not_emit_audio(self):
        for response in (httpx.Response(200, content=wav_bytes(16000)), httpx.Response(503)):
            adapter = SegmentedTts("http://tts/synthesize", b"reference", AudioDelivery(),
                    httpx.MockTransport(lambda request: response))
            with self.assertRaises((ValueError, httpx.HTTPStatusError)):
                await anext(adapter.stream("测试文本"))


class VoiceSocketTests(unittest.TestCase):
    def setUp(self):
        self.closed = False
        owner = self
        class Llm:
            def __init__(self, settings): pass
            async def stream_reply(self, prompt, cancel, user_id=None):
                try:
                    yield "这是第一段用于测试的完整回答。"
                    yield "这是第二段用于测试的完整回答。"
                finally:
                    owner.closed = True
        async def consume(ticket, origin, settings):
            return 7 if ticket == "valid" and origin == "http://localhost:8081" else None
        self.app = FastAPI()
        install_voice_route(self.app,
                SimpleNamespace(allowed_origins={"http://localhost:8081"}, deepseek_api_key="test"),
                consume, Llm)
        self.reference = patch("services.realtime.voice.reference_audio", return_value=wav_bytes())
        self.reference.start()
        transport = httpx.MockTransport(lambda request: httpx.Response(200, content=wav_bytes()))
        original = SegmentedTts.__init__
        def init(adapter, url, reference, delivery):
            original(adapter, url, reference, delivery, transport)
        self.tts = patch.object(SegmentedTts, "__init__", init)
        self.tts.start()

    def tearDown(self):
        self.tts.stop(); self.reference.stop()

    def connect(self, client, ticket="valid", origin="http://localhost:8081"):
        return client.websocket_connect("/realtime/voice?ticket=" + ticket, headers={"origin": origin})

    def test_full_reply_waits_for_playback_and_preserves_order(self):
        with TestClient(self.app) as client, self.connect(client) as ws:
            self.assertEqual("segmented", ws.receive_json()["synthesisMode"])
            ws.send_json({"event": "turn.start", "prompt": "测试"})
            sequence = 0
            audio_count = 0
            while True:
                event = ws.receive_json()
                sequence += 1
                self.assertEqual(sequence, event["sequence"])
                if event["event"] == "audio.chunk":
                    audio_count += 1
                    self.assertEqual(b"\x01\x00" * 2400, base64.b64decode(event["pcm"]))
                    ws.send_json({"event": "audio.ack", "turnId": event["turnId"], "sequence": event["sequence"]})
                if event["event"] == "audio.completed":
                    ws.send_json({"event": "playback.completed", "turnId": event["turnId"]})
                if event["event"] == "turn.completed":
                    break
            self.assertEqual(4, audio_count)
        self.assertTrue(self.closed)

    def test_interrupt_while_waiting_for_buffer_ack_emits_terminal_cancellation(self):
        with TestClient(self.app) as client, self.connect(client) as ws:
            ws.receive_json()
            ws.send_json({"event": "turn.start", "prompt": "测试"})
            while True:
                event = ws.receive_json()
                if event["event"] == "audio.chunk":
                    break
            ws.send_json({"event": "turn.interrupt", "turnId": event["turnId"]})
            while True:
                event = ws.receive_json()
                self.assertNotEqual("audio.chunk", event["event"])
                if event["event"] == "turn.cancelled":
                    break
        self.assertTrue(self.closed)

    def test_invalid_origin_and_invalid_ticket_are_rejected(self):
        from starlette.websockets import WebSocketDisconnect
        with TestClient(self.app) as client:
            for ticket, origin in (("invalid", "http://localhost:8081"), ("valid", "https://evil.example")):
                with self.assertRaises(WebSocketDisconnect):
                    with self.connect(client, ticket, origin):
                        pass

    def test_missing_configuration_is_explicit(self):
        with patch("services.realtime.voice.reference_audio", return_value=None):
            with TestClient(self.app) as client, self.connect(client) as ws:
                self.assertEqual("session.unavailable", ws.receive_json()["event"])

    def test_knowledge_socket_uses_authenticated_user_without_deepseek(self):
        from services.realtime.knowledge import KnowledgeReply
        from services.realtime.tests.test_knowledge import citation
        requests = []
        def search(request):
            requests.append(request)
            return httpx.Response(200, json={'citations': [citation()]})
        original = KnowledgeReply.__init__
        def init(adapter, settings, user_id):
            original(adapter, settings, user_id, httpx.MockTransport(search))
        class ForbiddenModel:
            def __init__(self, settings): pass
            async def stream_reply(self, *args, **kwargs):
                raise AssertionError('knowledge mode must not call DeepSeek')
                yield ''
        async def consume(*args): return 7
        app = FastAPI()
        settings = SimpleNamespace(allowed_origins={'http://localhost:8081'}, deepseek_api_key='',
                                   spring_boot_url='http://business', internal_key='fixture')
        install_voice_route(app, settings, consume, ForbiddenModel)
        with patch.object(KnowledgeReply, '__init__', init), TestClient(app) as client, self.connect(client) as ws:
            self.assertEqual('session.ready', ws.receive_json()['event'])
            ws.send_json({'event': 'turn.start', 'prompt': '报名材料', 'answerMode': 'knowledge', 'userId': 999})
            sources = []
            while True:
                event = ws.receive_json()
                if event['event'] == 'turn.sources': sources = event['citations']
                if event['event'] == 'audio.chunk':
                    self.assertTrue(sources)
                    ws.send_json({'event': 'audio.ack', 'turnId': event['turnId'], 'sequence': event['sequence']})
                if event['event'] == 'audio.completed': ws.send_json({'event': 'playback.completed', 'turnId': event['turnId']})
                if event['event'] in ('turn.failed', 'turn.completed'):
                    self.assertEqual('turn.completed', event['event'])
                    break
        import json
        self.assertEqual(7, json.loads(requests[0].content)['userId'])

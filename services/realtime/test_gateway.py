import asyncio
import base64
import json
import unittest
from contextlib import asynccontextmanager
from uuid import uuid4

import httpx

from gateway import (
    AsrResult,
    DeepSeekLlmAdapter,
    FunasrHttpFallbackAdapter,
    FunasrRealtimeAdapter,
    GatewaySession,
    GatewaySettings,
    RetrievedKnowledge,
    SpringKnowledgeRetriever,
    _extract_segment_citations,
    _retrieve_and_stream_llm_turn,
    _stream_llm_turn,
    _stream_asr_turn,
    XfyunTtsStreamingAdapter,
)


class EmptyFallback:
    async def transcribe_pcm(self, _pcm, _cancel):
        return ""


class GatewaySessionTests(unittest.IsolatedAsyncioTestCase):
    async def test_start_turn_enqueues_an_ordered_event(self):
        session = GatewaySession(str(uuid4()), max_outbound_events=2)
        turn_id = str(uuid4())

        await session.start_turn(turn_id)
        event = session.outbound.get_nowait()

        self.assertEqual("turn.start", event.event)
        self.assertEqual(turn_id, event.turn_id)
        self.assertIn(turn_id, session.chunkers)

    async def test_interrupt_cancels_registered_work_and_enqueues_terminal_event(self):
        session = GatewaySession(str(uuid4()), max_outbound_events=2)
        turn_id = str(uuid4())
        await session.start_turn(turn_id)
        session.outbound.get_nowait()

        async def waits_for_cancellation():
            await asyncio.sleep(60)

        task = asyncio.create_task(waits_for_cancellation())
        session.register_task(turn_id, task)
        await session.interrupt_turn(turn_id)

        with self.assertRaises(asyncio.CancelledError):
            await task
        self.assertTrue(session.cancel_signals[turn_id].is_set())
        self.assertEqual("turn.interrupt", session.outbound.get_nowait().event)
        self.assertEqual("turn.cancelled", session.outbound.get_nowait().event)

    async def test_interrupt_cancels_asr_llm_and_tts_tasks_in_one_turn(self):
        """One turn owns every upstream task, so one interrupt stops all of them."""
        session = GatewaySession(str(uuid4()), max_outbound_events=16)
        turn_id = str(uuid4())
        await session.start_turn(turn_id)
        session.outbound.get_nowait()

        asr_started = asyncio.Event()
        llm_started = asyncio.Event()
        tts_started = asyncio.Event()
        asr_cancelled = asyncio.Event()
        llm_cancelled = asyncio.Event()
        tts_cancelled = asyncio.Event()

        class BlockingAsr:
            async def stream_transcript(self, _audio, _cancel, _config):
                asr_started.set()
                try:
                    await asyncio.Future()
                except asyncio.CancelledError:
                    asr_cancelled.set()
                    raise
                if False:  # pragma: no cover - marks this as an async iterator.
                    yield AsrResult("", "", False)

        class BlockingLlm:
            async def stream_reply(self, _prompt, _cancel, *, user_id=None):
                llm_started.set()
                yield "这是一个正在生成的完整回答。"
                try:
                    await asyncio.Future()
                except asyncio.CancelledError:
                    llm_cancelled.set()
                    raise

        class BlockingTts:
            async def stream_audio(self, _text, _cancel):
                tts_started.set()
                try:
                    await asyncio.Future()
                except asyncio.CancelledError:
                    tts_cancelled.set()
                    raise
                if False:  # pragma: no cover - marks this as an async iterator.
                    yield b""

        asr_audio = session.open_asr_input(turn_id)
        asr_task = asyncio.create_task(
            _stream_asr_turn(
                session, BlockingAsr(), EmptyFallback(), BlockingLlm(), BlockingTts(), None, turn_id, asr_audio, {}, 7
            )
        )
        session.register_task(turn_id, asr_task)
        llm_task = asyncio.create_task(
            _stream_llm_turn(session, BlockingLlm(), BlockingTts(), turn_id, "测试", 7)
        )
        session.register_task(turn_id, llm_task)

        await asyncio.wait_for(asr_started.wait(), timeout=1)
        await asyncio.wait_for(llm_started.wait(), timeout=1)
        await asyncio.wait_for(tts_started.wait(), timeout=1)
        await session.interrupt_turn(turn_id)

        await asyncio.gather(asr_task, llm_task, return_exceptions=True)
        self.assertTrue(asr_cancelled.is_set())
        self.assertTrue(llm_cancelled.is_set())
        self.assertTrue(tts_cancelled.is_set())
        self.assertTrue(session.cancel_signals[turn_id].is_set())
        events = []
        while not session.outbound.empty():
            events.append(session.outbound.get_nowait().event)
        self.assertEqual("turn.cancelled", events[-1])


class FunasrRealtimeAdapterTests(unittest.IsolatedAsyncioTestCase):
    def _settings(self) -> GatewaySettings:
        return GatewaySettings(
            spring_boot_url="http://127.0.0.1:18080",
            internal_key="test-key",
            allowed_origins=frozenset({"http://localhost:8081"}),
            max_outbound_events=16,
            deepseek_api_key="",
            deepseek_model="deepseek-flash",
            deepseek_base_url="https://api.deepseek.com",
            xfyun_tts_app_id="",
            xfyun_tts_api_key="",
            xfyun_tts_api_secret="",
            xfyun_tts_api_password="",
            xfyun_tts_voice="x4_xiaoyan",
            xfyun_tts_endpoint="wss://tts-api.xfyun.cn/v2/tts",
            funasr_realtime_url="ws://127.0.0.1:10095",
        )

    async def test_forwards_pcm_and_marks_offline_result_as_final(self):
        sent: list[object] = []

        class FakeSocket:
            async def send(self, value: object) -> None:
                sent.append(value)

            def __aiter__(self):
                return self

            async def __anext__(self):
                if hasattr(self, "done"):
                    raise StopAsyncIteration
                self.done = True
                await asyncio.sleep(0)
                return json.dumps({"mode": "2pass-offline", "text": "识别完成"})

        @asynccontextmanager
        async def connect(_url: str, **_kwargs: object):
            yield FakeSocket()

        audio: asyncio.Queue[bytes | None] = asyncio.Queue()
        audio.put_nowait(b"pcm-frame")
        audio.put_nowait(None)
        adapter = FunasrRealtimeAdapter(self._settings(), connect)
        results = [result async for result in adapter.stream_transcript(audio, asyncio.Event(), {})]

        self.assertEqual([AsrResult("识别完成", "2pass-offline", True)], results)
        self.assertEqual("2pass", json.loads(sent[0])["mode"])
        self.assertIn(b"pcm-frame", sent)
        self.assertIn(json.dumps({"is_speaking": False}), sent)


class AsrToLlmFlowTests(unittest.IsolatedAsyncioTestCase):
    async def test_streaming_tts_events_have_contiguous_segment_and_chunk_sequences(self):
        session = GatewaySession(str(uuid4()), max_outbound_events=64)
        turn_id = str(uuid4())
        await session.start_turn(turn_id)
        session.outbound.get_nowait()

        class FakeLlm:
            async def stream_reply(self, _prompt, _cancel, *, user_id=None):
                yield "第一段文本用于音频顺序测试"

        class FakeTts:
            async def stream_audio(self, _text, _cancel):
                yield b"\x00\x00"
                yield b"\x01\x00"

        await _stream_llm_turn(session, FakeLlm(), FakeTts(), turn_id, "测试", 7)
        events = []
        while not session.outbound.empty():
            events.append(session.outbound.get_nowait())
        chunks = [event for event in events if event.event == "audio.chunk"]
        self.assertEqual([0, 1], [event.payload["chunkSequence"] for event in chunks])
        self.assertTrue(all(event.payload["segmentSequence"] == 0 for event in chunks))
        self.assertTrue(all(event.payload["codec"] == "pcm_s16le" for event in chunks))
        self.assertTrue(all(event.payload["synthesisMode"] == "streaming" for event in chunks))
        self.assertEqual(2, next(event for event in events if event.event == "tts.segment_completed").payload["chunkCount"])

    async def test_final_asr_text_starts_llm_for_the_same_turn(self):
        session = GatewaySession(str(uuid4()), max_outbound_events=32)
        turn_id = str(uuid4())
        await session.start_turn(turn_id)
        session.outbound.get_nowait()

        class FakeAsr:
            async def stream_transcript(self, _audio, _cancel, _config):
                yield AsrResult("你好", "2pass-online", False)
                yield AsrResult("请介绍这个平台。", "2pass-offline", True)

        prompts: list[str] = []

        class FakeLlm:
            async def stream_reply(self, prompt, _cancel, *, user_id=None):
                prompts.append(prompt)
                yield "这是语音交互平台。"

        audio: asyncio.Queue[bytes | None] = asyncio.Queue()
        await _stream_asr_turn(session, FakeAsr(), EmptyFallback(), FakeLlm(), None, None, turn_id, audio, {}, 7)
        pending = list(session.tasks[turn_id])
        await asyncio.gather(*pending)

        events = []
        while not session.outbound.empty():
            events.append(session.outbound.get_nowait().event)
        self.assertEqual("请介绍这个平台。", prompts[0])
        self.assertEqual(
            ["asr.started", "asr.partial", "asr.final", "llm.started", "llm.first_token", "llm.delta", "segment.ready", "llm.completed", "turn.completed"],
            events,
        )

    async def test_empty_realtime_result_uses_http_pcm_fallback(self):
        session = GatewaySession(str(uuid4()), max_outbound_events=32)
        turn_id = str(uuid4())
        await session.start_turn(turn_id)
        session.outbound.get_nowait()
        session.open_asr_input(turn_id)
        session.append_asr_pcm(turn_id, b"\x00\x00" * 2_000)
        test_case = self

        class EmptyAsr:
            async def stream_transcript(self, _audio, _cancel, _config):
                if False:
                    yield AsrResult("", "", False)

        class Fallback:
            async def transcribe_pcm(self, pcm, _cancel):
                test_case.assertEqual(4_000, len(pcm))
                return "后备识别成功"

        class FakeLlm:
            async def stream_reply(self, _prompt, _cancel, *, user_id=None):
                yield "收到"

        audio = session.asr_inputs[turn_id]
        await _stream_asr_turn(session, EmptyAsr(), Fallback(), FakeLlm(), None, None, turn_id, audio, {}, 7)
        await asyncio.gather(*session.tasks[turn_id])
        events = []
        while not session.outbound.empty():
            events.append(session.outbound.get_nowait().event)
        self.assertIn("asr.final", events)
        self.assertIn("turn.completed", events)

    async def test_retrieval_context_is_sent_to_llm_and_citations_follow_segments(self):
        session = GatewaySession(str(uuid4()), max_outbound_events=32)
        turn_id = str(uuid4())
        await session.start_turn(turn_id)
        session.outbound.get_nowait()

        class FakeRetriever:
            async def retrieve(self, _query, _user_id):
                return RetrievedKnowledge(
                    context="[C001] 企业命题要求可中断。",
                    citations={"C001": {"title": "方案", "chunkId": 1}},
                )

        captured_context: list[str] = []

        class FakeLlm:
            async def stream_reply(self, _prompt, _cancel, *, user_id=None, knowledge_context=""):
                captured_context.append(knowledge_context)
                yield "系统必须支持打断。【C001】"

        await _retrieve_and_stream_llm_turn(session, FakeLlm(), None, FakeRetriever(), turn_id, "命题要求", 7)
        events = []
        while not session.outbound.empty():
            events.append(session.outbound.get_nowait())

        segment = next(event for event in events if event.event == "segment.ready")
        self.assertEqual("[C001] 企业命题要求可中断。", captured_context[0])
        self.assertEqual(["C001"], segment.payload["citationIds"])
        self.assertEqual("系统必须支持打断。", segment.payload["text"])
        self.assertEqual(0, segment.payload["segmentSequence"])
        self.assertEqual("retrieval.completed", events[0].event)

    async def test_empty_authorized_knowledge_falls_back_to_general_llm_chat(self):
        session = GatewaySession(str(uuid4()), max_outbound_events=32)
        turn_id = str(uuid4())
        await session.start_turn(turn_id)
        session.outbound.get_nowait()

        class EmptyRetriever:
            async def retrieve(self, _query, _user_id):
                return RetrievedKnowledge(context="", citations={})

        captured_context: list[str] = []

        class GeneralChatLlm:
            async def stream_reply(self, _prompt, _cancel, *, user_id=None, knowledge_context=""):
                captured_context.append(knowledge_context)
                yield "你好，我是智能语音助手。"

        await _retrieve_and_stream_llm_turn(session, GeneralChatLlm(), None, EmptyRetriever(), turn_id, "你好", 7)
        events = []
        while not session.outbound.empty():
            events.append(session.outbound.get_nowait())
        segment = next(event for event in events if event.event == "segment.ready")
        self.assertEqual([], segment.payload["citationIds"])
        self.assertEqual("你好，我是智能语音助手。", segment.payload["text"])
        self.assertEqual([""], captured_context)

    def test_unknown_or_malformed_citation_tag_is_not_forwarded(self):
        text, citation_ids = _extract_segment_citations("资料【C001】和【C999】", {"C001": {}})

        self.assertEqual("资料和", text)
        self.assertEqual(["C001"], citation_ids)


class SpringKnowledgeRetrieverTests(unittest.IsolatedAsyncioTestCase):
    def _settings(self) -> GatewaySettings:
        return GatewaySettings(
            spring_boot_url="http://127.0.0.1:18080",
            internal_key="test-key",
            allowed_origins=frozenset({"http://localhost:8081"}),
            max_outbound_events=16,
            deepseek_api_key="",
            deepseek_model="deepseek-flash",
            deepseek_base_url="https://api.deepseek.com",
            xfyun_tts_app_id="",
            xfyun_tts_api_key="",
            xfyun_tts_api_secret="",
            xfyun_tts_api_password="",
            xfyun_tts_voice="x4_xiaoyan",
            xfyun_tts_endpoint="wss://tts-api.xfyun.cn/v2/tts",
            funasr_realtime_url="ws://127.0.0.1:10095",
        )

    async def test_uses_spring_internal_endpoint_and_keeps_only_valid_citations(self):
        async def handler(request: httpx.Request) -> httpx.Response:
            self.assertEqual("test-key", request.headers["X-Realtime-Gateway-Key"])
            self.assertEqual({"userId": 7, "query": "测试问题"}, json.loads(request.content))
            return httpx.Response(200, json={
                "context": "[C001] 已授权资料",
                "citations": {"C001": {"title": "资料"}, "invalid": {"title": "忽略"}},
            })

        retriever = SpringKnowledgeRetriever(self._settings(), httpx.MockTransport(handler))
        result = await retriever.retrieve("测试问题", 7)

        self.assertEqual("[C001] 已授权资料", result.context)
        self.assertEqual({"C001": {"title": "资料"}}, result.citations)


class DeepSeekLlmAdapterTests(unittest.IsolatedAsyncioTestCase):
    def _settings(self) -> GatewaySettings:
        return GatewaySettings(
            spring_boot_url="http://127.0.0.1:18080",
            internal_key="test-key",
            allowed_origins=frozenset({"http://localhost:8081"}),
            max_outbound_events=2,
            deepseek_api_key="test-api-key",
            deepseek_model="deepseek-flash",
            deepseek_base_url="https://api.deepseek.com",
            xfyun_tts_app_id="test-app-id",
            xfyun_tts_api_key="test-tts-key",
            xfyun_tts_api_secret="test-tts-secret",
            xfyun_tts_api_password="",
            xfyun_tts_voice="x4_xiaoyan",
            xfyun_tts_endpoint="wss://tts-api.xfyun.cn/v2/tts",
            funasr_realtime_url="ws://127.0.0.1:10095",
        )

    async def test_stream_reply_extracts_content_deltas_and_uses_non_thinking_mode(self):
        request_body: dict[str, object] = {}

        async def handler(request: httpx.Request) -> httpx.Response:
            nonlocal request_body
            request_body = json.loads(request.content)
            return httpx.Response(
                200,
                headers={"content-type": "text/event-stream"},
                content=(
                    'data: {"choices":[{"delta":{"content":"你好"}}]}\n\n'
                    'data: {"choices":[{"delta":{"content":"，欢迎使用。"}}]}\n\n'
                    "data: [DONE]\n\n"
                ).encode("utf-8"),
            )

        adapter = DeepSeekLlmAdapter(self._settings(), httpx.MockTransport(handler))
        chunks = [chunk async for chunk in adapter.stream_reply("测试", asyncio.Event(), user_id=7)]

        self.assertEqual(["你好", "，欢迎使用。"], chunks)
        self.assertEqual("deepseek-flash", request_body["model"])
        self.assertEqual({"type": "disabled"}, request_body["thinking"])
        self.assertTrue(request_body["stream"])


class XfyunTtsStreamingAdapterTests(unittest.IsolatedAsyncioTestCase):
    def _settings(self) -> GatewaySettings:
        return GatewaySettings(
            spring_boot_url="http://127.0.0.1:18080",
            internal_key="test-key",
            allowed_origins=frozenset({"http://localhost:8081"}),
            max_outbound_events=2,
            deepseek_api_key="",
            deepseek_model="deepseek-flash",
            deepseek_base_url="https://api.deepseek.com",
            xfyun_tts_app_id="test-app-id",
            xfyun_tts_api_key="test-tts-key",
            xfyun_tts_api_secret="test-tts-secret",
            xfyun_tts_api_password="",
            xfyun_tts_voice="x4_xiaoyan",
            xfyun_tts_endpoint="wss://tts-api.xfyun.cn/v2/tts",
            funasr_realtime_url="ws://127.0.0.1:10095",
        )

    async def test_stream_audio_decodes_pcm_and_sends_the_expected_request(self):
        sent: list[str] = []
        pcm = b"pcm-test-data"

        class FakeSocket:
            async def send(self, value: str) -> None:
                sent.append(value)

            def __aiter__(self):
                return self

            async def __anext__(self):
                if hasattr(self, "done"):
                    raise StopAsyncIteration
                self.done = True
                return json.dumps({
                    "code": 0,
                    "data": {"audio": base64.b64encode(pcm).decode("ascii"), "status": 2},
                })

        @asynccontextmanager
        async def connect(_url: str, **_kwargs: object):
            yield FakeSocket()

        adapter = XfyunTtsStreamingAdapter(self._settings(), connect)
        chunks = [chunk async for chunk in adapter.stream_audio("你好", asyncio.Event())]

        request = json.loads(sent[0])
        self.assertEqual([pcm], chunks)
        self.assertEqual("x4_xiaoyan", request["business"]["vcn"])
        self.assertEqual("raw", request["business"]["aue"])
        self.assertEqual("你好", base64.b64decode(request["data"]["text"]).decode("utf-8"))

    def test_authorized_url_does_not_contain_the_api_secret(self):
        url = XfyunTtsStreamingAdapter(self._settings())._authorized_url()

        self.assertIn("authorization=", url)
        self.assertNotIn("test-tts-secret", url)

    def test_api_password_uses_a_header_instead_of_a_signed_url(self):
        settings = self._settings()
        settings = GatewaySettings(
            **{**settings.__dict__, "xfyun_tts_api_password": "test-api-password"}
        )
        url, options = XfyunTtsStreamingAdapter(settings)._connection_target()

        self.assertEqual("wss://tts-api.xfyun.cn/v2/tts", url)
        self.assertEqual({"x-api-key": "test-api-password"}, options["extra_headers"])

    async def test_connection_error_reports_only_safe_error_type(self):
        @asynccontextmanager
        async def connect(_url: str, **_kwargs: object):
            raise OSError("the signed URL must not be displayed")
            yield  # pragma: no cover - keeps this an async context manager.

        adapter = XfyunTtsStreamingAdapter(self._settings(), connect)
        with self.assertRaisesRegex(Exception, r"connection failed \(OSError\)"):
            _ = [chunk async for chunk in adapter.stream_audio("你好", asyncio.Event())]

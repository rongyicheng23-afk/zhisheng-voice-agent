import asyncio
import json
import unittest
from uuid import uuid4

import httpx

from gateway import DeepSeekLlmAdapter, GatewaySession, GatewaySettings


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
        self.assertEqual("turn.cancelled", session.outbound.get_nowait().event)


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

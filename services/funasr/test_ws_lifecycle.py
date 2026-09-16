"""Exercise the actual websocket handler without loading production models.

Only ws_serve's AST is compiled; model helpers and transport are mocked.
This checks protocol ordering and failure handling, not recognition accuracy.
"""
import ast
import json
from pathlib import Path
from types import SimpleNamespace
import unittest
from unittest.mock import AsyncMock


class Socket:
    def __init__(self, messages):
        self.messages = iter(messages)
        self.sent = []

    def __aiter__(self):
        return self

    async def __anext__(self):
        try:
            return next(self.messages)
        except StopIteration:
            raise StopAsyncIteration

    async def send(self, payload):
        self.sent.append(json.loads(payload))


class LifecycleTests(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        source = ast.parse(Path(__file__).with_name('funasr_wss_server.py').read_text())
        handler = next(n for n in source.body if isinstance(n, ast.AsyncFunctionDef) and n.name == 'ws_serve')
        self.scope = dict(json=json, websocket_users=set(),
                          websockets=SimpleNamespace(ConnectionClosed=type('Closed', (Exception,), {}),
                                                     InvalidState=type('Invalid', (Exception,), {})),
                          async_vad=AsyncMock(return_value=(0, -1)),
                          async_asr=AsyncMock(), async_asr_online=AsyncMock(), ws_reset=AsyncMock())
        exec(compile(ast.Module(body=[handler], type_ignores=[]), '<production ws_serve>', 'exec'), self.scope)

    async def run_socket(self, mode='2pass', duplicate=False):
        config = json.dumps(dict(is_speaking=True, mode=mode, chunk_size=[5, 10, 5], chunk_interval=10))
        stop = json.dumps(dict(is_speaking=False))
        socket = Socket([config, b'\x00' * 3200, stop] + ([stop] if duplicate else []))
        await self.scope['ws_serve'](socket, '/')
        self.assertNotIn(socket, self.scope['websocket_users'])
        return socket

    async def test_final_text_precedes_single_completion(self):
        async def recognize(socket, audio):
            self.assertTrue(audio)
            await socket.send(json.dumps(dict(text='测试', mode='2pass-offline')))
        self.scope['async_asr'].side_effect = recognize
        socket = await self.run_socket(duplicate=True)
        self.assertEqual(socket.sent, [dict(text='测试', mode='2pass-offline'), dict(event='asr.completed')])
        self.scope['async_asr'].assert_awaited_once()

    async def test_offline_failure_never_completes(self):
        self.scope['async_asr'].side_effect = RuntimeError('private provider detail')
        socket = await self.run_socket()
        self.assertEqual(socket.sent, [dict(event='asr.failed', code='ASR_PROCESSING_FAILED')])

    async def test_vad_failure_never_completes(self):
        self.scope['async_vad'].side_effect = RuntimeError('private audio detail')
        socket = await self.run_socket()
        self.assertEqual(socket.sent, [dict(event='asr.failed', code='ASR_PROCESSING_FAILED')])
        self.scope['async_asr'].assert_not_awaited()

    async def test_online_tail_flushed_before_completion(self):
        async def recognize(socket, audio):
            self.assertEqual(audio, b'\x00' * 3200)
            self.assertTrue(socket.status_dict_asr_online['is_final'])
            await socket.send(json.dumps(dict(text='尾音', mode='online')))
        self.scope['async_asr_online'].side_effect = recognize
        socket = await self.run_socket(mode='online')
        self.assertEqual(socket.sent[-1], dict(event='asr.completed'))
        self.assertEqual(socket.sent[0]['text'], '尾音')
        self.scope['async_asr_online'].assert_awaited_once()

    async def test_online_failure_never_completes(self):
        self.scope['async_asr_online'].side_effect = RuntimeError('private detail')
        socket = await self.run_socket(mode='online')
        self.assertEqual(socket.sent, [dict(event='asr.failed', code='ASR_PROCESSING_FAILED')])

    async def test_normal_disconnect_releases_user(self):
        socket = Socket([])
        await self.scope['ws_serve'](socket, '/')
        self.assertFalse(self.scope['websocket_users'])
        self.assertEqual(socket.sent, [])


if __name__ == '__main__':
    unittest.main()

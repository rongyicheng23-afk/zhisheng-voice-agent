import asyncio
import json
import unittest
from types import SimpleNamespace

import httpx

from services.realtime.grounded import GroundedKnowledgeReply
from services.realtime.tests.test_knowledge import citation
from services.realtime.turns import RealtimeSession, AudioChunk


def answer():
    return {'status': 'answered', 'claims': [
        {'text': '需要提交学生证。', 'evidence': [
            {'sourceId': 'd:1:0', 'quote': '需要提交学生证'}]}]}


class GroundedTests(unittest.IsolatedAsyncioTestCase):
    def reply(self, payload=None, sources=None, model=None):
        self.calls = []
        calls = self.calls
        class Model:
            async def stream_reply(self, prompt, cancel, **kwargs):
                calls.append((json.loads(prompt), cancel, kwargs))
                raw = payload if isinstance(payload, str) else json.dumps(payload or answer(), ensure_ascii=False)
                for part in (raw[:10], raw[10:]):
                    yield part
        settings = SimpleNamespace(spring_boot_url='http://business', internal_key='test')
        transport = httpx.MockTransport(lambda _: httpx.Response(200, json={
            'citations': [citation()] if sources is None else sources}))
        return GroundedKnowledgeReply(settings, 7, model or Model(), transport)

    async def test_validated_claims_and_private_retrieval_reach_spoken_spans(self):
        reply = self.reply()
        sources = await reply.prepare('报名')
        self.assertEqual([citation()], sources)
        self.assertEqual(answer()['claims'], reply.claims)
        self.assertEqual(['d:1:0'], reply.citation_ids(reply.text))
        request, cancel, kwargs = self.calls[0]
        self.assertEqual('报名', request['question'])
        self.assertEqual([citation()], request['sources'])
        self.assertEqual(7, kwargs['user_id'])
        self.assertTrue(kwargs['json_mode'])
        self.assertIn('JSON', kwargs['system_prompt'])
        self.assertTrue(cancel.is_set())

    async def test_no_sources_never_calls_model(self):
        reply = self.reply(sources=[])
        self.assertEqual([], await reply.prepare('问题'))
        self.assertEqual([], self.calls)
        self.assertEqual([], reply.claims)
        self.assertIn('无法据此确认', reply.text)

    async def test_insufficient_refuses_and_keeps_sources_for_manual_review(self):
        reply = self.reply({'status': 'insufficient', 'claims': []})
        self.assertEqual([citation()], await reply.prepare('问题'))
        self.assertIn('不足', reply.text)
        self.assertEqual([], reply.citation_ids(reply.text))

    async def test_invalid_output_never_becomes_speech(self):
        malformed = ['not json', '{"status":', '[]', 'null', '{}', 'x' * 16001,
                     json.dumps({'status': 'answered', 'claims': []})]
        for field, value in [('text', ''), ('text', 'x' * 401), ('evidence', []),
                             ('evidence', [{'sourceId': 'unknown', 'quote': '学生证'}]),
                             ('evidence', [{'sourceId': 'd:1:0', 'quote': '身份证'}]),
                             ('evidence', [{'sourceId': 'd:1:0', 'quote': ' '}]),
                             ('evidence', [{'sourceId': [], 'quote': '学生证'}])]:
            payload = answer()
            payload['claims'][0][field] = value
            malformed.append(json.dumps(payload))
        duplicate = answer()
        duplicate['claims'][0]['evidence'] *= 2
        malformed.append(json.dumps(duplicate))
        for raw in malformed:
            with self.subTest(raw=raw[:80]):
                reply = self.reply(raw)
                with self.assertRaises(ValueError):
                    await reply.prepare('问题')
                self.assertEqual('', reply.text)
                self.assertEqual([], reply.claims)

    async def test_invalid_reference_fails_core_without_sources_text_or_audio(self):
        bad = answer()
        bad['claims'][0]['evidence'][0]['quote'] = '虚构原文'
        events = []
        class Tts:
            synthesis_mode = 'segmented'
            async def stream(self, text):
                raise AssertionError('invalid evidence must never reach TTS')
                yield AudioChunk(b'\x00\x00')
        async def send(event): events.append(event)
        session = RealtimeSession(self.reply(bad), Tts(), send)
        await session.start('问题')
        await asyncio.wait_for(session.active['task'], 2)
        self.assertEqual(['turn.started', 'turn.failed'], [event['event'] for event in events])
        await session.close()

    async def test_timeout_closes_model(self):
        closed = asyncio.Event()
        class Model:
            async def stream_reply(self, *args, **kwargs):
                try:
                    await asyncio.Event().wait()
                    yield ''
                finally:
                    closed.set()
        reply = self.reply(model=Model())
        reply.timeout_seconds = .01
        with self.assertRaises(asyncio.TimeoutError):
            await reply.prepare('问题')
        self.assertTrue(closed.is_set())
        self.assertEqual('', reply.text)

    async def test_interrupt_during_model_discards_partial_json_and_closes_stream(self):
        entered, closed = asyncio.Event(), asyncio.Event()
        class Model:
            async def stream_reply(self, *args, **kwargs):
                try:
                    yield '{"status":'
                    entered.set()
                    await asyncio.Event().wait()
                finally:
                    closed.set()
        class Tts:
            synthesis_mode = 'segmented'
        events = []
        async def send(event): events.append(event)
        session = RealtimeSession(self.reply(model=Model()), Tts(), send)
        await session.start('问题')
        await asyncio.wait_for(entered.wait(), 1)
        await session.interrupt(session.active['id'])
        self.assertTrue(closed.is_set())
        self.assertEqual(['turn.started', 'turn.cancelled'], [e['event'] for e in events])
        await session.close()

    async def test_core_publishes_claims_before_text_and_audio(self):
        events = []
        class Tts:
            synthesis_mode = 'segmented'
            async def stream(self, text): yield AudioChunk(b'\x00\x00')
        session = None
        async def send(event):
            events.append(event)
            if event['event'] == 'audio.completed':
                await session.playback_finished(event['turnId'])
        session = RealtimeSession(self.reply(), Tts(), send)
        await session.start('报名')
        await asyncio.wait_for(session.active['task'], 2)
        names = [event['event'] for event in events]
        self.assertLess(names.index('turn.sources'), names.index('llm.delta'))
        self.assertLess(names.index('turn.sources'), names.index('audio.chunk'))
        sources = next(e for e in events if e['event'] == 'turn.sources')
        self.assertEqual('grounded', sources['answerMode'])
        self.assertEqual(answer()['claims'], sources['claims'])
        self.assertEqual('turn.completed', names[-1])
        await session.close()

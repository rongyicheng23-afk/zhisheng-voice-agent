import asyncio
import unittest
from types import SimpleNamespace
import httpx
from services.realtime.knowledge import KnowledgeReply
from services.realtime.turns import RealtimeSession, AudioChunk


def citation():
    return dict(id='d:1:0', documentId='d', title='测试资料', quote='报名材料需要提交学生证。',
                sourceVersion='v1', publisher='测试单位', sourceUrl='https://example.org',
                validFrom='2026-09-01', validUntil='2026-10-01', paragraph=1)


class KnowledgeTests(unittest.IsolatedAsyncioTestCase):
    def adapter(self, payload, code=200):
        self.requests = []
        def serve(request):
            self.requests.append(request)
            return httpx.Response(code, json=payload)
        return KnowledgeReply(SimpleNamespace(spring_boot_url='http://business', internal_key='fixture-secret-unique'), 7, httpx.MockTransport(serve))

    async def test_private_user_is_from_server_context_and_internal_key_in_header(self):
        reply = self.adapter(dict(citations=[citation()]))
        await reply.prepare('报名材料')
        import json
        self.assertEqual(7, json.loads(self.requests[0].content)['userId'])
        self.assertEqual('fixture-secret-unique', self.requests[0].headers['X-Realtime-Gateway-Key'])
        self.assertNotIn('fixture-secret-unique', str(self.requests[0].url))
        self.assertIn(citation()['quote'], reply.text)

    async def test_no_evidence_refuses_without_model_call(self):
        reply = self.adapter(dict(citations=[]))
        self.assertEqual([], await reply.prepare('火星'))
        self.assertIn('无法据此确认', reply.text)
        self.assertEqual([], reply.citation_ids(reply.text))

    async def test_failed_retrieval_does_not_fall_back_to_general_answer(self):
        reply = self.adapter({}, 503)
        with self.assertRaises(httpx.HTTPStatusError): await reply.prepare('问题')
        self.assertEqual('', reply.text)

    async def test_malformed_or_duplicate_sources_are_rejected(self):
        for sources in (None, [{}], [citation(), citation()], [citation()] * 4):
            with self.subTest(sources=sources), self.assertRaises(ValueError):
                await self.adapter(dict(citations=sources)).prepare('问题')

    async def test_source_mapping_follows_exact_spoken_spans(self):
        reply = self.adapter(dict(citations=[citation()]))
        await reply.prepare('报名材料')
        start = reply.spans[0][0]
        self.assertEqual([], reply.citation_ids(reply.text[:start]))
        self.assertEqual(['d:1:0'], reply.citation_ids(reply.text[start:]))
        with self.assertRaises(ValueError): reply.citation_ids('fabricated')

    async def test_full_core_emits_sources_before_speech_and_only_registered_ids(self):
        events = []
        class Tts:
            synthesis_mode = 'segmented'
            async def stream(self, text): yield AudioChunk(b'\x00\x00')
        session = None
        async def send(event):
            events.append(event)
            if event['event'] == 'audio.completed':
                await session.playback_finished(event['turnId'])
        session = RealtimeSession(self.adapter(dict(citations=[citation()])), Tts(), send)
        await session.start('报名材料')
        await asyncio.wait_for(session.active['task'], 2)
        names = [e['event'] for e in events]
        self.assertLess(names.index('turn.sources'), names.index('audio.chunk'))
        self.assertEqual('turn.completed', names[-1])
        segments = [e for e in events if e['event'] == 'segment.ready']
        self.assertTrue(any(e['citationIds'] for e in segments))
        self.assertTrue(all(set(e['citationIds']) <= {'d:1:0'} for e in segments))

    async def test_interrupt_during_retrieval_produces_no_late_source_or_audio(self):
        entered = asyncio.Event()
        class Waiting:
            async def prepare(self, prompt):
                entered.set()
                await asyncio.Event().wait()
        class Tts: synthesis_mode = 'segmented'
        events = []
        async def send(event): events.append(event)
        session = RealtimeSession(Waiting(), Tts(), send)
        turn = await session.start('问题')
        await entered.wait()
        await session.interrupt(turn)
        self.assertEqual(['turn.started', 'turn.cancelled'], [e['event'] for e in events])

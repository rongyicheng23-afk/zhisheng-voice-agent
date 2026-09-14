import asyncio
import unittest

from services.realtime.chunking import SemanticChunker
from services.realtime.turns import AudioChunk, RealtimeSession


class ChunkingTests(unittest.TestCase):
    def test_incremental_text_is_lossless(self):
        text = '报名材料包括：1. 身份证明；2. 项目说明。预算为3.14万元（含税，须复核）。'
        for step in (1, 2, 7, len(text)):
            chunker = SemanticChunker(min_chars=5, max_chars=30)
            segments = []
            for index in range(0, len(text), step):
                segments += chunker.feed(text[index:index + step])
            segments += chunker.finish()
            self.assertEqual(''.join(s.text for s in segments), text)
            self.assertFalse(any(s.text.endswith('3.') for s in segments))

    def test_brackets_not_split(self):
        chunker = SemanticChunker(min_chars=2)
        self.assertEqual(chunker.feed('说明（第一项。第二项'), [])
        segments = chunker.feed('）已经完成。')
        self.assertEqual(len(segments), 1)

    def test_timeout_without_next_delta(self):
        now = [0]
        chunker = SemanticChunker(min_chars=3, clock=lambda: now[0])
        self.assertEqual(chunker.feed('第一项，第二项'), [])
        now[0] = 1
        self.assertEqual(chunker.poll()[0].reason, 'max_wait')

    def test_bad_input_is_bounded_and_observable(self):
        chunker = SemanticChunker(min_chars=3, max_chars=10)
        segments = chunker.feed('（' + '字' * 1000)
        self.assertLessEqual(len(chunker.buffer), 40)
        self.assertTrue(all(s.reason == 'forced_buffer_limit' for s in segments))

    def test_cancel_clears_tail(self):
        chunker = SemanticChunker()
        chunker.feed('未完成')
        chunker.cancel()
        self.assertEqual(chunker.finish(), [])

    def test_grouped_numbers_and_time(self):
        chunker = SemanticChunker(min_chars=2, max_chars=15)
        text = '金额100,000元，截止12:30。'
        segments = []
        for char in text:
            segments.extend(chunker.feed(char))
        segments.extend(chunker.finish())
        self.assertEqual(''.join(s.text for s in segments), text)
        self.assertFalse(any(s.text.endswith(('100,', '12:')) for s in segments))

    def test_ascii_quotes_close_before_emit(self):
        chunker = SemanticChunker(min_chars=2)
        self.assertEqual(chunker.feed('他说"不能。'), [])
        self.assertEqual(chunker.feed('"')[0].text, '他说"不能。"')


# Fixtures deliberately use synthetic PCM; these are not demonstration models.
class LlmFixture:
    closed = False

    async def stream(self, prompt):
        try:
            yield '这是用于单元测试的第一段完整文本。'
            yield '这是用于单元测试的第二段完整文本。'
        finally:
            self.closed = True


class TtsFixture:
    synthesis_mode = 'segmented'
    closed = False

    async def stream(self, text):
        try:
            yield AudioChunk(b'\x00\x00' * 128)
        finally:
            self.closed = True


class TurnTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.events = []

        async def send(event):
            self.events.append(event)

        self.session = RealtimeSession(LlmFixture(), TtsFixture(), send)

    async def asyncTearDown(self):
        await self.session.close()

    async def wait_event(self, name):
        for _ in range(200):
            if any(e['event'] == name for e in self.events):
                return
            await asyncio.sleep(0.005)
        self.fail('Missing event: ' + name)

    async def test_completion_waits_for_playback_and_events_ordered(self):
        turn = await self.session.start('test')
        await self.wait_event('audio.completed')
        self.assertFalse(any(e['event'] == 'turn.completed' for e in self.events))
        await self.session.playback_finished(turn)
        await self.wait_event('turn.completed')
        self.assertEqual([e['sequence'] for e in self.events], list(range(1, len(self.events) + 1)))
        audio = [e for e in self.events if e['event'] == 'audio.chunk']
        self.assertEqual([e['segmentSequence'] for e in audio], [1, 2])
        self.assertTrue(all(e['synthesisMode'] == 'segmented' for e in audio))

    async def test_cancel_once_and_drop_late_events(self):
        turn_id = await self.session.start('test')
        await self.wait_event('audio.completed')
        turn = self.session.active
        await self.session.interrupt(turn_id)
        await self.session.interrupt(turn_id)
        before = len(self.events)
        self.assertFalse(await self.session._emit(turn, 'llm.delta', text='late'))
        self.assertEqual(len(self.events), before)
        self.assertEqual(sum(e['event'] == 'turn.cancelled' for e in self.events), 1)
        self.assertTrue(self.session.llm.closed)

    async def test_replacement_cancels_old_before_new(self):
        old = await self.session.start('old')
        new = await self.session.start('new')
        await self.session.interrupt(old)
        self.assertEqual(self.session.active['id'], new)
        names = [(e['turnId'], e['event']) for e in self.events]
        self.assertLess(names.index((old, 'turn.cancelled')), names.index((new, 'turn.started')))

    async def test_invalid_audio_fails_without_provider_details(self):
        class InvalidTts(TtsFixture):
            async def stream(self, text):
                yield AudioChunk(b'odd')
        self.session.tts = InvalidTts()
        await self.session.start('test')
        await self.wait_event('turn.failed')
        self.assertEqual(self.events[-1]['code'], 'TURN_PROCESSING_FAILED')
        self.assertFalse(any(e['event'] == 'audio.chunk' for e in self.events))

    async def test_empty_model_output_fails_without_waiting_for_playback(self):
        class EmptyLlm:
            async def stream(self, prompt):
                if False: yield ''
        self.session.llm = EmptyLlm()
        await self.session.start('test')
        await self.wait_event('turn.failed')
        self.assertFalse(any(e['event'] == 'audio.completed' for e in self.events))

    async def test_rate_changes_are_rejected_before_bad_chunk_sent(self):
        class ChangedRate(TtsFixture):
            async def stream(self, text):
                yield AudioChunk(b'\0\0', sample_rate=24000)
                yield AudioChunk(b'\0\0', sample_rate=16000)
        self.session.tts = ChangedRate()
        await self.session.start('test')
        await self.wait_event('turn.failed')
        self.assertEqual(len([e for e in self.events if e['event'] == 'audio.chunk']), 1)

    async def test_total_model_text_is_bounded(self):
        class LongLlm:
            async def stream(self, prompt):
                yield 'x' * 32001
        self.session.llm = LongLlm()
        await self.session.start('test')
        await self.wait_event('turn.failed')

    async def test_interrupt_while_model_waits(self):
        closed = asyncio.Event()
        class WaitingLlm:
            async def stream(self, prompt):
                try:
                    await asyncio.sleep(100)
                    yield 'unreachable'
                finally:
                    closed.set()
        self.session.llm = WaitingLlm()
        turn = await self.session.start('test')
        await asyncio.sleep(0.02)
        await self.session.interrupt(turn)
        await asyncio.wait_for(closed.wait(), 1)
        self.assertEqual(self.events[-1]['event'], 'turn.cancelled')

    async def test_playback_timeout_is_failure(self):
        self.session.playback_timeout = 0.01
        await self.session.start('test')
        await self.wait_event('turn.failed')

    async def test_cancellation_closes_tts_stream(self):
        closed = asyncio.Event()
        started = asyncio.Event()
        class WaitingTts(TtsFixture):
            async def stream(self, text):
                try:
                    started.set()
                    await asyncio.sleep(100)
                    yield AudioChunk(b'\0\0')
                finally:
                    closed.set()
        self.session.tts = WaitingTts()
        turn = await self.session.start('test')
        await asyncio.wait_for(started.wait(), 1)
        await self.session.interrupt(turn)
        await asyncio.wait_for(closed.wait(), 1)
        self.assertFalse(any(e['event'] == 'audio.chunk' for e in self.events))


if __name__ == '__main__':
    unittest.main()

"""P0 orchestration core. Adapters must own/close their upstream streams.

No credentials, mock answers, HTTP authentication or public endpoint live here.
The authenticated gateway supplies adapters. Evidence adapters may prepare
authorised sources and map exact spoken text spans to citation IDs.
"""
import asyncio
from dataclasses import dataclass
from datetime import datetime, timezone
import uuid

try:
    from .chunking import SemanticChunker
except ImportError:
    from chunking import SemanticChunker


@dataclass(frozen=True)
class AudioChunk:
    pcm: bytes
    sample_rate: int = 24000
    channels: int = 1


def _observe_task(task):
    # Cancellation can race a completed anext() (StopAsyncIteration). Always
    # retrieve its exception; the normal await/result still propagates failures.
    if not task.cancelled():
        task.exception()


class RealtimeSession:
    def __init__(self, llm, tts, send, *, queue_size=4, send_timeout=2, playback_timeout=120):
        if queue_size < 1 or tts.synthesis_mode not in ('streaming', 'segmented'):
            raise ValueError('invalid adapter configuration')
        self.llm, self.tts, self.send = llm, tts, send
        self.queue_size, self.send_timeout = queue_size, send_timeout
        self.playback_timeout = playback_timeout
        self.session_id = uuid.uuid4().hex
        self.sequence = 0
        self.active = None
        self._control = asyncio.Lock()
        self._events = asyncio.Lock()
        self._pending = set()

    async def _emit(self, turn, event, **payload):
        async with self._events:
            if self.active is not turn or turn['state'] != 'ACTIVE':
                return False
            self.sequence += 1
            await asyncio.wait_for(self.send(dict(
                sessionId=self.session_id, turnId=turn['id'], event=event,
                sequence=self.sequence, timestamp=datetime.now(timezone.utc).isoformat(),
                **payload)), self.send_timeout)
            return True

    async def start(self, prompt, *, llm=None):
        if not isinstance(prompt, str) or not prompt.strip() or len(prompt) > 8000:
            raise ValueError('prompt must contain 1–8000 characters')
        async with self._control:
            await self._interrupt()
            self._pending = {task for task in self._pending if not task.done()}
            if self._pending:
                raise RuntimeError('previous adapter has not released its tasks')
            if llm is not None:
                self.llm = llm
            turn = dict(id=uuid.uuid4().hex, state='ACTIVE', playback=asyncio.Event(), audio_done=False)
            self.active = turn
            await self._emit(turn, 'turn.started', synthesisMode=self.tts.synthesis_mode)
            turn['task'] = asyncio.create_task(self._run(turn, prompt))
            turn['task'].add_done_callback(_observe_task)
            return turn['id']

    async def interrupt(self, turn_id):
        async with self._control:
            if self.active and self.active['id'] == turn_id:
                await self._interrupt()

    async def close(self):
        async with self._control:
            await self._interrupt()

    async def playback_finished(self, turn_id):
        turn = self.active
        if turn and turn['id'] == turn_id and turn['audio_done'] and turn['state'] == 'ACTIVE':
            turn['playback'].set()

    async def _terminal(self, turn, event, **payload):
        # Serialize the terminal event and the state transition with all writes.
        async with self._events:
            if self.active is not turn or turn['state'] in ('COMPLETED', 'CANCELLED', 'FAILED'):
                return
            turn['state'] = {'turn.completed': 'COMPLETED', 'turn.cancelled': 'CANCELLED', 'turn.failed': 'FAILED'}[event]
            self.sequence += 1
            await asyncio.wait_for(self.send(dict(
                sessionId=self.session_id, turnId=turn['id'], event=event,
                sequence=self.sequence, timestamp=datetime.now(timezone.utc).isoformat(), **payload)), self.send_timeout)

    async def _interrupt(self):
        turn = self.active
        if not turn or turn['state'] != 'ACTIVE':
            return
        async with self._events:
            turn['state'] = 'CANCELLING'
        task = turn.get('task')
        if task and not task.done():
            task.cancel()
            _, pending = await asyncio.wait({task}, timeout=1)
            self._pending.update(pending)
        await self._terminal(turn, 'turn.cancelled', cleanupPending=any(not t.done() for t in self._pending))

    async def _run(self, turn, prompt):
        queue = asyncio.Queue(maxsize=self.queue_size)
        chunker = SemanticChunker()
        llm, tts = self.llm, self.tts

        async def produce():
            prepare = getattr(llm, 'prepare', None)
            if prepare:
                citations = await prepare(prompt)
                await self._emit(turn, 'turn.sources', citations=citations, answerMode='extractive')
            iterator = llm.stream(prompt).__aiter__()
            next_token = None
            first = True
            generated_chars = 0
            try:
                while True:
                    if next_token is None:
                        next_token = asyncio.create_task(anext(iterator))
                        next_token.add_done_callback(_observe_task)
                    done, _ = await asyncio.wait({next_token}, timeout=0.05)
                    if not done:
                        for segment in chunker.poll():
                            await queue.put(segment)
                        continue
                    try:
                        delta = next_token.result()
                    except StopAsyncIteration:
                        break
                    next_token = None
                    if not isinstance(delta, str) or len(delta) > 32768:
                        raise ValueError('invalid model delta')
                    generated_chars += len(delta)
                    if generated_chars > 32000:
                        raise ValueError('turn text limit exceeded')
                    if first and delta:
                        await self._emit(turn, 'llm.first_token')
                        first = False
                    await self._emit(turn, 'llm.delta', text=delta)
                    for segment in chunker.feed(delta):
                        await queue.put(segment)
                for segment in chunker.finish():
                    await queue.put(segment)
                await queue.put(None)
            finally:
                chunker.cancel()
                if next_token and not next_token.done():
                    next_token.cancel()
                    _, pending = await asyncio.wait({next_token}, timeout=0.25)
                    self._pending.update(pending)
                if not next_token or next_token.done():
                    close = getattr(iterator, 'aclose', None)
                    if close:
                        await close()

        async def consume():
            segment_sequence = 0
            sample_rate = None
            while True:
                segment = await queue.get()
                if segment is None:
                    break
                segment_sequence += 1
                citation_ids = getattr(llm, 'citation_ids', lambda text: [])(segment.text)
                await self._emit(turn, 'segment.ready', segmentSequence=segment_sequence,
                                 text=segment.text, boundaryReason=segment.reason, citationIds=citation_ids)
                chunk_sequence = 0
                stream = tts.stream(segment.text)
                try:
                    async for chunk in stream:
                        if (not isinstance(chunk, AudioChunk) or chunk.channels != 1
                                or not isinstance(chunk.pcm, bytes)
                                or chunk.sample_rate not in (16000, 22050, 24000, 44100, 48000)
                                or not chunk.pcm or len(chunk.pcm) % 2 or len(chunk.pcm) > 262144):
                            raise ValueError('expected bounded mono PCM16 audio')
                        if sample_rate is not None and sample_rate != chunk.sample_rate:
                            raise ValueError('sample rate changed within turn')
                        sample_rate = chunk.sample_rate
                        chunk_sequence += 1
                        if chunk_sequence == 1:
                            await self._emit(turn, 'tts.first_audio', segmentSequence=segment_sequence,
                                             synthesisMode=tts.synthesis_mode)
                        await self._emit(turn, 'audio.chunk', segmentSequence=segment_sequence,
                                         chunkSequence=chunk_sequence, codec='pcm16', channels=1,
                                         sampleRate=chunk.sample_rate, synthesisMode=tts.synthesis_mode,
                                         pcm=chunk.pcm)
                finally:
                    close = getattr(stream, 'aclose', None)
                    if close:
                        await close()
                if not chunk_sequence:
                    raise ValueError('TTS returned no audio')
                await self._emit(turn, 'tts.segment_completed', segmentSequence=segment_sequence,
                                 synthesisMode=tts.synthesis_mode)
            if not segment_sequence:
                raise ValueError('model returned no text')
            turn['audio_done'] = True
            await self._emit(turn, 'audio.completed')

        children = [asyncio.create_task(produce()), asyncio.create_task(consume())]
        failure = False
        try:
            await asyncio.gather(*children)
            await asyncio.wait_for(turn['playback'].wait(), self.playback_timeout)
        except asyncio.CancelledError:
            raise
        except Exception:
            failure = True
        finally:
            for task in children:
                if not task.done():
                    task.cancel()
            _, pending = await asyncio.wait(children, timeout=0.5)
            self._pending.update(pending)
            # Retrieve errors without exposing provider payloads or credentials.
            for task in children:
                if task.done() and not task.cancelled():
                    task.exception()
            chunker.cancel()
        if turn['state'] == 'ACTIVE':
            await self._terminal(turn, 'turn.failed' if failure else 'turn.completed',
                                 **({'code': 'TURN_PROCESSING_FAILED'} if failure else {}))

#!/usr/bin/env python3
"""Measure a real local XTTS stream with an explicitly supplied, authorized voice sample.

Reports timings and PCM length only. Never prints or saves the reference or text.
"""
import argparse
import asyncio
import json
import sys
import time
from pathlib import Path


async def verify(url, reference, text):
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
    from services.realtime.voice import AudioDelivery, StreamingTts

    path = Path(reference)
    if not path.is_file() or not 44 < path.stat().st_size <= 10 * 1024 * 1024:
        raise ValueError('reference must be a WAV file between 45 bytes and 10 MB')
    adapter = StreamingTts(url, path.read_bytes(), AudioDelivery())
    started = time.perf_counter()
    first_ms = None
    chunks = samples = 0
    async for audio in adapter.stream(text):
        if first_ms is None:
            first_ms = round((time.perf_counter() - started) * 1000)
        chunks += 1
        samples += len(audio.pcm) // 2
    completed_ms = round((time.perf_counter() - started) * 1000)
    return {'synthesisMode': 'streaming', 'firstChunkMs': first_ms,
            'segmentCompletedMs': completed_ms, 'chunks': chunks,
            'audioDurationMs': round(samples / 24),
            'firstChunkBeforeCompletion': chunks > 1 and first_ms < completed_ms}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Verify local framed PCM16 streaming TTS')
    parser.add_argument('--reference', required=True, help='path to an authorized WAV voice sample')
    parser.add_argument('--url', default='http://127.0.0.1:8003/synthesize-stream')
    parser.add_argument('--text', default='欢迎使用智能语音交互系统。')
    args = parser.parse_args()
    print(json.dumps(asyncio.run(verify(args.url, args.reference, args.text)), ensure_ascii=False))

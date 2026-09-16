"""Manual local smoke test for the iFlytek streaming-TTS adapter.

Run this only from a terminal where XFYUN_TTS_APP_ID, XFYUN_TTS_API_KEY and
XFYUN_TTS_API_SECRET are already set. It neither writes an audio file nor
prints credentials; one run consumes one iFlytek service call.
"""

from __future__ import annotations

import asyncio
import sys

from gateway import GatewaySettings, TtsConfigurationError, TtsProviderError, XfyunTtsStreamingAdapter


async def main() -> int:
    settings = GatewaySettings.from_environment()
    adapter = XfyunTtsStreamingAdapter(settings)
    cancel = asyncio.Event()
    chunk_count = 0
    byte_count = 0
    try:
        async for chunk in adapter.stream_audio("你好，这是讯飞流式语音合成测试。", cancel):
            chunk_count += 1
            byte_count += len(chunk)
    except (TtsConfigurationError, TtsProviderError) as error:
        print(f"TTS_TEST_FAILED: {error}")
        return 1

    if not byte_count:
        print("TTS_TEST_FAILED: iFlytek returned no PCM audio")
        return 1
    print(f"TTS_TEST_OK: pcmChunks={chunk_count}, pcmBytes={byte_count}, voice={settings.xfyun_tts_voice}")
    return 0


if __name__ == "__main__":
    raise SystemExit(asyncio.run(main()))

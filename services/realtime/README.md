# Realtime Gateway

The gateway is the Python `asyncio + FastAPI + WebSocket` service described in
the solution. It provides secure ticket validation, ordered events, bounded
outbound delivery, cancellation, a DeepSeek streaming LLM adapter and an
iFlytek PCM streaming-TTS adapter.

## Local start

Use one identical, high-entropy value for the Spring Boot service and gateway:

```bash
export REALTIME_GATEWAY_INTERNAL_KEY='replace-with-a-long-random-local-secret'
export SPRING_BOOT_URL='http://127.0.0.1:18080'
export DEEPSEEK_API_KEY='keep-your-key-local-and-never-commit-it'
export DEEPSEEK_MODEL='deepseek-flash'
export XFYUN_TTS_APP_ID='keep-your-app-id-local'
export XFYUN_TTS_API_KEY='keep-your-api-key-local'
export XFYUN_TTS_API_SECRET='keep-your-api-secret-local'
# Optional and preferred for local development: create an APIPassword in the
# iFlytek console. When set, it is sent in a WSS header instead of a URL query.
export XFYUN_TTS_API_PASSWORD='keep-your-api-password-local'
export XFYUN_TTS_VOICE='x4_xiaoyan'
# This is the existing local FunASR real-time WebSocket service.
export FUNASR_REALTIME_URL='ws://127.0.0.1:10095'
cd "/Users/frank/Desktop/voice /services/realtime"
../../.venv-models/bin/uvicorn gateway:app --host 127.0.0.1 --port 18081
```

Start Spring Boot with the same `REALTIME_GATEWAY_INTERNAL_KEY`. Its ticket
endpoint must be running before a browser can connect to `ws://127.0.0.1:18081/realtime/ws?ticket=...`.

The gateway reads the API key only when it starts. Its health endpoint will show
`"llmAdapter":"deepseek"`, `"llmModel":"deepseek-flash"` and
`"ttsAdapter":"xfyun"` after the corresponding credentials are configured:

```bash
curl http://127.0.0.1:18081/realtime/health
```

The browser sends `turn.start` with a UUID `turnId` and an optional `prompt`,
or sends `llm.request` after a turn has started. To use the full voice path,
send `turn.start` without `prompt`, then send PCM audio as binary WebSocket
frames and finish with `{ "event": "audio.end", "turnId": "..." }`.
The gateway sends those frames to FunASR. Its final ASR text is automatically
used as the DeepSeek prompt **for the same turnId**; the browser does not need
to send a second `llm.request`. For every DeepSeek SSE text delta the gateway emits an ordered `llm.delta` event. It also emits
`tts.segment_ready` events for the existing semantic segmenter. Each completed
segment is submitted to iFlytek in order; its PCM output is emitted as
`tts.first_audio`, `audio.chunk`, and `tts.segment_completed` events. The
browser must decode and play the `audioBase64` PCM chunks with AudioWorklet;
that browser-playback layer remains separate from this gateway.

When the local RAG service is available, the gateway asks Spring Boot to
retrieve sources before each LLM request. Spring filters the logged-in user's
published, non-expired documents before returning context. The gateway emits
`retrieval.completed` with the session `citations` dictionary, while each
`tts.segment_ready`, `tts.first_audio`, and `audio.chunk` has only matching
`citationIds`. Tags such as `【C001】` are removed before TTS, so the source is
visible in the page but is never spoken aloud.

At any point the browser can send `{ "event": "turn.interrupt", "turnId":
"..." }`. The gateway immediately emits the terminal `turn.cancelled` event,
closes the active FunASR work, cancels the DeepSeek request and active iFlytek
TTS work, and rejects later events for that turn. The browser should also clear
its own queued audio when it receives `turn.cancelled`.

## Local TTS smoke test

With the three `XFYUN_TTS_*` credentials exported in the current terminal,
run the following once to verify that iFlytek returns PCM data. It prints only
the number of chunks and bytes, writes no audio file, and consumes one service
call:

```bash
../../.venv-models/bin/python smoke_tts.py
```

For production, place this service behind Nginx or an API gateway and expose it
as WSS only. Do not expose the Spring internal ticket-consumption endpoint to
the public internet.

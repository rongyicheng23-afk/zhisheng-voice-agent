# Realtime Gateway

The gateway is the Python `asyncio + FastAPI + WebSocket` service described in
the solution. It provides secure ticket validation, ordered events, bounded
outbound delivery, cancellation and a DeepSeek streaming LLM adapter. Streaming
TTS on the legacy text endpoint remains unconfigured. `/realtime/voice`
connects the existing turn core to XTTS and the browser PCM player. The voice
path supports segmented synthesis by default and an opt-in XTTS streaming route.

## Local start

Use one identical, high-entropy value for the Spring Boot service and gateway:

```bash
export REALTIME_GATEWAY_INTERNAL_KEY='replace-with-a-long-random-local-secret'
export SPRING_BOOT_URL='http://127.0.0.1:18080'
export DEEPSEEK_API_KEY='keep-your-key-local-and-never-commit-it'
export DEEPSEEK_MODEL='deepseek-v4-flash'
export DEEPSEEK_MAX_TOKENS='1024'
cd services/realtime
python -m uvicorn gateway:app --host 127.0.0.1 --port 18081
```

Start Spring Boot with the same `REALTIME_GATEWAY_INTERNAL_KEY`. Its ticket
endpoint must be running before a browser can connect to `ws://127.0.0.1:18081/realtime/ws?ticket=...`.

The gateway reads the API key only when it starts. Its health endpoint will show
`"llmAdapter":"deepseek"` and `"llmModel":"deepseek-v4-flash"` after a key is
configured:

```bash
curl http://127.0.0.1:18081/realtime/health
```

The browser sends `turn.start` with a UUID `turnId` and an optional `prompt`,
or sends `llm.request` after a turn has started. For every DeepSeek SSE text
delta the gateway emits an ordered `llm.delta` event. It also emits
`tts.segment_ready` events for the existing semantic segmenter, but it does
not synthesize audio until a streaming TTS adapter is connected.

## Voice replies (segmented and streaming XTTS)

The realtime recognition page now contains a voice reply panel. After recognition
finishes, use its result as the prompt, or type a question and click Send.
Auto reply is an explicit page-local opt-in and fires only after the recognition
completion event (not connection failure/timeout). Starting another recording or
clicking Interrupt stops playback and cancels consumption of the previous reply.
This is push-to-talk interruption, not continuous microphone/VAD barge-in.

Configure these variables in the shell used to start services:

- `REALTIME_GATEWAY_INTERNAL_KEY`: the same secret in Spring Boot and Python.
- `DEEPSEEK_API_KEY`: your own server-side API key; never put it in frontend env.
- `REALTIME_TTS_REFERENCE`: absolute path of a reference recording you are authorized to use.
- `REALTIME_TTS_URL`: optional trusted internal TTS URL, default `http://127.0.0.1:8003/synthesize`.
- `REALTIME_TTS_MODE`: `segmented` (default) or `streaming`.
- `REALTIME_TTS_STREAM_URL`: trusted internal stream URL, default `http://127.0.0.1:8003/synthesize-stream`.
- `REALTIME_PYTHON`: Python executable with dependencies from this folder's requirements.

Start the existing project services, then from the repository root run:

```bash
bash scripts/start-realtime-gateway.sh
```

The existing one-click launcher still starts ASR, XTTS, Java and Vue; it does not
automatically start this optional gateway. If Java was started without the
internal key, restart it from a shell that exports that key. Both processes must
inherit it. Stop the gateway using Ctrl+C in its terminal.

Local frontend defaults to port 18081. In production proxy `/realtime/voice` to
the gateway with WebSocket upgrade and WSS, or set the frontend
`VUE_APP_REALTIME_GATEWAY_URL` base URL at build time. Both gateway and Java must
allow the frontend Origin in `REALTIME_ALLOWED_ORIGINS`.

The protocol starts with `session.ready`, then accepts up to 20 `turn.start`
requests per connection. The same `sessionId` and monotonically increasing
event sequence span turns; each turn gets a new `turnId`. A new question interrupts
an active old turn. The browser reuses its connection and AudioContext until the
user stops, leaves the page, changes login token or the connection closes. It emits
`turn.started`, `llm.delta`, `segment.ready`, `audio.chunk`, `audio.completed`
and terminal states. PCM is base64, mono PCM16 at 24 kHz, at most 100 ms per chunk.
The client sends `audio.ack` with the received event sequence only when playback
buffer occupancy permits the next chunk; the server allows one unacknowledged
chunk. Completion waits for browser `playback.completed`.

The UI shows first-text, first TTS chunk and AudioWorklet playback-start times measured
from question submission, plus player buffer and underflow counts. They are local
measurements, not a measurement of sound-card output or a benchmark claim. In `segmented` mode XTTS completes one
semantic segment before its PCM is sent. In `streaming` mode the backend calls
the installed XTTS `inference_stream` generator, converts each model output to
24 kHz mono PCM16 and sends length-prefixed frames (up to 100 ms each) followed
by a zero-length completion frame. The gateway rejects malformed frames, wrong
mode/rate, truncated responses and bytes after completion. It only reports a
completed segment when the completion frame arrives. A partial failed stream
causes `turn.failed`; earlier audio may already have played.

Before starting the normal project launcher, opt in to model preload in the
launcher environment:

```bash
export TTS_PRELOAD_MODEL=1
```

After XTTS is healthy, opt in to streaming in the gateway environment:

```bash
export REALTIME_TTS_MODE=streaming
bash scripts/start-realtime-gateway.sh
```

Set `TTS_PRELOAD_MODEL=1` **before starting the XTTS backend process**; it loads
the model during startup and makes `/health` available only after preload succeeds.
This moves cold model loading ahead of the first request; it does not make the
model faster and increases startup time and memory use. The TTS service itself
uses its normal local launcher. The `streaming` setting
must be present in the gateway process; its health endpoint reports the configured
mode, not proof that the TTS service is reachable. The segmented route remains
available by setting `REALTIME_TTS_MODE=segmented`. Automatic provider fallback
is not implemented because switching after partial playback could duplicate speech.

With a WAV reference sample you have rights to use, measure the real local model:

```bash
python scripts/verify-streaming-tts.py --reference /absolute/path/to/authorized-reference.wav
```

Use the Python environment that has installed `services/realtime/requirements.txt`;
the system Python may not include FastAPI or `httpx`.

The verifier prints only mode, time to first chunk, time to completed segment,
number of chunks and audio duration. `firstChunkBeforeCompletion=true` indicates
this run delivered more than one chunk before segment completion. Unit tests use
a mock model and synthetic PCM; they do not establish real voice quality, GPU/CPU
latency or speaker-device output timing.

An already-running XTTS HTTP inference cannot be cancelled: its result is
discarded on interruption and no subsequent segments are requested. It may
temporarily return busy for a new request; the UI reports failure without retry
loops or duplicate billing. The gateway limits voice sessions to four concurrent
users and one per user in a single process. Use one worker or add shared admission
control before scaling horizontally.

Transport tests use synthetic PCM and mocked providers. They do not establish
real ASR accuracy, model latency, audio quality or cloud end-to-end performance.

For production, place this service behind Nginx or an API gateway and expose it
as WSS only. Do not expose the Spring internal ticket-consumption endpoint to
the public internet.

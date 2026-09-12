# Realtime Gateway

The gateway is the Python `asyncio + FastAPI + WebSocket` service described in
the solution. It provides secure ticket validation, ordered events, bounded
outbound delivery, cancellation and a DeepSeek streaming LLM adapter. Streaming
TTS remains intentionally unconfigured.

## Local start

Use one identical, high-entropy value for the Spring Boot service and gateway:

```bash
export REALTIME_GATEWAY_INTERNAL_KEY='replace-with-a-long-random-local-secret'
export SPRING_BOOT_URL='http://127.0.0.1:18080'
export DEEPSEEK_API_KEY='keep-your-key-local-and-never-commit-it'
export DEEPSEEK_MODEL='deepseek-flash'
cd "/Users/frank/Desktop/voice /services/realtime"
../../.venv-models/bin/uvicorn gateway:app --host 127.0.0.1 --port 18081
```

Start Spring Boot with the same `REALTIME_GATEWAY_INTERNAL_KEY`. Its ticket
endpoint must be running before a browser can connect to `ws://127.0.0.1:18081/realtime/ws?ticket=...`.

The gateway reads the API key only when it starts. Its health endpoint will show
`"llmAdapter":"deepseek"` and `"llmModel":"deepseek-flash"` after a key is
configured:

```bash
curl http://127.0.0.1:18081/realtime/health
```

The browser sends `turn.start` with a UUID `turnId` and an optional `prompt`,
or sends `llm.request` after a turn has started. For every DeepSeek SSE text
delta the gateway emits an ordered `llm.delta` event. It also emits
`tts.segment_ready` events for the existing semantic segmenter, but it does
not synthesize audio until a streaming TTS adapter is connected.

For production, place this service behind Nginx or an API gateway and expose it
as WSS only. Do not expose the Spring internal ticket-consumption endpoint to
the public internet.

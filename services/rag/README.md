# Local RAG Service

This small Python service uses the project's local `BGE-M3` model at
`services/rag/models/bge-m3` and a local normalized vector index for the initial
Chinese knowledge base. It does not own user authentication or document permissions:
Spring Boot filters those before calling `/rag/search`.

## First-time install

```bash
cd "/Users/frank/Desktop/voice /services/rag"
../../.venv-models/bin/pip install -r requirements.txt
```

The model has already been placed in `models/bge-m3`, so the first indexing
request does not download a model. BGE-M3 is about 4.3 GB: start this service
only when demonstrating or using the knowledge-base feature.

Start it after the install completes:

```bash
../../.venv-models/bin/uvicorn rag_service:app --host 127.0.0.1 --port 18082
```

Check its state without invoking the model:

```bash
curl http://127.0.0.1:18082/rag/health
```

The vector index and metadata are stored in `services/rag/rag-data/`. They are
local derived files and must not be committed to Git.

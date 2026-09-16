"""Small, local, persistent vector-search service.

Spring Boot owns authentication, document metadata, ownership, publication and
validity checks.  This service only indexes supplied chunks and performs vector
similarity search over the document IDs Spring has already authorized.
"""

from __future__ import annotations

import asyncio
import json
import os
import tempfile
from dataclasses import asdict, dataclass
from pathlib import Path
from threading import Lock

import numpy as np
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field


class KnowledgeChunk(BaseModel):
    documentId: int = Field(gt=0)
    chunkId: int = Field(gt=0)
    content: str = Field(min_length=1, max_length=8000)


class IndexRequest(BaseModel):
    documents: list[KnowledgeChunk] = Field(min_length=1, max_length=2000)


class SearchRequest(BaseModel):
    query: str = Field(min_length=1, max_length=4000)
    documentIds: list[int] = Field(min_length=1, max_length=1000)
    topK: int = Field(default=4, ge=1, le=10)


@dataclass(frozen=True)
class IndexedChunk:
    document_id: int
    chunk_id: int
    content: str


class LocalVectorIndex:
    """Cosine-similarity index persisted alongside its non-vector metadata.

    The initial knowledge base is deliberately small. A normalized NumPy matrix
    provides the same local-vector-index role as FAISS without imposing a
    second, incompatible NumPy dependency on the existing XTTS environment.
    """

    def __init__(self, data_dir: Path, model_name: str):
        self._data_dir = data_dir
        self._records_path = data_dir / "records.json"
        self._vectors_path = data_dir / "knowledge_vectors.npy"
        self._model_name = model_name
        self._lock = Lock()
        self._model = None
        self._vectors: np.ndarray | None = None
        self._records: list[IndexedChunk] = []
        self._load_records()

    @property
    def record_count(self) -> int:
        return len(self._records)

    @property
    def model_loaded(self) -> bool:
        return self._model is not None

    def replace_documents(self, chunks: list[KnowledgeChunk]) -> int:
        with self._lock:
            replaced_ids = {chunk.documentId for chunk in chunks}
            incoming = [
                IndexedChunk(chunk.documentId, chunk.chunkId, chunk.content.strip())
                for chunk in chunks
                if chunk.content.strip()
            ]
            if not incoming:
                raise ValueError("no non-empty chunks supplied")
            self._records = [record for record in self._records if record.document_id not in replaced_ids]
            self._records.extend(incoming)
            self._rebuild()
            return len(incoming)

    def search(self, query: str, allowed_document_ids: set[int], top_k: int) -> list[dict[str, object]]:
        with self._lock:
            if not self._records:
                return []
            self._ensure_vectors_loaded()
            query_vector = self._encode([query])
            # Search a broader candidate set before applying Spring's
            # authorization-derived document filter.
            candidate_count = min(len(self._records), max(top_k * 8, top_k))
            scores = (query_vector @ self._vectors.T)[0]
            positions = np.argsort(-scores)[:candidate_count]
            results: list[dict[str, object]] = []
            for position in positions:
                score = scores[int(position)]
                if position < 0:
                    continue
                record = self._records[int(position)]
                if record.document_id not in allowed_document_ids:
                    continue
                results.append({
                    "documentId": record.document_id,
                    "chunkId": record.chunk_id,
                    "content": record.content,
                    "score": round(float(score), 6),
                })
                if len(results) >= top_k:
                    break
            return results

    def _load_records(self) -> None:
        if not self._records_path.exists():
            return
        try:
            payload = json.loads(self._records_path.read_text(encoding="utf-8"))
            self._records = [IndexedChunk(**record) for record in payload]
        except (OSError, TypeError, ValueError, json.JSONDecodeError) as error:
            raise RuntimeError("RAG index metadata is invalid; rebuild the index") from error

    def _ensure_vectors_loaded(self) -> None:
        if self._vectors is not None:
            return
        if self._vectors_path.exists():
            self._vectors = np.load(self._vectors_path, allow_pickle=False)
            if self._vectors.shape[0] == len(self._records):
                return
        self._rebuild()

    def _rebuild(self) -> None:
        vectors = self._encode([record.content for record in self._records])
        self._data_dir.mkdir(parents=True, exist_ok=True)
        self._write_json_atomically([asdict(record) for record in self._records])
        with tempfile.NamedTemporaryFile(dir=self._data_dir, suffix=".npy", delete=False) as handle:
            temp_vectors = Path(handle.name)
            np.save(handle, vectors, allow_pickle=False)
        try:
            os.replace(temp_vectors, self._vectors_path)
        finally:
            temp_vectors.unlink(missing_ok=True)
        self._vectors = vectors

    def _write_json_atomically(self, payload: list[dict[str, object]]) -> None:
        self._data_dir.mkdir(parents=True, exist_ok=True)
        with tempfile.NamedTemporaryFile(
            dir=self._data_dir, suffix=".json", mode="w", encoding="utf-8", delete=False
        ) as handle:
            json.dump(payload, handle, ensure_ascii=False, separators=(",", ":"))
            temp_path = Path(handle.name)
        try:
            os.replace(temp_path, self._records_path)
        finally:
            temp_path.unlink(missing_ok=True)

    def _encode(self, texts: list[str]) -> np.ndarray:
        model = self._model_instance()
        try:
            vectors = model.encode(texts, normalize_embeddings=True, convert_to_numpy=True, show_progress_bar=False)
        except Exception as error:
            raise RuntimeError("BGE embedding model could not encode text") from error
        return np.ascontiguousarray(vectors, dtype=np.float32)

    def _model_instance(self):
        if self._model is None:
            try:
                from sentence_transformers import SentenceTransformer
                self._model = SentenceTransformer(self._model_name)
            except Exception as error:
                raise RuntimeError(
                    "BGE model is unavailable. Install requirements and download the configured local model first."
                ) from error
        return self._model

data_dir = Path(os.getenv("RAG_DATA_DIR", "./rag-data")).resolve()
# The repository deliberately keeps the large embedding model outside Git.  A
# local default also means first use never silently downloads a different model.
default_model_path = Path(__file__).resolve().parent / "models" / "bge-m3"
model_name = os.getenv("RAG_EMBEDDING_MODEL", str(default_model_path))
index = LocalVectorIndex(data_dir, model_name)
app = FastAPI(title="Zhisheng Local RAG", version="0.1.0")


@app.get("/rag/health")
async def health() -> dict[str, object]:
    return {
        "status": "UP",
        "embeddingModel": model_name,
        "modelLoaded": index.model_loaded,
        "indexedChunks": index.record_count,
        "vectorStore": "local-numpy-cosine",
    }


@app.post("/rag/index/documents")
async def index_documents(request: IndexRequest) -> dict[str, object]:
    try:
        count = await asyncio.to_thread(index.replace_documents, request.documents)
    except RuntimeError as error:
        raise HTTPException(status_code=503, detail=str(error)) from error
    except ValueError as error:
        raise HTTPException(status_code=400, detail=str(error)) from error
    return {"indexedChunks": count, "totalChunks": index.record_count}


@app.post("/rag/search")
async def search(request: SearchRequest) -> dict[str, object]:
    try:
        results = await asyncio.to_thread(index.search, request.query.strip(), set(request.documentIds), request.topK)
    except RuntimeError as error:
        raise HTTPException(status_code=503, detail=str(error)) from error
    return {"results": results}

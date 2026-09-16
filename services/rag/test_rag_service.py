import tempfile
import unittest
from pathlib import Path

import numpy as np

from rag_service import IndexedChunk, LocalVectorIndex, health, index


class RagServiceTests(unittest.IsolatedAsyncioTestCase):
    async def test_health_does_not_load_the_embedding_model(self):
        was_loaded = index.model_loaded
        result = await health()

        self.assertEqual("UP", result["status"])
        self.assertEqual(was_loaded, index.model_loaded)
        self.assertEqual("local-numpy-cosine", result["vectorStore"])

    async def test_search_filters_results_to_authorized_documents(self):
        with tempfile.TemporaryDirectory() as directory:
            vector_index = LocalVectorIndex(Path(directory), "unused")
            vector_index._records = [
                IndexedChunk(1, 11, "authorized"),
                IndexedChunk(2, 22, "private"),
            ]
            vector_index._vectors = np.asarray([[1.0, 0.0], [1.0, 0.0]], dtype=np.float32)
            vector_index._encode = lambda _texts: np.asarray([[1.0, 0.0]], dtype=np.float32)

            results = vector_index.search("query", {1}, 4)

            self.assertEqual(1, len(results))
            self.assertEqual(1, results[0]["documentId"])
            self.assertEqual(11, results[0]["chunkId"])


if __name__ == "__main__":
    unittest.main()

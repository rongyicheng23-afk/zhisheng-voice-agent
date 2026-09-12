"""Adaptive semantic chunking for incremental LLM text.

The segmenter deliberately waits for a natural boundary where possible.  It
does not treat every punctuation mark as a safe TTS boundary: unmatched
brackets and short trailing fragments remain buffered until more text arrives.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Optional


@dataclass(frozen=True)
class SemanticSegment:
    text: str
    reason: str


class AdaptiveSemanticChunker:
    """Convert incremental text into TTS-friendly, ordered segments."""

    _STRONG_BOUNDARIES = "。！？!?；;\n"
    _WEAK_BOUNDARIES = "，,、：:"
    _OPEN_TO_CLOSE = {"（": "）", "(": ")", "[": "]", "【": "】", "“": "”", "‘": "’"}
    _CLOSE = set(_OPEN_TO_CLOSE.values())

    def __init__(self, min_chars: int = 12, max_chars: int = 48, max_wait_ms: int = 900):
        if min_chars < 1 or max_chars <= min_chars or max_wait_ms < 0:
            raise ValueError("invalid chunking limits")
        self.min_chars = min_chars
        self.max_chars = max_chars
        self.max_wait_ms = max_wait_ms
        self._buffer = ""
        self._buffer_started_at: Optional[int] = None

    def feed(self, delta: str, now_ms: int) -> list[SemanticSegment]:
        """Add one LLM delta and emit every stable segment in order."""
        if not delta:
            return self._release_for_wait(now_ms)
        if not self._buffer:
            self._buffer_started_at = now_ms
        self._buffer += delta
        return self._drain(now_ms)

    def finish(self) -> list[SemanticSegment]:
        """Flush the final tail when the model declares the turn complete."""
        if not self._buffer.strip():
            self._reset()
            return []
        segment = SemanticSegment(self._buffer.strip(), "turn_finished")
        self._reset()
        return [segment]

    def _drain(self, now_ms: int) -> list[SemanticSegment]:
        emitted: list[SemanticSegment] = []
        while len(self._buffer) >= self.min_chars:
            index = self._best_boundary()
            if index is None and len(self._buffer) >= self.max_chars:
                index = self._forced_boundary()
                reason = "max_length"
            elif index is not None:
                reason = "semantic_boundary"
            else:
                break
            emitted.append(self._take(index + 1, reason, now_ms))
        emitted.extend(self._release_for_wait(now_ms))
        return emitted

    def _best_boundary(self) -> Optional[int]:
        depth = 0
        last_weak: Optional[int] = None
        for index, char in enumerate(self._buffer):
            if char in self._OPEN_TO_CLOSE:
                depth += 1
            elif char in self._CLOSE and depth:
                depth -= 1
            if index + 1 < self.min_chars or depth:
                continue
            if char in self._STRONG_BOUNDARIES:
                return index
            if char in self._WEAK_BOUNDARIES:
                last_weak = index
        return last_weak if len(self._buffer) >= self.max_chars else None

    def _forced_boundary(self) -> int:
        limit = min(self.max_chars, len(self._buffer))
        # Prefer a weak boundary near the target length, but never split before
        # a unit marker such as "3.5GB" or "2026年".
        for index in range(limit - 1, self.min_chars - 2, -1):
            if self._buffer[index] in self._WEAK_BOUNDARIES:
                return index
        return limit - 1

    def _release_for_wait(self, now_ms: int) -> list[SemanticSegment]:
        if (
            self._buffer_started_at is not None
            and len(self._buffer) >= self.min_chars
            and now_ms - self._buffer_started_at >= self.max_wait_ms
            and self._balanced()
        ):
            return [self._take(len(self._buffer), "max_wait", now_ms)]
        return []

    def _balanced(self) -> bool:
        depth = 0
        for char in self._buffer:
            if char in self._OPEN_TO_CLOSE:
                depth += 1
            elif char in self._CLOSE and depth:
                depth -= 1
        return depth == 0

    def _take(self, end: int, reason: str, now_ms: int) -> SemanticSegment:
        text = self._buffer[:end].strip()
        self._buffer = self._buffer[end:].lstrip()
        self._buffer_started_at = now_ms if self._buffer else None
        return SemanticSegment(text, reason)

    def _reset(self) -> None:
        self._buffer = ""
        self._buffer_started_at = None

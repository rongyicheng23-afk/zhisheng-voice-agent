"""Incremental semantic boundaries with bounded buffering, no model calls."""
from dataclasses import dataclass
import time


@dataclass(frozen=True)
class Segment:
    text: str
    reason: str


class SemanticChunker:
    def __init__(self, min_chars=12, max_chars=100, max_wait=0.6, clock=time.monotonic):
        if not 1 <= min_chars <= max_chars or max_wait <= 0:
            raise ValueError('invalid chunk limits')
        self.min_chars, self.max_chars, self.max_wait = min_chars, max_chars, max_wait
        self.clock = clock
        self.buffer = ''
        self.since = None

    def feed(self, delta):
        if delta and not self.buffer:
            self.since = self.clock()
        self.buffer += delta
        return self._drain()

    def poll(self):
        """Call periodically even while the upstream token stream is idle."""
        return self._drain()

    def finish(self):
        result = self._drain()
        if self.buffer:
            result.append(Segment(self.buffer, 'end_of_stream'))
        self.cancel()
        return result

    def cancel(self):
        self.buffer, self.since = '', None

    def _boundaries(self):
        stack, boundaries = [], []
        pairs = {'（': '）', '(': ')', '[': ']', '【': '】', '“': '”', '‘': '’'}
        text = self.buffer
        for index, char in enumerate(text):
            if char == '"':
                if stack and stack[-1] == '"':
                    stack.pop()
                else:
                    stack.append('"')
            elif char in pairs:
                stack.append(pairs[char])
            elif stack and char == stack[-1]:
                stack.pop()
            if stack:
                continue
            end = index + 1
            if char in ',:' and index > 0 and text[index - 1].isdigit():
                if end == len(text) or text[end].isdigit():
                    continue
            # Hold ambiguous decimal points, list markers and latin words.
            if char == '.':
                if end == len(text) or (index > 0 and text[index - 1].isdigit()):
                    continue
                if not text[end].isspace():
                    continue
            if char in '。！？!?；;\n.':
                boundaries.append((end, 'strong_punctuation'))
            elif char in '，,、：:' or char.isspace():
                boundaries.append((end, 'soft_boundary'))
            elif (char in pairs.values() or char == '"') and index > 0 and text[index - 1] in '。！？!?':
                boundaries.append((end, 'closed_quote'))
        return boundaries

    def _drain(self):
        segments = []
        while self.buffer:
            boundaries = self._boundaries()
            strong = [(n, why) for n, why in boundaries
                      if why != 'soft_boundary' and self.min_chars <= n <= self.max_chars]
            overdue = self.since is not None and self.clock() - self.since >= self.max_wait
            if strong:
                end, reason = strong[0]
            elif len(self.buffer) >= self.max_chars or overdue:
                safe = [(n, why) for n, why in boundaries if self.min_chars <= n <= self.max_chars]
                if safe:
                    end, _ = safe[-1]
                    reason = 'max_length' if len(self.buffer) >= self.max_chars else 'max_wait'
                elif len(self.buffer) > self.max_chars * 4:
                    end, reason = self.max_chars, 'forced_buffer_limit'
                else:
                    break
            else:
                break
            segments.append(Segment(self.buffer[:end], reason))
            self.buffer = self.buffer[end:]
            self.since = self.clock() if self.buffer else None
        return segments

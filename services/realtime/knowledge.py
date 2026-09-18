"""Evidence-only speech: read authorised current passages, never invent an answer."""
import httpx


class KnowledgeReply:
    def __init__(self, settings, user_id, transport=None):
        self.settings, self.user_id, self.transport = settings, user_id, transport
        self.text, self.spans, self.cursor = '', [], 0

    async def prepare(self, prompt):
        if len(prompt) > 1000:
            raise ValueError('knowledge query too long')
        async with httpx.AsyncClient(timeout=10, transport=self.transport) as client:
            async with client.stream('POST', self.settings.spring_boot_url + '/api/realtime/internal/knowledge/search',
                    headers={'X-Realtime-Gateway-Key': self.settings.internal_key},
                    json={'userId': self.user_id, 'question': prompt}) as response:
                response.raise_for_status()
                data = bytearray()
                async for chunk in response.aiter_bytes():
                    data.extend(chunk)
                    if len(data) > 20000:
                        raise ValueError('retrieval response too large')
        import json
        payload = json.loads(data)
        citations = payload.get('citations')
        if not isinstance(citations, list) or len(citations) > 3:
            raise ValueError('invalid citations')
        seen = set()
        for item in citations:
            if not isinstance(item, dict) or not all(isinstance(item.get(k), str) for k in
                    ('id', 'documentId', 'title', 'quote', 'sourceVersion', 'publisher', 'sourceUrl', 'validFrom', 'validUntil')):
                raise ValueError('invalid citation')
            if item['id'] in seen or not item['id'] or not 1 <= len(item['quote']) <= 500:
                raise ValueError('invalid citation quote')
            seen.add(item['id'])
        self.text = ('以下为检索到的相关原文，不是已核验结论；资料如有冲突，请核对发布方。' if citations
                     else '没有检索到当前有效的相关资料，无法据此确认答案。')
        self.spans, self.cursor = [], 0
        for index, item in enumerate(citations):
            start = len(self.text)
            self.text += f"\n来源{index + 1}，原文：{item['quote'].rstrip()}"
            self.spans.append((start, len(self.text), item['id']))
        return citations

    async def stream(self, prompt):
        yield self.text

    def citation_ids(self, text):
        start, end = self.cursor, self.cursor + len(text)
        if self.text[start:end] != text:
            raise ValueError('citation alignment lost')
        self.cursor = end
        return [label for left, right, label in self.spans if left < end and right > start]

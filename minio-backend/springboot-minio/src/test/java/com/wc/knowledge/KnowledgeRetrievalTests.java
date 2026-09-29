package com.wc.knowledge;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class KnowledgeRetrievalTests {
    private KnowledgeService.Document document(String id, String content) {
        return new KnowledgeService.Document(id, id, "标题", "", "单位", "v1",
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), content, "PUBLISHED", 1);
    }

    @Test void keywordAcrossOldBoundaryIsFoundWithOriginalOffset() {
        String content = "。".repeat(499) + "学生证" + "。".repeat(510);
        var hits = KnowledgeRetrieval.search(List.of(document("doc", content)), "学生证");
        assertTrue(hits.stream().anyMatch(hit -> hit.quote().contains("学生证")));
        for (var hit : hits) {
            int offset = Integer.parseInt(hit.id().split(":")[2]);
            assertEquals(content.substring(offset, offset + hit.quote().length()), hit.quote());
            assertTrue(hit.quote().length() <= 500);
        }
    }

    @Test void overlappingWindowsDoNotCreateAShortRedundantTail() {
        String content = "报名" + "。".repeat(598);
        var hits = KnowledgeRetrieval.search(List.of(document("doc", content)), "报名");
        assertEquals(1, hits.size());
        assertEquals("doc:1:0", hits.get(0).id());
    }

    @Test void duplicateParagraphsDoNotFillAllCitationSlots() {
        var repeated = document("a", "报名需要学生证。\n".repeat(12));
        var other = document("b", "报名还需要申请表。");
        var hits = KnowledgeRetrieval.search(List.of(repeated, other), "报名");
        assertEquals(2, hits.size());
        assertEquals(Set.of("a", "b"), new HashSet<>(hits.stream().map(KnowledgeService.Citation::documentId).toList()));
    }

    @Test void identicalQuotesInDifferentDocumentsKeepTheirProvenance() {
        var hits = KnowledgeRetrieval.search(List.of(document("a", "报名需要学生证。"),
                document("b", "报名需要学生证。")), "报名");
        assertEquals(2, hits.size());
        assertNotEquals(hits.get(0).documentId(), hits.get(1).documentId());
    }

    @Test void rareQueryTermsOutrankCommonTermsWithoutFrequencySpam() {
        List<KnowledgeService.Document> docs = List.of(document("a", "报名".repeat(100)),
                document("b", "报名"), document("c", "报名"), document("z", "学籍"));
        var hits = KnowledgeRetrieval.search(docs, "报名 学籍");
        assertEquals("z", hits.get(0).documentId());
        assertEquals(3, hits.size());
    }

    @Test void exactPhraseBreaksEqualTermCoverageTie() {
        var hits = KnowledgeRetrieval.search(List.of(document("a", "材料以及报名说明"),
                document("z", "报名 材料")), "报名 材料");
        assertEquals("z", hits.get(0).documentId());
    }

    @Test void fullWidthAndCaseMatchWithoutChangingQuotedText() {
        var hits = KnowledgeRetrieval.search(List.of(document("doc", "请填写 ＡＢＣ１２３ 表格。")), "abc123");
        assertEquals(1, hits.size());
        assertEquals("请填写 ＡＢＣ１２３ 表格。", hits.get(0).quote());
    }

    @Test void supplementaryHanUsesCodepointsNotSurrogateFragments() {
        String rare = new String(new int[]{0x20000, 0x20001}, 0, 2);
        assertEquals(Set.of(rare), KnowledgeRetrieval.terms(rare));
        var hits = KnowledgeRetrieval.search(List.of(document("doc", "。".repeat(499) + rare)), rare);
        assertEquals(1, hits.size());
        assertTrue(hits.get(0).quote().contains(rare));
        for (var hit : hits) {
            assertFalse(Character.isLowSurrogate(hit.quote().charAt(0)));
            assertFalse(Character.isHighSurrogate(hit.quote().charAt(hit.quote().length() - 1)));
        }
    }

    @Test void surrogatePairsAtOverlapStartRemainWhole() {
        String content = "。".repeat(399) + "😀" + "。".repeat(200) + "报名";
        var hits = KnowledgeRetrieval.search(List.of(document("doc", content)), "报名");
        assertEquals(1, hits.size());
        assertEquals("doc:1:399", hits.get(0).id());
        assertTrue(hits.get(0).quote().startsWith("😀"));
    }

    @Test void emptyStopwordOrUnrelatedQueriesReturnNoEvidence() {
        var docs = List.of(document("doc", "报名材料"));
        for (String query : List.of("", "如何", "the is a", "火星探测", "😀")) {
            assertTrue(KnowledgeRetrieval.search(docs, query).isEmpty(), query);
        }
    }

    @Test void orderingIsStableRegardlessOfDatabaseOrder() {
        var docs = new ArrayList<>(List.of(document("c", "报名材料"), document("a", "报名材料"), document("b", "报名材料")));
        var first = KnowledgeRetrieval.search(docs, "报名");
        Collections.reverse(docs);
        assertEquals(first, KnowledgeRetrieval.search(docs, "报名"));
    }

    @Test void normalisedMatchesDoNotDeduplicateDifferentOriginalText() {
        var hits = KnowledgeRetrieval.search(List.of(document("doc", "ＡＢＣ\nABC")), "abc");
        assertEquals(2, hits.size());
    }

    @Test void boundaryPositionsPreserveVerbatimOffsetsAndCompleteKeywords() {
        for (int offset = 390; offset <= 510; offset++) {
            String content = "。".repeat(offset) + "😀学生证" + "。".repeat(700);
            var hits = KnowledgeRetrieval.search(List.of(document("doc", content)), "学生证");
            assertTrue(hits.stream().anyMatch(hit -> hit.quote().contains("学生证")), "offset " + offset);
            for (var hit : hits) {
                int start = Integer.parseInt(hit.id().split(":")[2]);
                assertEquals(content.substring(start, start + hit.quote().length()), hit.quote());
                assertFalse(Character.isLowSurrogate(hit.quote().charAt(0)));
                assertFalse(Character.isHighSurrogate(hit.quote().charAt(hit.quote().length() - 1)));
            }
        }
    }
}

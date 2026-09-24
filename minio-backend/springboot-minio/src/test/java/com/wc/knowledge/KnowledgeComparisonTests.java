package com.wc.knowledge;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class KnowledgeComparisonTests {
    KnowledgeService.Document doc(String id, String content) {
        return new KnowledgeService.Document(id, "series", "通知", "", "发布方", id,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), content, "DRAFT", 1);
    }
    void reconstruct(KnowledgeComparison.Result result, String before, String after) {
        assertEquals(before.replace("\r\n", "\n"), String.join("\n", result.lines().stream().filter(l -> !l.kind().equals("ADDED")).map(KnowledgeComparison.LineChange::text).toList()));
        assertEquals(after.replace("\r\n", "\n"), String.join("\n", result.lines().stream().filter(l -> !l.kind().equals("REMOVED")).map(KnowledgeComparison.LineChange::text).toList()));
    }
    @Test void exactChangesKeepLineNumbersBlankLinesAndUnicode() {
        String before = "报名\r\n\r\n需学生证😀\r\n旧时间", after = "报名\n\n需身份证😀\n新时间";
        var result = KnowledgeComparison.compare(doc("v1", before), doc("v2", after));
        assertFalse(result.coarse()); assertEquals(2, result.added()); assertEquals(2, result.removed());
        assertEquals("版本号", result.fields().get(0).field());
        assertEquals(3, result.lines().stream().filter(l -> l.kind().equals("ADDED")).findFirst().orElseThrow().afterLine());
        reconstruct(result, before, after);
    }
    @Test void repeatedMovedLinesAndIdenticalDocumentsRemainLossless() {
        var result = KnowledgeComparison.compare(doc("v1", "甲\n乙\n甲"), doc("v2", "乙\n甲\n甲"));
        reconstruct(result, "甲\n乙\n甲", "乙\n甲\n甲");
        var same = KnowledgeComparison.compare(doc("v1", "甲\n"), doc("v1", "甲\n"));
        assertEquals(0, same.added()); assertEquals(0, same.removed()); assertTrue(same.fields().isEmpty());
    }
    @Test void manyLinesUseBoundedLosslessFallback() {
        String before = "首\n" + "甲\n".repeat(500) + "尾", after = "首\n" + "乙\n".repeat(500) + "尾";
        var result = KnowledgeComparison.compare(doc("v1", before), doc("v2", after));
        assertTrue(result.coarse()); assertEquals(500, result.added()); assertEquals(500, result.removed());
        reconstruct(result, before, after);
    }
    @Test void randomizedSmallDocumentsAlwaysReconstructBothInputs() {
        Random random = new Random(42);
        for (int n = 0; n < 150; n++) {
            String a = String.join("\n", random.ints(12, 0, 4).mapToObj(Integer::toString).toList());
            String b = String.join("\n", random.ints(15, 0, 4).mapToObj(Integer::toString).toList());
            reconstruct(KnowledgeComparison.compare(doc("v1", a), doc("v2", b)), a, b);
        }
    }
}

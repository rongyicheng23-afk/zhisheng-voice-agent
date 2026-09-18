package com.wc.knowledge;

import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class KnowledgeServiceTests {
    KnowledgeService service;
    TransactionTemplate tx;
    JdbcTemplate db;
    LocalDate today = LocalDate.of(2026, 9, 18);
    @BeforeEach void setup() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("sql/knowledge.sql")).execute(ds);
        db = new JdbcTemplate(ds);
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        service = new KnowledgeService(db, Clock.fixed(Instant.parse("2026-09-18T00:00:00Z"), ZoneId.of("Asia/Shanghai")));
    }
    KnowledgeService.Draft draft(String series, String version) {
        return new KnowledgeService.Draft("报名通知", "https://example.org/notice", "测试发布方", version,
                today.minusDays(1), today.plusDays(1), "报名材料需要提交报名表和学生证。\n报名截止时间为九月十九日。", series);
    }
    KnowledgeService.Document create(int uid, String series, String version) { return tx.execute(s -> service.create(uid, draft(series, version))); }
    KnowledgeService.Document publish(int uid, KnowledgeService.Document doc) {
        return tx.execute(s -> service.transition(uid, doc.id(), "PUBLISHED", doc.revision()));
    }
    @Test void draftExcludedAndPublishedQuotesRetainProvenance() {
        var doc = create(1, null, "v1");
        assertTrue(service.search(1, "报名材料").citations().isEmpty());
        publish(1, doc);
        var hits = service.search(1, "报名材料").citations();
        assertFalse(hits.isEmpty());
        assertTrue(hits.stream().allMatch(h -> doc.content().contains(h.quote())));
        assertEquals(doc.id(), hits.get(0).documentId());
        assertEquals("v1", hits.get(0).sourceVersion());
        assertEquals(1, hits.get(0).paragraph());
    }
    @Test void ownershipAppliesToListSearchVersionCreationAndStatus() {
        var doc = publish(1, create(1, null, "v1"));
        assertTrue(service.list(2).isEmpty());
        assertTrue(service.search(2, "报名材料").citations().isEmpty());
        assertThrows(ResponseStatusException.class, () -> create(2, doc.seriesId(), "v2"));
        assertThrows(ResponseStatusException.class, () -> tx.execute(s -> service.transition(2, doc.id(), "WITHDRAWN", doc.revision())));
    }
    @Test void publishingNewVersionWithdrawsOldAndPreservesOldContent() {
        var old = publish(1, create(1, null, "v1"));
        var next = publish(1, create(1, old.seriesId(), "v2"));
        assertEquals(2, service.list(1).size());
        assertEquals("WITHDRAWN", service.list(1).stream().filter(d -> d.id().equals(old.id())).findFirst().orElseThrow().status());
        assertTrue(service.search(1, "报名材料").citations().stream().allMatch(c -> c.documentId().equals(next.id())));
    }
    @Test void expiredAndFutureDatesCannotPublishAndExpiryIsRecheckedAtSearch() {
        var doc = publish(1, create(1, null, "v1"));
        var future = new KnowledgeService(db, Clock.fixed(Instant.parse("2026-09-20T00:00:00Z"), ZoneOffset.UTC));
        assertTrue(future.search(1, "报名材料").citations().isEmpty());
        var d = draft(null, "future");
        var later = tx.execute(s -> service.create(1, new KnowledgeService.Draft(d.title(), d.sourceUrl(), d.publisher(), d.sourceVersion(), today.plusDays(2), today.plusDays(3), d.content(), null)));
        assertThrows(ResponseStatusException.class, () -> publish(1, later));
        assertFalse(service.search(1, "报名材料").citations().isEmpty());
    }
    @Test void staleStatusVersionRejectedWithoutSideEffects() {
        var draft = create(1, null, "v1");
        publish(1, draft);
        var failure = assertThrows(ResponseStatusException.class,
                () -> tx.execute(s -> service.transition(1, draft.id(), "WITHDRAWN", draft.revision())));
        assertEquals(409, failure.getStatusCode().value());
        assertEquals("PUBLISHED", service.list(1).get(0).status());
    }
    @Test void duplicateVersionAndUnsafeSourceRejected() {
        var first = create(1, null, "v1");
        assertThrows(ResponseStatusException.class, () -> create(1, first.seriesId(), "v1"));
        var d = draft(null, "v2");
        for (String url : List.of("javascript:alert(1)", "file:///etc/passwd", "https://user:pass@example.org")) {
            assertThrows(ResponseStatusException.class, () -> tx.execute(s -> service.create(1,
                    new KnowledgeService.Draft(d.title(), url, d.publisher(), d.sourceVersion(), d.validFrom(), d.validUntil(), d.content(), null))));
        }
        assertEquals(1, service.list(1).size());
    }
    @Test void transactionRollbackRestoresPublicationOfBothVersions() {
        var first = publish(1, create(1, null, "v1"));
        var next = create(1, first.seriesId(), "v2");
        assertThrows(IllegalStateException.class, () -> tx.execute(s -> {
            service.transition(1, next.id(), "PUBLISHED", next.revision());
            throw new IllegalStateException("rollback fixture");
        }));
        assertTrue(service.search(1, "报名材料").citations().stream().allMatch(c -> c.documentId().equals(first.id())));
        assertEquals("DRAFT", service.list(1).stream().filter(d -> d.id().equals(next.id())).findFirst().orElseThrow().status());
    }
    @Test void concurrentPublicationsLeaveOnlyOneActiveVersion() throws Exception {
        var first = create(1, null, "v1"); var next = create(1, first.seriesId(), "v2");
        var pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> publish(1, first)); var b = pool.submit(() -> publish(1, next));
            a.get(5, TimeUnit.SECONDS); b.get(5, TimeUnit.SECONDS);
            assertEquals(1, service.list(1).stream().filter(d -> d.status().equals("PUBLISHED")).count());
        } finally { pool.shutdownNow(); }
    }
    @Test void noMatchDoesNotProduceFabricatedCitations() {
        publish(1, create(1, null, "v1"));
        assertTrue(service.search(1, "火星宇航员").citations().isEmpty());
        assertTrue(service.search(1, "如何").message().contains("无法"));
    }

    @Test void unicodeChunkBoundariesNeverSplitASurrogatePair() {
        var d = draft(null, "unicode");
        String text = "报名材料" + "中".repeat(495) + "😀报名材料需要学生证。";
        var doc = tx.execute(s -> service.create(1, new KnowledgeService.Draft(d.title(), d.sourceUrl(), d.publisher(), d.sourceVersion(),
                d.validFrom(), d.validUntil(), text, null)));
        publish(1, doc);
        var hits = service.search(1, "报名材料").citations();
        assertEquals(2, hits.size());
        for (var hit : hits) {
            assertFalse(Character.isLowSurrogate(hit.quote().charAt(0)));
            assertFalse(Character.isHighSurrogate(hit.quote().charAt(hit.quote().length() - 1)));
            assertTrue(text.contains(hit.quote()));
        }
    }
}

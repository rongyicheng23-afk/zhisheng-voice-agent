package com.wc.knowledge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in smoke test: all synthetic rows are rolled back, never committed. */
@EnabledIfEnvironmentVariable(named="KNOWLEDGE_MYSQL_URL", matches=".+")
class KnowledgeMySqlSmokeTests {
    @Test void actualMysqlPublicationAndRollback() throws Exception {
        var ds = new DriverManagerDataSource(System.getenv("KNOWLEDGE_MYSQL_URL"),
                System.getenv().getOrDefault("MYSQL_USERNAME", "root"), System.getenv("MYSQL_PASSWORD"));
        var db = new JdbcTemplate(ds);
        int owner = 2147483640;
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM knowledge_space WHERE owner_id=?", Integer.class, owner));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM knowledge_document WHERE owner_id=?", Integer.class, owner));
        var service = new KnowledgeService(db);
        var uploaded = new org.springframework.mock.web.MockMultipartFile("file", "临时资料.docx", "application/octet-stream",
                KnowledgeImportTests.docx(KnowledgeImportTests.xml("<w:p><w:r><w:t>报名材料测试原文。</w:t></w:r></w:p>")));
        var imported = new KnowledgeImportService().extract(uploaded);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        tx.execute(status -> {
            status.setRollbackOnly();
            var today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
            var draft = new KnowledgeService.Draft("自动化临时资料", "", "测试", "fixture", today.minusDays(1), today.plusDays(1), imported.content(), null, java.util.UUID.randomUUID().toString());
            var doc = service.create(owner, draft);
            service.transition(owner, doc.id(), "PUBLISHED", 1);
            var retry = service.create(owner, draft);
            assertEquals(doc.id(), retry.id());
            assertEquals("PUBLISHED", retry.status());
            assertEquals(1, service.list(owner).size());
            assertFalse(service.search(owner, "报名材料").citations().isEmpty());
            assertEquals(imported.content(), service.search(owner, "报名材料").citations().get(0).quote());
            assertTrue(service.search(owner - 1, "报名材料").citations().isEmpty());
            var next = service.create(owner, new KnowledgeService.Draft(draft.title(), draft.sourceUrl(), draft.publisher(), "fixture-v2",
                    draft.validFrom(), draft.validUntil(), "报名材料更新测试原文。", doc.seriesId()));
            var review = service.review(owner, next.id());
            assertEquals(doc.id(), review.replaced().get(0).id());
            assertEquals(1, review.comparison().added());
            service.publishReviewed(owner, next.id(), review.reviewToken());
            assertEquals("WITHDRAWN", service.review(owner, doc.id()).candidate().status());
            assertTrue(service.search(owner, "报名材料").citations().stream().allMatch(c -> c.documentId().equals(next.id())));
            assertThrows(org.springframework.web.server.ResponseStatusException.class,
                    () -> service.publishReviewed(owner, next.id(), review.reviewToken()));
            service.transition(owner, next.id(), "WITHDRAWN", 2);
            assertTrue(service.search(owner, "报名材料").citations().isEmpty());
            String boundaryText = "。".repeat(499) + "学生证及 ＡＢＣ１２３ 证明😀" + "。".repeat(510);
            var boundary = service.create(owner, new KnowledgeService.Draft("切片边界", "", "测试", "boundary",
                    today, today, boundaryText, null));
            service.transition(owner, boundary.id(), "PUBLISHED", boundary.revision());
            var hits = service.search(owner, "学生证 abc123").citations();
            assertTrue(hits.stream().anyMatch(hit -> hit.quote().contains("学生证及 ＡＢＣ１２３ 证明😀")));
            assertTrue(hits.stream().allMatch(hit -> boundaryText.contains(hit.quote())));
            assertTrue(service.search(owner - 1, "学生证 abc123").citations().isEmpty());
            return null;
        });
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM knowledge_space WHERE owner_id=?", Integer.class, owner));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM knowledge_document WHERE owner_id=?", Integer.class, owner));
    }
}

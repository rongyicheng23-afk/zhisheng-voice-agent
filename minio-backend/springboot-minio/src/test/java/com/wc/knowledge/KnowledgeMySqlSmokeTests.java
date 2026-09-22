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
    @Test void actualMysqlPublicationAndRollback() {
        var ds = new DriverManagerDataSource(System.getenv("KNOWLEDGE_MYSQL_URL"),
                System.getenv().getOrDefault("MYSQL_USERNAME", "root"), System.getenv("MYSQL_PASSWORD"));
        var db = new JdbcTemplate(ds);
        int owner = 2147483640;
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM knowledge_space WHERE owner_id=?", Integer.class, owner));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM knowledge_document WHERE owner_id=?", Integer.class, owner));
        var service = new KnowledgeService(db);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        tx.execute(status -> {
            status.setRollbackOnly();
            var today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
            var draft = new KnowledgeService.Draft("自动化临时资料", "", "测试", "fixture", today.minusDays(1), today.plusDays(1), "报名材料测试原文。", null, java.util.UUID.randomUUID().toString());
            var doc = service.create(owner, draft);
            service.transition(owner, doc.id(), "PUBLISHED", 1);
            var retry = service.create(owner, draft);
            assertEquals(doc.id(), retry.id());
            assertEquals("PUBLISHED", retry.status());
            assertEquals(1, service.list(owner).size());
            assertFalse(service.search(owner, "报名材料").citations().isEmpty());
            assertTrue(service.search(owner - 1, "报名材料").citations().isEmpty());
            service.transition(owner, doc.id(), "WITHDRAWN", 2);
            assertTrue(service.search(owner, "报名材料").citations().isEmpty());
            return null;
        });
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM knowledge_space WHERE owner_id=?", Integer.class, owner));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM knowledge_document WHERE owner_id=?", Integer.class, owner));
    }
}

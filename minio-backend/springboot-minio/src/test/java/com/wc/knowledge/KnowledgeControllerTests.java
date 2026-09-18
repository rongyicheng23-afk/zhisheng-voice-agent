package com.wc.knowledge;

import com.wc.service.UserInfoService;
import com.wc.entity.UserInfo;
import com.wc.utils.ThreadLocalUtil;
import com.wc.utils.JwtUtil;
import com.wc.interceptors.LoginInterceptor;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class KnowledgeControllerTests {
    KnowledgeService knowledge = mock(KnowledgeService.class);
    UserInfoService users = mock(UserInfoService.class);
    KnowledgeController controller = new KnowledgeController(knowledge, users, "fixture-key");
    @AfterEach void cleanup() { ThreadLocalUtil.remove(); }

    @Test void publicSearchRequiresLoginAndUsesClaimedUserNotBodyUser() throws Exception {
        UserInfo user = new UserInfo(); user.setId(7);
        when(users.getUserById(7)).thenReturn(user);
        when(knowledge.search(7, "问题")).thenReturn(new KnowledgeService.SearchResult("extractive", "无资料", List.of()));
        var mvc = MockMvcBuilders.standaloneSetup(controller).addInterceptors(new LoginInterceptor(users)).build();
        mvc.perform(post("/api/knowledge/search").contentType("application/json").content("{\"question\":\"问题\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(knowledge);
        mvc.perform(post("/api/knowledge/search").header("Authorization", "Bearer " + JwtUtil.genToken(Map.of("id", 7)))
                .contentType("application/json").content("{\"question\":\"问题\",\"userId\":999}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        verify(knowledge).search(7, "问题");
    }

    @Test void internalAccessRequiresConfiguredKeyAndExistingUser() {
        assertThrows(ResponseStatusException.class, () -> controller.internal("bad", new KnowledgeController.InternalQuery(7, "问题")));
        assertThrows(ResponseStatusException.class, () -> controller.internal("fixture-key", new KnowledgeController.InternalQuery(7, "问题")));
        var disabled = new KnowledgeController(knowledge, users, "");
        assertThrows(ResponseStatusException.class, () -> disabled.internal("", new KnowledgeController.InternalQuery(7, "问题")));
        verifyNoInteractions(knowledge);
    }

    @Test void databaseFailuresAreSanitizedAndNotCached() throws Exception {
        ThreadLocalUtil.set(Map.of("id", 7));
        UserInfo user = new UserInfo(); user.setId(7); when(users.getUserById(7)).thenReturn(user);
        when(knowledge.list(7)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("secret SQL and password"));
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(get("/api/knowledge/documents")).andExpect(status().isServiceUnavailable())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))))
                .andExpect(header().string("Cache-Control", "no-store"));
    }
}

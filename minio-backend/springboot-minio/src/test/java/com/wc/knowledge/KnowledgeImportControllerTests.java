package com.wc.knowledge;

import com.wc.entity.UserInfo;
import com.wc.service.UserInfoService;
import com.wc.utils.JwtUtil;
import com.wc.utils.ThreadLocalUtil;
import com.wc.interceptors.LoginInterceptor;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class KnowledgeImportControllerTests {
    @AfterEach void cleanup() { ThreadLocalUtil.remove(); }
    @Test void importRequiresExistingLoginAndReturnsNoStorePreviewOnly() throws Exception {
        var users = mock(UserInfoService.class);
        var parser = spy(new KnowledgeImportService());
        var mvc = MockMvcBuilders.standaloneSetup(new KnowledgeImportController(parser, users))
                .addInterceptors(new LoginInterceptor(users)).build();
        var file = new MockMultipartFile("file", "notice.txt", "text/plain", "Student ID required".getBytes());
        mvc.perform(multipart("/api/knowledge/import-preview").file(file)).andExpect(status().isUnauthorized());
        verifyNoInteractions(parser);
        UserInfo user = new UserInfo(); user.setId(7); when(users.getUserById(7)).thenReturn(user);
        String auth = "Bearer " + JwtUtil.genToken(Map.of("id", 7));
        mvc.perform(multipart("/api/knowledge/import-preview").file(file).header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.content").value("Student ID required")).andExpect(jsonPath("$.suggestedTitle").value("notice"));
        mvc.perform(multipart("/api/knowledge/import-preview").file(new MockMultipartFile("file", "bad.doc", "application/octet-stream", new byte[]{1})).header("Authorization", auth))
                .andExpect(status().isUnsupportedMediaType()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.message").isString());
    }
}

package com.wc.controller;

import com.wc.entity.UserInfo;
import com.wc.service.UserInfoService;
import com.wc.utils.ThreadLocalUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class UserIsolationTests {
    private UserInfoController controller;
    private UserInfoService service;
    @BeforeEach void setup() {
        controller = new UserInfoController();
        service = mock(UserInfoService.class);
        ReflectionTestUtils.setField(controller, "userInfoService", service);
        ThreadLocalUtil.set(Map.of("id", 1));
        UserInfo self = new UserInfo(); self.setId(1);
        when(service.getUserById(1)).thenReturn(self);
    }
    @AfterEach void cleanup() { ThreadLocalUtil.remove(); }
    @Test void rejectsOtherUserBeforeReadWriteOrDelete() {
        assertThrows(ResponseStatusException.class, () -> controller.user(2));
        assertThrows(ResponseStatusException.class, () -> controller.deleteUser(2));
        assertThrows(ResponseStatusException.class, () -> controller.image(null, 2));
        assertThrows(ResponseStatusException.class, () -> controller.contract(null, 2));
        assertThrows(ResponseStatusException.class, () -> controller.download(2, null));
        verifyNoInteractions(service);
    }
    @Test void profileUpdateCannotChangeCredentials() {
        UserInfo input = new UserInfo(); input.setId(1); input.setUsername("admin"); input.setPassword("attacker"); input.setNick("name");
        controller.updateUser(input);
        var capture = org.mockito.ArgumentCaptor.forClass(UserInfo.class);
        verify(service).updateById(capture.capture());
        assertNull(capture.getValue().getPassword());
        assertNull(capture.getValue().getUsername());
        assertEquals("name", capture.getValue().getNick());
    }
    @Test void listNeverLoadsAllUsers() {
        controller.users();
        verify(service, never()).getUserList();
        verify(service).getUserById(1);
    }
    @Test void serializingUserNeverExposesPassword() throws Exception {
        UserInfo input = new UserInfo(); input.setPassword("secret"); input.setId(1);
        assertFalse(new ObjectMapper().writeValueAsString(input).contains("secret"));
    }
}

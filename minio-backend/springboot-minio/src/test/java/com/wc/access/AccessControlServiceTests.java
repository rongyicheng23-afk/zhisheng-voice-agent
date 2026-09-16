package com.wc.access;

import com.wc.entity.UserRoleAssignment;
import com.wc.mapper.UserInfoMapper;
import com.wc.mapper.UserRoleAssignmentMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccessControlServiceTests {
    @Test
    void ordinaryUserDoesNotReceiveAdministrativeCapabilities() {
        UserRoleAssignmentMapper assignments = mock(UserRoleAssignmentMapper.class);
        when(assignments.selectList(any())).thenReturn(List.of());
        AccessControlService service = new AccessControlService(assignments, mock(UserInfoMapper.class), "");

        assertFalse(service.canManageKnowledge(7));
        assertFalse(service.canObserveSystem(7));
        assertFalse(service.canManageUsers(7));
    }

    @Test
    void superAdminReceivesEveryAdministrativeCapability() {
        UserRoleAssignmentMapper assignments = mock(UserRoleAssignmentMapper.class);
        UserRoleAssignment role = new UserRoleAssignment();
        role.setUserId(7);
        role.setRoleCode(AppRole.SUPER_ADMIN.name());
        when(assignments.selectList(any())).thenReturn(List.of(role));
        AccessControlService service = new AccessControlService(assignments, mock(UserInfoMapper.class), "");

        assertTrue(service.canManageKnowledge(7));
        assertTrue(service.canObserveSystem(7));
        assertTrue(service.canManageUsers(7));
    }
}

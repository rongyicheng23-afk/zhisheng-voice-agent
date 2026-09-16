package com.wc.access;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wc.entity.UserInfo;
import com.wc.entity.UserRoleAssignment;
import com.wc.mapper.UserInfoMapper;
import com.wc.mapper.UserRoleAssignmentMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
public class AccessControlService {
    private final UserRoleAssignmentMapper assignments;
    private final UserInfoMapper users;
    private final String bootstrapAdminUsername;

    public AccessControlService(UserRoleAssignmentMapper assignments, UserInfoMapper users,
                                @Value("${app.bootstrap-admin-username:}") String bootstrapAdminUsername) {
        this.assignments = assignments;
        this.users = users;
        this.bootstrapAdminUsername = bootstrapAdminUsername == null ? "" : bootstrapAdminUsername.trim();
    }

    public Set<AppRole> rolesFor(Integer userId) {
        bootstrapConfiguredAdmin(userId);
        EnumSet<AppRole> result = EnumSet.of(AppRole.USER);
        assignments.selectList(new LambdaQueryWrapper<UserRoleAssignment>()
                .eq(UserRoleAssignment::getUserId, userId)).forEach(item -> {
                    try { result.add(AppRole.valueOf(item.getRoleCode())); } catch (IllegalArgumentException ignored) { }
                });
        return result;
    }

    public boolean canManageKnowledge(Integer userId) {
        Set<AppRole> roles = rolesFor(userId);
        return roles.contains(AppRole.KNOWLEDGE_ADMIN) || roles.contains(AppRole.SUPER_ADMIN);
    }

    public boolean canObserveSystem(Integer userId) {
        Set<AppRole> roles = rolesFor(userId);
        return roles.contains(AppRole.OPS_ADMIN) || roles.contains(AppRole.SUPER_ADMIN);
    }

    public boolean canManageUsers(Integer userId) { return rolesFor(userId).contains(AppRole.SUPER_ADMIN); }

    public void requireKnowledgeManager(Integer userId) {
        if (!canManageKnowledge(userId)) throw new AccessDeniedException("需要资料管理员权限");
    }

    public void requireOpsObserver(Integer userId) {
        if (!canObserveSystem(userId)) throw new AccessDeniedException("需要运维管理员权限");
    }

    public void requireSuperAdmin(Integer userId) {
        if (!canManageUsers(userId)) throw new AccessDeniedException("需要超级管理员权限");
    }

    @Transactional
    public void setRoles(Integer targetUserId, List<String> requestedRoles) {
        if (targetUserId == null || users.selectById(targetUserId) == null) throw new IllegalArgumentException("用户不存在");
        EnumSet<AppRole> next = EnumSet.of(AppRole.USER);
        if (requestedRoles != null) for (String value : requestedRoles) {
            try { next.add(AppRole.valueOf(value)); }
            catch (Exception error) { throw new IllegalArgumentException("存在未知角色：" + value); }
        }
        boolean currentlySuper = rolesFor(targetUserId).contains(AppRole.SUPER_ADMIN);
        if (currentlySuper && !next.contains(AppRole.SUPER_ADMIN) && superAdminCount() <= 1) {
            throw new IllegalArgumentException("至少要保留一名超级管理员");
        }
        assignments.delete(new LambdaQueryWrapper<UserRoleAssignment>().eq(UserRoleAssignment::getUserId, targetUserId));
        for (AppRole role : next) insertRole(targetUserId, role);
    }

    public void assignDefaultRole(Integer userId) {
        if (assignments.selectCount(new LambdaQueryWrapper<UserRoleAssignment>().eq(UserRoleAssignment::getUserId, userId)) == 0) {
            insertRole(userId, AppRole.USER);
        }
    }

    private void bootstrapConfiguredAdmin(Integer userId) {
        if (bootstrapAdminUsername.isEmpty()) return;
        UserInfo user = users.selectById(userId);
        if (user == null || !bootstrapAdminUsername.equals(user.getUsername())) return;
        if (!rolesForRaw(userId).contains(AppRole.SUPER_ADMIN)) {
            insertRole(userId, AppRole.SUPER_ADMIN);
        }
    }

    private Set<AppRole> rolesForRaw(Integer userId) {
        EnumSet<AppRole> roles = EnumSet.of(AppRole.USER);
        assignments.selectList(new LambdaQueryWrapper<UserRoleAssignment>().eq(UserRoleAssignment::getUserId, userId))
                .forEach(item -> { try { roles.add(AppRole.valueOf(item.getRoleCode())); } catch (IllegalArgumentException ignored) { } });
        return roles;
    }

    private long superAdminCount() {
        return assignments.selectCount(new LambdaQueryWrapper<UserRoleAssignment>().eq(UserRoleAssignment::getRoleCode, AppRole.SUPER_ADMIN.name()));
    }

    private void insertRole(Integer userId, AppRole role) {
        Long count = assignments.selectCount(new LambdaQueryWrapper<UserRoleAssignment>()
                .eq(UserRoleAssignment::getUserId, userId).eq(UserRoleAssignment::getRoleCode, role.name()));
        if (count != null && count > 0) return;
        UserRoleAssignment assignment = new UserRoleAssignment();
        assignment.setUserId(userId);
        assignment.setRoleCode(role.name());
        assignment.setCreateTime(new Date());
        assignments.insert(assignment);
    }
}

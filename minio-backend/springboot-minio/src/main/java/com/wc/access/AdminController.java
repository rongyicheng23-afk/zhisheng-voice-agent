package com.wc.access;

import com.wc.entity.UserInfo;
import com.wc.result.result.R;
import com.wc.service.UserInfoService;
import com.wc.utils.AuthContextUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AccessControlService access;
    private final UserInfoService users;
    public AdminController(AccessControlService access, UserInfoService users) { this.access = access; this.users = users; }

    @GetMapping("/me")
    public R me() {
        Integer userId = AuthContextUtil.currentUserId();
        return R.OK(Map.of("roles", access.rolesFor(userId).stream().map(Enum::name).sorted().toList(),
                "canManageKnowledge", access.canManageKnowledge(userId), "canObserveSystem", access.canObserveSystem(userId),
                "canManageUsers", access.canManageUsers(userId)));
    }

    @GetMapping("/users")
    public R listUsers() {
        try {
            access.requireSuperAdmin(AuthContextUtil.currentUserId());
            List<Map<String, Object>> result = users.getUserList().stream().map(user -> Map.<String, Object>of(
                    "id", user.getId(), "username", user.getUsername(), "nickname", user.getNick() == null ? user.getUsername() : user.getNick(),
                    "roles", access.rolesFor(user.getId()).stream().map(Enum::name).sorted().toList())).toList();
            return R.OK(result);
        } catch (AccessDeniedException error) { return new R(403, error.getMessage(), null); }
    }

    @PutMapping("/users/{userId}/roles")
    public R updateRoles(@PathVariable Integer userId, @RequestBody RoleUpdateRequest request) {
        try {
            access.requireSuperAdmin(AuthContextUtil.currentUserId());
            access.setRoles(userId, request.roleCodes());
            return R.OK(Map.of("roles", access.rolesFor(userId).stream().map(Enum::name).sorted().toList()));
        } catch (AccessDeniedException error) { return new R(403, error.getMessage(), null); }
        catch (IllegalArgumentException error) { return new R(400, error.getMessage(), null); }
    }

    public record RoleUpdateRequest(List<String> roleCodes) { }
}

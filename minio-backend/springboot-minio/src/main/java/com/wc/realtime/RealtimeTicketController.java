package com.wc.realtime;

import com.wc.service.UserInfoService;
import com.wc.utils.AuthContextUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class RealtimeTicketController {
    private final RealtimeTickets tickets;
    private final RealtimeOrigins origins;
    private final UserInfoService users;
    public RealtimeTicketController(RealtimeTickets tickets, RealtimeOrigins origins, UserInfoService users) {
        this.tickets = tickets; this.origins = origins; this.users = users;
    }
    @PostMapping("/api/realtime/ticket")
    public ResponseEntity<?> issue(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (!origins.allows(origin)) return response(403, "来源不允许", null);
        int userId = AuthContextUtil.currentUserId();
        if (users.getUserById(userId) == null) return response(401, "登录已失效", null);
        try {
            return response(200, "成功", Map.of("ticket", tickets.issue(userId, origin),
                    "expiresInSeconds", 30, "purpose", "funasr"));
        } catch (IllegalStateException busy) {
            return response(429, "连接请求过于频繁，请稍后重试", null);
        }
    }
    private ResponseEntity<?> response(int status, String message, Object data) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("code", status); body.put("msg", message); body.put("data", data);
        return ResponseEntity.status(status).header("Cache-Control", "no-store").body(body);
    }
}

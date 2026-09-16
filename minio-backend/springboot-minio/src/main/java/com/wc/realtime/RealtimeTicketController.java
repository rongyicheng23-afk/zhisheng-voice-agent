package com.wc.realtime;

import com.wc.service.UserInfoService;
import com.wc.utils.AuthContextUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Issues purpose-specific one-time tickets without placing JWTs in WebSocket URLs. */
@RestController
public class RealtimeTicketController {
    private final RealtimeTickets funasrTickets;
    private final RealtimeTicketService gatewayTickets;
    private final RealtimeOrigins origins;
    private final UserInfoService users;

    public RealtimeTicketController(RealtimeTickets funasrTickets,
                                    RealtimeTicketService gatewayTickets,
                                    RealtimeOrigins origins,
                                    UserInfoService users) {
        this.funasrTickets = funasrTickets;
        this.gatewayTickets = gatewayTickets;
        this.origins = origins;
        this.users = users;
    }

    @PostMapping("/api/realtime/ticket")
    public ResponseEntity<?> issueFunasrTicket(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (!origins.allows(origin)) return response(403, "来源不允许", null);
        int userId = verifiedUserId();
        if (userId <= 0) return response(401, "登录已失效", null);
        try {
            return response(200, "成功", Map.of("ticket", funasrTickets.issue(userId, origin),
                    "expiresInSeconds", 30, "purpose", "funasr"));
        } catch (IllegalStateException busy) {
            return response(429, "连接请求过于频繁，请稍后重试", null);
        }
    }

    @PostMapping("/api/realtime/tickets")
    public ResponseEntity<RealtimeTicketService.Issued> issueGatewayTicket(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (!origins.allows(origin)) return ResponseEntity.status(403).build();
        int userId = verifiedUserId();
        if (userId <= 0) return ResponseEntity.status(401).build();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(gatewayTickets.issue(userId, origin));
    }

    private int verifiedUserId() {
        int userId = AuthContextUtil.currentUserId();
        return users.getUserById(userId) == null ? -1 : userId;
    }

    private ResponseEntity<?> response(int status, String message, Object data) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("code", status);
        body.put("msg", message);
        body.put("data", data);
        return ResponseEntity.status(status).header("Cache-Control", "no-store").body(body);
    }
}

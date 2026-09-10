package com.wc.realtime;
import com.wc.utils.AuthContextUtil;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
@RestController
@RequestMapping("/api/realtime")
public class RealtimeTicketController {
    private final RealtimeTicketService service;
    public RealtimeTicketController(RealtimeTicketService service) { this.service = service; }
    @PostMapping("/tickets")
    public ResponseEntity<RealtimeTicketService.Issued> issue() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.issue(AuthContextUtil.currentUserId()));
    }
}

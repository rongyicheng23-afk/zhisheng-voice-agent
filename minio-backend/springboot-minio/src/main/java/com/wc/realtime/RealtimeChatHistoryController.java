package com.wc.realtime;

import com.wc.entity.UserRealtimeChatMessage;
import com.wc.result.result.R;
import com.wc.service.UserRealtimeChatHistoryService;
import com.wc.utils.AuthContextUtil;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/realtime/history")
public class RealtimeChatHistoryController {
    private final UserRealtimeChatHistoryService historyService;

    public RealtimeChatHistoryController(UserRealtimeChatHistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping
    public R list(@RequestParam(value = "limit", defaultValue = "100") int limit) {
        List<UserRealtimeChatMessage> messages = historyService.list(AuthContextUtil.currentUserId(), limit);
        Collections.reverse(messages);
        return R.OK(messages);
    }

    @PostMapping
    public R save(@RequestBody SaveRequest request) {
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求不能为空");
        try {
            return R.OK(historyService.save(
                    AuthContextUtil.currentUserId(), request.role(), request.content(), request.inputMode()));
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage());
        }
    }

    @DeleteMapping
    public R clear() {
        historyService.clear(AuthContextUtil.currentUserId());
        return R.OK();
    }

    public record SaveRequest(String role, String content, String inputMode) { }
}

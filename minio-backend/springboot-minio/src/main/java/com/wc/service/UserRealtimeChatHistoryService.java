package com.wc.service;

import com.wc.entity.UserRealtimeChatMessage;

import java.util.List;

public interface UserRealtimeChatHistoryService {
    UserRealtimeChatMessage save(Integer userId, String role, String content, String inputMode);
    List<UserRealtimeChatMessage> list(Integer userId, int limit);
    void clear(Integer userId);
}

package com.wc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wc.entity.UserRealtimeChatMessage;
import com.wc.mapper.UserRealtimeChatMessageMapper;
import com.wc.service.UserRealtimeChatHistoryService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;

@Service
public class UserRealtimeChatHistoryServiceImpl
        extends ServiceImpl<UserRealtimeChatMessageMapper, UserRealtimeChatMessage>
        implements UserRealtimeChatHistoryService {

    private static final int MAX_CONTENT_LENGTH = 12000;

    @Override
    public UserRealtimeChatMessage save(Integer userId, String role, String content, String inputMode) {
        if (userId == null) throw new IllegalArgumentException("未登录或登录已过期");
        if (!"user".equals(role) && !"assistant".equals(role)) throw new IllegalArgumentException("无效的消息角色");
        if (!StringUtils.hasText(content)) throw new IllegalArgumentException("消息内容不能为空");
        if (content.length() > MAX_CONTENT_LENGTH) throw new IllegalArgumentException("单条消息不能超过12000个字符");

        UserRealtimeChatMessage message = new UserRealtimeChatMessage();
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content.trim());
        message.setInputMode("voice".equals(inputMode) ? "voice" : "text");
        message.setCreateTime(new Date());
        save(message);
        return message;
    }

    @Override
    public List<UserRealtimeChatMessage> list(Integer userId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return list(new LambdaQueryWrapper<UserRealtimeChatMessage>()
                .eq(UserRealtimeChatMessage::getUserId, userId)
                .orderByDesc(UserRealtimeChatMessage::getCreateTime)
                .orderByDesc(UserRealtimeChatMessage::getId)
                .last("LIMIT " + safeLimit));
    }

    @Override
    public void clear(Integer userId) {
        remove(new LambdaQueryWrapper<UserRealtimeChatMessage>()
                .eq(UserRealtimeChatMessage::getUserId, userId));
    }
}

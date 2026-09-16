package com.wc.tts.model;

import org.springframework.web.multipart.MultipartFile;

public final class TtsRequestLimits {
    public static final long MAX_REQUEST_BYTES = 20L * 1024 * 1024;
    private TtsRequestLimits() {}

    public static void validateText(String text) {
        if (text == null || text.strip().isEmpty()) {
            throw new IllegalArgumentException("请输入要合成的文本");
        }
        String trimmed = text.strip();
        if (trimmed.codePointCount(0, trimmed.length()) > 500) {
            throw new IllegalArgumentException("文本不能超过500字符，请按语义分段提交");
        }
    }

    public static void validateAudio(MultipartFile audio) {
        if (audio == null || audio.isEmpty()) {
            throw new IllegalArgumentException("请上传非空参考音频");
        }
        if (audio.getSize() >= MAX_REQUEST_BYTES) {
            throw new IllegalArgumentException("参考音频须小于20 MB，完整上传请求不能超过20 MB");
        }
    }
}

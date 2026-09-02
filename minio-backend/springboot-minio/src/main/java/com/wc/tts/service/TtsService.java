package com.wc.tts.service;

import com.wc.tts.model.TtsSynthesisResult;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

public interface TtsService {

    Map<String, Object> health();

    TtsSynthesisResult synthesize(
            MultipartFile audio,
            String text,
            String emotion,
            String language,
            String format
    ) throws IOException;
}

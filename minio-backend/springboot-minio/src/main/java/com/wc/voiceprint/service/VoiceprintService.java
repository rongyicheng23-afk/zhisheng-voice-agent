package com.wc.voiceprint.service;

import com.wc.voiceprint.model.VoiceprintCompareResult;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

public interface VoiceprintService {

    Map<String, Object> health();

    VoiceprintCompareResult compare(MultipartFile file1, MultipartFile file2) throws IOException;
}

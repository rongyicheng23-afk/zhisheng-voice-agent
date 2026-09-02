package com.wc.funasr.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

public interface FunasrService {

    Map<String, Object> health();

    JsonNode transcribeAudio(MultipartFile file, Integer batchSizeS, String hotword) throws IOException;

    Map<String, Object> transcribeRealtimePcm(
            MultipartFile file,
            String wavName,
            String mode,
            String chunkSize,
            int chunkInterval,
            int encoderChunkLookBack,
            int decoderChunkLookBack,
            String hotwords
    ) throws Exception;
}

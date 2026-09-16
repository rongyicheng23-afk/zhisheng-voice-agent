package com.wc.tts.model;

import java.io.IOException;

/** Only fixed, user-safe messages may cross the API/history boundary. */
public final class TtsUpstreamException extends IOException {
    private final int code;
    public TtsUpstreamException(int code, String message) {
        super(message);
        this.code = code;
    }
    public int getCode() { return code; }
}

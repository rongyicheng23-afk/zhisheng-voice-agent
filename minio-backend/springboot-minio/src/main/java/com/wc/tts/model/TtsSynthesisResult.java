package com.wc.tts.model;

public class TtsSynthesisResult {

    private byte[] audioBytes;
    private String contentType;
    private String upstreamFilename;
    private long contentLength;

    public byte[] getAudioBytes() {
        return audioBytes;
    }

    public void setAudioBytes(byte[] audioBytes) {
        this.audioBytes = audioBytes;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getUpstreamFilename() {
        return upstreamFilename;
    }

    public void setUpstreamFilename(String upstreamFilename) {
        this.upstreamFilename = upstreamFilename;
    }

    public long getContentLength() {
        return contentLength;
    }

    public void setContentLength(long contentLength) {
        this.contentLength = contentLength;
    }
}

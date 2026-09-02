package com.wc.voiceprint.model;

import java.math.BigDecimal;

public class VoiceprintCompareResult {

    private String status;
    private String file1Name;
    private String file2Name;
    private BigDecimal score;
    private BigDecimal threshold;
    private Boolean samePerson;
    private String message;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getFile1Name() {
        return file1Name;
    }

    public void setFile1Name(String file1Name) {
        this.file1Name = file1Name;
    }

    public String getFile2Name() {
        return file2Name;
    }

    public void setFile2Name(String file2Name) {
        this.file2Name = file2Name;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public BigDecimal getThreshold() {
        return threshold;
    }

    public void setThreshold(BigDecimal threshold) {
        this.threshold = threshold;
    }

    public Boolean getSamePerson() {
        return samePerson;
    }

    public void setSamePerson(Boolean samePerson) {
        this.samePerson = samePerson;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}

package com.wc.meeting.model;

import java.util.List;

public class MeetingCorrectionRequest {

    private String title;
    private String summaryText;
    private List<String> keywords;
    private List<String> todos;
    private String fullTranscript;
    private List<MeetingSegmentCorrectionItem> speakerSegments;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummaryText() {
        return summaryText;
    }

    public void setSummaryText(String summaryText) {
        this.summaryText = summaryText;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public void setKeywords(List<String> keywords) {
        this.keywords = keywords;
    }

    public List<String> getTodos() {
        return todos;
    }

    public void setTodos(List<String> todos) {
        this.todos = todos;
    }

    public String getFullTranscript() {
        return fullTranscript;
    }

    public void setFullTranscript(String fullTranscript) {
        this.fullTranscript = fullTranscript;
    }

    public List<MeetingSegmentCorrectionItem> getSpeakerSegments() {
        return speakerSegments;
    }

    public void setSpeakerSegments(List<MeetingSegmentCorrectionItem> speakerSegments) {
        this.speakerSegments = speakerSegments;
    }
}

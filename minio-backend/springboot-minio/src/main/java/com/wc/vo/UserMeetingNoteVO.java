package com.wc.vo;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.Date;
import java.util.List;

public class UserMeetingNoteVO {

    private Integer id;
    private Integer userId;
    private String title;
    private String sceneType;
    private String rawBucket;
    private String rawObject;
    private String rawFilename;
    private String rawContentType;
    private Long rawFileSize;
    private String fullTranscript;
    private String summaryText;
    private List<String> keywords;
    private List<String> todos;
    private List<UserMeetingInsightSectionVO> structuredSections;
    private List<UserMeetingRoleInsightVO> roleInsights;
    private List<UserMeetingTodoChainVO> todoChains;
    private List<UserMeetingDecisionInsightVO> decisionInsights;
    private String speakerTranscript;
    private List<UserMeetingSpeakerBlockVO> speakerBlocks;
    private List<UserMeetingSegmentVO> speakerSegments;
    private String status;
    private String processingStage;
    private String processingLabel;
    private String processingDescription;
    private Integer processingPercent;
    private String errorMessage;
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
    private Boolean hasRawAudio;
    private String rawAudioUrl;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSceneType() {
        return sceneType;
    }

    public void setSceneType(String sceneType) {
        this.sceneType = sceneType;
    }

    public String getRawBucket() {
        return rawBucket;
    }

    public void setRawBucket(String rawBucket) {
        this.rawBucket = rawBucket;
    }

    public String getRawObject() {
        return rawObject;
    }

    public void setRawObject(String rawObject) {
        this.rawObject = rawObject;
    }

    public String getRawFilename() {
        return rawFilename;
    }

    public void setRawFilename(String rawFilename) {
        this.rawFilename = rawFilename;
    }

    public String getRawContentType() {
        return rawContentType;
    }

    public void setRawContentType(String rawContentType) {
        this.rawContentType = rawContentType;
    }

    public Long getRawFileSize() {
        return rawFileSize;
    }

    public void setRawFileSize(Long rawFileSize) {
        this.rawFileSize = rawFileSize;
    }

    public String getFullTranscript() {
        return fullTranscript;
    }

    public void setFullTranscript(String fullTranscript) {
        this.fullTranscript = fullTranscript;
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

    public List<UserMeetingInsightSectionVO> getStructuredSections() {
        return structuredSections;
    }

    public void setStructuredSections(List<UserMeetingInsightSectionVO> structuredSections) {
        this.structuredSections = structuredSections;
    }

    public List<UserMeetingRoleInsightVO> getRoleInsights() {
        return roleInsights;
    }

    public void setRoleInsights(List<UserMeetingRoleInsightVO> roleInsights) {
        this.roleInsights = roleInsights;
    }

    public List<UserMeetingTodoChainVO> getTodoChains() {
        return todoChains;
    }

    public void setTodoChains(List<UserMeetingTodoChainVO> todoChains) {
        this.todoChains = todoChains;
    }

    public List<UserMeetingDecisionInsightVO> getDecisionInsights() {
        return decisionInsights;
    }

    public void setDecisionInsights(List<UserMeetingDecisionInsightVO> decisionInsights) {
        this.decisionInsights = decisionInsights;
    }

    public String getSpeakerTranscript() {
        return speakerTranscript;
    }

    public void setSpeakerTranscript(String speakerTranscript) {
        this.speakerTranscript = speakerTranscript;
    }

    public List<UserMeetingSpeakerBlockVO> getSpeakerBlocks() {
        return speakerBlocks;
    }

    public void setSpeakerBlocks(List<UserMeetingSpeakerBlockVO> speakerBlocks) {
        this.speakerBlocks = speakerBlocks;
    }

    public List<UserMeetingSegmentVO> getSpeakerSegments() {
        return speakerSegments;
    }

    public void setSpeakerSegments(List<UserMeetingSegmentVO> speakerSegments) {
        this.speakerSegments = speakerSegments;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getProcessingStage() {
        return processingStage;
    }

    public void setProcessingStage(String processingStage) {
        this.processingStage = processingStage;
    }

    public String getProcessingLabel() {
        return processingLabel;
    }

    public void setProcessingLabel(String processingLabel) {
        this.processingLabel = processingLabel;
    }

    public String getProcessingDescription() {
        return processingDescription;
    }

    public void setProcessingDescription(String processingDescription) {
        this.processingDescription = processingDescription;
    }

    public Integer getProcessingPercent() {
        return processingPercent;
    }

    public void setProcessingPercent(Integer processingPercent) {
        this.processingPercent = processingPercent;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public Boolean getHasRawAudio() {
        return hasRawAudio;
    }

    public void setHasRawAudio(Boolean hasRawAudio) {
        this.hasRawAudio = hasRawAudio;
    }

    public String getRawAudioUrl() {
        return rawAudioUrl;
    }

    public void setRawAudioUrl(String rawAudioUrl) {
        this.rawAudioUrl = rawAudioUrl;
    }
}

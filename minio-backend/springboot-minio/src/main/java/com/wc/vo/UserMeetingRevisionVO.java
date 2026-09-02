package com.wc.vo;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.Date;
import java.util.List;

public class UserMeetingRevisionVO {

    private Integer id;
    private Integer meetingId;
    private Integer versionNo;
    private String revisionType;
    private String title;
    private String summaryText;
    private List<String> keywords;
    private List<String> todos;
    private String fullTranscript;
    private List<UserMeetingRoleInsightVO> roleInsights;
    private List<UserMeetingTodoChainVO> todoChains;
    private List<UserMeetingDecisionInsightVO> decisionInsights;
    private String speakerTranscript;
    private List<UserMeetingSpeakerBlockVO> speakerBlocks;
    private List<UserMeetingSegmentVO> speakerSegments;
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getMeetingId() {
        return meetingId;
    }

    public void setMeetingId(Integer meetingId) {
        this.meetingId = meetingId;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public String getRevisionType() {
        return revisionType;
    }

    public void setRevisionType(String revisionType) {
        this.revisionType = revisionType;
    }

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

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}

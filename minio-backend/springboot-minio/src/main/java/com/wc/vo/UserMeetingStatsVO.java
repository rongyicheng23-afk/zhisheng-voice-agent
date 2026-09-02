package com.wc.vo;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.Date;

public class UserMeetingStatsVO {

    private Integer totalNotes;
    private Integer meetingNotes;
    private Integer classroomNotes;
    private Integer successNotes;
    private Integer failedNotes;
    private Integer speakerProfiles;
    private Integer totalSegments;
    private Integer totalTodos;
    private Integer recentSevenDaysNotes;
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date latestCreateTime;

    public Integer getTotalNotes() {
        return totalNotes;
    }

    public void setTotalNotes(Integer totalNotes) {
        this.totalNotes = totalNotes;
    }

    public Integer getMeetingNotes() {
        return meetingNotes;
    }

    public void setMeetingNotes(Integer meetingNotes) {
        this.meetingNotes = meetingNotes;
    }

    public Integer getClassroomNotes() {
        return classroomNotes;
    }

    public void setClassroomNotes(Integer classroomNotes) {
        this.classroomNotes = classroomNotes;
    }

    public Integer getSuccessNotes() {
        return successNotes;
    }

    public void setSuccessNotes(Integer successNotes) {
        this.successNotes = successNotes;
    }

    public Integer getFailedNotes() {
        return failedNotes;
    }

    public void setFailedNotes(Integer failedNotes) {
        this.failedNotes = failedNotes;
    }

    public Integer getSpeakerProfiles() {
        return speakerProfiles;
    }

    public void setSpeakerProfiles(Integer speakerProfiles) {
        this.speakerProfiles = speakerProfiles;
    }

    public Integer getTotalSegments() {
        return totalSegments;
    }

    public void setTotalSegments(Integer totalSegments) {
        this.totalSegments = totalSegments;
    }

    public Integer getTotalTodos() {
        return totalTodos;
    }

    public void setTotalTodos(Integer totalTodos) {
        this.totalTodos = totalTodos;
    }

    public Integer getRecentSevenDaysNotes() {
        return recentSevenDaysNotes;
    }

    public void setRecentSevenDaysNotes(Integer recentSevenDaysNotes) {
        this.recentSevenDaysNotes = recentSevenDaysNotes;
    }

    public Date getLatestCreateTime() {
        return latestCreateTime;
    }

    public void setLatestCreateTime(Date latestCreateTime) {
        this.latestCreateTime = latestCreateTime;
    }
}

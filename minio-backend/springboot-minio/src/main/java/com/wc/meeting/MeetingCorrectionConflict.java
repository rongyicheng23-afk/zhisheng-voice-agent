package com.wc.meeting;

public class MeetingCorrectionConflict extends IllegalArgumentException {
    public MeetingCorrectionConflict() {
        super("纪要已被更新或缺少校正版本，请保留草稿，重新打开最新纪要核对后再保存");
    }
}

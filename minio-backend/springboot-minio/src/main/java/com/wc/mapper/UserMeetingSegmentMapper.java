package com.wc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wc.entity.UserMeetingSegment;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface UserMeetingSegmentMapper extends BaseMapper<UserMeetingSegment> {
    // Explicit NULL assignments are required: updateById normally skips null fields.
    @Update({"<script>",
            "UPDATE t_user_meeting_segment SET speaker_name = #{segment.speakerName},",
            "transcript = #{segment.transcript}, update_time = #{segment.updateTime}",
            "<if test='renamed'>, speaker_profile_id = NULL, match_score = NULL</if>",
            "WHERE id = #{segment.id} AND meeting_id = #{segment.meetingId}",
            "</script>"})
    int updateCorrection(@Param("segment") UserMeetingSegment segment, @Param("renamed") boolean renamed);
}

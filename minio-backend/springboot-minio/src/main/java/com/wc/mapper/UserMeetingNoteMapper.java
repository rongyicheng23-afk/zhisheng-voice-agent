package com.wc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wc.entity.UserMeetingNote;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface UserMeetingNoteMapper extends BaseMapper<UserMeetingNote> {
    @Select("SELECT * FROM t_user_meeting_note WHERE id = #{id} AND uid = #{userId} FOR UPDATE")
    UserMeetingNote selectOwnedForCorrection(@Param("id") Integer id, @Param("userId") Integer userId);
}

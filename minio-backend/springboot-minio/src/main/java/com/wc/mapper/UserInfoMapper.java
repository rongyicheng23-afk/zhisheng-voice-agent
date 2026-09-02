package com.wc.mapper;

import com.wc.entity.UserInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.List;

/**
* @author apple
* @description 针对表【t_user_info】的数据库操作Mapper
* @createDate 2024-12-24 20:04:40
* @Entity com.wc.entity.UserInfo
*/
public interface UserInfoMapper extends BaseMapper<UserInfo> {

    UserInfo selectUserById(Integer id);

    List<UserInfo> selectUserList();
}





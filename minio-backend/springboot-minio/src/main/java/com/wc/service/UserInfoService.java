package com.wc.service;

import com.wc.entity.UserInfo;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
* @author apple
* @description 针对表【t_user_info】的数据库操作Service
* @createDate 2024-12-24 20:04:40
*/
public interface UserInfoService extends IService<UserInfo> {

    List<UserInfo> getUserList();

    UserInfo getUserById(Integer id);

    UserInfo getUserByUsername(String username);

    UserInfo registerUser(String username, String password, String nickname, String email);

    boolean deleteUserById(Integer id)throws Exception;
}

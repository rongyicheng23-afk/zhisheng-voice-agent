package com.wc.service;

import com.wc.entity.UserImage;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author apple
* @description 针对表【t_user_image】的数据库操作Service
* @createDate 2024-12-24 20:05:59
*/
public interface UserImageService extends IService<UserImage> {
    boolean saveOrUpdateUserImage(Integer uid,String bucket,String object);
}

package com.wc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wc.entity.UserContract;
import com.wc.entity.UserImage;
import com.wc.entity.UserInfo;
import com.wc.mapper.UserContractMapper;
import com.wc.mapper.UserImageMapper;
import com.wc.service.UserInfoService;
import com.wc.mapper.UserInfoMapper;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
* @author apple
* @description 针对表【t_user_info】的数据库操作Service实现
* @createDate 2024-12-24 20:04:40
*/
@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo>
    implements UserInfoService{

    @Resource
    private MinioClient minioClient;
    @Resource
    private UserInfoMapper userInfoMapper;
    @Resource
    private UserImageMapper userImageMapper;
    @Resource
    private UserContractMapper userContractMapper;

    @Override
    public List<UserInfo> getUserList() {
        return userInfoMapper.selectUserList();
    }

    @Override
    public UserInfo getUserById(Integer id) {
        return userInfoMapper.selectUserById(id);
    }

    @Override
    public UserInfo getUserByUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return null;
        }
        return lambdaQuery()
                .eq(UserInfo::getUsername, username.trim())
                .one();
    }

    @Override
    public UserInfo registerUser(String username, String password, String nickname, String email) {
        UserInfo userInfo = new UserInfo();
        userInfo.setUsername(username);
        userInfo.setNick(StringUtils.hasText(nickname) ? nickname : username);
        userInfo.setPassword(password);
        userInfo.setEmail(StringUtils.hasText(email) ? email.trim() : null);
        userInfo.setSex(1);
        userInfo.setPhone("");
        userInfo.setAddress("");
        userInfo.setCreateTime(new Date());
        userInfo.setUpdateTime(new Date());
        save(userInfo);
        return getUserById(userInfo.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean deleteUserById(Integer id)throws Exception {
        //删除3个表的数据 只要用户删除成功了其他的成功不成功也不会显示
        //删除用户
        UserInfo userInfo = userInfoMapper.selectUserById(id);
        int deleteUser = userInfoMapper.deleteById(id);
        //删除头像
        LambdaQueryWrapper<UserImage> imageWrapper = new LambdaQueryWrapper<>();
        imageWrapper.eq(UserImage::getUid,id);
        int deleteImage = userImageMapper.delete(imageWrapper);
        //删除合同
        LambdaQueryWrapper<UserContract> contractWrapper = new LambdaQueryWrapper<>();
        contractWrapper.eq(UserContract::getUid,id);
        int deleteContract = userContractMapper.delete(contractWrapper);
        //删除minio的文件
        //删除头像
        if (userInfo.getUserImageDO().getBucket() != null) {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(userInfo.getUserImageDO().getBucket())
                    .object(userInfo.getUserImageDO().getObject())
                    .build());
        }

        if (userInfo.getUserContractDO().getBucket() != null) {
            //删除合同
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(userInfo.getUserContractDO().getBucket())
                    .object(userInfo.getUserContractDO().getObject())
                    .build());
        }
     return deleteUser >= 1;
    }
}





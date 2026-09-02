package com.wc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wc.entity.UserContract;
import com.wc.entity.UserImage;
import com.wc.service.UserContractService;
import com.wc.mapper.UserContractMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
* @author apple
* @description 针对表【t_user_contract】的数据库操作Service实现
* @createDate 2024-12-24 20:06:06
*/
@Service
public class UserContractServiceImpl extends ServiceImpl<UserContractMapper, UserContract>
    implements UserContractService{


    @Resource
    UserContractMapper userContractMapper;
    @Override
    public boolean saveOrUpdateUserContract(Integer uid, String bucket, String object) {
        boolean flag = false;
        UserContract userContract = new UserContract();
        userContract.setBucket(bucket);
        userContract.setObject(object);
        userContract.setUid(uid);
        LambdaQueryWrapper<UserContract> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserContract::getUid,uid);
        Long count = userContractMapper.selectCount(wrapper);
        if (count <= 0) {
            userContract.setCreateTime(new Date());
            flag = userContractMapper.insert(userContract) >= 1;
        }else {
            userContract.setUpdateTime(new Date());
            flag = userContractMapper.update(userContract,wrapper) >= 1;
        }
        return flag;
    }

}





package com.wc.service;

import com.wc.entity.UserContract;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author apple
* @description 针对表【t_user_contract】的数据库操作Service
* @createDate 2024-12-24 20:06:06
*/
public interface UserContractService extends IService<UserContract> {
    boolean saveOrUpdateUserContract(Integer uid,String bucket,String object);
}

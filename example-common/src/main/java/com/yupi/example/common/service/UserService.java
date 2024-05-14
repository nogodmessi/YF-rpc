package com.yupi.example.common.service;

import com.yupi.example.common.model.User;

/**
 * @author 杨帆
 */
public interface UserService {

    /**
     * 获取用户
     * @param user
     * @return
     */
    User getUser(User user);

    /**
     * 检验 Mock
     * 新方法 - 获取数字
     */
    default short getNumber(){
        return 1;
    }
}

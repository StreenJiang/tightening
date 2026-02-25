package com.tightening.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tightening.entity.UserAccountInfo;
import com.tightening.mapper.UserAccountInfoMapper;
import org.springframework.stereotype.Service;

@Service
public class UserAccountInfoService extends ServiceImpl<UserAccountInfoMapper, UserAccountInfo> {
}

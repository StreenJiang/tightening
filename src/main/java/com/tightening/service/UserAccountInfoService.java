package com.tightening.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tightening.dto.UserAccountInfoDTO;
import com.tightening.entity.UserAccountInfo;
import com.tightening.mapper.UserAccountInfoMapper;
import com.tightening.util.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class UserAccountInfoService extends ServiceImpl<UserAccountInfoMapper, UserAccountInfo> {

    public List<UserAccountInfoDTO> getUserList() {
        List<UserAccountInfo> list = list();
        return Converter.entity2Dto(list, UserAccountInfoDTO::new);
    }

    public UserAccountInfoDTO getUserById(Long id) {
        UserAccountInfoDTO dto = new UserAccountInfoDTO();
        BeanUtils.copyProperties(getById(id), dto);
        return dto;
    }
}

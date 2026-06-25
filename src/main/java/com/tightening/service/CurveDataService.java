package com.tightening.service;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tightening.entity.CurveData;
import com.tightening.mapper.CurveDataMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CurveDataService extends ServiceImpl<CurveDataMapper, CurveData> {

}

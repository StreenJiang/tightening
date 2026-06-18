package com.tightening.service;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tightening.entity.MissionRecord;
import com.tightening.mapper.MissionRecordMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class MissionRecordService extends ServiceImpl<MissionRecordMapper, MissionRecord> {}

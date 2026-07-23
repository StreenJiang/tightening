package com.tightening.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tightening.entity.TaskRecord;

@Mapper
public interface TaskRecordMapper extends BaseMapper<TaskRecord> {}

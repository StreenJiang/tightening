package com.tightening.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tightening.entity.MissionPrerequisite;
import com.tightening.mapper.MissionPrerequisiteMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class MissionPrerequisiteService extends ServiceImpl<MissionPrerequisiteMapper, MissionPrerequisite> {

    public List<MissionPrerequisite> listByMissionId(Long missionId) {
        return lambdaQuery()
                .eq(MissionPrerequisite::getMissionId, missionId)
                .list();
    }
}

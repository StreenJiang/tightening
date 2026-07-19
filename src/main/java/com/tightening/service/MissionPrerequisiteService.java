package com.tightening.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tightening.constant.PrerequisiteType;
import com.tightening.dto.MissionPrerequisiteDTO;
import com.tightening.entity.MissionPrerequisite;
import com.tightening.entity.ProductMission;
import com.tightening.mapper.MissionPrerequisiteMapper;
import com.tightening.mapper.ProductMissionMapper;
import com.tightening.util.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MissionPrerequisiteService extends ServiceImpl<MissionPrerequisiteMapper, MissionPrerequisite> {
    private final ProductMissionMapper missionMapper;

    public MissionPrerequisiteService(ProductMissionMapper missionMapper) {
        this.missionMapper = missionMapper;
    }

    public List<MissionPrerequisiteDTO> listByMissionId(Long missionId) {
        List<MissionPrerequisite> prerequisites = lambdaQuery()
                .eq(MissionPrerequisite::getMissionId, missionId)
                .list();

        if (prerequisites.isEmpty()) return Collections.emptyList();

        List<Long> ids = prerequisites.stream()
                .map(MissionPrerequisite::getPrerequisiteMissionId).toList();
        Map<Long, String> nameMap = missionMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(ProductMission::getId, ProductMission::getName, (a, b) -> a));

        return prerequisites.stream().map(p -> {
            MissionPrerequisiteDTO dto = Converter.entity2Dto(p, MissionPrerequisiteDTO::new);
            dto.setPrerequisiteType(PrerequisiteType.fromCode(p.getPrerequisiteType()));
            dto.setPrerequisiteMissionName(nameMap.get(p.getPrerequisiteMissionId()));
            return dto;
        }).toList();
    }
}

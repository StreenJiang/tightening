package com.tightening.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tightening.constant.BarCodeRuleType;
import com.tightening.entity.BarCodeMatchingRule;
import com.tightening.entity.InspectionMissionBinding;
import com.tightening.entity.MissionPrerequisite;
import com.tightening.entity.ProductMission;
import com.tightening.entity.ProductSide;
import com.tightening.mapper.ProductMissionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductMissionService extends ServiceImpl<ProductMissionMapper, ProductMission> {
    private final MissionConfigValidator validator;
    private final ProductSideService sideService;
    private final MissionPrerequisiteService prerequisiteService;
    private final InspectionMissionBindingService bindingService;
    private final BarCodeMatchingRuleService barcodeRuleService;

    public void addPrerequisite(Long missionId, Long prerequisiteMissionId, Integer prerequisiteType) {
        validator.validateNoCircularDependency(missionId, prerequisiteMissionId);
        ProductMission target = getById(prerequisiteMissionId);
        validator.validatePrerequisiteType(target, prerequisiteType);
        MissionPrerequisite mp = new MissionPrerequisite()
                .setMissionId(missionId)
                .setPrerequisiteMissionId(prerequisiteMissionId)
                .setPrerequisiteType(prerequisiteType);
        prerequisiteService.save(mp);
    }

    public void addInspectionBinding(Long inspectionMissionId, Long boundMissionId) {
        ProductMission bound = getById(boundMissionId);
        validator.validateInspectionBinding(bound);
        InspectionMissionBinding binding = new InspectionMissionBinding()
                .setInspectionMissionId(inspectionMissionId)
                .setBoundMissionId(boundMissionId);
        bindingService.save(binding);
    }

    public void addBarcodeRule(BarCodeMatchingRule rule) {
        validator.validateKeyCharLength(rule);
        if (BarCodeRuleType.PRODUCT_TRACE.getCode() == rule.getRuleType()) {
            validator.validateProductTraceUnique(rule.getProductMissionId(), rule.getId());
        }
        barcodeRuleService.saveOrUpdate(rule);
    }

    @Transactional
    public void cascadeDelete(Long missionId) {
        // Collect all side IDs and bolt IDs for batch deletion
        List<Long> sideIds = sideService.lambdaQuery()
                .select(ProductSide::getId)
                .eq(ProductSide::getProductMissionId, missionId)
                .list().stream().map(ProductSide::getId).toList();

        if (!sideIds.isEmpty()) {
            sideService.deleteBoltsBySideIds(sideIds);
            sideService.lambdaUpdate().in(ProductSide::getId, sideIds).remove();
        }

        prerequisiteService.lambdaUpdate()
                .eq(MissionPrerequisite::getMissionId, missionId).or()
                .eq(MissionPrerequisite::getPrerequisiteMissionId, missionId).remove();
        bindingService.lambdaUpdate()
                .eq(InspectionMissionBinding::getInspectionMissionId, missionId).or()
                .eq(InspectionMissionBinding::getBoundMissionId, missionId).remove();
        barcodeRuleService.lambdaUpdate()
                .eq(BarCodeMatchingRule::getProductMissionId, missionId).remove();
        removeById(missionId);
    }

}

package com.tightening.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tightening.constant.BarCodeRuleType;
import com.tightening.constant.InspectionScope;
import com.tightening.constant.PrerequisiteType;
import com.tightening.dto.BarCodeRuleSaveItem;
import com.tightening.dto.BoltDeviceBindingSaveItem;
import com.tightening.dto.BoltPartsBarcodeSaveItem;
import com.tightening.dto.PrerequisiteSaveItem;
import com.tightening.dto.ProductBoltSaveItem;
import com.tightening.dto.ProductMissionSaveDTO;
import com.tightening.dto.ProductSideSaveItem;
import com.tightening.entity.BarCodeMatchingRule;
import com.tightening.entity.BoltDeviceBinding;
import com.tightening.entity.BoltPartsBarcode;
import com.tightening.entity.InspectionMissionBinding;
import com.tightening.entity.MissionPrerequisite;
import com.tightening.entity.ProductBolt;
import com.tightening.entity.ProductMission;
import com.tightening.entity.ProductSide;
import com.tightening.mapper.ProductMissionMapper;
import com.tightening.util.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ProductMissionService extends ServiceImpl<ProductMissionMapper, ProductMission> {
    private final MissionConfigValidator validator;
    private final ProductSideService sideService;
    private final MissionPrerequisiteService prerequisiteService;
    private final InspectionMissionBindingService bindingService;
    private final BarCodeMatchingRuleService barcodeRuleService;
    private final ProductBoltService boltService;
    private final BoltDeviceBindingService deviceBindingService;
    private final BoltPartsBarcodeService partsBarcodeService;

    public ProductMissionService(MissionConfigValidator validator,
            ProductSideService sideService,
            MissionPrerequisiteService prerequisiteService,
            InspectionMissionBindingService bindingService,
            BarCodeMatchingRuleService barcodeRuleService,
            ProductBoltService boltService,
            BoltDeviceBindingService deviceBindingService,
            BoltPartsBarcodeService partsBarcodeService) {
        this.validator = validator;
        this.sideService = sideService;
        this.prerequisiteService = prerequisiteService;
        this.bindingService = bindingService;
        this.barcodeRuleService = barcodeRuleService;
        this.boltService = boltService;
        this.deviceBindingService = deviceBindingService;
        this.partsBarcodeService = partsBarcodeService;
    }

    public boolean isNameDuplicate(String name, Long excludeId) {
        return lambdaQuery()
                .eq(ProductMission::getName, name)
                .ne(excludeId != null, ProductMission::getId, excludeId)
                .count() > 0;
    }

    public Page<ProductMission> listByPage(int page, int size, String name) {
        int safePage = Math.min(Math.max(1, page), 1000);
        int safeSize = Math.min(Math.max(1, size), 500);
        var wrapper = lambdaQuery()
                .orderByDesc(ProductMission::getId);
        if (name != null && !name.isBlank()) {
            wrapper.like(ProductMission::getName, name);
        }
        return wrapper.page(new Page<>(safePage, safeSize));
    }

    @Transactional
    public ProductMissionSaveDTO saveMission(ProductMissionSaveDTO dto, Map<String, byte[]> imageMap) {
        ProductMission mission = Converter.dto2Entity(dto, ProductMission::new);
        if (mission.getInspectionScope() == null) mission.setInspectionScope(InspectionScope.NONE);
        saveOrUpdate(mission);
        Long missionId = mission.getId();
        dto.setId(missionId);

        validator.validateInspectionScope(mission, dto.getInspectionBoundMissionIds());

        syncInspectionBindings(missionId, dto.getInspectionBoundMissionIds());

        BarcodeDiffResult barcodeResult = diffBarcodeRules(missionId, dto.getBarcodeRules());
        validator.validateBarcodeRules(barcodeResult.rules());

        diffPrerequisites(missionId, dto.getPrerequisites(), barcodeResult);
        validator.validateInspectionChainSelfInspection(mission, dto.getPrerequisites());

        diffSides(missionId, dto.getSides(), imageMap, barcodeResult);

        return dto;
    }

    @Transactional
    public void syncInspectionBindings(Long missionId, List<Long> boundMissionIds) {
        if (boundMissionIds == null || boundMissionIds.isEmpty()) return;
        bindingService.lambdaUpdate()
                .eq(InspectionMissionBinding::getInspectionMissionId, missionId)
                .remove();
        List<ProductMission> bounds = lambdaQuery().in(ProductMission::getId, boundMissionIds).list();
        for (ProductMission bound : bounds) {
            validator.validateInspectionBinding(bound);
            InspectionMissionBinding binding = new InspectionMissionBinding()
                    .setInspectionMissionId(missionId)
                    .setBoundMissionId(bound.getId());
            bindingService.save(binding);
        }
    }

    private BarcodeDiffResult diffBarcodeRules(Long missionId, List<BarCodeRuleSaveItem> dtoItems) {
        List<BarCodeMatchingRule> existing = barcodeRuleService.lambdaQuery()
                .eq(BarCodeMatchingRule::getProductMissionId, missionId)
                .eq(BarCodeMatchingRule::getDeleted, 0)
                .list();

        Set<Long> dtoIds = new HashSet<>();
        List<BarCodeMatchingRule> result = new ArrayList<>();
        Map<String, Long> clientRefMap = new HashMap<>();

        if (dtoItems != null) {
            for (BarCodeRuleSaveItem item : dtoItems) {
                BarCodeMatchingRule entity = Converter.dto2Entity(item, BarCodeMatchingRule::new);
                entity.setProductMissionId(missionId);
                entity.setRuleType(item.getRuleType().getCode());
                validator.validateKeyCharLength(entity);
                if (BarCodeRuleType.PRODUCT_TRACE.getCode() == entity.getRuleType()) {
                    validator.validateProductTraceUnique(missionId, entity.getId());
                }
                if (item.getClientRef() != null && item.getId() != null) {
                    throw new IllegalArgumentException("clientRef 仅用于新增规则，已有规则不可设置");
                }
                if (item.getId() != null) {
                    dtoIds.add(item.getId());
                    barcodeRuleService.updateById(entity);
                } else {
                    barcodeRuleService.save(entity);
                    item.setId(entity.getId());
                    if (item.getClientRef() != null) {
                        clientRefMap.put(item.getClientRef(), entity.getId());
                    }
                }
                result.add(entity);
            }
        }

        for (BarCodeMatchingRule ex : existing) {
            if (!dtoIds.contains(ex.getId())) {
                barcodeRuleService.removeById(ex.getId());
            }
        }

        return new BarcodeDiffResult(result, clientRefMap);
    }

    private void diffPrerequisites(Long missionId, List<PrerequisiteSaveItem> dtoItems, BarcodeDiffResult barcodeResult) {
        List<MissionPrerequisite> existing = prerequisiteService.lambdaQuery()
                .eq(MissionPrerequisite::getMissionId, missionId)
                .eq(MissionPrerequisite::getDeleted, 0)
                .list();

        Set<Long> dtoIds = new HashSet<>();

        if (dtoItems != null && !dtoItems.isEmpty()) {
            // Batch load all target missions
            List<Long> targetIds = dtoItems.stream()
                    .map(PrerequisiteSaveItem::getPrerequisiteMissionId).toList();
            List<ProductMission> targets = lambdaQuery().in(ProductMission::getId, targetIds).list();
            Map<Long, ProductMission> targetMap = targets.stream()
                    .collect(Collectors.toMap(ProductMission::getId, m -> m));

            for (PrerequisiteSaveItem item : dtoItems) {
                MissionPrerequisite entity = Converter.dto2Entity(item, MissionPrerequisite::new);
                entity.setMissionId(missionId);
                entity.setPrerequisiteType(item.getPrerequisiteType().getCode());
                validator.validateNoCircularDependency(missionId, item.getPrerequisiteMissionId());
                validator.validatePrerequisiteType(targetMap.get(item.getPrerequisiteMissionId()), item.getPrerequisiteType());

                resolveAndValidateBarcodeRule(item, entity, barcodeResult);

                if (item.getId() != null) {
                    dtoIds.add(item.getId());
                    prerequisiteService.updateById(entity);
                } else {
                    prerequisiteService.save(entity);
                    item.setId(entity.getId());
                }
            }
        }

        for (MissionPrerequisite ex : existing) {
            if (!dtoIds.contains(ex.getId())) {
                prerequisiteService.removeById(ex.getId());
            }
        }
    }

    private Long resolveBarcodeRef(String barcodeRuleRef, Long barcodeRuleId,
                                    BarcodeDiffResult barcodeResult) {
        if (barcodeRuleRef != null) {
            if (barcodeRuleId != null) {
                throw new IllegalArgumentException("barcodeRuleRef 和 barcodeRuleId 不能同时存在");
            }
            Long resolvedId = barcodeResult.clientRefMap().get(barcodeRuleRef);
            if (resolvedId == null) {
                throw new IllegalArgumentException("找不到 clientRef='" + barcodeRuleRef + "' 对应的条码规则");
            }
            return resolvedId;
        }
        return barcodeRuleId;
    }

    private void resolveAndValidateBarcodeRule(PrerequisiteSaveItem item, MissionPrerequisite entity,
                                               BarcodeDiffResult barcodeResult) {
        Long resolvedId = resolveBarcodeRef(item.getBarcodeRuleRef(), item.getBarcodeRuleId(), barcodeResult);
        entity.setBarcodeRuleId(resolvedId);

        Long ruleId = entity.getBarcodeRuleId();
        BarCodeMatchingRule rule = null;
        if (ruleId != null) {
            rule = barcodeResult.rules().stream()
                    .filter(r -> ruleId.equals(r.getId()))
                    .findFirst().orElse(null);
        }
        validator.validateBarcodeRuleForPrerequisite(rule, item.getPrerequisiteType());
    }

    private void diffSides(Long missionId, List<ProductSideSaveItem> dtoSides,
                            Map<String, byte[]> imageMap, BarcodeDiffResult barcodeResult) {
        List<ProductSide> existingSides = sideService.lambdaQuery()
                .eq(ProductSide::getProductMissionId, missionId)
                .eq(ProductSide::getDeleted, 0)
                .list();

        Set<Long> dtoSideIds = new HashSet<>();

        if (dtoSides != null) {
            for (int i = 0; i < dtoSides.size(); i++) {
                ProductSideSaveItem sideItem = dtoSides.get(i);
                ProductSide sideEntity = Converter.dto2Entity(sideItem, ProductSide::new);
                sideEntity.setProductMissionId(missionId);

                if (sideItem.getId() != null) {
                    dtoSideIds.add(sideItem.getId());
                    sideService.updateById(sideEntity);
                } else {
                    sideService.save(sideEntity);
                    sideItem.setId(sideEntity.getId());
                }

                byte[] image = imageMap != null ? imageMap.get("sides[" + i + "].image") : null;
                byte[] renderedImage = imageMap != null ? imageMap.get("sides[" + i + "].renderedImage") : null;
                byte[] thumbnail = imageMap != null ? imageMap.get("sides[" + i + "].thumbnail") : null;
                if (image != null) sideService.updateImageData(sideEntity.getId(), image);
                if (renderedImage != null) sideService.updateRenderedImageData(sideEntity.getId(), renderedImage);
                if (thumbnail != null) sideService.updateThumbnailData(sideEntity.getId(), thumbnail);

                diffBolts(sideEntity.getId(), missionId, sideItem.getBolts(), barcodeResult);
            }
        }

        for (ProductSide ex : existingSides) {
            if (!dtoSideIds.contains(ex.getId())) {
                sideService.cascadeDelete(ex.getId());
            }
        }
    }

    private void diffBolts(Long sideId, Long missionId, List<ProductBoltSaveItem> dtoBolts,
                            BarcodeDiffResult barcodeResult) {
        List<ProductBolt> existingBolts = boltService.lambdaQuery()
                .eq(ProductBolt::getProductSideId, sideId)
                .eq(ProductBolt::getDeleted, 0)
                .list();

        Set<Long> dtoBoltIds = new HashSet<>();

        if (dtoBolts != null) {
            for (ProductBoltSaveItem boltItem : dtoBolts) {
                ProductBolt boltEntity = Converter.dto2Entity(boltItem, ProductBolt::new);
                boltEntity.setProductSideId(sideId);

                if (boltItem.getId() != null) {
                    dtoBoltIds.add(boltItem.getId());
                    boltService.updateById(boltEntity);
                } else {
                    boltService.saveBolt(boltEntity, missionId);
                    boltItem.setId(boltEntity.getId());
                }

                diffDeviceBindings(boltEntity.getId(), boltItem.getDeviceBindings());
                diffPartsBarcodes(boltEntity.getId(), boltItem.getPartsBarcodes(), barcodeResult);
            }
        }

        for (ProductBolt ex : existingBolts) {
            if (!dtoBoltIds.contains(ex.getId())) {
                boltService.cascadeDelete(ex.getId());
            }
        }
    }

    private void diffDeviceBindings(Long boltId, List<BoltDeviceBindingSaveItem> dtoBindings) {
        List<BoltDeviceBinding> existing = deviceBindingService.lambdaQuery()
                .eq(BoltDeviceBinding::getProductBoltId, boltId)
                .eq(BoltDeviceBinding::getDeleted, 0)
                .list();

        Set<Long> dtoIds = new HashSet<>();

        if (dtoBindings != null) {
            for (BoltDeviceBindingSaveItem item : dtoBindings) {
                BoltDeviceBinding entity = Converter.dto2Entity(item, BoltDeviceBinding::new);
                entity.setProductBoltId(boltId);
                if (item.getId() != null) {
                    dtoIds.add(item.getId());
                    deviceBindingService.updateById(entity);
                } else {
                    deviceBindingService.save(entity);
                    item.setId(entity.getId());
                }
            }
        }

        for (BoltDeviceBinding ex : existing) {
            if (!dtoIds.contains(ex.getId())) {
                deviceBindingService.removeById(ex.getId());
            }
        }
    }

    private void diffPartsBarcodes(Long boltId, List<BoltPartsBarcodeSaveItem> dtoItems,
                                    BarcodeDiffResult barcodeResult) {
        List<BoltPartsBarcode> existing = partsBarcodeService.lambdaQuery()
                .eq(BoltPartsBarcode::getProductBoltId, boltId)
                .eq(BoltPartsBarcode::getDeleted, 0)
                .list();

        Set<Long> dtoIds = new HashSet<>();

        if (dtoItems != null) {
            for (BoltPartsBarcodeSaveItem item : dtoItems) {
                Long resolvedId = resolveBarcodeRef(item.getBarcodeRuleRef(),
                        item.getBarCodeMatchingRuleId(), barcodeResult);
                if (resolvedId != null) {
                    item.setBarCodeMatchingRuleId(resolvedId);
                }

                BoltPartsBarcode entity = Converter.dto2Entity(item, BoltPartsBarcode::new);
                entity.setProductBoltId(boltId);
                if (item.getId() != null) {
                    dtoIds.add(item.getId());
                    partsBarcodeService.updateById(entity);
                } else {
                    partsBarcodeService.save(entity);
                    item.setId(entity.getId());
                }
            }
        }

        for (BoltPartsBarcode ex : existing) {
            if (!dtoIds.contains(ex.getId())) {
                partsBarcodeService.removeById(ex.getId());
            }
        }
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

    private record BarcodeDiffResult(List<BarCodeMatchingRule> rules, Map<String, Long> clientRefMap) {}

}

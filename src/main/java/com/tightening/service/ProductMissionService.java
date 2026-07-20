package com.tightening.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tightening.constant.BarCodeRuleType;
import com.tightening.constant.InspectionScope;
import com.tightening.constant.PrerequisiteType;
import com.tightening.dto.BarCodeRuleDetailItem;
import com.tightening.dto.BoltDeviceBindingDetailItem;
import com.tightening.dto.BoltPartsBarcodeDetailItem;
import com.tightening.dto.PageResult;
import com.tightening.dto.PrerequisiteDetailItem;
import com.tightening.dto.ProductBoltDetailItem;
import com.tightening.dto.ProductMissionDTO;
import com.tightening.dto.ProductMissionDetailDTO;
import com.tightening.dto.ProductSideDetailItem;
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
import java.util.Base64;
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

    public PageResult<ProductMissionDTO> listByPage(int page, int size, String name) {
        int safePage = Math.min(Math.max(1, page), 1000);
        int safeSize = Math.min(Math.max(1, size), 500);
        var wrapper = lambdaQuery()
                .orderByDesc(ProductMission::getId);
        if (name != null && !name.isBlank()) {
            wrapper.like(ProductMission::getName, name);
        }
        Page<ProductMission> resultPage = wrapper.page(new Page<>(safePage, safeSize));

        List<ProductMissionDTO> dtos = Converter.entity2Dto(resultPage.getRecords(), ProductMissionDTO::new);

        List<Long> missionIds = resultPage.getRecords().stream().map(ProductMission::getId).toList();
        if (!missionIds.isEmpty()) {
            Map<Long, String> thumbnailMap = loadFirstSideThumbnails(missionIds);
            for (ProductMissionDTO dto : dtos) {
                dto.setThumbnail(thumbnailMap.get(dto.getId()));
            }
        }

        return PageResult.of(resultPage, dtos);
    }

    private Map<Long, String> loadFirstSideThumbnails(List<Long> missionIds) {
        Map<Long, String> result = new HashMap<>();
        List<ProductSide> sides = sideService.getBaseMapper().selectFirstSidePerMission(missionIds);
        for (ProductSide side : sides) {
            result.put(side.getProductMissionId(), encodeBase64(side.getThumbnailData()));
        }
        return result;
    }

    public ProductMissionDetailDTO getDetail(Long missionId) {
        ProductMission mission = getById(missionId);
        if (mission == null) return null;

        ProductMissionDetailDTO dto = Converter.entity2Dto(mission, ProductMissionDetailDTO::new);

        List<ProductSide> sides = sideService.lambdaQuery()
                .eq(ProductSide::getProductMissionId, missionId)
                .eq(ProductSide::getDeleted, 0)
                .list();

        List<Long> sideIds = sides.stream().map(ProductSide::getId).toList();
        Map<Long, List<ProductBolt>> boltsBySide = sideIds.isEmpty() ? Map.of()
                : boltService.lambdaQuery()
                        .in(ProductBolt::getProductSideId, sideIds)
                        .eq(ProductBolt::getDeleted, 0)
                        .orderByAsc(ProductBolt::getBoltSerialNum)
                        .list()
                        .stream().collect(Collectors.groupingBy(ProductBolt::getProductSideId));

        List<Long> boltIds = boltsBySide.values().stream()
                .flatMap(List::stream).map(ProductBolt::getId).toList();
        Map<Long, List<BoltDeviceBinding>> bindingsByBolt = boltIds.isEmpty() ? Map.of()
                : deviceBindingService.lambdaQuery()
                        .in(BoltDeviceBinding::getProductBoltId, boltIds)
                        .eq(BoltDeviceBinding::getDeleted, 0)
                        .orderByAsc(BoltDeviceBinding::getSortOrder)
                        .list()
                        .stream().collect(Collectors.groupingBy(BoltDeviceBinding::getProductBoltId));
        Map<Long, List<BoltPartsBarcode>> barcodesByBolt = boltIds.isEmpty() ? Map.of()
                : partsBarcodeService.lambdaQuery()
                        .in(BoltPartsBarcode::getProductBoltId, boltIds)
                        .eq(BoltPartsBarcode::getDeleted, 0)
                        .list()
                        .stream().collect(Collectors.groupingBy(BoltPartsBarcode::getProductBoltId));

        List<BarCodeMatchingRule> rules = barcodeRuleService.listByMissionId(missionId);
        Map<Long, BarCodeRuleDetailItem> ruleItemMap = new HashMap<>();
        for (BarCodeMatchingRule rule : rules) {
            BarCodeRuleDetailItem item = Converter.entity2Dto(rule, BarCodeRuleDetailItem::new);
            item.setRuleType(BarCodeRuleType.fromCode(rule.getRuleType()));
            ruleItemMap.put(item.getId(), item);
        }

        List<ProductSideDetailItem> sideItems = new ArrayList<>();
        Set<Long> boltRuleIds = new HashSet<>();
        for (ProductSide side : sides) {
            ProductSideDetailItem item = Converter.entity2Dto(side, ProductSideDetailItem::new);
            item.setImage(encodeBase64(side.getImageData()));
            item.setRenderedImage(encodeBase64(side.getRenderedImageData()));
            item.setThumbnail(encodeBase64(side.getThumbnailData()));

            List<ProductBolt> sideBolts = boltsBySide.getOrDefault(side.getId(), List.of());
            List<ProductBoltDetailItem> boltItems = new ArrayList<>();
            for (ProductBolt bolt : sideBolts) {
                ProductBoltDetailItem boltItem = Converter.entity2Dto(bolt, ProductBoltDetailItem::new);
                boltItem.setDeviceBindings(Converter.entity2Dto(
                        bindingsByBolt.getOrDefault(bolt.getId(), List.of()),
                        BoltDeviceBindingDetailItem::new));
                List<BoltPartsBarcode> boltBarcodes = barcodesByBolt.getOrDefault(bolt.getId(), List.of());
                BoltPartsBarcodeDetailItem bpItem = null;
                if (!boltBarcodes.isEmpty()) {
                    BoltPartsBarcode bpEntity = boltBarcodes.get(0);
                    bpItem = Converter.entity2Dto(bpEntity, BoltPartsBarcodeDetailItem::new);
                    bpItem.setBarcodeRule(ruleItemMap.get(bpEntity.getBarCodeMatchingRuleId()));
                    if (bpItem.getBarcodeRule() != null) {
                        boltRuleIds.add(bpEntity.getBarCodeMatchingRuleId());
                    }
                }
                boltItem.setPartsBarcode(bpItem);
                boltItems.add(boltItem);
            }
            item.setBolts(boltItems);

            sideItems.add(item);
        }
        dto.setSides(sideItems);

        List<MissionPrerequisite> prerequisites = prerequisiteService.lambdaQuery()
                .eq(MissionPrerequisite::getMissionId, missionId)
                .eq(MissionPrerequisite::getDeleted, 0)
                .list();
        List<PrerequisiteDetailItem> prerequisiteItems = new ArrayList<>();
        for (MissionPrerequisite p : prerequisites) {
            PrerequisiteDetailItem item = Converter.entity2Dto(p, PrerequisiteDetailItem::new);
            item.setPrerequisiteType(PrerequisiteType.fromCode(p.getPrerequisiteType()));
            prerequisiteItems.add(item);
        }
        dto.setPrerequisites(prerequisiteItems);

        List<BarCodeRuleDetailItem> ruleItems = new ArrayList<>(ruleItemMap.values());
        ruleItems.removeIf(r -> boltRuleIds.contains(r.getId()));
        dto.setBarcodeRules(ruleItems);

        List<InspectionMissionBinding> bindings = bindingService.listByInspectionMissionId(missionId);
        dto.setInspectionBoundMissionIds(
                bindings.stream().map(InspectionMissionBinding::getBoundMissionId).toList());

        return dto;
    }

    private static String encodeBase64(byte[] data) {
        return data != null ? Base64.getEncoder().encodeToString(data) : null;
    }

    @Transactional
    public ProductMissionDetailDTO saveMission(ProductMissionDetailDTO dto) {
        ProductMission mission = Converter.dto2Entity(dto, ProductMission::new);
        if (mission.getInspectionScope() == null) mission.setInspectionScope(InspectionScope.NONE);
        saveOrUpdate(mission);
        Long missionId = mission.getId();
        dto.setId(missionId);

        validator.validateInspectionScope(mission, dto.getInspectionBoundMissionIds());

        syncInspectionBindings(missionId, dto.getInspectionBoundMissionIds());

        List<BarCodeRuleDetailItem> allRules = collectAllBarcodeRules(dto);
        validateNoDuplicateBarcodeRules(dto);
        BarcodeDiffResult barcodeResult = diffBarcodeRules(missionId, allRules);
        validator.validateBarcodeRules(barcodeResult.rules());

        diffPrerequisites(missionId, dto.getPrerequisites(), barcodeResult);
        validator.validateInspectionChainSelfInspection(mission, dto.getPrerequisites());

        diffSides(missionId, dto.getSides(), barcodeResult);

        return dto;
    }

    @Transactional
    public void syncInspectionBindings(Long missionId, List<Long> boundMissionIds) {
        if (boundMissionIds == null) return;
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

    private void forEachBoltBarcodeRule(ProductMissionDetailDTO dto,
                                         java.util.function.Consumer<BarCodeRuleDetailItem> consumer) {
        if (dto.getSides() == null) return;
        for (ProductSideDetailItem side : dto.getSides()) {
            if (side.getBolts() == null) continue;
            for (ProductBoltDetailItem bolt : side.getBolts()) {
                BoltPartsBarcodeDetailItem bp = bolt.getPartsBarcode();
                if (bp != null && bp.getBarcodeRule() != null) {
                    consumer.accept(bp.getBarcodeRule());
                }
            }
        }
    }

    private void validateNoDuplicateBarcodeRules(ProductMissionDetailDTO dto) {
        Set<Long> boltRuleIds = new HashSet<>();
        Set<String> boltRefs = new HashSet<>();
        forEachBoltBarcodeRule(dto, rule -> {
            if (rule.getId() != null) boltRuleIds.add(rule.getId());
            if (rule.getClientRef() != null) boltRefs.add(rule.getClientRef());
        });
        if (dto.getBarcodeRules() != null) {
            for (BarCodeRuleDetailItem r : dto.getBarcodeRules()) {
                if (r.getId() != null && boltRuleIds.contains(r.getId())) {
                    throw new IllegalArgumentException("Rule id=" + r.getId()
                            + " 已绑定在 bolt 上，不应出现在顶层 barcodeRules 中");
                }
                if (r.getClientRef() != null && boltRefs.contains(r.getClientRef())) {
                    throw new IllegalArgumentException("Rule clientRef=" + r.getClientRef()
                            + " 已绑定在 bolt 上，不应出现在顶层 barcodeRules 中");
                }
            }
        }
    }

    private List<BarCodeRuleDetailItem> collectAllBarcodeRules(ProductMissionDetailDTO dto) {
        List<BarCodeRuleDetailItem> all = new ArrayList<>();
        Set<String> seenRefs = new HashSet<>();
        Set<Long> seenIds = new HashSet<>();

        if (dto.getBarcodeRules() != null) {
            for (BarCodeRuleDetailItem r : dto.getBarcodeRules()) {
                addRuleIfNotDuplicate(r, all, seenRefs, seenIds);
            }
        }

        forEachBoltBarcodeRule(dto, rule -> addRuleIfNotDuplicate(rule, all, seenRefs, seenIds));

        return all;
    }

    private void addRuleIfNotDuplicate(BarCodeRuleDetailItem rule, List<BarCodeRuleDetailItem> all,
                                        Set<String> seenRefs, Set<Long> seenIds) {
        if (rule.getClientRef() != null) {
            if (seenRefs.add(rule.getClientRef())) {
                all.add(rule);
            }
        } else if (rule.getId() != null) {
            if (seenIds.add(rule.getId())) {
                all.add(rule);
            }
        } else {
            all.add(rule);
        }
    }

    private BarcodeDiffResult diffBarcodeRules(Long missionId, List<BarCodeRuleDetailItem> dtoItems) {
        List<BarCodeMatchingRule> existing = barcodeRuleService.lambdaQuery()
                .eq(BarCodeMatchingRule::getProductMissionId, missionId)
                .eq(BarCodeMatchingRule::getDeleted, 0)
                .list();

        Set<Long> dtoIds = new HashSet<>();
        List<BarCodeMatchingRule> result = new ArrayList<>();
        Map<String, Long> clientRefMap = new HashMap<>();

        if (dtoItems != null) {
            for (BarCodeRuleDetailItem item : dtoItems) {
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

    private void diffPrerequisites(Long missionId, List<PrerequisiteDetailItem> dtoItems, BarcodeDiffResult barcodeResult) {
        List<MissionPrerequisite> existing = prerequisiteService.lambdaQuery()
                .eq(MissionPrerequisite::getMissionId, missionId)
                .eq(MissionPrerequisite::getDeleted, 0)
                .list();

        Set<Long> dtoIds = new HashSet<>();

        if (dtoItems != null && !dtoItems.isEmpty()) {
            // Batch load all target missions
            List<Long> targetIds = dtoItems.stream()
                    .map(PrerequisiteDetailItem::getPrerequisiteMissionId).toList();
            List<ProductMission> targets = lambdaQuery().in(ProductMission::getId, targetIds).list();
            Map<Long, ProductMission> targetMap = targets.stream()
                    .collect(Collectors.toMap(ProductMission::getId, m -> m));

            for (PrerequisiteDetailItem item : dtoItems) {
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

    private void resolveAndValidateBarcodeRule(PrerequisiteDetailItem item, MissionPrerequisite entity,
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

    private void diffSides(Long missionId, List<ProductSideDetailItem> dtoSides,
                            BarcodeDiffResult barcodeResult) {
        List<ProductSide> existingSides = sideService.lambdaQuery()
                .eq(ProductSide::getProductMissionId, missionId)
                .eq(ProductSide::getDeleted, 0)
                .list();

        Map<Long, ProductSide> existingSideMap = existingSides.stream()
                .collect(Collectors.toMap(ProductSide::getId, s -> s));

        Set<Long> dtoSideIds = new HashSet<>();

        if (dtoSides != null) {
            for (int i = 0; i < dtoSides.size(); i++) {
                ProductSideDetailItem sideItem = dtoSides.get(i);
                ProductSide sideEntity = Converter.dto2Entity(sideItem, ProductSide::new);
                sideEntity.setProductMissionId(missionId);

                if (sideItem.getId() != null) {
                    ProductSide existing = existingSideMap.get(sideItem.getId());
                    if (existing != null) {
                        if (sideItem.getImage() == null) sideEntity.setImageData(existing.getImageData());
                        if (sideItem.getRenderedImage() == null) sideEntity.setRenderedImageData(existing.getRenderedImageData());
                        if (sideItem.getThumbnail() == null) sideEntity.setThumbnailData(existing.getThumbnailData());
                    }
                    dtoSideIds.add(sideItem.getId());
                    applyImageFromBase64(sideItem, sideEntity);
                    sideService.updateById(sideEntity);
                } else {
                    applyImageFromBase64(sideItem, sideEntity);
                    sideService.save(sideEntity);
                    sideItem.setId(sideEntity.getId());
                }

                diffBolts(sideEntity.getId(), missionId, sideItem.getBolts(), barcodeResult);
            }
        }

        for (ProductSide ex : existingSides) {
            if (!dtoSideIds.contains(ex.getId())) {
                sideService.cascadeDelete(ex.getId());
            }
        }
    }

    private void applyImageFromBase64(ProductSideDetailItem item, ProductSide entity) {
        if (item.getImage() != null) entity.setImageData(decodeBase64(item.getImage()));
        if (item.getRenderedImage() != null) entity.setRenderedImageData(decodeBase64(item.getRenderedImage()));
        if (item.getThumbnail() != null) entity.setThumbnailData(decodeBase64(item.getThumbnail()));
    }

    private static byte[] decodeBase64(String data) {
        return data.isEmpty() ? null : Base64.getDecoder().decode(data);
    }

    private void diffBolts(Long sideId, Long missionId, List<ProductBoltDetailItem> dtoBolts,
                            BarcodeDiffResult barcodeResult) {
        List<ProductBolt> existingBolts = boltService.lambdaQuery()
                .eq(ProductBolt::getProductSideId, sideId)
                .eq(ProductBolt::getDeleted, 0)
                .list();

        Set<Long> dtoBoltIds = new HashSet<>();

        if (dtoBolts != null) {
            for (ProductBoltDetailItem boltItem : dtoBolts) {
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
                diffPartsBarcode(boltEntity.getId(), boltItem.getPartsBarcode(), barcodeResult);
            }
        }

        for (ProductBolt ex : existingBolts) {
            if (!dtoBoltIds.contains(ex.getId())) {
                boltService.cascadeDelete(ex.getId());
            }
        }
    }

    private void diffDeviceBindings(Long boltId, List<BoltDeviceBindingDetailItem> dtoBindings) {
        List<BoltDeviceBinding> existing = deviceBindingService.lambdaQuery()
                .eq(BoltDeviceBinding::getProductBoltId, boltId)
                .eq(BoltDeviceBinding::getDeleted, 0)
                .list();

        Set<Long> dtoIds = new HashSet<>();

        if (dtoBindings != null) {
            for (BoltDeviceBindingDetailItem item : dtoBindings) {
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

    private void diffPartsBarcode(Long boltId, BoltPartsBarcodeDetailItem dtoItem,
                                   BarcodeDiffResult barcodeResult) {
        List<BoltPartsBarcode> existing = partsBarcodeService.lambdaQuery()
                .eq(BoltPartsBarcode::getProductBoltId, boltId)
                .eq(BoltPartsBarcode::getDeleted, 0)
                .list();

        if (dtoItem == null) {
            for (BoltPartsBarcode ex : existing) {
                partsBarcodeService.removeById(ex.getId());
            }
            return;
        }

        Long ruleId = resolveBarcodeRef(dtoItem.getBarcodeRuleRef(),
                dtoItem.getBarcodeRule() != null ? dtoItem.getBarcodeRule().getId() : null,
                barcodeResult);

        BoltPartsBarcode entity = Converter.dto2Entity(dtoItem, BoltPartsBarcode::new);
        entity.setProductBoltId(boltId);
        entity.setBarCodeMatchingRuleId(ruleId);

        if (dtoItem.getId() != null) {
            partsBarcodeService.updateById(entity);
        } else {
            partsBarcodeService.save(entity);
            dtoItem.setId(entity.getId());
        }

        for (BoltPartsBarcode ex : existing) {
            if (!ex.getId().equals(dtoItem.getId())) {
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

package com.tightening.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tightening.constant.BarCodeRuleType;
import com.tightening.constant.InspectionScope;
import com.tightening.constant.PrerequisiteType;
import com.tightening.i18n.BusinessException;
import com.tightening.dto.BarCodeRuleDetailItem;
import com.tightening.dto.BoltDeviceBindingDetailItem;
import com.tightening.dto.BoltPartsBarcodeDetailItem;
import com.tightening.dto.PageResult;
import com.tightening.dto.PrerequisiteDetailItem;
import com.tightening.dto.ProductBoltDetailItem;
import com.tightening.dto.ProductTaskDTO;
import com.tightening.dto.ProductTaskDetailDTO;
import com.tightening.dto.ProductSideDetailItem;
import com.tightening.entity.BarCodeMatchingRule;
import com.tightening.entity.BoltDeviceBinding;
import com.tightening.entity.BoltPartsBarcode;
import com.tightening.entity.InspectionTaskBinding;
import com.tightening.entity.TaskPrerequisite;
import com.tightening.entity.ProductBolt;
import com.tightening.entity.ProductTask;
import com.tightening.entity.ProductSide;
import com.tightening.mapper.ProductTaskMapper;
import com.tightening.util.Converter;
import com.tightening.util.JsonUtils;
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
public class ProductTaskService extends ServiceImpl<ProductTaskMapper, ProductTask> {
    private final TaskConfigValidator validator;
    private final ProductSideService sideService;
    private final TaskPrerequisiteService prerequisiteService;
    private final InspectionTaskBindingService bindingService;
    private final BarCodeMatchingRuleService barcodeRuleService;
    private final ProductBoltService boltService;
    private final BoltDeviceBindingService deviceBindingService;
    private final BoltPartsBarcodeService partsBarcodeService;

    public ProductTaskService(TaskConfigValidator validator,
            ProductSideService sideService,
            TaskPrerequisiteService prerequisiteService,
            InspectionTaskBindingService bindingService,
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
                .eq(ProductTask::getName, name)
                .ne(excludeId != null, ProductTask::getId, excludeId)
                .count() > 0;
    }

    public PageResult<ProductTaskDTO> listByPage(int page, int size, String name) {
        int safePage = Math.min(Math.max(1, page), 1000);
        int safeSize = Math.min(Math.max(1, size), 500);
        var wrapper = lambdaQuery()
                .orderByDesc(ProductTask::getId);
        if (name != null && !name.isBlank()) {
            wrapper.like(ProductTask::getName, name);
        }
        Page<ProductTask> resultPage = wrapper.page(new Page<>(safePage, safeSize));

        List<ProductTaskDTO> dtos = Converter.entity2Dto(resultPage.getRecords(), ProductTaskDTO::new);

        List<Long> taskIds = resultPage.getRecords().stream().map(ProductTask::getId).toList();
        if (!taskIds.isEmpty()) {
            Map<Long, String> thumbnailMap = loadFirstSideThumbnails(taskIds);
            for (ProductTaskDTO dto : dtos) {
                dto.setThumbnail(thumbnailMap.get(dto.getId()));
            }
        }

        return PageResult.of(resultPage, dtos);
    }

    private Map<Long, String> loadFirstSideThumbnails(List<Long> taskIds) {
        Map<Long, String> result = new HashMap<>();
        List<ProductSide> sides = sideService.getBaseMapper().selectFirstSidePerTask(taskIds);
        for (ProductSide side : sides) {
            result.put(side.getProductTaskId(), encodeBase64(side.getThumbnailData()));
        }
        return result;
    }

    public ProductTaskDetailDTO getDetail(Long taskId) {
        ProductTask task = getById(taskId);
        if (task == null) return null;

        ProductTaskDetailDTO dto = Converter.entity2Dto(task, ProductTaskDetailDTO::new);

        List<ProductSide> sides = sideService.lambdaQuery()
                .eq(ProductSide::getProductTaskId, taskId)
                .eq(ProductSide::getDeleted, 0)
                .list();

        List<Long> sideIds = sides.stream().map(ProductSide::getId).toList();
        Map<Long, List<ProductBolt>> boltsBySide = sideIds.isEmpty() ? Map.of()
                : boltService.lambdaQuery()
                        .in(ProductBolt::getProductSideId, sideIds)
                        .eq(ProductBolt::getDeleted, 0)
                        .orderByAsc(ProductBolt::getSerialNum)
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

        List<BarCodeMatchingRule> rules = barcodeRuleService.listByTaskId(taskId);
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

        List<TaskPrerequisite> prerequisites = prerequisiteService.lambdaQuery()
                .eq(TaskPrerequisite::getTaskId, taskId)
                .eq(TaskPrerequisite::getDeleted, 0)
                .list();
        List<PrerequisiteDetailItem> prerequisiteItems = new ArrayList<>();
        for (TaskPrerequisite p : prerequisites) {
            PrerequisiteDetailItem item = Converter.entity2Dto(p, PrerequisiteDetailItem::new);
            item.setPrerequisiteType(PrerequisiteType.fromCode(p.getPrerequisiteType()));
            prerequisiteItems.add(item);
        }
        dto.setPrerequisites(prerequisiteItems);

        List<BarCodeRuleDetailItem> ruleItems = new ArrayList<>(ruleItemMap.values());
        ruleItems.removeIf(r -> boltRuleIds.contains(r.getId()));
        dto.setBarcodeRules(ruleItems);

        List<InspectionTaskBinding> bindings = bindingService.listByInspectionTaskId(taskId);
        dto.setInspectionBoundTaskIds(
                bindings.stream().map(InspectionTaskBinding::getBoundTaskId).toList());

        return dto;
    }

    private static String encodeBase64(byte[] data) {
        return data != null ? Base64.getEncoder().encodeToString(data) : null;
    }

    @Transactional
    public ProductTaskDetailDTO saveTask(ProductTaskDetailDTO dto) {
        log.info("saveTask id={}, name={}, sides={}, rules={}, prerequisites={}",
                dto.getId(), dto.getName(),
                dto.getSides() != null ? dto.getSides().size() : 0,
                dto.getBarcodeRules() != null ? dto.getBarcodeRules().size() : 0,
                dto.getPrerequisites() != null ? dto.getPrerequisites().size() : 0);
        if (log.isDebugEnabled()) {
            log.debug("saveTask full request: {}", JsonUtils.toJson(snapshotForLog(dto)));
        }
        ProductTask task = Converter.dto2Entity(dto, ProductTask::new);
        if (task.getInspectionScope() == null) task.setInspectionScope(InspectionScope.NONE);
        saveOrUpdate(task);
        Long taskId = task.getId();
        dto.setId(taskId);

        validator.validateInspectionScope(task, dto.getInspectionBoundTaskIds());

        syncInspectionBindings(taskId, dto.getInspectionBoundTaskIds());

        List<BarCodeRuleDetailItem> allRules = collectAllBarcodeRules(dto);
        validateNoDuplicateBarcodeRules(dto);
        BarcodeDiffResult barcodeResult = diffBarcodeRules(taskId, allRules);
        validator.validateBarcodeRules(barcodeResult.rules());

        diffPrerequisites(taskId, dto.getPrerequisites(), barcodeResult);
        validator.validateInspectionChainSelfInspection(task, dto.getPrerequisites());

        diffSides(taskId, dto.getSides(), barcodeResult);

        clearAllTempRefs(dto);
        return dto;
    }

    private void clearAllTempRefs(ProductTaskDetailDTO dto) {
        if (dto.getBarcodeRules() != null) {
            for (BarCodeRuleDetailItem rule : dto.getBarcodeRules()) {
                rule.setClientRef(null);
            }
        }
        if (dto.getPrerequisites() != null) {
            for (PrerequisiteDetailItem p : dto.getPrerequisites()) {
                p.setBarcodeRuleRef(null);
            }
        }
        if (dto.getSides() != null) {
            for (ProductSideDetailItem side : dto.getSides()) {
                if (side.getBolts() != null) {
                    for (ProductBoltDetailItem bolt : side.getBolts()) {
                        BoltPartsBarcodeDetailItem bp = bolt.getPartsBarcode();
                        if (bp != null) {
                            bp.setBarcodeRuleRef(null);
                            if (bp.getBarcodeRule() != null) {
                                bp.getBarcodeRule().setClientRef(null);
                            }
                        }
                    }
                }
            }
        }
    }

    @Transactional
    public void syncInspectionBindings(Long taskId, List<Long> boundTaskIds) {
        if (boundTaskIds == null) return;
        bindingService.lambdaUpdate()
                .eq(InspectionTaskBinding::getInspectionTaskId, taskId)
                .remove();
        List<ProductTask> bounds = lambdaQuery().in(ProductTask::getId, boundTaskIds).list();
        for (ProductTask bound : bounds) {
            validator.validateInspectionBinding(bound);
            InspectionTaskBinding binding = new InspectionTaskBinding()
                    .setInspectionTaskId(taskId)
                    .setBoundTaskId(bound.getId());
            bindingService.save(binding);
        }
    }

    private void forEachBoltBarcodeRule(ProductTaskDetailDTO dto,
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

    private void validateNoDuplicateBarcodeRules(ProductTaskDetailDTO dto) {
        Set<Long> boltRuleIds = new HashSet<>();
        Set<String> boltRefs = new HashSet<>();
        forEachBoltBarcodeRule(dto, rule -> {
            if (rule.getId() != null) boltRuleIds.add(rule.getId());
            if (rule.getClientRef() != null) boltRefs.add(rule.getClientRef());
        });
        if (dto.getBarcodeRules() != null) {
            for (BarCodeRuleDetailItem r : dto.getBarcodeRules()) {
                if (r.getId() != null && boltRuleIds.contains(r.getId())) {
                    throw BusinessException.of("barcode.bolt_rule_conflict", "id", r.getId());
                }
                if (r.getClientRef() != null && boltRefs.contains(r.getClientRef())) {
                    throw BusinessException.of("barcode.bolt_rule_conflict", "clientRef", r.getClientRef());
                }
            }
        }
    }

    private List<BarCodeRuleDetailItem> collectAllBarcodeRules(ProductTaskDetailDTO dto) {
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

    private BarcodeDiffResult diffBarcodeRules(Long taskId, List<BarCodeRuleDetailItem> dtoItems) {
        List<BarCodeMatchingRule> existing = barcodeRuleService.lambdaQuery()
                .eq(BarCodeMatchingRule::getProductTaskId, taskId)
                .eq(BarCodeMatchingRule::getDeleted, 0)
                .list();

        Set<Long> dtoIds = new HashSet<>();
        List<BarCodeMatchingRule> result = new ArrayList<>();
        Map<String, Long> clientRefMap = new HashMap<>();

        if (dtoItems != null) {
            for (BarCodeRuleDetailItem item : dtoItems) {
                BarCodeMatchingRule entity = Converter.dto2Entity(item, BarCodeMatchingRule::new);
                entity.setProductTaskId(taskId);
                entity.setRuleType(item.getRuleType().getCode());
                validator.validateKeyCharLength(entity);
                if (BarCodeRuleType.PRODUCT_TRACE.getCode() == entity.getRuleType()) {
                    validator.validateProductTraceUnique(taskId, entity.getId());
                }
                if (item.getClientRef() != null && item.getId() != null) {
                    throw BusinessException.of("barcode.client_ref_only_for_new");
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

    private void diffPrerequisites(Long taskId, List<PrerequisiteDetailItem> dtoItems, BarcodeDiffResult barcodeResult) {
        List<TaskPrerequisite> existing = prerequisiteService.lambdaQuery()
                .eq(TaskPrerequisite::getTaskId, taskId)
                .eq(TaskPrerequisite::getDeleted, 0)
                .list();

        Set<Long> dtoIds = new HashSet<>();

        if (dtoItems != null && !dtoItems.isEmpty()) {
            // Batch load all target tasks
            List<Long> targetIds = dtoItems.stream()
                    .map(PrerequisiteDetailItem::getPrerequisiteTaskId).toList();
            List<ProductTask> targets = lambdaQuery().in(ProductTask::getId, targetIds).list();
            Map<Long, ProductTask> targetMap = targets.stream()
                    .collect(Collectors.toMap(ProductTask::getId, m -> m));

            for (PrerequisiteDetailItem item : dtoItems) {
                TaskPrerequisite entity = Converter.dto2Entity(item, TaskPrerequisite::new);
                entity.setTaskId(taskId);
                entity.setPrerequisiteType(item.getPrerequisiteType().getCode());
                validator.validateNoCircularDependency(taskId, item.getPrerequisiteTaskId());
                validator.validatePrerequisiteType(targetMap.get(item.getPrerequisiteTaskId()), item.getPrerequisiteType());

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

        for (TaskPrerequisite ex : existing) {
            if (!dtoIds.contains(ex.getId())) {
                prerequisiteService.removeById(ex.getId());
            }
        }
    }

    private Long resolveBarcodeRef(String barcodeRuleRef, Long barcodeRuleId,
                                    BarcodeDiffResult barcodeResult) {
        if (barcodeRuleRef != null) {
            if (barcodeRuleId != null) {
                throw BusinessException.of("barcode.ref_and_id_conflict");
            }
            Long resolvedId = barcodeResult.clientRefMap().get(barcodeRuleRef);
            if (resolvedId == null) {
                throw BusinessException.of("barcode.client_ref_not_found", barcodeRuleRef);
            }
            return resolvedId;
        }
        return barcodeRuleId;
    }

    private void resolveAndValidateBarcodeRule(PrerequisiteDetailItem item, TaskPrerequisite entity,
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

    private void diffSides(Long taskId, List<ProductSideDetailItem> dtoSides,
                            BarcodeDiffResult barcodeResult) {
        List<ProductSide> existingSides = sideService.lambdaQuery()
                .eq(ProductSide::getProductTaskId, taskId)
                .eq(ProductSide::getDeleted, 0)
                .list();

        Map<Long, ProductSide> existingSideMap = existingSides.stream()
                .collect(Collectors.toMap(ProductSide::getId, s -> s));

        Set<Long> dtoSideIds = new HashSet<>();

        if (dtoSides != null) {
            for (int i = 0; i < dtoSides.size(); i++) {
                ProductSideDetailItem sideItem = dtoSides.get(i);
                ProductSide sideEntity = Converter.dto2Entity(sideItem, ProductSide::new);
                sideEntity.setProductTaskId(taskId);

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

                diffBolts(sideEntity.getId(), taskId, sideItem.getBolts(), barcodeResult);
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

    private void diffBolts(Long sideId, Long taskId, List<ProductBoltDetailItem> dtoBolts,
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
                    boltService.saveBolt(boltEntity);
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

    private Long resolveRuleIdFromBoltBarcode(BoltPartsBarcodeDetailItem dtoItem,
                                               BarcodeDiffResult barcodeResult) {
        if (dtoItem.getBarcodeRule() == null) return null;
        Long id = dtoItem.getBarcodeRule().getId();
        if (id != null) return id;
        String clientRef = dtoItem.getBarcodeRule().getClientRef();
        if (clientRef != null) {
            Long resolved = barcodeResult.clientRefMap().get(clientRef);
            if (resolved == null) {
                throw BusinessException.of("barcode.client_ref_not_found", clientRef);
            }
            return resolved;
        }
        return null;
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

        Long ruleId = resolveRuleIdFromBoltBarcode(dtoItem, barcodeResult);

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

    public void updateEnabled(Long id, int enabled) {
        lambdaUpdate()
                .eq(ProductTask::getId, id)
                .set(ProductTask::getEnabled, enabled)
                .update();
    }

    @Transactional
    public void cascadeDelete(Long taskId) {
        // Collect all side IDs and bolt IDs for batch deletion
        List<Long> sideIds = sideService.lambdaQuery()
                .select(ProductSide::getId)
                .eq(ProductSide::getProductTaskId, taskId)
                .list().stream().map(ProductSide::getId).toList();

        if (!sideIds.isEmpty()) {
            sideService.deleteBoltsBySideIds(sideIds);
            sideService.lambdaUpdate().in(ProductSide::getId, sideIds).remove();
        }

        prerequisiteService.lambdaUpdate()
                .eq(TaskPrerequisite::getTaskId, taskId).or()
                .eq(TaskPrerequisite::getPrerequisiteTaskId, taskId).remove();
        bindingService.lambdaUpdate()
                .eq(InspectionTaskBinding::getInspectionTaskId, taskId).or()
                .eq(InspectionTaskBinding::getBoundTaskId, taskId).remove();
        barcodeRuleService.lambdaUpdate()
                .eq(BarCodeMatchingRule::getProductTaskId, taskId).remove();
        removeById(taskId);
    }

    private record BarcodeDiffResult(List<BarCodeMatchingRule> rules, Map<String, Long> clientRefMap) {}

    private ProductTaskDetailDTO snapshotForLog(ProductTaskDetailDTO dto) {
        try {
            ProductTaskDetailDTO copy = JsonUtils.OBJECT_MAPPER.readValue(
                    JsonUtils.toJson(dto), ProductTaskDetailDTO.class);
            if (copy.getSides() != null) {
                for (ProductSideDetailItem side : copy.getSides()) {
                    if (side.getImage() != null) side.setImage("[base64 image]");
                    if (side.getRenderedImage() != null) side.setRenderedImage("[base64 rendered]");
                    if (side.getThumbnail() != null) side.setThumbnail("[base64 thumb]");
                }
            }
            return copy;
        } catch (Exception e) {
            log.warn("Failed to snapshot DTO for logging, falling back to original", e);
            return dto;
        }
    }

}

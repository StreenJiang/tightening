package com.tightening.controller;

import com.tightening.dto.BarCodeMatchingRuleDTO;
import com.tightening.dto.InspectionMissionBindingDTO;
import com.tightening.dto.MissionPrerequisiteDTO;
import com.tightening.dto.ProductMissionDTO;
import com.tightening.entity.BarCodeMatchingRule;
import com.tightening.entity.InspectionMissionBinding;
import com.tightening.entity.MissionPrerequisite;
import com.tightening.entity.ProductMission;
import com.tightening.service.BarCodeMatchingRuleService;
import com.tightening.service.InspectionMissionBindingService;
import com.tightening.service.MissionPrerequisiteService;
import com.tightening.service.ProductMissionService;
import com.tightening.util.Converter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("api/missions")
@RequiredArgsConstructor
public class ProductMissionController {

    private final ProductMissionService missionService;
    private final MissionPrerequisiteService prerequisiteService;
    private final InspectionMissionBindingService bindingService;
    private final BarCodeMatchingRuleService barcodeRuleService;

    record PrerequisiteRequest(Long prerequisiteMissionId, Integer prerequisiteType) {}
    record InspectionBindingRequest(Long boundMissionId) {}

    @GetMapping
    public ResponseEntity<List<ProductMissionDTO>> list(@RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "100") int size) {
        int safePage = Math.min(Math.max(1, page), 1000);
        int safeSize = Math.min(Math.max(1, size), 500);
        List<ProductMission> missions = missionService.lambdaQuery()
                .eq(ProductMission::getDeleted, 0)
                .orderByDesc(ProductMission::getId)
                .last("LIMIT " + safeSize + " OFFSET " + ((safePage - 1) * safeSize))
                .list();
        return ResponseEntity.ok(Converter.entity2Dto(missions, ProductMissionDTO::new));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductMissionDTO> get(@PathVariable Long id) {
        ProductMission mission = missionService.getById(id);
        if (mission == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Converter.entity2Dto(mission, ProductMissionDTO::new));
    }

    @PostMapping
    public ResponseEntity<String> create(@RequestBody ProductMissionDTO dto) {
        ProductMission entity = Converter.dto2Entity(dto, ProductMission::new);
        missionService.saveOrUpdate(entity);
        return ResponseEntity.ok(String.valueOf(entity.getId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<String> update(@PathVariable Long id, @RequestBody ProductMissionDTO dto) {
        try {
            ProductMission entity = Converter.dto2Entity(dto, ProductMission::new);
            entity.setId(id);
            missionService.saveOrUpdate(entity);
            return ResponseEntity.ok(String.valueOf(entity.getId()));
        } catch (Exception e) {
            log.error("Update mission failed: id={}", id, e);
            return ResponseEntity.internalServerError().body("更新失败");
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id) {
        missionService.cascadeDelete(id);
        return ResponseEntity.ok("ok");
    }

    @PostMapping("/{missionId}/prerequisites")
    public ResponseEntity<String> addPrerequisite(@PathVariable Long missionId,
                                                   @RequestBody PrerequisiteRequest request) {
        missionService.addPrerequisite(missionId, request.prerequisiteMissionId(), request.prerequisiteType());
        return ResponseEntity.ok("ok");
    }

    @GetMapping("/{missionId}/prerequisites")
    public ResponseEntity<List<MissionPrerequisiteDTO>> listPrerequisites(@PathVariable Long missionId) {
        List<MissionPrerequisite> prerequisites = prerequisiteService.lambdaQuery()
                .eq(MissionPrerequisite::getMissionId, missionId)
                .eq(MissionPrerequisite::getDeleted, 0)
                .list();
        return ResponseEntity.ok(Converter.entity2Dto(prerequisites, MissionPrerequisiteDTO::new));
    }

    @DeleteMapping("/{missionId}/prerequisites/{id}")
    public ResponseEntity<String> deletePrerequisite(@PathVariable Long missionId, @PathVariable Long id) {
        prerequisiteService.removeById(id);
        return ResponseEntity.ok("ok");
    }

    @PostMapping("/{inspectionMissionId}/inspection-bindings")
    public ResponseEntity<String> addInspectionBinding(@PathVariable Long inspectionMissionId,
                                                        @RequestBody InspectionBindingRequest request) {
        missionService.addInspectionBinding(inspectionMissionId, request.boundMissionId());
        return ResponseEntity.ok("ok");
    }

    @GetMapping("/{missionId}/inspection-bindings")
    public ResponseEntity<List<InspectionMissionBindingDTO>> listInspectionBindings(@PathVariable Long missionId) {
        List<InspectionMissionBinding> bindings = bindingService.lambdaQuery()
                .eq(InspectionMissionBinding::getInspectionMissionId, missionId)
                .eq(InspectionMissionBinding::getDeleted, 0)
                .list();
        return ResponseEntity.ok(Converter.entity2Dto(bindings, InspectionMissionBindingDTO::new));
    }

    @DeleteMapping("/{missionId}/inspection-bindings/{id}")
    public ResponseEntity<String> deleteInspectionBinding(@PathVariable Long missionId, @PathVariable Long id) {
        bindingService.removeById(id);
        return ResponseEntity.ok("ok");
    }

    @PostMapping("/{missionId}/barcode-rules")
    public ResponseEntity<String> addBarcodeRule(@PathVariable Long missionId,
                                                  @RequestBody BarCodeMatchingRuleDTO dto) {
        BarCodeMatchingRule entity = Converter.dto2Entity(dto, BarCodeMatchingRule::new);
        entity.setProductMissionId(missionId);  // Use path variable, not dto
        missionService.addBarcodeRule(entity);
        return ResponseEntity.ok(String.valueOf(entity.getId()));
    }

    @GetMapping("/{missionId}/barcode-rules")
    public ResponseEntity<List<BarCodeMatchingRuleDTO>> listBarcodeRules(@PathVariable Long missionId) {
        List<BarCodeMatchingRule> rules = barcodeRuleService.lambdaQuery()
                .eq(BarCodeMatchingRule::getProductMissionId, missionId)
                .eq(BarCodeMatchingRule::getDeleted, 0)
                .list();
        return ResponseEntity.ok(Converter.entity2Dto(rules, BarCodeMatchingRuleDTO::new));
    }

    @DeleteMapping("/{missionId}/barcode-rules/{id}")
    public ResponseEntity<String> deleteBarcodeRule(@PathVariable Long missionId, @PathVariable Long id) {
        barcodeRuleService.removeById(id);
        return ResponseEntity.ok("ok");
    }
}

package com.tightening.controller;

import com.tightening.constant.InspectionScope;
import com.tightening.dto.ApiResponse;
import com.tightening.dto.BarCodeMatchingRuleDTO;
import com.tightening.dto.InspectionMissionBindingDTO;
import com.tightening.dto.MissionPrerequisiteDTO;
import com.tightening.dto.PageResult;
import com.tightening.dto.ProductMissionDTO;
import com.tightening.entity.BarCodeMatchingRule;
import com.tightening.entity.InspectionMissionBinding;
import com.tightening.entity.MissionPrerequisite;
import com.tightening.entity.ProductMission;
import com.tightening.service.BarCodeMatchingRuleService;
import com.tightening.service.InspectionMissionBindingService;
import com.tightening.service.MissionConfigValidator;
import com.tightening.service.MissionPrerequisiteService;
import com.tightening.service.ProductMissionService;
import com.tightening.util.Converter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
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
    private final MissionConfigValidator missionValidator;

    record PrerequisiteRequest(Long prerequisiteMissionId, Integer prerequisiteType) {}
    record InspectionBindingRequest(Long boundMissionId) {}

    @GetMapping
    public ResponseEntity<ApiResponse<PageResult<ProductMissionDTO>>> list(@RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "100") int size,
                                                        @RequestParam(required = false) String name) {
        var resultPage = missionService.listByPage(page, size, name);
        var dtos = Converter.entity2Dto(resultPage.getRecords(), ProductMissionDTO::new);
        return ResponseEntity.ok(ApiResponse.ok(PageResult.of(resultPage, dtos)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductMissionDTO>> get(@PathVariable Long id) {
        ProductMission mission = missionService.getById(id);
        if (mission == null) return ResponseEntity.ok(ApiResponse.fail("not found"));
        return ResponseEntity.ok(ApiResponse.ok(Converter.entity2Dto(mission, ProductMissionDTO::new)));
    }

    @GetMapping("/check-name")
    public ResponseEntity<ApiResponse<Boolean>> checkName(@RequestParam String name,
                                                           @RequestParam(required = false) Long excludeId) {
        boolean exists = missionService.isNameDuplicate(name, excludeId);
        return ResponseEntity.ok(ApiResponse.ok(exists));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<String>> create(@RequestBody ProductMissionDTO dto) {
        ProductMission entity = Converter.dto2Entity(dto, ProductMission::new);
        if (entity.getInspectionScope() == null) entity.setInspectionScope(InspectionScope.NONE);
        try {
            missionValidator.validateInspectionScope(entity, dto.getInspectionBoundMissionIds());
            missionService.saveOrUpdate(entity);
            missionService.syncInspectionBindings(entity.getId(), dto.getInspectionBoundMissionIds());
            return ResponseEntity.ok(ApiResponse.ok(String.valueOf(entity.getId())));
        } catch (DuplicateKeyException e) {
            return ResponseEntity.ok(ApiResponse.fail("任务名称已存在"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(ApiResponse.fail(e.getMessage()));
        } catch (Exception e) {
            log.error("Create mission failed", e);
            return ResponseEntity.ok(ApiResponse.fail("更新失败"));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> update(@PathVariable Long id, @RequestBody ProductMissionDTO dto) {
        try {
            ProductMission entity = Converter.dto2Entity(dto, ProductMission::new);
            entity.setId(id);
            if (entity.getInspectionScope() == null) entity.setInspectionScope(InspectionScope.NONE);
            missionValidator.validateInspectionScope(entity, dto.getInspectionBoundMissionIds());
            missionService.saveOrUpdate(entity);
            missionService.syncInspectionBindings(entity.getId(), dto.getInspectionBoundMissionIds());
            return ResponseEntity.ok(ApiResponse.ok(String.valueOf(entity.getId())));
        } catch (DuplicateKeyException e) {
            return ResponseEntity.ok(ApiResponse.fail("任务名称已存在"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(ApiResponse.fail(e.getMessage()));
        } catch (Exception e) {
            log.error("Update mission failed: id={}", id, e);
            return ResponseEntity.ok(ApiResponse.fail("更新失败"));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> delete(@PathVariable Long id) {
        missionService.cascadeDelete(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/{missionId}/prerequisites")
    public ResponseEntity<ApiResponse<String>> addPrerequisite(@PathVariable Long missionId,
                                                   @RequestBody PrerequisiteRequest request) {
        missionService.addPrerequisite(missionId, request.prerequisiteMissionId(), request.prerequisiteType());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @GetMapping("/{missionId}/prerequisites")
    public ResponseEntity<ApiResponse<List<MissionPrerequisiteDTO>>> listPrerequisites(@PathVariable Long missionId) {
        List<MissionPrerequisite> prerequisites = prerequisiteService.listByMissionId(missionId);
        return ResponseEntity.ok(ApiResponse.ok(Converter.entity2Dto(prerequisites, MissionPrerequisiteDTO::new)));
    }

    @DeleteMapping("/{missionId}/prerequisites/{id}")
    public ResponseEntity<ApiResponse<String>> deletePrerequisite(@PathVariable Long missionId, @PathVariable Long id) {
        prerequisiteService.removeById(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/{inspectionMissionId}/inspection-bindings")
    public ResponseEntity<ApiResponse<String>> addInspectionBinding(@PathVariable Long inspectionMissionId,
                                                        @RequestBody InspectionBindingRequest request) {
        missionService.addInspectionBinding(inspectionMissionId, request.boundMissionId());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @GetMapping("/{missionId}/inspection-bindings")
    public ResponseEntity<ApiResponse<List<InspectionMissionBindingDTO>>> listInspectionBindings(@PathVariable Long missionId) {
        List<InspectionMissionBinding> bindings = bindingService.listByInspectionMissionId(missionId);
        return ResponseEntity.ok(ApiResponse.ok(Converter.entity2Dto(bindings, InspectionMissionBindingDTO::new)));
    }

    @DeleteMapping("/{missionId}/inspection-bindings/{id}")
    public ResponseEntity<ApiResponse<String>> deleteInspectionBinding(@PathVariable Long missionId, @PathVariable Long id) {
        bindingService.removeById(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/{missionId}/barcode-rules")
    public ResponseEntity<ApiResponse<String>> addBarcodeRule(@PathVariable Long missionId,
                                                  @RequestBody BarCodeMatchingRuleDTO dto) {
        BarCodeMatchingRule entity = Converter.dto2Entity(dto, BarCodeMatchingRule::new);
        entity.setProductMissionId(missionId);  // Use path variable, not dto
        missionService.addBarcodeRule(entity);
        return ResponseEntity.ok(ApiResponse.ok(String.valueOf(entity.getId())));
    }

    @GetMapping("/{missionId}/barcode-rules")
    public ResponseEntity<ApiResponse<List<BarCodeMatchingRuleDTO>>> listBarcodeRules(@PathVariable Long missionId) {
        List<BarCodeMatchingRule> rules = barcodeRuleService.listByMissionId(missionId);
        return ResponseEntity.ok(ApiResponse.ok(Converter.entity2Dto(rules, BarCodeMatchingRuleDTO::new)));
    }

    @DeleteMapping("/{missionId}/barcode-rules/{id}")
    public ResponseEntity<ApiResponse<String>> deleteBarcodeRule(@PathVariable Long missionId, @PathVariable Long id) {
        barcodeRuleService.removeById(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}

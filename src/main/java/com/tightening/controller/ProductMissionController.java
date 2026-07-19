package com.tightening.controller;

import com.tightening.dto.ApiResponse;
import com.tightening.dto.BarCodeMatchingRuleDTO;
import com.tightening.dto.InspectionMissionBindingDTO;
import com.tightening.dto.MissionPrerequisiteDTO;
import com.tightening.dto.PageResult;
import com.tightening.dto.ProductMissionDTO;
import com.tightening.dto.ProductMissionDetailDTO;
import com.tightening.entity.BarCodeMatchingRule;
import com.tightening.entity.InspectionMissionBinding;
import com.tightening.service.BarCodeMatchingRuleService;
import com.tightening.service.InspectionMissionBindingService;
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

    @GetMapping
    public ResponseEntity<ApiResponse<PageResult<ProductMissionDTO>>> list(@RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "100") int size,
                                                        @RequestParam(required = false) String name) {
        var resultPage = missionService.listByPage(page, size, name);
        var dtos = Converter.entity2Dto(resultPage.getRecords(), ProductMissionDTO::new);
        return ResponseEntity.ok(ApiResponse.ok(PageResult.of(resultPage, dtos)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductMissionDetailDTO>> get(@PathVariable Long id) {
        ProductMissionDetailDTO dto = missionService.getDetail(id);
        if (dto == null) return ResponseEntity.ok(ApiResponse.fail("not found"));
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    @GetMapping("/check-name")
    public ResponseEntity<ApiResponse<Boolean>> checkName(@RequestParam String name,
                                                           @RequestParam(required = false) Long excludeId) {
        boolean exists = missionService.isNameDuplicate(name, excludeId);
        return ResponseEntity.ok(ApiResponse.ok(exists));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProductMissionDetailDTO>> create(@RequestBody ProductMissionDetailDTO dto) {
        try {
            ProductMissionDetailDTO result = missionService.saveMission(dto);
            return ResponseEntity.ok(ApiResponse.ok(result));
        } catch (DuplicateKeyException e) {
            return ResponseEntity.ok(ApiResponse.fail("任务名称已存在"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(ApiResponse.fail(e.getMessage()));
        } catch (RuntimeException e) {
            log.error("Create mission failed", e);
            return ResponseEntity.ok(ApiResponse.fail("新增失败: " + unwrapCause(e)));
        } catch (Exception e) {
            log.error("Create mission failed", e);
            return ResponseEntity.ok(ApiResponse.fail("新增失败"));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductMissionDetailDTO>> update(@PathVariable Long id, @RequestBody ProductMissionDetailDTO dto) {
        try {
            dto.setId(id);
            ProductMissionDetailDTO result = missionService.saveMission(dto);
            return ResponseEntity.ok(ApiResponse.ok(result));
        } catch (DuplicateKeyException e) {
            return ResponseEntity.ok(ApiResponse.fail("任务名称已存在"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(ApiResponse.fail(e.getMessage()));
        } catch (RuntimeException e) {
            log.error("Update mission failed: id={}", id, e);
            return ResponseEntity.ok(ApiResponse.fail("更新失败: " + unwrapCause(e)));
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

    @GetMapping("/{missionId}/prerequisites")
    public ResponseEntity<ApiResponse<List<MissionPrerequisiteDTO>>> listPrerequisites(@PathVariable Long missionId) {
        List<MissionPrerequisiteDTO> prerequisites = prerequisiteService.listByMissionId(missionId);
        return ResponseEntity.ok(ApiResponse.ok(prerequisites));
    }

    @GetMapping("/{missionId}/inspection-bindings")
    public ResponseEntity<ApiResponse<List<InspectionMissionBindingDTO>>> listInspectionBindings(@PathVariable Long missionId) {
        List<InspectionMissionBinding> bindings = bindingService.listByInspectionMissionId(missionId);
        return ResponseEntity.ok(ApiResponse.ok(Converter.entity2Dto(bindings, InspectionMissionBindingDTO::new)));
    }

    @GetMapping("/{missionId}/barcode-rules")
    public ResponseEntity<ApiResponse<List<BarCodeMatchingRuleDTO>>> listBarcodeRules(@PathVariable Long missionId) {
        List<BarCodeMatchingRule> rules = barcodeRuleService.listByMissionId(missionId);
        return ResponseEntity.ok(ApiResponse.ok(Converter.entity2Dto(rules, BarCodeMatchingRuleDTO::new)));
    }

    private static String unwrapCause(RuntimeException e) {
        return e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
    }
}

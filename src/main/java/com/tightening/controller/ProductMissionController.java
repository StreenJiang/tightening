package com.tightening.controller;

import com.tightening.dto.ApiResponse;
import com.tightening.dto.BarCodeMatchingRuleDTO;
import com.tightening.dto.InspectionMissionBindingDTO;
import com.tightening.dto.MissionPrerequisiteDTO;
import com.tightening.dto.PageResult;
import com.tightening.dto.ProductMissionDTO;
import com.tightening.dto.ProductMissionSaveDTO;
import com.tightening.entity.BarCodeMatchingRule;
import com.tightening.entity.InspectionMissionBinding;
import com.tightening.entity.MissionPrerequisite;
import com.tightening.entity.ProductMission;
import com.tightening.service.BarCodeMatchingRuleService;
import com.tightening.service.InspectionMissionBindingService;
import com.tightening.service.MissionPrerequisiteService;
import com.tightening.service.ProductMissionService;
import com.tightening.util.Converter;
import com.tightening.util.JsonUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<String>> create(HttpServletRequest request) {
        try {
            ProductMissionSaveDTO dto = parseDto(request);
            Long missionId = missionService.saveMission(dto, extractImages(request));
            return ResponseEntity.ok(ApiResponse.ok(String.valueOf(missionId)));
        } catch (DuplicateKeyException e) {
            return ResponseEntity.ok(ApiResponse.fail("任务名称已存在"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(ApiResponse.fail(e.getMessage()));
        } catch (Exception e) {
            log.error("Create mission failed", e);
            return ResponseEntity.ok(ApiResponse.fail("新增失败"));
        }
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<String>> update(@PathVariable Long id, HttpServletRequest request) {
        try {
            ProductMissionSaveDTO dto = parseDto(request);
            dto.setId(id);
            Long missionId = missionService.saveMission(dto, extractImages(request));
            return ResponseEntity.ok(ApiResponse.ok(String.valueOf(missionId)));
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

    @GetMapping("/{missionId}/prerequisites")
    public ResponseEntity<ApiResponse<List<MissionPrerequisiteDTO>>> listPrerequisites(@PathVariable Long missionId) {
        List<MissionPrerequisite> prerequisites = prerequisiteService.listByMissionId(missionId);
        return ResponseEntity.ok(ApiResponse.ok(Converter.entity2Dto(prerequisites, MissionPrerequisiteDTO::new)));
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

    private static MultipartHttpServletRequest asMultipart(HttpServletRequest request) {
        if (!(request instanceof MultipartHttpServletRequest mpRequest)) {
            throw new IllegalArgumentException("请求必须是 multipart/form-data");
        }
        return mpRequest;
    }

    private ProductMissionSaveDTO parseDto(HttpServletRequest request) throws Exception {
        MultipartHttpServletRequest mpRequest = asMultipart(request);
        String dtoJson = mpRequest.getParameter("dto");
        if (dtoJson == null) {
            throw new IllegalArgumentException("缺少 dto 字段");
        }
        return JsonUtils.parse(dtoJson, ProductMissionSaveDTO.class);
    }

    private Map<String, byte[]> extractImages(HttpServletRequest request) throws Exception {
        MultipartHttpServletRequest mpRequest = asMultipart(request);
        Map<String, byte[]> imageMap = new HashMap<>();
        for (Map.Entry<String, MultipartFile> entry : mpRequest.getFileMap().entrySet()) {
            imageMap.put(entry.getKey(), entry.getValue().getBytes());
        }
        return imageMap;
    }
}

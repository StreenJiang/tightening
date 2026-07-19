package com.tightening.controller;

import com.tightening.dto.ApiResponse;
import com.tightening.dto.PageResult;
import com.tightening.dto.ProductMissionDTO;
import com.tightening.dto.ProductMissionDetailDTO;
import com.tightening.service.ProductMissionService;
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

@Slf4j
@RestController
@RequestMapping("api/missions")
@RequiredArgsConstructor
public class ProductMissionController {

    private final ProductMissionService missionService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResult<ProductMissionDTO>>> list(@RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "100") int size,
                                                        @RequestParam(required = false) String name) {
        return ResponseEntity.ok(ApiResponse.ok(missionService.listByPage(page, size, name)));
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

    private static String unwrapCause(RuntimeException e) {
        return e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
    }
}

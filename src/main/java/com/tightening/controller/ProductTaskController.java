package com.tightening.controller;

import com.tightening.dto.ApiResponse;
import com.tightening.dto.PageResult;
import com.tightening.dto.ProductTaskDTO;
import com.tightening.dto.ProductTaskDetailDTO;
import com.tightening.constant.EnabledStatus;
import com.tightening.service.ProductTaskService;
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
@RequestMapping("api/tasks")
@RequiredArgsConstructor
public class ProductTaskController {

    private final ProductTaskService taskService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResult<ProductTaskDTO>>> list(@RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "100") int size,
                                                        @RequestParam(required = false) String name) {
        return ResponseEntity.ok(ApiResponse.ok(taskService.listByPage(page, size, name)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductTaskDetailDTO>> get(@PathVariable Long id) {
        ProductTaskDetailDTO dto = taskService.getDetail(id);
        if (dto == null) return ResponseEntity.ok(ApiResponse.fail("not found"));
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    @GetMapping("/check-name")
    public ResponseEntity<ApiResponse<Boolean>> checkName(@RequestParam String name,
                                                           @RequestParam(required = false) Long excludeId) {
        boolean exists = taskService.isNameDuplicate(name, excludeId);
        return ResponseEntity.ok(ApiResponse.ok(exists));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProductTaskDetailDTO>> create(@RequestBody ProductTaskDetailDTO dto) {
        try {
            ProductTaskDetailDTO result = taskService.saveTask(dto);
            return ResponseEntity.ok(ApiResponse.ok(result));
        } catch (DuplicateKeyException e) {
            return ResponseEntity.ok(ApiResponse.fail("任务名称已存在"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(ApiResponse.fail(e.getMessage()));
        } catch (RuntimeException e) {
            log.error("Create task failed", e);
            return ResponseEntity.ok(ApiResponse.fail("新增失败: " + unwrapCause(e)));
        } catch (Exception e) {
            log.error("Create task failed", e);
            return ResponseEntity.ok(ApiResponse.fail("新增失败"));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductTaskDetailDTO>> update(@PathVariable Long id, @RequestBody ProductTaskDetailDTO dto) {
        try {
            dto.setId(id);
            ProductTaskDetailDTO result = taskService.saveTask(dto);
            return ResponseEntity.ok(ApiResponse.ok(result));
        } catch (DuplicateKeyException e) {
            return ResponseEntity.ok(ApiResponse.fail("任务名称已存在"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(ApiResponse.fail(e.getMessage()));
        } catch (RuntimeException e) {
            log.error("Update task failed: id={}", id, e);
            return ResponseEntity.ok(ApiResponse.fail("更新失败: " + unwrapCause(e)));
        } catch (Exception e) {
            log.error("Update task failed: id={}", id, e);
            return ResponseEntity.ok(ApiResponse.fail("更新失败"));
        }
    }

    @PutMapping("/{id}/enabled")
    public ResponseEntity<ApiResponse<String>> setEnabled(@PathVariable Long id,
                                                           @RequestBody EnabledStatus status) {
        taskService.updateEnabled(id, status.getCode());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> delete(@PathVariable Long id) {
        taskService.cascadeDelete(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    private static String unwrapCause(RuntimeException e) {
        return e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
    }
}

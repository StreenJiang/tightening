package com.tightening.controller;

import com.tightening.constant.EnabledStatus;
import com.tightening.dto.ApiResponse;
import com.tightening.dto.PageResult;
import com.tightening.dto.ProductTaskDTO;
import com.tightening.dto.ProductTaskDetailDTO;
import com.tightening.i18n.BusinessException;
import com.tightening.service.ProductTaskService;
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

@Slf4j
@RestController
@RequestMapping("api/tasks")
@RequiredArgsConstructor
public class ProductTaskController {

    private final ProductTaskService taskService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResult<ProductTaskDTO>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) String name) {
        return ResponseEntity.ok(ApiResponse.ok(taskService.listByPage(page, size, name)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductTaskDetailDTO>> get(@PathVariable Long id) {
        ProductTaskDetailDTO dto = taskService.getDetail(id);
        if (dto == null) throw BusinessException.notFound("task.not_found");
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
        ProductTaskDetailDTO result = taskService.saveTask(dto);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductTaskDetailDTO>> update(@PathVariable Long id,
                                                                     @RequestBody ProductTaskDetailDTO dto) {
        dto.setId(id);
        ProductTaskDetailDTO result = taskService.saveTask(dto);
        return ResponseEntity.ok(ApiResponse.ok(result));
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
}

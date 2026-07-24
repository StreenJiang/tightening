package com.tightening.controller;

import com.tightening.constant.LockReason;
import com.tightening.dto.*;
import com.tightening.entity.ProductBolt;
import com.tightening.entity.ProductTask;
import com.tightening.i18n.BusinessException;
import com.tightening.lifecycle.TaskContext;
import com.tightening.lifecycle.TaskOrchestrator;
import com.tightening.service.BarcodeValidationService;
import com.tightening.service.ProductBoltService;
import com.tightening.service.ProductTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskLifecycleController {

    private final TaskOrchestrator orchestrator;
    private final ProductTaskService taskService;
    private final ProductBoltService boltService;
    private final BarcodeValidationService barcodeService;

    @PostMapping("/{id}/validate-product-barcode")
    public ResponseEntity<ApiResponse<BarcodeValidationResult>> validateProductBarcode(
            @PathVariable Long id,
            @RequestBody ValidateProductBarcodeRequest req) {
        var result = barcodeService.validateProductCode(id, req.productCode());
        if (result.matched()) {
            return ResponseEntity.ok(ApiResponse.ok(BarcodeValidationResult.matched()));
        }
        if (result.suggestedTaskId() != null) {
            return ResponseEntity.ok(ApiResponse.ok(
                    BarcodeValidationResult.wrongTask(result.suggestedTaskId())));
        }
        return ResponseEntity.ok(ApiResponse.ok(BarcodeValidationResult.notMatched()));
    }

    @PostMapping("/{id}/validate-parts-barcode")
    public ResponseEntity<ApiResponse<BarcodeValidationResult>> validatePartsBarcode(
            @PathVariable Long id,
            @RequestBody ValidatePartsBarcodeRequest req) {
        boolean pass = barcodeService.validatePartsCode(id, req.partsCode());
        if (pass) {
            orchestrator.getActiveEngine(id).ifPresent(engine -> {
                engine.getContext().setPartsCode(req.partsCode());
                engine.getContext().getLockReasons().remove(LockReason.BARCODE_REQUIRED);
            });
            return ResponseEntity.ok(ApiResponse.ok(BarcodeValidationResult.pass()));
        }
        return ResponseEntity.ok(ApiResponse.ok(
                BarcodeValidationResult.fail("barcode.material_not_matched")));
    }

    @PostMapping("/{id}/trigger")
    public ResponseEntity<ApiResponse<String>> trigger(
            @PathVariable Long id,
            @RequestBody TriggerRequestDto req) {
        if (orchestrator.getActiveEngine(id).isPresent()) {
            throw BusinessException.conflict("task.already_active");
        }
        ProductTask task = taskService.getById(id);
        if (task == null) {
            throw BusinessException.notFound("task.not_found");
        }
        List<ProductBolt> bolts = boltService.listByTaskId(id);
        if (bolts.isEmpty()) {
            throw BusinessException.of("task.has_no_bolts");
        }
        var engine = orchestrator.trigger(task, bolts,
                req.productCode(), req.partsCode());
        if (engine == null) {
            throw BusinessException.conflict("task.already_active");
        }
        return ResponseEntity.accepted()
                .body(ApiResponse.ok("trigger request accepted"));
    }

    @PostMapping("/{id}/interrupt")
    public ResponseEntity<ApiResponse<String>> interruptTask(@PathVariable Long id) {
        var engine = orchestrator.getActiveEngine(id);
        if (engine.isEmpty()) {
            throw BusinessException.notFound("task.no_active_task");
        }
        engine.get().interrupt("user interrupt");
        return ResponseEntity.ok(ApiResponse.ok("interrupted"));
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<ApiResponse<TaskStatus>> getTaskStatus(@PathVariable Long id) {
        var engineOpt = orchestrator.getActiveEngine(id);
        if (engineOpt.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.ok(
                    new TaskStatus("idle", null, null, 0, 0, null)));
        }
        var engine = engineOpt.get();
        TaskContext ctx = engine.getContext();
        String status = engine.isAlive() ? "running" : "finished";
        String stage = ctx != null && ctx.getCurrentStage() != null
                ? ctx.getCurrentStage().name() : null;
        String subState = ctx != null && ctx.getCurrentSubState() != null
                ? ctx.getCurrentSubState().name() : null;
        int currentBoltIndex = ctx != null ? ctx.getCurrentBoltIndex() : 0;
        int totalBolts = ctx != null ? ctx.totalBolts() : 0;
        Long taskRecordId = ctx != null && ctx.getTaskRecord() != null
                ? ctx.getTaskRecord().getId() : null;
        return ResponseEntity.ok(ApiResponse.ok(
                new TaskStatus(status, stage, subState, currentBoltIndex, totalBolts, taskRecordId)));
    }
}

package com.tightening.controller;

import com.tightening.constant.LockReason;
import com.tightening.dto.*;
import com.tightening.entity.ProductBolt;
import com.tightening.entity.ProductTask;
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
                BarcodeValidationResult.fail("物料码不匹配")));
    }

    @PostMapping("/{id}/trigger")
    public ResponseEntity<ApiResponse<String>> trigger(
            @PathVariable Long id,
            @RequestBody TriggerRequestDto req) {
        if (orchestrator.getActiveEngine(id).isPresent()) {
            return ResponseEntity.ok()
                    .body(ApiResponse.fail("task already active: " + id));
        }
        ProductTask task = taskService.getById(id);
        if (task == null) {
            return ResponseEntity.ok()
                    .body(ApiResponse.fail("task not found: " + id));
        }
        List<ProductBolt> bolts = boltService.listByTaskId(id);
        if (bolts.isEmpty()) {
            return ResponseEntity.ok()
                    .body(ApiResponse.fail("task has no bolts: " + id));
        }
        var engine = orchestrator.trigger(task, bolts,
                req.productCode(), req.partsCode());
        if (engine == null) {
            return ResponseEntity.ok()
                    .body(ApiResponse.fail("task already active: " + id));
        }
        return ResponseEntity.accepted()
                .body(ApiResponse.ok("trigger request accepted"));
    }

    // 原有端点保留
    @PostMapping("/{id}/interrupt")
    public ResponseEntity<ApiResponse<String>> interruptTask(@PathVariable Long id) {
        var engine = orchestrator.getActiveEngine(id);
        if (engine.isEmpty()) {
            return ResponseEntity.ok()
                    .body(ApiResponse.fail("no active task: " + id));
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

package com.tightening.controller;

import com.tightening.constant.LockReason;
import com.tightening.dto.*;
import com.tightening.entity.ProductBolt;
import com.tightening.entity.ProductMission;
import com.tightening.lifecycle.MissionContext;
import com.tightening.lifecycle.MissionOrchestrator;
import com.tightening.service.BarcodeValidationService;
import com.tightening.service.ProductBoltService;
import com.tightening.service.ProductMissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/missions")
@RequiredArgsConstructor
public class MissionLifecycleController {

    private final MissionOrchestrator orchestrator;
    private final ProductMissionService missionService;
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
        if (result.suggestedMissionId() != null) {
            return ResponseEntity.ok(ApiResponse.ok(
                    BarcodeValidationResult.wrongMission(result.suggestedMissionId())));
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
                    .body(ApiResponse.fail("mission already active: " + id));
        }
        ProductMission mission = missionService.getById(id);
        if (mission == null) {
            return ResponseEntity.ok()
                    .body(ApiResponse.fail("mission not found: " + id));
        }
        List<ProductBolt> bolts = boltService.listByMissionId(id);
        if (bolts.isEmpty()) {
            return ResponseEntity.ok()
                    .body(ApiResponse.fail("mission has no bolts: " + id));
        }
        var engine = orchestrator.trigger(mission, bolts,
                req.productCode(), req.partsCode());
        if (engine == null) {
            return ResponseEntity.ok()
                    .body(ApiResponse.fail("mission already active: " + id));
        }
        return ResponseEntity.accepted()
                .body(ApiResponse.ok("trigger request accepted"));
    }

    // 原有端点保留
    @PostMapping("/{id}/interrupt")
    public ResponseEntity<ApiResponse<String>> interruptMission(@PathVariable Long id) {
        var engine = orchestrator.getActiveEngine(id);
        if (engine.isEmpty()) {
            return ResponseEntity.ok()
                    .body(ApiResponse.fail("no active mission: " + id));
        }
        engine.get().interrupt("user interrupt");
        return ResponseEntity.ok(ApiResponse.ok("interrupted"));
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<ApiResponse<MissionStatus>> getMissionStatus(@PathVariable Long id) {
        var engineOpt = orchestrator.getActiveEngine(id);
        if (engineOpt.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.ok(
                    new MissionStatus("idle", null, null, 0, 0, null)));
        }
        var engine = engineOpt.get();
        MissionContext ctx = engine.getContext();
        String status = engine.isAlive() ? "running" : "finished";
        String stage = ctx != null && ctx.getCurrentStage() != null
                ? ctx.getCurrentStage().name() : null;
        String subState = ctx != null && ctx.getCurrentSubState() != null
                ? ctx.getCurrentSubState().name() : null;
        int currentBoltIndex = ctx != null ? ctx.getCurrentBoltIndex() : 0;
        int totalBolts = ctx != null ? ctx.totalBolts() : 0;
        Long missionRecordId = ctx != null && ctx.getMissionRecord() != null
                ? ctx.getMissionRecord().getId() : null;
        return ResponseEntity.ok(ApiResponse.ok(
                new MissionStatus(status, stage, subState, currentBoltIndex, totalBolts, missionRecordId)));
    }
}

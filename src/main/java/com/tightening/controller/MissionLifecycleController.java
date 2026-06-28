package com.tightening.controller;

import com.tightening.dto.ApiResponse;
import com.tightening.entity.ProductBolt;
import com.tightening.entity.ProductMission;
import com.tightening.lifecycle.MissionOrchestrator;
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

    @PostMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<Long>> activateMission(@PathVariable Long id) {
        if (orchestrator.getActiveEngine(id).isPresent()) {
            return ResponseEntity.ok(ApiResponse.fail("mission already active: " + id));
        }
        ProductMission mission = missionService.getById(id);
        if (mission == null) {
            return ResponseEntity.ok(ApiResponse.fail("mission not found: " + id));
        }
        List<ProductBolt> bolts = boltService.listByMissionId(id);
        if (bolts.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.fail("mission has no bolts: " + id));
        }
        var engine = orchestrator.startMission(mission, bolts);
        if (engine == null) {
            return ResponseEntity.ok(ApiResponse.fail("mission already active: " + id));
        }
        return ResponseEntity.ok(ApiResponse.ok(mission.getId()));
    }

    @PostMapping("/{id}/interrupt")
    public ResponseEntity<ApiResponse<String>> interruptMission(@PathVariable Long id) {
        var engine = orchestrator.getActiveEngine(id);
        if (engine.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.fail("no active mission: " + id));
        }
        engine.get().interrupt("user interrupt");
        return ResponseEntity.ok(ApiResponse.ok("interrupted"));
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<ApiResponse<String>> getMissionStatus(@PathVariable Long id) {
        var engine = orchestrator.getActiveEngine(id);
        if (engine.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.ok("idle"));
        }
        String status = engine.get().isAlive() ? "running" : "finished";
        return ResponseEntity.ok(ApiResponse.ok(status));
    }
}

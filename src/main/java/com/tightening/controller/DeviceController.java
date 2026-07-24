package com.tightening.controller;

import com.tightening.config.ToolCommonConfig;
import com.tightening.device.DeviceManager;
import com.tightening.device.handler.DeviceHandler;
import com.tightening.device.handler.ToolHandler;
import com.tightening.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

@RestController
@RequestMapping("api/devices")
public class DeviceController {

    @Autowired
    private DeviceManager deviceManager;
    @Autowired
    private ToolCommonConfig toolCommonConfig;

    @PutMapping("/{id}/enable")
    public DeferredResult<ResponseEntity<ApiResponse<Boolean>>> enableTool(@PathVariable("id") long deviceId) {
        DeviceHandler handler = deviceManager.getHandler(deviceId);
        DeferredResult<ResponseEntity<ApiResponse<Boolean>>> deferredResult = newDeferredResult();

        if (handler instanceof ToolHandler toolHandler) {
            toolHandler.unlock(deviceId)
                    .thenApply(result -> ResponseEntity.ok(ApiResponse.ok(result)))
                    .exceptionally(ex -> ResponseEntity.ok(
                            ApiResponse.fail("device.cmd.unlock_failed")))
                    .thenAccept(deferredResult::setResult);
            return deferredResult;
        }

        deferredResult.setResult(ResponseEntity.ok(ApiResponse.ok(false)));
        return deferredResult;
    }

    @PutMapping("/{id}/disable")
    public DeferredResult<ResponseEntity<ApiResponse<Boolean>>> disableTool(@PathVariable("id") long deviceId) {
        DeviceHandler handler = deviceManager.getHandler(deviceId);
        DeferredResult<ResponseEntity<ApiResponse<Boolean>>> deferredResult = newDeferredResult();

        if (handler instanceof ToolHandler toolHandler) {
            toolHandler.lock(deviceId)
                    .thenApply(result -> ResponseEntity.ok(ApiResponse.ok(result)))
                    .exceptionally(ex -> ResponseEntity.ok(
                            ApiResponse.fail("device.cmd.lock_failed")))
                    .thenAccept(deferredResult::setResult);
            return deferredResult;
        }

        deferredResult.setResult(ResponseEntity.ok(ApiResponse.ok(false)));
        return deferredResult;
    }

    @GetMapping("/{id}/enabled")
    public ResponseEntity<ApiResponse<Boolean>> getEnabled(@PathVariable("id") long deviceId) {
        DeviceHandler handler = deviceManager.getHandler(deviceId);
        boolean result = false;
        if (handler instanceof ToolHandler toolHandler) {
            result = toolHandler.isUnlocked(deviceId);
        }
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PutMapping("/{id}/parameter-set/{pSet}")
    public DeferredResult<ResponseEntity<ApiResponse<Boolean>>> sendPSet(@PathVariable("id") long deviceId,
                                                                          @PathVariable int pSet) {
        DeviceHandler handler = deviceManager.getHandler(deviceId);
        DeferredResult<ResponseEntity<ApiResponse<Boolean>>> deferredResult = newDeferredResult();

        if (handler instanceof ToolHandler toolHandler) {
            toolHandler.sendPSetOp(deviceId, pSet)
                    .thenApply(result -> ResponseEntity.ok(ApiResponse.ok(result)))
                    .exceptionally(ex -> ResponseEntity.ok(
                            ApiResponse.fail("device.cmd.pset_failed")))
                    .thenAccept(deferredResult::setResult);
            return deferredResult;
        }

        deferredResult.setResult(ResponseEntity.ok(ApiResponse.ok(false)));
        return deferredResult;
    }

    private DeferredResult<ResponseEntity<ApiResponse<Boolean>>> newDeferredResult() {
        DeferredResult<ResponseEntity<ApiResponse<Boolean>>> dr =
                new DeferredResult<>(toolCommonConfig.getCmdTimeoutMs());
        dr.onTimeout(() -> dr.setResult(ResponseEntity.ok(
                ApiResponse.fail(HttpStatus.REQUEST_TIMEOUT, "device.cmd.timeout"))));
        return dr;
    }
}

package com.tightening.controller;

import com.tightening.device.DeviceManager;
import com.tightening.device.handler.DeviceHandler;
import com.tightening.device.handler.ToolHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.concurrent.CompletableFuture;

import static com.tightening.constant.ToolConstants.CMD_TIMEOUT_MS;

@RestController
@RequestMapping("api/devices")
public class DeviceController {

    @Autowired
    private DeviceManager deviceManager;

    @PutMapping("/{id}/enable")
    public DeferredResult<ResponseEntity<Boolean>> enableTool(@PathVariable("id") long deviceId) {
        DeviceHandler handler = deviceManager.getHandler(deviceId);
        DeferredResult<ResponseEntity<Boolean>> deferredResult = new DeferredResult<>(CMD_TIMEOUT_MS);
        deferredResult.onTimeout(() -> deferredResult.setResult(ResponseEntity.status(408).body(false)));

        if (handler instanceof ToolHandler toolHandler) {
            toolHandler.unlock(deviceId)
                    .thenApply(ResponseEntity::ok)
                    .exceptionally(ex -> ResponseEntity.status(500).body(false))
                    .thenAccept(deferredResult::setResult);
            return deferredResult;
        }

        deferredResult.setResult(ResponseEntity.ok(false));
        return deferredResult;
    }

    @PutMapping("/{id}/disable")
    public DeferredResult<ResponseEntity<Boolean>> disableTool(@PathVariable("id") long deviceId) {
        DeviceHandler handler = deviceManager.getHandler(deviceId);
        DeferredResult<ResponseEntity<Boolean>> deferredResult = new DeferredResult<>(CMD_TIMEOUT_MS);
        deferredResult.onTimeout(() -> deferredResult.setResult(ResponseEntity.status(408).body(false)));

        if (handler instanceof ToolHandler toolHandler) {
            toolHandler.lock(deviceId)
                    .thenApply(ResponseEntity::ok)
                    .exceptionally(ex -> ResponseEntity.status(500).body(false))
                    .thenAccept(deferredResult::setResult);
            return deferredResult;
        }

        deferredResult.setResult(ResponseEntity.ok(false));
        return deferredResult;
    }

    @GetMapping("/{id}/enabled")
    public ResponseEntity<Boolean> getEnabled(@PathVariable("id") long deviceId) {
        DeviceHandler handler = deviceManager.getHandler(deviceId);
        boolean result = false;
        if (handler instanceof ToolHandler toolHandler) {
            result = toolHandler.isUnlocked(deviceId);
        }
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{id}/parameter-set/{pSet}")
    public DeferredResult<ResponseEntity<Boolean>> sendPSet(@PathVariable("id") long deviceId,
                                                            @PathVariable int pSet) {
        DeviceHandler handler = deviceManager.getHandler(deviceId);
        DeferredResult<ResponseEntity<Boolean>> deferredResult = new DeferredResult<>(CMD_TIMEOUT_MS);
        deferredResult.onTimeout(() -> deferredResult.setResult(ResponseEntity.status(408).body(false)));

        if (handler instanceof ToolHandler toolHandler) {
            toolHandler.sendPSetOp(deviceId, pSet).thenApply(ResponseEntity::ok)
                    .exceptionally(ex -> ResponseEntity.status(500).body(false))
                    .thenAccept(deferredResult::setResult);
            return deferredResult;
        }

        deferredResult.setResult(ResponseEntity.ok(false));
        return deferredResult;
    }
}

package com.tightening.controller;

import com.tightening.device.DeviceManager;
import com.tightening.device.handler.DeviceHandler;
import com.tightening.device.handler.ToolHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.concurrent.CompletableFuture;

import static com.tightening.constant.ToolConstants.CMD_TIMEOUT;

@RestController
@RequestMapping("api/devices")
public class DeviceController {

    @Autowired
    private DeviceManager deviceManager;

    @PostMapping("/{id}/{enable}")
    public DeferredResult<ResponseEntity<Boolean>> sendTargetEnabled(@PathVariable("id") long deviceId,
                                                                     @PathVariable boolean enable) {
        DeviceHandler handler = deviceManager.getHandler(deviceId);
        DeferredResult<ResponseEntity<Boolean>> deferredResult = new DeferredResult<>(CMD_TIMEOUT);
        deferredResult.onTimeout(() -> deferredResult.setResult(ResponseEntity.status(408).body(false)));

        if (handler instanceof ToolHandler toolHandler) {
            CompletableFuture<Boolean> resultFuture;
            if (enable) {
                resultFuture = toolHandler.enableToolOp(deviceId);
            } else {
                resultFuture = toolHandler.disableToolOp(deviceId);
            }
            resultFuture.thenApply(ResponseEntity::ok)
                    .exceptionally(ex -> ResponseEntity.status(500).body(false))
                    .thenAccept(deferredResult::setResult);
            return deferredResult;
        }

        deferredResult.setResult(ResponseEntity.ok(false));
        return deferredResult;
    }

    @PostMapping("enabled/{id}")
    public ResponseEntity<Boolean> getEnabled(@PathVariable("id") long deviceId) {
        DeviceHandler handler = deviceManager.getHandler(deviceId);
        boolean result = false;
        if (handler instanceof ToolHandler toolHandler) {
            result = toolHandler.isToolEnabled();
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("parameter-set/{id}/{pSet}")
    public DeferredResult<ResponseEntity<Boolean>> sendPSet(@PathVariable("id") long deviceId,
                                                            @PathVariable int pSet) {
        DeviceHandler handler = deviceManager.getHandler(deviceId);
        DeferredResult<ResponseEntity<Boolean>> deferredResult = new DeferredResult<>(CMD_TIMEOUT);
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

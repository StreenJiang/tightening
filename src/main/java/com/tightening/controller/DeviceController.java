package com.tightening.controller;

import com.tightening.constant.TCPCommand;
import com.tightening.device.DeviceManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/devices")
public class DeviceController {

    @Autowired
    private DeviceManager deviceManager;

    @GetMapping("/{id}/{enable}")
    public ResponseEntity<Boolean> sendCommand(@PathVariable long id, @PathVariable boolean enable) {
        boolean result = deviceManager.sendCommand(
                id, enable ? TCPCommand.TOOL_ENABLE : TCPCommand.TOOL_DISABLE);
        return ResponseEntity.ok(result);
    }
}

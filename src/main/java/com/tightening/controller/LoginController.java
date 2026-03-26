package com.tightening.controller;

import com.tightening.constant.DeviceType;
import com.tightening.device.DeviceManager;
import com.tightening.entity.Device;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("api/login")
public class LoginController {

    @Autowired
    private DeviceManager deviceManager;

    @GetMapping()
    public ResponseEntity<Void> userLogin() {
        Device d1 = new Device();
        d1.setId(1L);
        d1.setType(DeviceType.FIT_FTC6.getId());
        d1.setDetail("""
                             {"ip":"172.17.10.10","port":5000}
                             """);
        deviceManager.userLoggedIn(List.of(d1));
        return ResponseEntity.ok(null);
    }
}

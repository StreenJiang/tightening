package com.tightening.controller;

import com.tightening.constant.DeviceType;
import com.tightening.device.DeviceManager;
import com.tightening.dto.UserAccountInfoDTO;
import com.tightening.entity.Device;
import com.tightening.service.UserAccountInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("api/users")
public class UserAccountInfoController {
    private final UserAccountInfoService userAccountInfoService;

    @Autowired
    private DeviceManager deviceManager;

    public UserAccountInfoController(final UserAccountInfoService userAccountInfoService) {
        this.userAccountInfoService = userAccountInfoService;
    }

    @GetMapping("/login")
    public ResponseEntity<Void> userLogin() {
        Device d1 = new Device();
        d1.setId(1L);
        d1.setType(DeviceType.ATLAS_PF4000.getId());

        Device d2 = new Device();
        d2.setId(2L);
        d2.setType(DeviceType.ATLAS_PF6000_OP.getId());

        Device d3 = new Device();
        d3.setId(3L);
        d3.setType(DeviceType.ATLAS_PF6000_OP.getId());

        deviceManager.userLoggedIn(List.of(d1, d2, d3));
        return ResponseEntity.ok(null);
    }

    @GetMapping
    public ResponseEntity<List<UserAccountInfoDTO>> getUsers() {
        return ResponseEntity.ok(userAccountInfoService.getUserList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserAccountInfoDTO> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userAccountInfoService.getUserById(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Boolean> deleteUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userAccountInfoService.removeById(id));
    }
}

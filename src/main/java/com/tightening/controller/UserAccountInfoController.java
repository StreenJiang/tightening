package com.tightening.controller;

import com.tightening.dto.UserAccountInfoDTO;
import com.tightening.service.UserAccountInfoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("api/users")
public class UserAccountInfoController {
    private final UserAccountInfoService userAccountInfoService;

    public UserAccountInfoController(final UserAccountInfoService userAccountInfoService) {
        this.userAccountInfoService = userAccountInfoService;
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

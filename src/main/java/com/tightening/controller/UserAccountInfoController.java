package com.tightening.controller;

import com.tightening.dto.response.UserAccountInfoDTO;
import com.tightening.entity.UserAccountInfo;
import com.tightening.service.UserAccountInfoService;
import org.springframework.beans.BeanUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
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
        List<UserAccountInfo> list = userAccountInfoService.list();

        List<UserAccountInfoDTO> result = new ArrayList<>();
        list.forEach(user -> {
            UserAccountInfoDTO userAccountInfoDTO = new UserAccountInfoDTO();
            BeanUtils.copyProperties(user, userAccountInfoDTO);
            result.add(userAccountInfoDTO);
        });

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserAccountInfoDTO> getUserById(@PathVariable Long id) {
        UserAccountInfo byId = userAccountInfoService.getById(id);
        UserAccountInfoDTO dto = new UserAccountInfoDTO();
        BeanUtils.copyProperties(byId, dto);
        return ResponseEntity.ok(dto);
    }
}

package com.tightening.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserAccountInfoDTO {
    private Long id;
    private String staffId;
    private String name;
    private String position;
    private String account;
    private String password;
    private String operationPassword;
    private Long userId;
    private Integer deleted;
    private String creator;
    private String modifier;
    private LocalDateTime createTime;
    private LocalDateTime modifyTime;
}

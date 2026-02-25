package com.tightening.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("user_account_info")
public class UserAccountInfo {

    @TableId(type = IdType.AUTO)
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

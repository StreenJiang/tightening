package com.tightening.dto;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class UserAccountInfoDTO extends BaseDTO {
    private String staffId;
    private String name;
    private String position;
    private String account;
    private String password;
    private String operationPassword;
}

package com.tightening.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class BaseDTO {
    private Long id;
    private Integer deleted;
    private Long creatorId;
    private Long modifierId;
    private LocalDateTime createTime;
    private LocalDateTime modifyTime;
}

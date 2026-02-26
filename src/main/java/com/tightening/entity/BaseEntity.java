package com.tightening.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer deleted;
    private Long creatorId;
    private Long modifierId;
    private LocalDateTime createTime;
    private LocalDateTime modifyTime;
}

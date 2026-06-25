package com.tightening.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

@Setter
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("bolt_device_binding")
public class BoltDeviceBinding extends BaseEntity {
    private Long productBoltId;
    private Long deviceId;
    private Integer deviceRole;
    private Double deviceSpec;
    private Integer sortOrder;
}

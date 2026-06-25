package com.tightening.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.tightening.config.SqliteBlobTypeHandler;
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
@TableName(value = "product_side", autoResultMap = true)
public class ProductSide extends BaseEntity {
    private Long productMissionId;
    private String name;

    @JsonIgnore
    @TableField(typeHandler = SqliteBlobTypeHandler.class)
    private byte[] imageData;

    @JsonIgnore
    @TableField(typeHandler = SqliteBlobTypeHandler.class)
    private byte[] renderedImageData;

    @JsonIgnore
    @TableField(typeHandler = SqliteBlobTypeHandler.class)
    private byte[] thumbnailData;
}

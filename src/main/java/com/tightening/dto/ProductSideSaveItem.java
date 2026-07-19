package com.tightening.dto;

import java.util.List;

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
public class ProductSideSaveItem extends BaseDTO {
    private String name;
    private List<ProductBoltSaveItem> bolts;
    private String image;
    private String renderedImage;
    private String thumbnail;
}

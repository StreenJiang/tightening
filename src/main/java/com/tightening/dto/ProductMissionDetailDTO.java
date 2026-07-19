package com.tightening.dto;

import com.tightening.constant.InspectionScope;
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
public class ProductMissionDetailDTO extends BaseDTO {
    private String name;
    private Integer maxNgCount;
    private Integer passwordRequiredNgCount;
    private Integer enabled;
    private Integer multiDeviceIndependent;
    private Integer skipScrew;
    private Integer isInspection;
    private InspectionScope inspectionScope;

    private List<Long> inspectionBoundMissionIds;
    private List<PrerequisiteSaveItem> prerequisites;
    private List<BarCodeRuleSaveItem> barcodeRules;
    private List<ProductSideSaveItem> sides;
}

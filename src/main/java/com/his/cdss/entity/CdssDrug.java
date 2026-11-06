package com.his.cdss.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** E4-01 药品知识库：适应症、禁忌、剂量、特殊人群。 */
@Data
@TableName("cdss_drug")
public class CdssDrug {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String drugCode;

    private String drugName;

    private String category;

    private String indicationsJson;

    /** 禁忌诊断 ICD-10 编码数组。 */
    private String contraindicationsJson;

    private String adultDose;

    /** 儿童单次剂量（mg/kg）。 */
    private BigDecimal childDoseMgPerKg;

    private BigDecimal maxDailyDoseMg;

    private String renalAdjustNote;

    private String pregnancyNote;

    private Boolean enabled;

    private LocalDateTime createdAt;
}

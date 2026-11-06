package com.his.cdss.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** E4-05 临床路径模板。 */
@Data
@TableName("clinical_pathway")
public class ClinicalPathway {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String pathwayCode;

    private String name;

    private String diagnosisCode;

    private String department;

    private Integer version;

    private Integer standardDays;

    private String description;

    private Boolean enabled;

    private LocalDateTime createdAt;
}

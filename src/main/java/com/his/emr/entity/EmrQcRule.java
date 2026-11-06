package com.his.emr.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("emr_qc_rule")
public class EmrQcRule {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String ruleCode;

    private String name;

    private String category;

    private String severity;

    private String docType;

    private String paramsJson;

    private Boolean enabled;

    private LocalDateTime createdAt;
}

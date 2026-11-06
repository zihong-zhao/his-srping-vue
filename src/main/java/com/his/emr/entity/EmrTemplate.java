package com.his.emr.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("emr_template")
public class EmrTemplate {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String docType;

    private String name;

    private String scope;

    private Long deptId;

    private Integer version;

    private String schemaJson;

    private Boolean enabled;

    private LocalDateTime createdAt;
}

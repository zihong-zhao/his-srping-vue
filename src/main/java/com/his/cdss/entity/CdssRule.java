package com.his.cdss.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** E4 规则元数据：控制校验项启用与严重级别。 */
@Data
@TableName("cdss_rule")
public class CdssRule {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String ruleCode;

    private String name;

    private String category;

    private String severity;

    private String description;

    private Boolean enabled;

    private LocalDateTime createdAt;
}

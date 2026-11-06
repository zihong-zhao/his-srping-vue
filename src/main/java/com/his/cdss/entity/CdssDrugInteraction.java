package com.his.cdss.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** E4-01 药品-药品相互作用。 */
@Data
@TableName("cdss_drug_interaction")
public class CdssDrugInteraction {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String drugACode;

    private String drugBCode;

    private String severity;

    private String mechanism;

    private Boolean enabled;

    private LocalDateTime createdAt;
}

package com.his.emr.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** E7-05 ICD 编码字典：ICD10 诊断、ICD9CM3 手术操作。 */
@Data
@TableName("emr_icd_code")
public class EmrIcdCode {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String code;

    private String name;

    private String type;

    private Boolean enabled;

    private LocalDateTime createdAt;
}

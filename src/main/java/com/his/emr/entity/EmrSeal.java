package com.his.emr.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 封存/解封留痕，只增不改。 */
@Data
@TableName("emr_seal")
public class EmrSeal {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long docId;

    /** SEAL / RELEASE */
    private String action;

    private String reason;

    private Long operatorId;

    private String operatorName;

    private LocalDateTime createdAt;
}

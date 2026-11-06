package com.his.cdss.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** E4-06/E4-07 提醒触发与处理留痕。 */
@Data
@TableName("cdss_alert_log")
public class CdssAlertLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long patientId;

    private Long visitId;

    private String bizType;

    private String bizNo;

    private String ruleCode;

    private String severity;

    private String message;

    private String decision;

    private String overrideReason;

    private Long operatorId;

    private String operatorName;

    private LocalDateTime createdAt;
}

package com.his.emr.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("emr_access_log")
public class EmrAccessLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long docId;

    private Long operatorId;

    private String operatorName;

    private String action;

    private String reason;

    private String ip;

    private LocalDateTime createdAt;
}

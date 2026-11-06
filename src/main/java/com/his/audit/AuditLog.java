package com.his.audit;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audit_log")
public class AuditLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long operatorId;

    private String operatorName;

    private String action;

    private String resourceType;

    private String requestUri;

    private String httpMethod;

    private String ip;

    private Boolean success;

    private String errorMsg;

    private Long durationMs;

    private LocalDateTime createdAt;
}

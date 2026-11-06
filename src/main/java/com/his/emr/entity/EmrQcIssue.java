package com.his.emr.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("emr_qc_issue")
public class EmrQcIssue {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long docId;

    private String ruleCode;

    private String ruleName;

    private String category;

    private String severity;

    private String defectDesc;

    private String status;

    private Long assigneeId;

    private String assigneeName;

    private Long closedBy;

    private LocalDateTime closedAt;

    private String closedNote;

    private LocalDateTime createdAt;
}

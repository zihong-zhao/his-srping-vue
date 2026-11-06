package com.his.emr.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** E7-04 病案借阅申请，状态：PENDING / APPROVED / REJECTED / RETURNED。 */
@Data
@TableName("emr_borrow_request")
public class EmrBorrowRequest {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long docId;

    private Long applicantId;

    private String applicantName;

    private String reason;

    private Integer days;

    private String status;

    private Long approverId;

    private String approverName;

    private String approveNote;

    private LocalDateTime approvedAt;

    private LocalDateTime startAt;

    private LocalDateTime dueAt;

    private LocalDateTime returnedAt;

    private LocalDateTime createdAt;
}

package com.his.emr.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** E7-04 病案复印申请，状态：PENDING / APPROVED / REJECTED / COMPLETED。 */
@Data
@TableName("emr_copy_request")
public class EmrCopyRequest {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long docId;

    private Long applicantId;

    private String applicantName;

    private String purpose;

    private Integer copies;

    private String status;

    private Long approverId;

    private String approverName;

    private String approveNote;

    private LocalDateTime approvedAt;

    private Long completedBy;

    private String completedByName;

    private LocalDateTime completedAt;

    private LocalDateTime createdAt;
}

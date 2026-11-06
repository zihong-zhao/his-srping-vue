package com.his.cdss.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/** E4-04 危急值闭环：上报 → 确认 → 处理关闭。 */
@Data
@TableName("cdss_critical_value")
public class CdssCriticalValue {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long patientId;

    private Long visitId;

    private String itemCode;

    private String itemName;

    private String resultValue;

    private String unit;

    private String referenceRange;

    private String severity;

    private String status;

    private Long reportedBy;

    private String reportedByName;

    private LocalDateTime reportedAt;

    private Long confirmedBy;

    private String confirmedByName;

    private LocalDateTime confirmedAt;

    private String handlingNote;

    private LocalDateTime closedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Version
    private Integer version;
}

package com.his.cdss.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/** E4-05 患者入径实例。 */
@Data
@TableName("pathway_instance")
public class PathwayInstance {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long pathwayId;

    private Long patientId;

    private Long visitId;

    private String status;

    private Integer currentSequence;

    private Long startedBy;

    private String startedByName;

    private LocalDateTime startedAt;

    private LocalDateTime endedAt;

    private String exitReason;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Version
    private Integer version;
}

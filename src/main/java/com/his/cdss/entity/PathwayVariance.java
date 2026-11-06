package com.his.cdss.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** E4-05 路径变异记录。 */
@Data
@TableName("pathway_variance")
public class PathwayVariance {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long instanceId;

    private Long nodeId;

    private String nodeCode;

    private String varianceType;

    private String reason;

    private Long recordedBy;

    private String recordedByName;

    private LocalDateTime recordedAt;
}

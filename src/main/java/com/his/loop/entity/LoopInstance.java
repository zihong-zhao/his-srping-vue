package com.his.loop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/** E5 闭环单据实例：医嘱 / 处方 / 检验 / 检查。 */
@Data
@TableName("loop_instance")
public class LoopInstance {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String bizNo;

    private String loopType;

    private Long patientId;

    private Long visitId;

    private String title;

    private String status;

    private String currentNode;

    private Long startedBy;

    private String startedByName;

    private LocalDateTime startedAt;

    private LocalDateTime closedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Version
    private Integer version;
}

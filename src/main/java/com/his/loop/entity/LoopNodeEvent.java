package com.his.loop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** E5 闭环节点事件：记录每个节点、操作人、时间与业务载荷。 */
@Data
@TableName("loop_node_event")
public class LoopNodeEvent {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long loopId;

    private String nodeCode;

    private String nodeName;

    private Integer sequence;

    private Long operatorId;

    private String operatorName;

    private LocalDateTime occurredAt;

    private String payloadJson;
}

package com.his.cdss.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** E4-05 路径节点执行记录。 */
@Data
@TableName("pathway_node_record")
public class PathwayNodeRecord {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long instanceId;

    private Long nodeId;

    private String nodeCode;

    private String nodeName;

    private Integer sequence;

    private Long completedBy;

    private String completedByName;

    private LocalDateTime completedAt;

    private String note;
}

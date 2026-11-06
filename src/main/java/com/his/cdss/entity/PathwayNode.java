package com.his.cdss.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** E4-05 路径节点。 */
@Data
@TableName("pathway_node")
public class PathwayNode {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long pathwayId;

    private Integer sequence;

    private String nodeCode;

    private String nodeName;

    private Integer dayOffset;

    private Boolean required;

    private String description;
}

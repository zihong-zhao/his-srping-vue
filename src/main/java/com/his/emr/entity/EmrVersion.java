package com.his.emr.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("emr_version")
public class EmrVersion {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long docId;

    private Integer versionNo;

    private String contentJson;

    private String changeSummary;

    private Long createdBy;

    private String createdByName;

    private LocalDateTime createdAt;
}

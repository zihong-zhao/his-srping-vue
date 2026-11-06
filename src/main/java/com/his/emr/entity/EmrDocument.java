package com.his.emr.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("emr_document")
public class EmrDocument {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long patientId;

    private Long visitId;

    private String docType;

    private Long templateId;

    private Integer templateVersion;

    private String title;

    private String status;

    private Long authorId;

    private String authorName;

    private String contentJson;

    private Integer currentVersion;

    private LocalDateTime signedAt;

    private LocalDateTime archivedAt;

    private Long archivedBy;

    private String archiveHash;

    private Boolean sealed;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Long updatedBy;

    @Version
    private Integer version;
}

package com.his.emr.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("emr_signature")
public class EmrSignature {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long docId;

    private Integer versionNo;

    private Long signerId;

    private String signerName;

    private String signType;

    private String certSn;

    private String contentHash;

    private String signature;

    private LocalDateTime signedAt;
}

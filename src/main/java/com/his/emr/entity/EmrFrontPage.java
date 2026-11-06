package com.his.emr.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/** E7-01 病案首页：诊断、手术、麻醉、离院方式、费用归类。 */
@Data
@TableName("emr_front_page")
public class EmrFrontPage {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long patientId;

    private Long visitId;

    private Long docId;

    private String inpatientNo;

    private LocalDateTime admissionDate;

    private LocalDateTime dischargeDate;

    private String admissionDept;

    private String dischargeDept;

    private String dischargeWay;

    private String anesthesiaType;

    private String attendingDoctor;

    private String diagnosesJson;

    private String surgeriesJson;

    private String costJson;

    private String status;

    private Long verifiedBy;

    private String verifiedByName;

    private LocalDateTime verifiedAt;

    private String verifyIssuesJson;

    private Long createdBy;

    private String createdByName;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Version
    private Integer version;
}

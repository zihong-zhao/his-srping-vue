package com.his.emr.qc;

import com.his.emr.entity.EmrDocument;
import com.his.emr.entity.EmrTemplate;

import java.time.LocalDateTime;

/** 质控校验上下文：一次校验所需的病历、模板与基准时间。 */
public record QcCheckContext(EmrDocument document, EmrTemplate template, LocalDateTime now) {
}

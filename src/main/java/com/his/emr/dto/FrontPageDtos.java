package com.his.emr.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class FrontPageDtos {

    private FrontPageDtos() {
    }

    public record DiagnosisItem(
            @NotBlank(message = "诊断编码不能为空") String code,
            String name,
            boolean main) {
    }

    public record SurgeryItem(
            @NotBlank(message = "手术编码不能为空") String code,
            String name,
            LocalDateTime surgeryDate) {
    }

    public record CostBreakdown(
            BigDecimal total,
            BigDecimal drug,
            BigDecimal material,
            BigDecimal bed,
            BigDecimal exam,
            BigDecimal treatment,
            BigDecimal nursing,
            BigDecimal other) {
    }

    public record SaveFrontPageRequest(
            @NotNull(message = "患者 ID 不能为空") Long patientId,
            @NotNull(message = "就诊 ID 不能为空") Long visitId,
            Long docId,
            String inpatientNo,
            LocalDateTime admissionDate,
            LocalDateTime dischargeDate,
            String admissionDept,
            String dischargeDept,
            @NotBlank(message = "离院方式不能为空") String dischargeWay,
            String anesthesiaType,
            String attendingDoctor,
            @Valid @NotEmpty(message = "至少填写一条诊断") List<DiagnosisItem> diagnoses,
            @Valid List<SurgeryItem> surgeries,
            @Valid CostBreakdown cost) {
    }

    public record IcdCodeView(
            Long id,
            String code,
            String name,
            String type) {
    }

    public record CodingIssue(
            String code,
            String item,
            String severity,
            String message) {
    }

    public record VerifyResult(
            Long frontPageId,
            boolean verified,
            List<CodingIssue> issues,
            LocalDateTime verifiedAt) {
    }

    public record FrontPageView(
            Long id,
            Long patientId,
            Long visitId,
            Long docId,
            String inpatientNo,
            LocalDateTime admissionDate,
            LocalDateTime dischargeDate,
            String admissionDept,
            String dischargeDept,
            String dischargeWay,
            String anesthesiaType,
            String attendingDoctor,
            List<DiagnosisItem> diagnoses,
            List<SurgeryItem> surgeries,
            CostBreakdown cost,
            String status,
            String verifiedByName,
            LocalDateTime verifiedAt,
            List<CodingIssue> verifyIssues,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }
}

package com.his.cdss.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class CdssDtos {

    private CdssDtos() {
    }

    public record DrugView(
            Long id,
            String drugCode,
            String drugName,
            String category,
            List<String> indications,
            List<String> contraindications,
            String adultDose,
            BigDecimal childDoseMgPerKg,
            BigDecimal maxDailyDoseMg,
            String renalAdjustNote,
            String pregnancyNote) {
    }

    public record DrugReviewItem(
            @NotBlank(message = "药品编码不能为空") String drugCode,
            BigDecimal dose,
            String unit,
            String frequency) {
    }

    public record ReviewRequest(
            @NotNull(message = "患者 ID 不能为空") Long patientId,
            Long visitId,
            Integer ageYears,
            BigDecimal weightKg,
            BigDecimal egfr,
            Boolean pregnant,
            List<String> diagnosisCodes,
            @Valid @NotEmpty(message = "至少有一味药品") List<DrugReviewItem> drugs,
            String bizNo,
            String overrideReason) {
    }

    public record AlertView(
            Long id,
            String ruleCode,
            String severity,
            String message,
            String decision,
            String overrideReason,
            LocalDateTime createdAt) {
    }

    public record ReviewResult(
            Long patientId,
            Long visitId,
            String bizNo,
            boolean blocked,
            boolean requiresOverride,
            String decision,
            List<AlertView> alerts) {
    }

    public record IgnoreRequest(@NotBlank(message = "忽略理由不能为空") String reason) {
    }

    public record ReportCriticalValueRequest(
            @NotNull(message = "患者 ID 不能为空") Long patientId,
            Long visitId,
            @NotBlank(message = "项目编码不能为空") String itemCode,
            @NotBlank(message = "项目名称不能为空") String itemName,
            @NotBlank(message = "结果值不能为空") String resultValue,
            String unit,
            String referenceRange,
            @NotBlank(message = "危急值级别不能为空") String severity) {
    }

    public record CloseCriticalValueRequest(@NotBlank(message = "处理措施不能为空") String handlingNote) {
    }

    public record CriticalValueView(
            Long id,
            Long patientId,
            Long visitId,
            String itemCode,
            String itemName,
            String resultValue,
            String unit,
            String referenceRange,
            String severity,
            String status,
            String reportedByName,
            LocalDateTime reportedAt,
            String confirmedByName,
            LocalDateTime confirmedAt,
            String handlingNote,
            LocalDateTime closedAt) {
    }
}

package com.his.cdss.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public final class ExamItemDtos {

    private ExamItemDtos() {
    }

    public record ExamItemView(
            Long id,
            String itemCode,
            String itemName,
            String itemType,
            String category,
            String specimen,
            String precaution,
            List<String> indications,
            String referenceRange,
            String unit,
            BigDecimal criticalLow,
            BigDecimal criticalHigh,
            Integer turnaroundMinutes) {
    }

    public record IndicationCheckRequest(
            @NotBlank(message = "项目编码不能为空") String itemCode,
            List<String> diagnosisCodes) {
    }

    public record IndicationCheckResult(
            String itemCode,
            String itemName,
            String itemType,
            boolean indicated,
            boolean restricted,
            List<String> matchedDiagnoses,
            String specimen,
            String precaution,
            String message) {
    }

    public record CriticalCheckRequest(
            @NotBlank(message = "项目编码不能为空") String itemCode,
            @NotNull(message = "结果值不能为空") BigDecimal value) {
    }

    public record CriticalCheckResult(
            String itemCode,
            String itemName,
            String severity,
            BigDecimal value,
            String unit,
            String referenceRange,
            BigDecimal criticalLow,
            BigDecimal criticalHigh,
            String message) {
    }
}

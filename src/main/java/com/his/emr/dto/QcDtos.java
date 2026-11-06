package com.his.emr.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.List;

public final class QcDtos {

    private QcDtos() {
    }

    public record QcRuleView(
            Long id,
            String ruleCode,
            String name,
            String category,
            String severity,
            String docType,
            boolean enabled) {
    }

    public record QcIssueView(
            Long id,
            Long docId,
            String ruleCode,
            String ruleName,
            String category,
            String severity,
            String defectDesc,
            String status,
            String assigneeName,
            String closedNote,
            LocalDateTime createdAt,
            LocalDateTime closedAt) {
    }

    public record QcRunResult(
            Long docId,
            boolean blocked,
            List<QcIssueView> issues) {
    }

    public record CloseIssueRequest(
            @NotBlank(message = "关闭缺陷必须填写整改说明") String note) {
    }
}

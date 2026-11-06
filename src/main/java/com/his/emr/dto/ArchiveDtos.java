package com.his.emr.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public final class ArchiveDtos {

    private ArchiveDtos() {
    }

    public record BorrowCreateRequest(
            @NotNull(message = "病历 ID 不能为空") Long docId,
            @NotBlank(message = "借阅理由不能为空") String reason,
            @Min(value = 1, message = "借阅天数至少 1 天")
            @Max(value = 30, message = "借阅天数不能超过 30 天") Integer days) {
    }

    public record CopyCreateRequest(
            @NotNull(message = "病历 ID 不能为空") Long docId,
            @NotBlank(message = "复印用途不能为空") String purpose,
            @Min(value = 1, message = "复印份数至少 1 份")
            @Max(value = 20, message = "复印份数不能超过 20 份") Integer copies) {
    }

    public record DecisionRequest(String note) {
    }

    public record RejectRequest(
            @NotBlank(message = "驳回必须填写原因") String note) {
    }

    public record BorrowView(
            Long id,
            Long docId,
            String docTitle,
            Long applicantId,
            String applicantName,
            String reason,
            Integer days,
            String status,
            String approverName,
            String approveNote,
            LocalDateTime approvedAt,
            LocalDateTime startAt,
            LocalDateTime dueAt,
            LocalDateTime returnedAt,
            LocalDateTime createdAt) {
    }

    public record CopyView(
            Long id,
            Long docId,
            String docTitle,
            Long applicantId,
            String applicantName,
            String purpose,
            Integer copies,
            String status,
            String approverName,
            String approveNote,
            LocalDateTime approvedAt,
            String completedByName,
            LocalDateTime completedAt,
            LocalDateTime createdAt) {
    }
}

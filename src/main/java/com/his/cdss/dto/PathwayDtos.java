package com.his.cdss.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class PathwayDtos {

    private PathwayDtos() {
    }

    public record PathwayNodeView(
            Long id,
            Integer sequence,
            String nodeCode,
            String nodeName,
            Integer dayOffset,
            Boolean required,
            String description) {
    }

    public record PathwayView(
            Long id,
            String pathwayCode,
            String name,
            String diagnosisCode,
            String department,
            Integer version,
            Integer standardDays,
            String description,
            List<PathwayNodeView> nodes) {
    }

    public record EnrollRequest(
            @NotNull(message = "患者 ID 不能为空") Long patientId,
            Long visitId) {
    }

    public record CompleteNodeRequest(String note) {
    }

    public record VarianceRequest(
            Long nodeId,
            @NotBlank(message = "变异类型不能为空") String varianceType,
            @NotBlank(message = "变异原因不能为空") String reason) {
    }

    public record ExitRequest(@NotBlank(message = "退出原因不能为空") String reason) {
    }

    public record VarianceView(
            Long id,
            String nodeCode,
            String varianceType,
            String reason,
            String recordedByName,
            LocalDateTime recordedAt) {
    }

    public record NodeRecordView(
            String nodeCode,
            String nodeName,
            Integer sequence,
            String completedByName,
            LocalDateTime completedAt,
            String note) {
    }

    public record InstanceView(
            Long id,
            Long pathwayId,
            String pathwayCode,
            String pathwayName,
            Long patientId,
            Long visitId,
            String status,
            Integer currentSequence,
            String currentNodeCode,
            String currentNodeName,
            Integer totalNodes,
            Integer completedNodes,
            String startedByName,
            LocalDateTime startedAt,
            LocalDateTime endedAt,
            String exitReason,
            List<NodeRecordView> nodeRecords,
            List<VarianceView> variances) {
    }

    public record PathwayStats(
            Long pathwayId,
            String pathwayCode,
            String pathwayName,
            long total,
            long completed,
            long exited,
            BigDecimal rate) {
    }
}

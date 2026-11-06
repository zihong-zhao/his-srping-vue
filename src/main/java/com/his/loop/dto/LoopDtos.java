package com.his.loop.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class LoopDtos {

    private LoopDtos() {
    }

    public record CreateLoopRequest(
            @NotBlank(message = "闭环类型不能为空") String loopType,
            @NotNull(message = "患者 ID 不能为空") Long patientId,
            Long visitId,
            @NotBlank(message = "单据标题不能为空") String title,
            JsonNode detail) {
    }

    public record AdvanceRequest(
            @NotBlank(message = "节点编码不能为空") String nodeCode,
            JsonNode payload) {
    }

    public record LoopNodeView(
            String nodeCode,
            String nodeName,
            Integer sequence,
            String operatorName,
            LocalDateTime occurredAt,
            JsonNode payload) {
    }

    public record LoopView(
            Long id,
            String bizNo,
            String loopType,
            String title,
            Long patientId,
            Long visitId,
            String status,
            String currentNode,
            String currentNodeName,
            List<String> expectedNodes,
            LocalDateTime startedAt,
            LocalDateTime closedAt,
            List<LoopNodeView> nodes) {
    }

    public record LoopSummary(
            Long id,
            String bizNo,
            String loopType,
            String title,
            Long patientId,
            Long visitId,
            String status,
            String currentNode,
            LocalDateTime startedAt,
            LocalDateTime closedAt) {
    }

    public record LoopStats(
            String loopType,
            long total,
            long closed,
            BigDecimal rate) {
    }
}

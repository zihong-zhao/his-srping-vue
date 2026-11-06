package com.his.emr.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public final class EmrDtos {

    private EmrDtos() {
    }

    public record CreateDocumentRequest(
            @NotNull(message = "患者 ID 不能为空") Long patientId,
            Long visitId,
            @NotBlank(message = "文书类型不能为空") String docType,
            @NotNull(message = "模板不能为空") Long templateId,
            String title) {
    }

    public record SaveDocumentRequest(
            @NotNull(message = "病历内容不能为空") JsonNode content,
            String changeSummary) {
    }

    public record DocumentSummary(
            Long id,
            Long patientId,
            Long visitId,
            String docType,
            String title,
            String status,
            Integer currentVersion,
            String authorName,
            LocalDateTime createdAt,
            LocalDateTime signedAt,
            boolean sealed) {
    }

    public record DocumentView(
            Long id,
            Long patientId,
            Long visitId,
            String docType,
            Long templateId,
            Integer templateVersion,
            String title,
            String status,
            Integer currentVersion,
            String authorName,
            JsonNode content,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime signedAt,
            LocalDateTime archivedAt,
            String archiveHash,
            boolean sealed) {
    }

    public record VersionView(
            Integer versionNo,
            JsonNode content,
            String changeSummary,
            String createdByName,
            LocalDateTime createdAt) {
    }

    public record SectionChange(
            String code,
            String title,
            String changeType,
            String before,
            String after) {
    }

    public record DiffResult(
            Integer fromVersion,
            Integer toVersion,
            List<SectionChange> changes) {
    }

    public record VerifyResult(
            boolean valid,
            String message,
            Integer versionNo,
            String signerName,
            String certSn,
            String contentHash,
            String storedSignature,
            String computedSignature,
            LocalDateTime signedAt) {
    }

    public record SealRequest(
            @NotBlank(message = "封存/解封必须填写原因") String reason) {
    }
}

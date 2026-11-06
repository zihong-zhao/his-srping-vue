package com.his.cdr.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class CdrDtos {

    private CdrDtos() {
    }

    public record TimelineEvent(
            String type,
            Long docId,
            String docType,
            String title,
            String status,
            String actor,
            LocalDateTime occurredAt) {
    }

    public record PatientTimeline(
            Long patientId,
            int totalDocuments,
            int signedDocuments,
            Map<String, Integer> byDocType,
            LocalDateTime latestActivityAt,
            List<TimelineEvent> events) {
    }
}

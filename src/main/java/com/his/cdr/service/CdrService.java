package com.his.cdr.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.his.cdr.dto.CdrDtos;
import com.his.emr.entity.EmrDocument;
import com.his.emr.mapper.EmrDocumentMapper;
import com.his.security.UserPrincipal;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * E3 最小实现：不建独立 CDR 库，直接按 patient_id 聚合病历数据。
 * 后续接入门诊/住院/检验检查后，只需在此扩展事件来源。
 */
@Service
public class CdrService {

    private final EmrDocumentMapper documentMapper;

    public CdrService(EmrDocumentMapper documentMapper) {
        this.documentMapper = documentMapper;
    }

    public CdrDtos.PatientTimeline timeline(Long patientId, UserPrincipal user) {
        List<EmrDocument> documents = documentMapper.selectList(Wrappers.<EmrDocument>lambdaQuery()
                .eq(EmrDocument::getPatientId, patientId)
                .eq(!isPrivileged(user), EmrDocument::getAuthorId, user.id())
                .orderByDesc(EmrDocument::getCreatedAt)
                .last("limit 200"));

        List<CdrDtos.TimelineEvent> events = new ArrayList<>();
        Map<String, Integer> byDocType = new LinkedHashMap<>();
        LocalDateTime latest = null;
        int signedCount = 0;

        for (EmrDocument document : documents) {
            byDocType.merge(document.getDocType(), 1, Integer::sum);
            events.add(new CdrDtos.TimelineEvent(
                    "DOC_CREATED", document.getId(), document.getDocType(), document.getTitle(),
                    document.getStatus(), document.getAuthorName(), document.getCreatedAt()));
            latest = max(latest, document.getCreatedAt());
            if (document.getSignedAt() != null) {
                signedCount++;
                events.add(new CdrDtos.TimelineEvent(
                        "DOC_SIGNED", document.getId(), document.getDocType(), document.getTitle(),
                        document.getStatus(), document.getAuthorName(), document.getSignedAt()));
                latest = max(latest, document.getSignedAt());
            }
        }

        events.sort(Comparator.comparing(CdrDtos.TimelineEvent::occurredAt).reversed());
        return new CdrDtos.PatientTimeline(
                patientId, documents.size(), signedCount, byDocType, latest, events);
    }

    private LocalDateTime max(LocalDateTime current, LocalDateTime candidate) {
        if (candidate == null) {
            return current;
        }
        return current == null || candidate.isAfter(current) ? candidate : current;
    }

    private boolean isPrivileged(UserPrincipal user) {
        return "ADMIN".equals(user.role()) || "QC".equals(user.role());
    }
}

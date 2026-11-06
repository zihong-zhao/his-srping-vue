package com.his.emr.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.his.common.BizException;
import com.his.emr.dto.EmrDtos;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SectionDiffService {

    private final ObjectMapper objectMapper;

    public SectionDiffService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public EmrDtos.DiffResult compare(String beforeJson, String afterJson, int fromVersion, int toVersion) {
        Map<String, JsonNode> before = sections(beforeJson);
        Map<String, JsonNode> after = sections(afterJson);

        Map<String, JsonNode> ordered = new LinkedHashMap<>(before);
        after.forEach(ordered::putIfAbsent);

        List<EmrDtos.SectionChange> changes = new ArrayList<>();
        for (Map.Entry<String, JsonNode> entry : ordered.entrySet()) {
            String code = entry.getKey();
            JsonNode b = before.get(code);
            JsonNode a = after.get(code);
            String title = titleOf(a != null ? a : b, code);
            String beforeText = b == null ? null : valueOf(b.path("value"));
            String afterText = a == null ? null : valueOf(a.path("value"));

            if (b == null) {
                changes.add(new EmrDtos.SectionChange(code, title, "ADDED", null, afterText));
            } else if (a == null) {
                changes.add(new EmrDtos.SectionChange(code, title, "REMOVED", beforeText, null));
            } else if (!beforeText.equals(afterText)) {
                changes.add(new EmrDtos.SectionChange(code, title, "MODIFIED", beforeText, afterText));
            }
        }
        return new EmrDtos.DiffResult(fromVersion, toVersion, changes);
    }

    private Map<String, JsonNode> sections(String json) {
        try {
            Map<String, JsonNode> result = new LinkedHashMap<>();
            JsonNode root = objectMapper.readTree(json);
            JsonNode sections = root.path("sections");
            if (sections.isArray()) {
                for (JsonNode section : sections) {
                    String code = section.path("code").asText("");
                    if (!code.isBlank()) {
                        result.put(code, section);
                    }
                }
            }
            return result;
        } catch (Exception ex) {
            throw new BizException("INVALID_CONTENT", "病历内容不是合法的 JSON");
        }
    }

    private String titleOf(JsonNode section, String fallback) {
        String title = section.path("title").asText("");
        return title.isBlank() ? fallback : title;
    }

    private String valueOf(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return "";
        }
        return value.isValueNode() ? value.asText() : value.toString();
    }
}

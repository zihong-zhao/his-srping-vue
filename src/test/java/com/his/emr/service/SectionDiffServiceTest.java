package com.his.emr.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.his.emr.dto.EmrDtos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SectionDiffServiceTest {

    private final SectionDiffService service = new SectionDiffService(new ObjectMapper());

    @Test
    void shouldDetectModifiedAddedAndRemovedSections() {
        String before = """
                {"sections":[
                  {"code":"chief","title":"主诉","value":"发热三天"},
                  {"code":"diag","title":"诊断","value":[{"code":"J18.9"}]},
                  {"code":"old","title":"已删除段落","value":"旧内容"}
                ]}
                """;
        String after = """
                {"sections":[
                  {"code":"chief","title":"主诉","value":"发热五天"},
                  {"code":"diag","title":"诊断","value":[{"code":"J18.9"}]},
                  {"code":"new","title":"新增段落","value":"新增内容"}
                ]}
                """;

        EmrDtos.DiffResult result = service.compare(before, after, 1, 2);

        assertEquals(3, result.changes().size());
        assertEquals("MODIFIED", result.changes().get(0).changeType());
        assertEquals("REMOVED", result.changes().get(1).changeType());
        assertEquals("ADDED", result.changes().get(2).changeType());
    }

    @Test
    void shouldReturnEmptyWhenNothingChanged() {
        String content = """
                {"sections":[{"code":"chief","title":"主诉","value":"发热三天"}]}
                """;
        assertEquals(0, service.compare(content, content, 1, 2).changes().size());
    }
}

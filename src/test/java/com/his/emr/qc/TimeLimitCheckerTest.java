package com.his.emr.qc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.his.emr.entity.EmrDocument;
import com.his.emr.entity.EmrQcRule;
import com.his.emr.entity.EmrTemplate;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeLimitCheckerTest {

    private final TimeLimitChecker checker = new TimeLimitChecker(new ObjectMapper());

    @Test
    void reportsDefectWhenDeadlineExceeded() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 6, 12, 0);
        EmrDocument document = document(now.minusHours(10));

        assertEquals(1, checker.check(rule(), new QcCheckContext(document, null, now)).size());
    }

    @Test
    void passesWithinDeadline() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 6, 12, 0);
        EmrDocument document = document(now.minusHours(6));

        assertTrue(checker.check(rule(), new QcCheckContext(document, null, now)).isEmpty());
    }

    @Test
    void usesSignedAtAsCompletionTime() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 6, 12, 0);
        EmrDocument document = document(now.minusHours(10));
        document.setSignedAt(now.minusHours(1));

        assertEquals(1, checker.check(rule(), new QcCheckContext(document, null, now)).size());
    }

    private EmrQcRule rule() {
        EmrQcRule rule = new EmrQcRule();
        rule.setRuleCode("TIME_LIMIT_FIRST_COURSE_8H");
        rule.setName("首次病程 8 小时时限");
        rule.setCategory("FORM");
        rule.setSeverity("WARN");
        rule.setParamsJson("{\"hours\":8}");
        return rule;
    }

    private EmrDocument document(LocalDateTime createdAt) {
        EmrDocument document = new EmrDocument();
        document.setCreatedAt(createdAt);
        return document;
    }
}

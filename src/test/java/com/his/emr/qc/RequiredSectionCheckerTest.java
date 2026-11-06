package com.his.emr.qc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.his.emr.entity.EmrDocument;
import com.his.emr.entity.EmrQcRule;
import com.his.emr.entity.EmrTemplate;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequiredSectionCheckerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RequiredSectionChecker checker = new RequiredSectionChecker(objectMapper);

    @Test
    void reportsMissingRequiredSection() {
        EmrTemplate template = template("""
                {"sections":[
                  {"code":"chief_complaint","title":"主诉","required":true},
                  {"code":"diagnosis","title":"诊断","type":"icd","required":true}
                ]}
                """);
        EmrDocument document = document("""
                {"sections":[
                  {"code":"chief_complaint","title":"主诉","value":"发热三天"},
                  {"code":"diagnosis","title":"诊断","value":[]}
                ]}
                """);

        List<String> defects = checker.check(rule(), context(document, template));

        assertEquals(1, defects.size());
        assertTrue(defects.get(0).contains("诊断"));
    }

    @Test
    void passesWhenAllRequiredSectionsFilled() {
        EmrTemplate template = template("""
                {"sections":[{"code":"chief_complaint","title":"主诉","required":true}]}
                """);
        EmrDocument document = document("""
                {"sections":[{"code":"chief_complaint","title":"主诉","value":"发热三天"}]}
                """);

        assertTrue(checker.check(rule(), context(document, template)).isEmpty());
    }

    private EmrQcRule rule() {
        EmrQcRule rule = new EmrQcRule();
        rule.setRuleCode(RequiredSectionChecker.RULE_CODE);
        rule.setName("必填段落完整性");
        rule.setCategory("FORM");
        rule.setSeverity("BLOCK");
        return rule;
    }

    private EmrTemplate template(String schema) {
        EmrTemplate template = new EmrTemplate();
        template.setSchemaJson(schema);
        return template;
    }

    private EmrDocument document(String content) {
        EmrDocument document = new EmrDocument();
        document.setContentJson(content);
        return document;
    }

    private QcCheckContext context(EmrDocument document, EmrTemplate template) {
        return new QcCheckContext(document, template, LocalDateTime.now());
    }
}

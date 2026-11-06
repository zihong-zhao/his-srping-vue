package com.his.emr.qc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.his.common.BizException;
import com.his.emr.entity.EmrQcRule;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 形式质控：按模板 required 标记校验必填段落是否完成。 */
@Component
public class RequiredSectionChecker implements QcChecker {

    public static final String RULE_CODE = "REQUIRED_SECTION";

    private final ObjectMapper objectMapper;

    public RequiredSectionChecker(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(EmrQcRule rule) {
        return RULE_CODE.equals(rule.getRuleCode());
    }

    @Override
    public List<String> check(EmrQcRule rule, QcCheckContext context) {
        try {
            JsonNode schema = objectMapper.readTree(context.template().getSchemaJson());
            JsonNode content = objectMapper.readTree(context.document().getContentJson());
            Map<String, JsonNode> byCode = new LinkedHashMap<>();
            for (JsonNode section : content.path("sections")) {
                byCode.put(section.path("code").asText(""), section);
            }

            List<String> defects = new ArrayList<>();
            for (JsonNode section : schema.path("sections")) {
                if (!section.path("required").asBoolean(false)) {
                    continue;
                }
                String code = section.path("code").asText("");
                String title = section.path("title").asText(code);
                JsonNode actual = byCode.get(code);
                if (actual == null || isBlankValue(actual.get("value"))) {
                    defects.add("缺少必填段落：" + title);
                }
            }
            return defects;
        } catch (Exception ex) {
            throw new BizException("QC_RULE_ERROR", "质控规则 " + rule.getRuleCode() + " 执行失败");
        }
    }

    private boolean isBlankValue(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return true;
        }
        if (value.isTextual()) {
            return value.asText().isBlank();
        }
        if (value.isArray() || value.isObject()) {
            return value.isEmpty();
        }
        return false;
    }
}

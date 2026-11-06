package com.his.emr.qc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.his.common.BizException;
import com.his.emr.entity.EmrQcRule;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 形式质控：病历书写时限。
 * 基准为文书创建时间，实际完成时间取签名时间（未签名则取当前时间）；
 * 时限小时数来自规则 params_json，例如 {"hours":24}。
 */
@Component
public class TimeLimitChecker implements QcChecker {

    private static final int DEFAULT_HOURS = 24;

    private final ObjectMapper objectMapper;

    public TimeLimitChecker(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(EmrQcRule rule) {
        return rule.getRuleCode() != null && rule.getRuleCode().startsWith("TIME_LIMIT");
    }

    @Override
    public List<String> check(EmrQcRule rule, QcCheckContext context) {
        LocalDateTime anchor = context.document().getCreatedAt();
        if (anchor == null) {
            return List.of();
        }
        LocalDateTime actual = context.document().getSignedAt() != null
                ? context.document().getSignedAt()
                : context.now();
        int hours = limitHours(rule);
        long elapsed = Duration.between(anchor, actual).toHours();
        if (elapsed <= hours) {
            return List.of();
        }
        List<String> defects = new ArrayList<>();
        defects.add("超出书写时限：" + rule.getName() + "，要求 " + hours + " 小时内完成，实际用时 " + elapsed + " 小时");
        return defects;
    }

    private int limitHours(EmrQcRule rule) {
        String params = rule.getParamsJson();
        if (params == null || params.isBlank()) {
            return DEFAULT_HOURS;
        }
        try {
            JsonNode node = objectMapper.readTree(params);
            return node.path("hours").asInt(DEFAULT_HOURS);
        } catch (Exception ex) {
            throw new BizException("QC_RULE_ERROR", "质控规则 " + rule.getRuleCode() + " 参数非法");
        }
    }
}

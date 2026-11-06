package com.his.cdss.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.his.cdss.dto.CdssDtos;
import com.his.cdss.entity.CdssAlertLog;
import com.his.cdss.entity.CdssDrug;
import com.his.cdss.entity.CdssDrugInteraction;
import com.his.cdss.entity.CdssRule;
import com.his.cdss.mapper.CdssAlertLogMapper;
import com.his.cdss.mapper.CdssDrugInteractionMapper;
import com.his.cdss.mapper.CdssDrugMapper;
import com.his.cdss.mapper.CdssRuleMapper;
import com.his.common.BizException;
import com.his.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** E4-02 合理用药实时审查 + E4-07 忽略留痕。 */
@Service
public class CdssService {

    public static final String SEVERITY_BLOCK = "BLOCK";
    public static final String SEVERITY_WARN = "WARN";
    public static final String SEVERITY_INFO = "INFO";

    public static final String DECISION_PASSED = "PASSED";
    public static final String DECISION_BLOCKED = "BLOCKED";
    public static final String DECISION_PENDING = "PENDING";
    public static final String DECISION_OVERRIDDEN = "OVERRIDDEN";
    public static final String DECISION_IGNORED = "IGNORED";

    private static final String RULE_UNKNOWN = "DRUG_UNKNOWN";
    private static final String RULE_CONTRAINDICATION = "DRUG_CONTRAINDICATION";
    private static final String RULE_INTERACTION = "DRUG_INTERACTION";
    private static final String RULE_DUPLICATE = "DUPLICATE_DRUG";
    private static final String RULE_CHILD_DOSE = "CHILD_DOSE";
    private static final String RULE_MAX_DAILY_DOSE = "MAX_DAILY_DOSE";
    private static final String RULE_RENAL_ADJUST = "RENAL_ADJUST";
    private static final String RULE_PREGNANCY = "PREGNANCY";

    private static final BigDecimal CHILD_AGE_LIMIT = BigDecimal.valueOf(14);
    private static final BigDecimal RENAL_EGFR_LIMIT = BigDecimal.valueOf(60);

    private record DraftAlert(String ruleCode, String severity, String message) {
    }

    private final CdssRuleMapper ruleMapper;
    private final CdssDrugMapper drugMapper;
    private final CdssDrugInteractionMapper interactionMapper;
    private final CdssAlertLogMapper alertLogMapper;
    private final ObjectMapper objectMapper;

    public CdssService(CdssRuleMapper ruleMapper,
                       CdssDrugMapper drugMapper,
                       CdssDrugInteractionMapper interactionMapper,
                       CdssAlertLogMapper alertLogMapper,
                       ObjectMapper objectMapper) {
        this.ruleMapper = ruleMapper;
        this.drugMapper = drugMapper;
        this.interactionMapper = interactionMapper;
        this.alertLogMapper = alertLogMapper;
        this.objectMapper = objectMapper;
    }

    public List<CdssDtos.DrugView> searchDrugs(String keyword, int limit) {
        int size = Math.max(1, Math.min(limit, 200));
        var query = Wrappers.<CdssDrug>lambdaQuery()
                .eq(CdssDrug::getEnabled, true)
                .orderByAsc(CdssDrug::getDrugCode)
                .last("limit " + size);
        if (keyword != null && !keyword.isBlank()) {
            query.and(w -> w.like(CdssDrug::getDrugCode, keyword).or().like(CdssDrug::getDrugName, keyword));
        }
        return drugMapper.selectList(query).stream().map(this::toDrugView).toList();
    }

    /** 开立前同步审查：禁忌、重复用药、相互作用、儿童/肾功能/孕产妇剂量、日剂量上限。 */
    @Transactional
    public CdssDtos.ReviewResult review(CdssDtos.ReviewRequest request, UserPrincipal user) {
        LocalDateTime now = LocalDateTime.now();
        Map<String, CdssRule> rules = loadRules();
        Map<String, CdssDrug> drugByCode = loadDrugs(request.drugs());
        Map<String, CdssDrugInteraction> interactions = loadInteractions();
        List<String> diagnosisCodes = request.diagnosisCodes() == null
                ? List.of() : request.diagnosisCodes();

        List<DraftAlert> alerts = new ArrayList<>();
        Set<String> seenCodes = new HashSet<>();
        for (CdssDtos.DrugReviewItem item : request.drugs()) {
            CdssDrug drug = drugByCode.get(item.drugCode());
            if (drug == null) {
                addAlert(alerts, rules, RULE_UNKNOWN,
                        "药品知识库未收录编码：" + item.drugCode() + "，请人工核对");
                continue;
            }
            if (!seenCodes.add(item.drugCode())) {
                addAlert(alerts, rules, RULE_DUPLICATE, "重复用药：" + drug.getDrugName() + " 在一次处方内出现多次");
            }
            checkContraindication(drug, diagnosisCodes, rules, alerts);
            checkDose(drug, item, request, rules, alerts);
            checkRenal(drug, request.egfr(), rules, alerts);
            checkPregnancy(drug, request.pregnant(), rules, alerts);
        }
        checkInteractions(request.drugs(), drugByCode, interactions, rules, alerts);

        boolean blocked = alerts.stream().anyMatch(a -> SEVERITY_BLOCK.equals(a.severity()));
        boolean warned = alerts.stream().anyMatch(a -> SEVERITY_WARN.equals(a.severity()));
        String decision = resolveDecision(blocked, warned, request.overrideReason());
        boolean requiresOverride = warned && !blocked
                && (request.overrideReason() == null || request.overrideReason().isBlank());

        List<CdssDtos.AlertView> views = persistAlerts(request, alerts, decision, now, user);
        return new CdssDtos.ReviewResult(
                request.patientId(), request.visitId(), request.bizNo(),
                blocked, requiresOverride, decision, views);
    }

    public List<CdssDtos.AlertView> listAlerts(Long patientId, String bizNo, int limit) {
        if (patientId == null && (bizNo == null || bizNo.isBlank())) {
            throw new BizException("QUERY_REQUIRED", "请至少按患者或业务单号查询提醒记录");
        }
        int size = Math.max(1, Math.min(limit, 200));
        return alertLogMapper.selectList(Wrappers.<CdssAlertLog>lambdaQuery()
                        .eq(patientId != null, CdssAlertLog::getPatientId, patientId)
                        .eq(bizNo != null && !bizNo.isBlank(), CdssAlertLog::getBizNo, bizNo)
                        .orderByDesc(CdssAlertLog::getCreatedAt)
                        .last("limit " + size))
                .stream().map(this::toAlertView).toList();
    }

    /** E4-07 医生忽略强提醒必须填写理由，留痕待评审举证。 */
    @Transactional
    public CdssDtos.AlertView ignoreAlert(Long id, String reason, UserPrincipal user) {
        CdssAlertLog log = alertLogMapper.selectById(id);
        if (log == null) {
            throw new BizException("ALERT_NOT_FOUND", "提醒记录不存在", HttpStatus.NOT_FOUND);
        }
        if (SEVERITY_BLOCK.equals(log.getSeverity())) {
            throw new BizException("ALERT_BLOCKED", "阻断级提醒不可忽略", HttpStatus.CONFLICT);
        }
        if (DECISION_IGNORED.equals(log.getDecision())) {
            throw new BizException("ALERT_ALREADY_IGNORED", "该提醒已忽略", HttpStatus.CONFLICT);
        }
        if (reason == null || reason.isBlank()) {
            throw new BizException("REASON_REQUIRED", "忽略提醒必须填写理由");
        }
        log.setDecision(DECISION_IGNORED);
        log.setOverrideReason(reason);
        alertLogMapper.updateById(log);
        return toAlertView(log);
    }

    private String resolveDecision(boolean blocked, boolean warned, String overrideReason) {
        if (blocked) {
            return DECISION_BLOCKED;
        }
        if (!warned) {
            return DECISION_PASSED;
        }
        return overrideReason == null || overrideReason.isBlank()
                ? DECISION_PENDING : DECISION_OVERRIDDEN;
    }

    private Map<String, CdssRule> loadRules() {
        Map<String, CdssRule> rules = new HashMap<>();
        for (CdssRule rule : ruleMapper.selectList(Wrappers.<CdssRule>lambdaQuery()
                .eq(CdssRule::getEnabled, true))) {
            rules.put(rule.getRuleCode(), rule);
        }
        return rules;
    }

    private Map<String, CdssDrug> loadDrugs(List<CdssDtos.DrugReviewItem> items) {
        Set<String> codes = new HashSet<>();
        for (CdssDtos.DrugReviewItem item : items) {
            codes.add(item.drugCode());
        }
        if (codes.isEmpty()) {
            return Map.of();
        }
        Map<String, CdssDrug> result = new LinkedHashMap<>();
        for (CdssDrug drug : drugMapper.selectList(Wrappers.<CdssDrug>lambdaQuery()
                .in(CdssDrug::getDrugCode, codes)
                .eq(CdssDrug::getEnabled, true))) {
            result.put(drug.getDrugCode(), drug);
        }
        return result;
    }

    private Map<String, CdssDrugInteraction> loadInteractions() {
        Map<String, CdssDrugInteraction> result = new HashMap<>();
        for (CdssDrugInteraction interaction : interactionMapper.selectList(
                Wrappers.<CdssDrugInteraction>lambdaQuery().eq(CdssDrugInteraction::getEnabled, true))) {
            result.put(pairKey(interaction.getDrugACode(), interaction.getDrugBCode()), interaction);
        }
        return result;
    }

    private void checkContraindication(CdssDrug drug, List<String> diagnosisCodes,
                                       Map<String, CdssRule> rules, List<DraftAlert> alerts) {
        if (diagnosisCodes.isEmpty()) {
            return;
        }
        for (String code : readStringList(drug.getContraindicationsJson())) {
            if (diagnosisCodes.contains(code)) {
                addAlert(alerts, rules, RULE_CONTRAINDICATION,
                        drug.getDrugName() + " 禁用于诊断 " + code);
            }
        }
    }

    private void checkDose(CdssDrug drug, CdssDtos.DrugReviewItem item,
                           CdssDtos.ReviewRequest request,
                           Map<String, CdssRule> rules, List<DraftAlert> alerts) {
        if (request.ageYears() != null
                && BigDecimal.valueOf(request.ageYears()).compareTo(CHILD_AGE_LIMIT) < 0) {
            if (drug.getChildDoseMgPerKg() == null) {
                addAlert(alerts, rules, RULE_CHILD_DOSE,
                        drug.getDrugName() + " 缺少儿童剂量，儿童患者需人工核对");
            } else if (request.weightKg() == null) {
                addAlert(alerts, rules, RULE_CHILD_DOSE,
                        "儿童患者缺少体重，无法校验 " + drug.getDrugName() + " 的剂量");
            } else if (item.dose() != null) {
                BigDecimal maxSingle = drug.getChildDoseMgPerKg().multiply(request.weightKg());
                if (item.dose().compareTo(maxSingle) > 0) {
                    addAlert(alerts, rules, RULE_CHILD_DOSE,
                            drug.getDrugName() + " 单次剂量 " + item.dose() + " mg 超过按体重上限 "
                                    + maxSingle.stripTrailingZeros().toPlainString() + " mg");
                }
            }
        }

        if (item.dose() != null && drug.getMaxDailyDoseMg() != null) {
            BigDecimal daily = item.dose().multiply(BigDecimal.valueOf(timesPerDay(item.frequency())));
            if (daily.compareTo(drug.getMaxDailyDoseMg()) > 0) {
                addAlert(alerts, rules, RULE_MAX_DAILY_DOSE,
                        drug.getDrugName() + " 日剂量 " + daily.stripTrailingZeros().toPlainString()
                                + " mg 超过上限 " + drug.getMaxDailyDoseMg().stripTrailingZeros().toPlainString() + " mg");
            }
        }
    }

    private void checkRenal(CdssDrug drug, BigDecimal egfr,
                            Map<String, CdssRule> rules, List<DraftAlert> alerts) {
        if (egfr == null || egfr.compareTo(RENAL_EGFR_LIMIT) >= 0) {
            return;
        }
        if (drug.getRenalAdjustNote() != null && !drug.getRenalAdjustNote().isBlank()) {
            addAlert(alerts, rules, RULE_RENAL_ADJUST,
                    drug.getDrugName() + " 肾功能不全（eGFR " + egfr.stripTrailingZeros().toPlainString()
                            + "）：" + drug.getRenalAdjustNote());
        }
    }

    private void checkPregnancy(CdssDrug drug, Boolean pregnant,
                                Map<String, CdssRule> rules, List<DraftAlert> alerts) {
        if (!Boolean.TRUE.equals(pregnant)) {
            return;
        }
        if (drug.getPregnancyNote() != null && !drug.getPregnancyNote().isBlank()) {
            addAlert(alerts, rules, RULE_PREGNANCY,
                    drug.getDrugName() + " 孕产妇用药： " + drug.getPregnancyNote());
        }
    }

    private void checkInteractions(List<CdssDtos.DrugReviewItem> items,
                                   Map<String, CdssDrug> drugByCode,
                                   Map<String, CdssDrugInteraction> interactions,
                                   Map<String, CdssRule> rules,
                                   List<DraftAlert> alerts) {
        CdssRule rule = rules.get(RULE_INTERACTION);
        if (rule == null) {
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            CdssDrug first = drugByCode.get(items.get(i).drugCode());
            if (first == null) {
                continue;
            }
            for (int j = i + 1; j < items.size(); j++) {
                CdssDrug second = drugByCode.get(items.get(j).drugCode());
                if (second == null) {
                    continue;
                }
                CdssDrugInteraction interaction = interactions.get(
                        pairKey(first.getDrugCode(), second.getDrugCode()));
                if (interaction == null) {
                    continue;
                }
                String severity = SEVERITY_BLOCK.equals(interaction.getSeverity())
                        ? SEVERITY_BLOCK : rule.getSeverity();
                String message = first.getDrugName() + " + " + second.getDrugName()
                        + " 相互作用：" + (interaction.getMechanism() == null ? "" : interaction.getMechanism());
                alerts.add(new DraftAlert(RULE_INTERACTION, severity, message));
            }
        }
    }

    private void addAlert(List<DraftAlert> alerts, Map<String, CdssRule> rules,
                          String ruleCode, String message) {
        CdssRule rule = rules.get(ruleCode);
        if (rule == null) {
            return;
        }
        alerts.add(new DraftAlert(ruleCode, rule.getSeverity(), message));
    }

    private List<CdssDtos.AlertView> persistAlerts(CdssDtos.ReviewRequest request,
                                                   List<DraftAlert> alerts,
                                                   String decision,
                                                   LocalDateTime now,
                                                   UserPrincipal user) {
        List<CdssDtos.AlertView> views = new ArrayList<>();
        for (DraftAlert alert : alerts) {
            CdssAlertLog log = new CdssAlertLog();
            log.setPatientId(request.patientId());
            log.setVisitId(request.visitId());
            log.setBizType("PRESCRIPTION");
            log.setBizNo(request.bizNo());
            log.setRuleCode(alert.ruleCode());
            log.setSeverity(alert.severity());
            log.setMessage(alert.message());
            log.setDecision(decision);
            log.setOverrideReason(DECISION_OVERRIDDEN.equals(decision) ? request.overrideReason() : null);
            log.setOperatorId(user.id());
            log.setOperatorName(user.displayName());
            log.setCreatedAt(now);
            alertLogMapper.insert(log);
            views.add(toAlertView(log));
        }
        return views;
    }

    private int timesPerDay(String frequency) {
        if (frequency == null || frequency.isBlank()) {
            return 1;
        }
        return switch (frequency.trim().toUpperCase()) {
            case "BID", "Q12H" -> 2;
            case "TID", "Q8H" -> 3;
            case "QID", "Q6H" -> 4;
            default -> 1;
        };
    }

    private String pairKey(String first, String second) {
        return first.compareTo(second) <= 0 ? first + "|" + second : second + "|" + first;
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException e) {
            throw new BizException("JSON_PARSE_FAILED", "药品知识库数据解析失败");
        }
    }

    private CdssDtos.DrugView toDrugView(CdssDrug drug) {
        return new CdssDtos.DrugView(
                drug.getId(),
                drug.getDrugCode(),
                drug.getDrugName(),
                drug.getCategory(),
                readStringList(drug.getIndicationsJson()),
                readStringList(drug.getContraindicationsJson()),
                drug.getAdultDose(),
                drug.getChildDoseMgPerKg(),
                drug.getMaxDailyDoseMg(),
                drug.getRenalAdjustNote(),
                drug.getPregnancyNote());
    }

    private CdssDtos.AlertView toAlertView(CdssAlertLog log) {
        return new CdssDtos.AlertView(
                log.getId(),
                log.getRuleCode(),
                log.getSeverity(),
                log.getMessage(),
                log.getDecision(),
                log.getOverrideReason(),
                log.getCreatedAt());
    }
}

package com.his.emr.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.his.common.BizException;
import com.his.emr.dto.QcDtos;
import com.his.emr.entity.EmrDocument;
import com.his.emr.entity.EmrQcIssue;
import com.his.emr.entity.EmrQcRule;
import com.his.emr.entity.EmrTemplate;
import com.his.emr.mapper.EmrDocumentMapper;
import com.his.emr.mapper.EmrQcIssueMapper;
import com.his.emr.mapper.EmrQcRuleMapper;
import com.his.emr.mapper.EmrTemplateMapper;
import com.his.emr.qc.QcCheckContext;
import com.his.emr.qc.QcRuleEngine;
import com.his.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class QcService {

    public static final String SEVERITY_BLOCK = "BLOCK";
    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_CLOSED = "CLOSED";

    private final EmrDocumentMapper documentMapper;
    private final EmrTemplateMapper templateMapper;
    private final EmrQcRuleMapper ruleMapper;
    private final EmrQcIssueMapper issueMapper;
    private final QcRuleEngine ruleEngine;

    public QcService(EmrDocumentMapper documentMapper,
                     EmrTemplateMapper templateMapper,
                     EmrQcRuleMapper ruleMapper,
                     EmrQcIssueMapper issueMapper,
                     QcRuleEngine ruleEngine) {
        this.documentMapper = documentMapper;
        this.templateMapper = templateMapper;
        this.ruleMapper = ruleMapper;
        this.issueMapper = issueMapper;
        this.ruleEngine = ruleEngine;
    }

    /** 独立事务：即使调用方（如提交）后续失败回滚，缺陷记录也已落库。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public QcDtos.QcRunResult run(Long docId, UserPrincipal user) {
        EmrDocument document = requireDocument(docId);
        checkRead(document, user);
        EmrTemplate template = templateMapper.selectById(document.getTemplateId());
        if (template == null) {
            throw new BizException("TEMPLATE_NOT_FOUND", "模板不存在");
        }

        List<EmrQcRule> rules = ruleMapper.selectList(Wrappers.<EmrQcRule>lambdaQuery()
                .eq(EmrQcRule::getEnabled, true)
                .and(w -> w.isNull(EmrQcRule::getDocType)
                        .or().eq(EmrQcRule::getDocType, document.getDocType()))
                .orderByAsc(EmrQcRule::getId));
        List<QcRuleEngine.Defect> defects = ruleEngine.evaluate(
                rules, new QcCheckContext(document, template, LocalDateTime.now()));

        clearOpen(docId);
        LocalDateTime now = LocalDateTime.now();
        for (QcRuleEngine.Defect defect : defects) {
            issueMapper.insert(buildIssue(document, defect, now));
        }
        return new QcDtos.QcRunResult(docId, hasBlocking(defects), currentIssues(docId, STATUS_OPEN));
    }

    public void clearOpen(Long docId) {
        issueMapper.delete(Wrappers.<EmrQcIssue>lambdaQuery()
                .eq(EmrQcIssue::getDocId, docId)
                .eq(EmrQcIssue::getStatus, STATUS_OPEN));
    }

    public List<QcDtos.QcIssueView> listIssues(Long docId, String status, int limit, UserPrincipal user) {
        int size = Math.max(1, Math.min(limit, 200));
        List<EmrQcIssue> issues = issueMapper.selectList(Wrappers.<EmrQcIssue>lambdaQuery()
                .eq(docId != null, EmrQcIssue::getDocId, docId)
                .eq(status != null && !status.isBlank(), EmrQcIssue::getStatus, status)
                .eq(!isPrivileged(user), EmrQcIssue::getAssigneeId, user.id())
                .orderByDesc(EmrQcIssue::getCreatedAt)
                .last("limit " + size));
        return issues.stream().map(this::toView).toList();
    }

    @Transactional
    public QcDtos.QcIssueView close(Long issueId, QcDtos.CloseIssueRequest request, UserPrincipal user) {
        EmrQcIssue issue = issueMapper.selectById(issueId);
        if (issue == null) {
            throw new BizException("QC_ISSUE_NOT_FOUND", "质控缺陷不存在", HttpStatus.NOT_FOUND);
        }
        if (STATUS_CLOSED.equals(issue.getStatus())) {
            throw new BizException("QC_ISSUE_CLOSED", "该缺陷已关闭", HttpStatus.CONFLICT);
        }
        boolean assignee = issue.getAssigneeId() != null && issue.getAssigneeId().equals(user.id());
        if (!isPrivileged(user) && !assignee) {
            throw new BizException("FORBIDDEN", "无权关闭该缺陷", HttpStatus.FORBIDDEN);
        }
        issue.setStatus(STATUS_CLOSED);
        issue.setClosedBy(user.id());
        issue.setClosedAt(LocalDateTime.now());
        issue.setClosedNote(request.note());
        issueMapper.updateById(issue);
        return toView(issue);
    }

    public List<QcDtos.QcRuleView> listRules() {
        return ruleMapper.selectList(Wrappers.<EmrQcRule>lambdaQuery()
                        .orderByAsc(EmrQcRule::getId))
                .stream()
                .map(rule -> new QcDtos.QcRuleView(
                        rule.getId(), rule.getRuleCode(), rule.getName(), rule.getCategory(),
                        rule.getSeverity(), rule.getDocType(), Boolean.TRUE.equals(rule.getEnabled())))
                .toList();
    }

    private List<QcDtos.QcIssueView> currentIssues(Long docId, String status) {
        return issueMapper.selectList(Wrappers.<EmrQcIssue>lambdaQuery()
                        .eq(EmrQcIssue::getDocId, docId)
                        .eq(EmrQcIssue::getStatus, status)
                        .orderByAsc(EmrQcIssue::getId))
                .stream().map(this::toView).toList();
    }

    private EmrQcIssue buildIssue(EmrDocument document, QcRuleEngine.Defect defect, LocalDateTime now) {
        EmrQcRule rule = defect.rule();
        EmrQcIssue issue = new EmrQcIssue();
        issue.setDocId(document.getId());
        issue.setRuleCode(rule.getRuleCode());
        issue.setRuleName(rule.getName());
        issue.setCategory(rule.getCategory());
        issue.setSeverity(rule.getSeverity());
        issue.setDefectDesc(defect.desc());
        issue.setStatus(STATUS_OPEN);
        issue.setAssigneeId(document.getAuthorId());
        issue.setAssigneeName(document.getAuthorName());
        issue.setCreatedAt(now);
        return issue;
    }

    private boolean hasBlocking(List<QcRuleEngine.Defect> defects) {
        return defects.stream().anyMatch(defect -> SEVERITY_BLOCK.equals(defect.rule().getSeverity()));
    }

    private EmrDocument requireDocument(Long docId) {
        EmrDocument document = documentMapper.selectById(docId);
        if (document == null) {
            throw new BizException("DOC_NOT_FOUND", "病历不存在", HttpStatus.NOT_FOUND);
        }
        return document;
    }

    private void checkRead(EmrDocument document, UserPrincipal user) {
        if (isPrivileged(user) || document.getAuthorId().equals(user.id())) {
            return;
        }
        throw new BizException("FORBIDDEN", "无权访问该病历", HttpStatus.FORBIDDEN);
    }

    private boolean isPrivileged(UserPrincipal user) {
        return "ADMIN".equals(user.role()) || "QC".equals(user.role());
    }

    private QcDtos.QcIssueView toView(EmrQcIssue issue) {
        return new QcDtos.QcIssueView(
                issue.getId(),
                issue.getDocId(),
                issue.getRuleCode(),
                issue.getRuleName(),
                issue.getCategory(),
                issue.getSeverity(),
                issue.getDefectDesc(),
                issue.getStatus(),
                issue.getAssigneeName(),
                issue.getClosedNote(),
                issue.getCreatedAt(),
                issue.getClosedAt());
    }
}

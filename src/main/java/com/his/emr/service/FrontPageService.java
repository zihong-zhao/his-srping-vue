package com.his.emr.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.his.common.BizException;
import com.his.emr.dto.FrontPageDtos;
import com.his.emr.entity.EmrDocument;
import com.his.emr.entity.EmrFrontPage;
import com.his.emr.entity.EmrIcdCode;
import com.his.emr.mapper.EmrDocumentMapper;
import com.his.emr.mapper.EmrFrontPageMapper;
import com.his.emr.mapper.EmrIcdCodeMapper;
import com.his.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** E7-01 病案首页 + E7-05 ICD 编码核对。 */
@Service
public class FrontPageService {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_VERIFIED = "VERIFIED";

    public static final String TYPE_ICD10 = "ICD10";
    public static final String TYPE_ICD9CM3 = "ICD9CM3";

    public static final String SEVERITY_BLOCK = "BLOCK";
    public static final String SEVERITY_WARN = "WARN";

    private static final Set<String> DISCHARGE_WAYS = Set.of(
            "医嘱离院", "医嘱转院", "医嘱转社区", "非医嘱离院", "死亡", "其他");
    private static final BigDecimal COST_TOLERANCE = new BigDecimal("0.01");

    private final EmrFrontPageMapper frontPageMapper;
    private final EmrIcdCodeMapper icdCodeMapper;
    private final EmrDocumentMapper documentMapper;
    private final ObjectMapper objectMapper;

    public FrontPageService(EmrFrontPageMapper frontPageMapper,
                            EmrIcdCodeMapper icdCodeMapper,
                            EmrDocumentMapper documentMapper,
                            ObjectMapper objectMapper) {
        this.frontPageMapper = frontPageMapper;
        this.icdCodeMapper = icdCodeMapper;
        this.documentMapper = documentMapper;
        this.objectMapper = objectMapper;
    }

    public List<FrontPageDtos.IcdCodeView> searchIcd(String type, String keyword, int limit) {
        int size = Math.max(1, Math.min(limit, 200));
        var query = Wrappers.<EmrIcdCode>lambdaQuery()
                .eq(EmrIcdCode::getEnabled, true)
                .eq(type != null && !type.isBlank(), EmrIcdCode::getType, type)
                .orderByAsc(EmrIcdCode::getCode)
                .last("limit " + size);
        if (keyword != null && !keyword.isBlank()) {
            query.and(w -> w.like(EmrIcdCode::getCode, keyword).or().like(EmrIcdCode::getName, keyword));
        }
        return icdCodeMapper.selectList(query).stream()
                .map(code -> new FrontPageDtos.IcdCodeView(code.getId(), code.getCode(), code.getName(), code.getType()))
                .toList();
    }

    @Transactional
    public FrontPageDtos.FrontPageView save(FrontPageDtos.SaveFrontPageRequest request, UserPrincipal user) {
        LocalDateTime now = LocalDateTime.now();
        EmrFrontPage page = frontPageMapper.selectOne(Wrappers.<EmrFrontPage>lambdaQuery()
                .eq(EmrFrontPage::getVisitId, request.visitId()));
        boolean creating = page == null;
        if (creating) {
            page = new EmrFrontPage();
            page.setVisitId(request.visitId());
            page.setCreatedBy(user.id());
            page.setCreatedByName(user.displayName());
            page.setCreatedAt(now);
        }

        page.setPatientId(request.patientId());
        page.setDocId(request.docId());
        page.setInpatientNo(request.inpatientNo());
        page.setAdmissionDate(request.admissionDate());
        page.setDischargeDate(request.dischargeDate());
        page.setAdmissionDept(request.admissionDept());
        page.setDischargeDept(request.dischargeDept());
        page.setDischargeWay(request.dischargeWay());
        page.setAnesthesiaType(request.anesthesiaType());
        page.setAttendingDoctor(request.attendingDoctor());
        page.setDiagnosesJson(write(request.diagnoses()));
        page.setSurgeriesJson(write(request.surgeries() == null ? List.of() : request.surgeries()));
        page.setCostJson(write(request.cost()));
        // 内容变更后需重新编码核对
        page.setStatus(STATUS_DRAFT);
        page.setVerifiedBy(null);
        page.setVerifiedByName(null);
        page.setVerifiedAt(null);
        page.setVerifyIssuesJson(null);
        page.setUpdatedAt(now);

        if (creating) {
            frontPageMapper.insert(page);
        } else if (frontPageMapper.updateById(page) == 0) {
            throw new BizException("CONFLICT", "病案首页已被他人修改，请刷新后重试", HttpStatus.CONFLICT);
        }
        return toView(page);
    }

    public FrontPageDtos.FrontPageView get(Long id) {
        return toView(requirePage(id));
    }

    public List<FrontPageDtos.FrontPageView> list(Long patientId, Long visitId, int limit) {
        if (patientId == null && visitId == null) {
            throw new BizException("QUERY_REQUIRED", "请至少按患者或就诊 ID 查询");
        }
        int size = Math.max(1, Math.min(limit, 200));
        return frontPageMapper.selectList(Wrappers.<EmrFrontPage>lambdaQuery()
                        .eq(patientId != null, EmrFrontPage::getPatientId, patientId)
                        .eq(visitId != null, EmrFrontPage::getVisitId, visitId)
                        .orderByDesc(EmrFrontPage::getCreatedAt)
                        .last("limit " + size))
                .stream().map(this::toView).toList();
    }

    /** E7-05 编码核对：诊断/手术编码必须在字典内，主诊断唯一，费用与离院方式合法。 */
    @Transactional
    public FrontPageDtos.VerifyResult verify(Long id, UserPrincipal user) {
        EmrFrontPage page = requirePage(id);
        List<FrontPageDtos.DiagnosisItem> diagnoses = readDiagnoses(page);
        List<FrontPageDtos.SurgeryItem> surgeries = readSurgeries(page);
        List<FrontPageDtos.CodingIssue> issues = new ArrayList<>();

        int mainCount = (int) diagnoses.stream().filter(FrontPageDtos.DiagnosisItem::main).count();
        if (diagnoses.isEmpty()) {
            issues.add(issue(null, "诊断", SEVERITY_BLOCK, "病案首页缺少诊断"));
        } else if (mainCount == 0) {
            issues.add(issue(null, "诊断", SEVERITY_BLOCK, "必须指定一条主诊断"));
        } else if (mainCount > 1) {
            issues.add(issue(null, "诊断", SEVERITY_BLOCK, "主诊断只能有一条"));
        }

        Set<String> seenDiagnoses = new HashSet<>();
        for (FrontPageDtos.DiagnosisItem item : diagnoses) {
            if (item.code() == null || item.code().isBlank()) {
                issues.add(issue(null, "诊断", SEVERITY_BLOCK, "诊断编码为空"));
                continue;
            }
            if (!existsIcd(TYPE_ICD10, item.code())) {
                issues.add(issue(item.code(), "诊断", SEVERITY_BLOCK,
                        "ICD-10 编码不存在或已停用：" + (item.name() == null ? item.code() : item.name())));
            }
            if (!seenDiagnoses.add(item.code())) {
                issues.add(issue(item.code(), "诊断", SEVERITY_WARN, "诊断编码重复"));
            }
        }

        Set<String> seenSurgeries = new HashSet<>();
        for (FrontPageDtos.SurgeryItem item : surgeries) {
            if (item.code() == null || item.code().isBlank()) {
                issues.add(issue(null, "手术", SEVERITY_BLOCK, "手术编码为空"));
                continue;
            }
            if (!existsIcd(TYPE_ICD9CM3, item.code())) {
                issues.add(issue(item.code(), "手术", SEVERITY_BLOCK,
                        "ICD-9-CM3 编码不存在或已停用：" + (item.name() == null ? item.code() : item.name())));
            }
            if (!seenSurgeries.add(item.code())) {
                issues.add(issue(item.code(), "手术", SEVERITY_WARN, "手术编码重复"));
            }
        }

        if (page.getDischargeWay() == null || !DISCHARGE_WAYS.contains(page.getDischargeWay())) {
            issues.add(issue(null, "离院方式", SEVERITY_BLOCK, "离院方式不合法：" + page.getDischargeWay()));
        }
        if (page.getAdmissionDate() != null && page.getDischargeDate() != null
                && page.getDischargeDate().isBefore(page.getAdmissionDate())) {
            issues.add(issue(null, "住院日期", SEVERITY_BLOCK, "出院日期早于入院日期"));
        }
        if (page.getAdmissionDate() == null || page.getDischargeDate() == null) {
            issues.add(issue(null, "住院日期", SEVERITY_WARN, "入院或出院日期缺失"));
        }
        checkCost(readCost(page), issues);
        checkDocumentBinding(page, issues);

        boolean verified = issues.stream().noneMatch(i -> SEVERITY_BLOCK.equals(i.severity()));
        page.setStatus(verified ? STATUS_VERIFIED : STATUS_DRAFT);
        page.setVerifyIssuesJson(write(issues));
        if (verified) {
            page.setVerifiedBy(user.id());
            page.setVerifiedByName(user.displayName());
            page.setVerifiedAt(LocalDateTime.now());
        } else {
            page.setVerifiedBy(null);
            page.setVerifiedByName(null);
            page.setVerifiedAt(null);
        }
        page.setUpdatedAt(LocalDateTime.now());
        frontPageMapper.updateById(page);
        return new FrontPageDtos.VerifyResult(page.getId(), verified, issues, page.getVerifiedAt());
    }

    private void checkCost(FrontPageDtos.CostBreakdown cost, List<FrontPageDtos.CodingIssue> issues) {
        if (cost == null) {
            issues.add(issue(null, "费用", SEVERITY_WARN, "未填写住院费用"));
            return;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal value : new BigDecimal[]{cost.drug(), cost.material(), cost.bed(),
                cost.exam(), cost.treatment(), cost.nursing(), cost.other()}) {
            if (value == null) {
                continue;
            }
            if (value.signum() < 0) {
                issues.add(issue(null, "费用", SEVERITY_BLOCK, "费用分类金额不能为负"));
                return;
            }
            sum = sum.add(value);
        }
        if (cost.total() == null) {
            issues.add(issue(null, "费用", SEVERITY_WARN, "未填写费用合计"));
            return;
        }
        if (cost.total().signum() < 0) {
            issues.add(issue(null, "费用", SEVERITY_BLOCK, "费用合计不能为负"));
            return;
        }
        if (cost.total().subtract(sum).abs().compareTo(COST_TOLERANCE) > 0) {
            issues.add(issue(null, "费用", SEVERITY_WARN,
                    "费用合计与分类合计不一致：合计 " + cost.total() + "，分类 " + sum));
        }
    }

    private void checkDocumentBinding(EmrFrontPage page, List<FrontPageDtos.CodingIssue> issues) {
        if (page.getDocId() == null) {
            return;
        }
        EmrDocument document = documentMapper.selectById(page.getDocId());
        if (document == null) {
            issues.add(issue(null, "关联病历", SEVERITY_WARN, "关联病历不存在：" + page.getDocId()));
            return;
        }
        if (!page.getPatientId().equals(document.getPatientId())
                || (page.getVisitId() != null && !page.getVisitId().equals(document.getVisitId()))) {
            issues.add(issue(null, "关联病历", SEVERITY_BLOCK, "病案首页与关联病历的患者或就诊不一致"));
        }
    }

    private boolean existsIcd(String type, String code) {
        return icdCodeMapper.selectCount(Wrappers.<EmrIcdCode>lambdaQuery()
                .eq(EmrIcdCode::getType, type)
                .eq(EmrIcdCode::getCode, code)
                .eq(EmrIcdCode::getEnabled, true)) > 0;
    }

    private EmrFrontPage requirePage(Long id) {
        EmrFrontPage page = frontPageMapper.selectById(id);
        if (page == null) {
            throw new BizException("FRONT_PAGE_NOT_FOUND", "病案首页不存在", HttpStatus.NOT_FOUND);
        }
        return page;
    }

    private FrontPageDtos.CodingIssue issue(String code, String item, String severity, String message) {
        return new FrontPageDtos.CodingIssue(code, item, severity, message);
    }

    private List<FrontPageDtos.DiagnosisItem> readDiagnoses(EmrFrontPage page) {
        List<FrontPageDtos.DiagnosisItem> value = read(page.getDiagnosesJson(),
                new TypeReference<List<FrontPageDtos.DiagnosisItem>>() {
                });
        return value == null ? List.of() : value;
    }

    private List<FrontPageDtos.SurgeryItem> readSurgeries(EmrFrontPage page) {
        List<FrontPageDtos.SurgeryItem> value = read(page.getSurgeriesJson(),
                new TypeReference<List<FrontPageDtos.SurgeryItem>>() {
                });
        return value == null ? List.of() : value;
    }

    private FrontPageDtos.CostBreakdown readCost(EmrFrontPage page) {
        return read(page.getCostJson(), new TypeReference<FrontPageDtos.CostBreakdown>() {
        });
    }

    private List<FrontPageDtos.CodingIssue> readIssues(EmrFrontPage page) {
        List<FrontPageDtos.CodingIssue> value = read(page.getVerifyIssuesJson(),
                new TypeReference<List<FrontPageDtos.CodingIssue>>() {
                });
        return value == null ? List.of() : value;
    }

    private <T> T read(String json, TypeReference<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new BizException("JSON_PARSE_FAILED", "病案首页数据解析失败");
        }
    }

    private String write(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BizException("JSON_SERIALIZE_FAILED", "病案首页数据保存失败");
        }
    }

    private FrontPageDtos.FrontPageView toView(EmrFrontPage page) {
        List<FrontPageDtos.DiagnosisItem> diagnoses = readDiagnoses(page);
        List<FrontPageDtos.SurgeryItem> surgeries = readSurgeries(page);
        List<FrontPageDtos.CodingIssue> issues = readIssues(page);
        return new FrontPageDtos.FrontPageView(
                page.getId(),
                page.getPatientId(),
                page.getVisitId(),
                page.getDocId(),
                page.getInpatientNo(),
                page.getAdmissionDate(),
                page.getDischargeDate(),
                page.getAdmissionDept(),
                page.getDischargeDept(),
                page.getDischargeWay(),
                page.getAnesthesiaType(),
                page.getAttendingDoctor(),
                diagnoses == null ? List.of() : diagnoses,
                surgeries == null ? List.of() : surgeries,
                readCost(page),
                page.getStatus(),
                page.getVerifiedByName(),
                page.getVerifiedAt(),
                issues == null ? List.of() : issues,
                page.getCreatedAt(),
                page.getUpdatedAt());
    }
}

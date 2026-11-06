package com.his.emr.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.his.common.BizException;
import com.his.emr.dto.EmrDtos;
import com.his.emr.dto.QcDtos;
import com.his.emr.entity.EmrAccessLog;
import com.his.emr.entity.EmrDocument;
import com.his.emr.entity.EmrSeal;
import com.his.emr.entity.EmrSignature;
import com.his.emr.entity.EmrTemplate;
import com.his.emr.entity.EmrVersion;
import com.his.emr.mapper.EmrAccessLogMapper;
import com.his.emr.mapper.EmrDocumentMapper;
import com.his.emr.mapper.EmrSealMapper;
import com.his.emr.mapper.EmrSignatureMapper;
import com.his.emr.mapper.EmrTemplateMapper;
import com.his.emr.mapper.EmrVersionMapper;
import com.his.security.UserPrincipal;
import com.his.signature.SignCommand;
import com.his.signature.SignaturePort;
import com.his.signature.SignatureResult;
import com.his.signature.VerifyCommand;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class EmrDocumentService {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_SUBMITTED = "SUBMITTED";
    public static final String STATUS_SIGNED = "SIGNED";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    private final EmrDocumentMapper documentMapper;
    private final EmrTemplateMapper templateMapper;
    private final EmrVersionMapper versionMapper;
    private final EmrSignatureMapper signatureMapper;
    private final EmrSealMapper sealMapper;
    private final EmrAccessLogMapper accessLogMapper;
    private final SignaturePort signaturePort;
    private final SectionDiffService sectionDiffService;
    private final QcService qcService;
    private final ArchiveCirculationService circulationService;
    private final ObjectMapper objectMapper;

    public EmrDocumentService(EmrDocumentMapper documentMapper,
                              EmrTemplateMapper templateMapper,
                              EmrVersionMapper versionMapper,
                              EmrSignatureMapper signatureMapper,
                              EmrSealMapper sealMapper,
                              EmrAccessLogMapper accessLogMapper,
                              SignaturePort signaturePort,
                              SectionDiffService sectionDiffService,
                              QcService qcService,
                              ArchiveCirculationService circulationService,
                              ObjectMapper objectMapper) {
        this.documentMapper = documentMapper;
        this.templateMapper = templateMapper;
        this.versionMapper = versionMapper;
        this.signatureMapper = signatureMapper;
        this.sealMapper = sealMapper;
        this.accessLogMapper = accessLogMapper;
        this.signaturePort = signaturePort;
        this.sectionDiffService = sectionDiffService;
        this.qcService = qcService;
        this.circulationService = circulationService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public EmrDtos.DocumentView create(EmrDtos.CreateDocumentRequest request, UserPrincipal user) {
        EmrTemplate template = templateMapper.selectById(request.templateId());
        if (template == null || Boolean.FALSE.equals(template.getEnabled())) {
            throw new BizException("TEMPLATE_NOT_FOUND", "模板不存在或已停用");
        }
        if (!template.getDocType().equals(request.docType())) {
            throw new BizException("TEMPLATE_MISMATCH", "模板与文书类型不匹配");
        }

        LocalDateTime now = LocalDateTime.now();
        EmrDocument document = new EmrDocument();
        document.setPatientId(request.patientId());
        document.setVisitId(request.visitId());
        document.setDocType(request.docType());
        document.setTemplateId(template.getId());
        document.setTemplateVersion(template.getVersion());
        document.setTitle(request.title() == null || request.title().isBlank()
                ? template.getName() : request.title());
        document.setStatus(STATUS_DRAFT);
        document.setAuthorId(user.id());
        document.setAuthorName(user.displayName());
        document.setContentJson(initialContent(template.getSchemaJson()));
        document.setCurrentVersion(1);
        document.setCreatedAt(now);
        document.setUpdatedAt(now);
        document.setUpdatedBy(user.id());
        document.setVersion(1);
        documentMapper.insert(document);

        insertVersion(document, 1, document.getContentJson(), "创建病历", user, now);
        return toView(document);
    }

    public List<EmrDtos.DocumentSummary> list(Long patientId, Long visitId,
                                              String status, UserPrincipal user) {
        if (patientId == null && visitId == null) {
            throw new BizException("QUERY_REQUIRED", "请至少按患者或就诊 ID 查询");
        }
        var query = Wrappers.<EmrDocument>lambdaQuery()
                .eq(patientId != null, EmrDocument::getPatientId, patientId)
                .eq(visitId != null, EmrDocument::getVisitId, visitId)
                .eq(status != null && !status.isBlank(), EmrDocument::getStatus, status)
                .orderByDesc(EmrDocument::getCreatedAt);
        if (!isPrivileged(user)) {
            var borrowed = circulationService.activeBorrowedDocIds(user.id());
            if (borrowed.isEmpty()) {
                query.eq(EmrDocument::getAuthorId, user.id());
            } else {
                query.and(w -> w.eq(EmrDocument::getAuthorId, user.id())
                        .or().in(EmrDocument::getId, borrowed));
            }
        }
        if (!"ADMIN".equals(user.role())) {
            query.eq(EmrDocument::getSealed, false);
        }
        return documentMapper.selectList(query).stream().map(this::toSummary).toList();
    }

    @Transactional
    public EmrDtos.DocumentView get(Long id, String reason, UserPrincipal user) {
        EmrDocument document = requireDocument(id);
        checkRead(document, user);
        logAccess(document.getId(), user, "VIEW", reason);
        return toView(document);
    }

    @Transactional
    public EmrDtos.DocumentView save(Long id, EmrDtos.SaveDocumentRequest request, UserPrincipal user) {
        EmrDocument document = requireDocument(id);
        checkWrite(document, user);
        if (!STATUS_DRAFT.equals(document.getStatus())) {
            throw new BizException("DOC_LOCKED", "仅草稿状态可编辑，已提交或已签名病历不可修改");
        }

        String normalized = normalizeContent(request.content());
        LocalDateTime now = LocalDateTime.now();
        int nextVersion = document.getCurrentVersion() + 1;

        document.setContentJson(normalized);
        document.setCurrentVersion(nextVersion);
        document.setUpdatedAt(now);
        document.setUpdatedBy(user.id());
        if (documentMapper.updateById(document) == 0) {
            throw new BizException("CONFLICT", "病历已被他人修改，请刷新后重试", HttpStatus.CONFLICT);
        }

        insertVersion(document, nextVersion, normalized, request.changeSummary(), user, now);
        // 内容已修改，旧缺陷不再可信，由下一次质控重新生成
        qcService.clearOpen(document.getId());
        return toView(document);
    }

    @Transactional
    public EmrDtos.DocumentView submit(Long id, UserPrincipal user) {
        EmrDocument document = requireDocument(id);
        checkWrite(document, user);
        if (!STATUS_DRAFT.equals(document.getStatus())) {
            throw new BizException("INVALID_STATUS", "仅草稿状态可提交");
        }
        // 提交前跑一遍形式质控，阻断级缺陷不允许提交
        QcDtos.QcRunResult qc = qcService.run(document.getId(), user);
        if (qc.blocked()) {
            String details = qc.issues().stream()
                    .filter(issue -> QcService.SEVERITY_BLOCK.equals(issue.severity()))
                    .map(QcDtos.QcIssueView::defectDesc)
                    .reduce((a, b) -> a + "；" + b)
                    .orElse("存在阻断级缺陷");
            throw new BizException("QC_BLOCKED", "存在阻断级质控缺陷：" + details);
        }

        document.setStatus(STATUS_SUBMITTED);
        document.setUpdatedAt(LocalDateTime.now());
        document.setUpdatedBy(user.id());
        if (documentMapper.updateById(document) == 0) {
            throw new BizException("CONFLICT", "病历已被他人修改，请刷新后重试", HttpStatus.CONFLICT);
        }
        return toView(document);
    }

    @Transactional
    public EmrDtos.DocumentView sign(Long id, UserPrincipal user) {
        EmrDocument document = requireDocument(id);
        checkWrite(document, user);
        if (!STATUS_SUBMITTED.equals(document.getStatus())) {
            throw new BizException("INVALID_STATUS", "仅已提交状态可签名");
        }

        LocalDateTime signedAt = LocalDateTime.now();
        SignCommand command = new SignCommand(
                document.getId(), document.getCurrentVersion(),
                document.getContentJson(), user.id(), user.displayName(), signedAt);
        SignatureResult result = signaturePort.sign(command);

        EmrSignature signature = new EmrSignature();
        signature.setDocId(document.getId());
        signature.setVersionNo(document.getCurrentVersion());
        signature.setSignerId(user.id());
        signature.setSignerName(user.displayName());
        signature.setSignType(result.signType());
        signature.setCertSn(result.certSn());
        signature.setContentHash(result.contentHash());
        signature.setSignature(result.signature());
        signature.setSignedAt(result.signedAt());
        signatureMapper.insert(signature);

        document.setStatus(STATUS_SIGNED);
        document.setSignedAt(signedAt);
        document.setUpdatedAt(signedAt);
        document.setUpdatedBy(user.id());
        if (documentMapper.updateById(document) == 0) {
            throw new BizException("CONFLICT", "病历已被他人修改，请刷新后重试", HttpStatus.CONFLICT);
        }
        logAccess(document.getId(), user, "SIGN", null);
        return toView(document);
    }

    /**
     * E7-02 归档：仅已签名且签名校验通过可归档，归档后只读。
     * 病案室（QC）与管理员可执行。
     */
    @Transactional
    public EmrDtos.DocumentView archive(Long id, UserPrincipal user) {
        EmrDocument document = requireDocument(id);
        if (!isPrivileged(user)) {
            throw new BizException("FORBIDDEN", "仅病案质控或管理员可归档", HttpStatus.FORBIDDEN);
        }
        if (STATUS_ARCHIVED.equals(document.getStatus())) {
            throw new BizException("ALREADY_ARCHIVED", "该病历已归档", HttpStatus.CONFLICT);
        }
        if (!STATUS_SIGNED.equals(document.getStatus())) {
            throw new BizException("INVALID_STATUS", "仅已签名病历可归档");
        }

        EmrSignature signature = latestSignature(id);
        if (signature == null) {
            throw new BizException("SIGNATURE_MISSING", "病历未签名，禁止归档");
        }
        EmrVersion signedVersion = requireVersion(id, signature.getVersionNo());
        boolean signatureValid = signaturePort.verify(new VerifyCommand(
                id, signature.getVersionNo(), signedVersion.getContentJson(),
                signature.getSignerId(), signature.getSignedAt(), signature.getSignature()));
        if (!signatureValid) {
            throw new BizException("ARCHIVE_SIGNATURE_INVALID", "签名校验失败，禁止归档", HttpStatus.CONFLICT);
        }

        LocalDateTime now = LocalDateTime.now();
        document.setStatus(STATUS_ARCHIVED);
        document.setArchivedAt(now);
        document.setArchivedBy(user.id());
        document.setArchiveHash(signature.getContentHash());
        document.setUpdatedAt(now);
        document.setUpdatedBy(user.id());
        if (documentMapper.updateById(document) == 0) {
            throw new BizException("CONFLICT", "病历已被他人修改，请刷新后重试", HttpStatus.CONFLICT);
        }
        logAccess(id, user, "ARCHIVE", null);
        return toView(document);
    }

    /** E6-07 封存：仅管理员可执行，必须填写原因，封存后非管理员不可调阅。 */
    @Transactional
    public EmrDtos.DocumentView seal(Long id, EmrDtos.SealRequest request, UserPrincipal user) {
        requireAdmin(user);
        EmrDocument document = requireDocument(id);
        if (!STATUS_ARCHIVED.equals(document.getStatus())) {
            throw new BizException("INVALID_STATUS", "仅已归档病历可封存");
        }
        if (Boolean.TRUE.equals(document.getSealed())) {
            throw new BizException("ALREADY_SEALED", "该病历已封存", HttpStatus.CONFLICT);
        }
        LocalDateTime now = LocalDateTime.now();
        document.setSealed(true);
        document.setUpdatedAt(now);
        document.setUpdatedBy(user.id());
        if (documentMapper.updateById(document) == 0) {
            throw new BizException("CONFLICT", "病历已被他人修改，请刷新后重试", HttpStatus.CONFLICT);
        }
        insertSeal(id, "SEAL", request.reason(), user, now);
        logAccess(id, user, "SEAL", request.reason());
        return toView(document);
    }

    /** E6-07 解封：仅管理员可执行，留痕原因。 */
    @Transactional
    public EmrDtos.DocumentView unseal(Long id, EmrDtos.SealRequest request, UserPrincipal user) {
        requireAdmin(user);
        EmrDocument document = requireDocument(id);
        if (!Boolean.TRUE.equals(document.getSealed())) {
            throw new BizException("NOT_SEALED", "该病历未封存", HttpStatus.CONFLICT);
        }
        LocalDateTime now = LocalDateTime.now();
        document.setSealed(false);
        document.setUpdatedAt(now);
        document.setUpdatedBy(user.id());
        if (documentMapper.updateById(document) == 0) {
            throw new BizException("CONFLICT", "病历已被他人修改，请刷新后重试", HttpStatus.CONFLICT);
        }
        insertSeal(id, "RELEASE", request.reason(), user, now);
        logAccess(id, user, "UNSEAL", request.reason());
        return toView(document);
    }

    public List<EmrDtos.VersionView> versions(Long id, UserPrincipal user) {
        EmrDocument document = requireDocument(id);
        checkRead(document, user);
        List<EmrVersion> versions = versionMapper.selectList(
                Wrappers.<EmrVersion>lambdaQuery()
                        .eq(EmrVersion::getDocId, id)
                        .orderByDesc(EmrVersion::getVersionNo));
        List<EmrDtos.VersionView> result = new ArrayList<>(versions.size());
        for (EmrVersion version : versions) {
            result.add(new EmrDtos.VersionView(
                    version.getVersionNo(),
                    readTree(version.getContentJson()),
                    version.getChangeSummary(),
                    version.getCreatedByName(),
                    version.getCreatedAt()));
        }
        return result;
    }

    public EmrDtos.DiffResult diff(Long id, Integer fromVersion, Integer toVersion, UserPrincipal user) {
        EmrDocument document = requireDocument(id);
        checkRead(document, user);
        EmrVersion from = requireVersion(id, fromVersion);
        EmrVersion to = requireVersion(id, toVersion);
        if (from.getVersionNo() > to.getVersionNo()) {
            EmrVersion temp = from;
            from = to;
            to = temp;
        }
        return sectionDiffService.compare(
                from.getContentJson(), to.getContentJson(),
                from.getVersionNo(), to.getVersionNo());
    }

    @Transactional
    public EmrDtos.VerifyResult verify(Long id, Integer versionNo, UserPrincipal user) {
        EmrDocument document = requireDocument(id);
        checkRead(document, user);

        EmrSignature signature = signatureMapper.selectOne(
                Wrappers.<EmrSignature>lambdaQuery()
                        .eq(EmrSignature::getDocId, id)
                        .eq(versionNo != null, EmrSignature::getVersionNo, versionNo)
                        .orderByDesc(EmrSignature::getVersionNo)
                        .last("limit 1"));
        if (signature == null) {
            return new EmrDtos.VerifyResult(false, "该病历尚未签名", null,
                    null, null, null, null, null, null);
        }

        EmrVersion version = requireVersion(id, signature.getVersionNo());
        VerifyCommand command = new VerifyCommand(
                id, signature.getVersionNo(), version.getContentJson(),
                signature.getSignerId(), signature.getSignedAt(), signature.getSignature());
        boolean valid = signaturePort.verify(command);
        SignatureResult recomputed = signaturePort.sign(new SignCommand(
                id, signature.getVersionNo(), version.getContentJson(),
                signature.getSignerId(), signature.getSignerName(), signature.getSignedAt()));

        logAccess(id, user, "VERIFY", null);
        return new EmrDtos.VerifyResult(
                valid,
                valid ? "签名有效" : "签名校验失败，内容可能被篡改",
                signature.getVersionNo(),
                signature.getSignerName(),
                signature.getCertSn(),
                signature.getContentHash(),
                signature.getSignature(),
                recomputed.signature(),
                signature.getSignedAt());
    }

    private EmrDocument requireDocument(Long id) {
        EmrDocument document = documentMapper.selectById(id);
        if (document == null) {
            throw new BizException("DOC_NOT_FOUND", "病历不存在", HttpStatus.NOT_FOUND);
        }
        return document;
    }

    private EmrVersion requireVersion(Long docId, Integer versionNo) {
        EmrVersion version = versionMapper.selectOne(
                Wrappers.<EmrVersion>lambdaQuery()
                        .eq(EmrVersion::getDocId, docId)
                        .eq(EmrVersion::getVersionNo, versionNo));
        if (version == null) {
            throw new BizException("VERSION_NOT_FOUND", "病历版本不存在", HttpStatus.NOT_FOUND);
        }
        return version;
    }

    private void checkRead(EmrDocument document, UserPrincipal user) {
        if (Boolean.TRUE.equals(document.getSealed()) && !"ADMIN".equals(user.role())) {
            throw new BizException("DOC_SEALED", "病历已封存，仅管理员可调阅", HttpStatus.FORBIDDEN);
        }
        if (isPrivileged(user) || document.getAuthorId().equals(user.id())) {
            return;
        }
        // E7-04 借阅期内，申请人可调阅已归档病历
        if (circulationService.hasActiveBorrow(document.getId(), user.id())) {
            return;
        }
        throw new BizException("FORBIDDEN", "无权访问该病历", HttpStatus.FORBIDDEN);
    }

    private void checkWrite(EmrDocument document, UserPrincipal user) {
        if ("ADMIN".equals(user.role()) || document.getAuthorId().equals(user.id())) {
            return;
        }
        throw new BizException("FORBIDDEN", "无权修改该病历", HttpStatus.FORBIDDEN);
    }

    private boolean isPrivileged(UserPrincipal user) {
        return "ADMIN".equals(user.role()) || "QC".equals(user.role());
    }

    private void insertVersion(EmrDocument document, int versionNo, String content,
                               String changeSummary, UserPrincipal user, LocalDateTime now) {
        EmrVersion version = new EmrVersion();
        version.setDocId(document.getId());
        version.setVersionNo(versionNo);
        version.setContentJson(content);
        version.setChangeSummary(changeSummary);
        version.setCreatedBy(user.id());
        version.setCreatedByName(user.displayName());
        version.setCreatedAt(now);
        versionMapper.insert(version);
    }

    private void logAccess(Long docId, UserPrincipal user, String action, String reason) {
        EmrAccessLog log = new EmrAccessLog();
        log.setDocId(docId);
        log.setOperatorId(user.id());
        log.setOperatorName(user.displayName());
        log.setAction(action);
        log.setReason(reason);
        log.setIp(com.his.common.WebUtils.clientIp());
        log.setCreatedAt(LocalDateTime.now());
        accessLogMapper.insert(log);
    }

    private String initialContent(String templateSchema) {
        try {
            JsonNode schema = objectMapper.readTree(templateSchema);
            ArrayNode sections = objectMapper.createArrayNode();
            for (JsonNode section : schema.path("sections")) {
                ObjectNode node = objectMapper.createObjectNode();
                String code = section.path("code").asText("");
                String type = section.path("type").asText("text");
                node.put("code", code);
                node.put("title", section.path("title").asText(code));
                node.put("type", type);
                if ("icd".equals(type) || "multi".equals(type)) {
                    node.set("value", objectMapper.createArrayNode());
                } else {
                    node.put("value", "");
                }
                sections.add(node);
            }
            ObjectNode root = objectMapper.createObjectNode();
            root.set("sections", sections);
            return objectMapper.writeValueAsString(root);
        } catch (Exception ex) {
            throw new BizException("TEMPLATE_INVALID", "模板结构非法");
        }
    }

    private String normalizeContent(JsonNode content) {
        if (content == null || !content.isObject()) {
            throw new BizException("INVALID_CONTENT", "病历内容必须是 JSON 对象");
        }
        JsonNode sections = content.path("sections");
        if (!sections.isArray()) {
            throw new BizException("INVALID_CONTENT", "病历内容缺少 sections 数组");
        }
        for (JsonNode section : sections) {
            if (section.path("code").asText("").isBlank()) {
                throw new BizException("INVALID_CONTENT", "段落缺少 code");
            }
        }
        try {
            return objectMapper.writeValueAsString(content);
        } catch (Exception ex) {
            throw new BizException("INVALID_CONTENT", "病历内容序列化失败");
        }
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new BizException("CONTENT_INVALID", "病历内容解析失败");
        }
    }

    private EmrDtos.DocumentSummary toSummary(EmrDocument document) {
        return new EmrDtos.DocumentSummary(
                document.getId(),
                document.getPatientId(),
                document.getVisitId(),
                document.getDocType(),
                document.getTitle(),
                document.getStatus(),
                document.getCurrentVersion(),
                document.getAuthorName(),
                document.getCreatedAt(),
                document.getSignedAt(),
                Boolean.TRUE.equals(document.getSealed()));
    }

    private EmrDtos.DocumentView toView(EmrDocument document) {
        return new EmrDtos.DocumentView(
                document.getId(),
                document.getPatientId(),
                document.getVisitId(),
                document.getDocType(),
                document.getTemplateId(),
                document.getTemplateVersion(),
                document.getTitle(),
                document.getStatus(),
                document.getCurrentVersion(),
                document.getAuthorName(),
                readTree(document.getContentJson()),
                document.getCreatedAt(),
                document.getUpdatedAt(),
                document.getSignedAt(),
                document.getArchivedAt(),
                document.getArchiveHash(),
                Boolean.TRUE.equals(document.getSealed()));
    }

    private EmrSignature latestSignature(Long docId) {
        List<EmrSignature> signatures = signatureMapper.selectList(Wrappers.<EmrSignature>lambdaQuery()
                .eq(EmrSignature::getDocId, docId)
                .orderByDesc(EmrSignature::getSignedAt)
                .last("limit 1"));
        return signatures.isEmpty() ? null : signatures.get(0);
    }

    private void insertSeal(Long docId, String action, String reason, UserPrincipal user, LocalDateTime now) {
        EmrSeal seal = new EmrSeal();
        seal.setDocId(docId);
        seal.setAction(action);
        seal.setReason(reason);
        seal.setOperatorId(user.id());
        seal.setOperatorName(user.displayName());
        seal.setCreatedAt(now);
        sealMapper.insert(seal);
    }

    private void requireAdmin(UserPrincipal user) {
        if (!"ADMIN".equals(user.role())) {
            throw new BizException("FORBIDDEN", "仅系统管理员可执行封存/解封", HttpStatus.FORBIDDEN);
        }
    }
}

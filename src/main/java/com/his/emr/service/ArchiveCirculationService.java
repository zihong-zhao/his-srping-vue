package com.his.emr.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.his.common.BizException;
import com.his.emr.dto.ArchiveDtos;
import com.his.emr.entity.EmrAccessLog;
import com.his.emr.entity.EmrBorrowRequest;
import com.his.emr.entity.EmrCopyRequest;
import com.his.emr.entity.EmrDocument;
import com.his.emr.mapper.EmrAccessLogMapper;
import com.his.emr.mapper.EmrBorrowRequestMapper;
import com.his.emr.mapper.EmrCopyRequestMapper;
import com.his.emr.mapper.EmrDocumentMapper;
import com.his.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** E7-04 病案借阅与复印：申请 -> 审批 -> 借阅/复印 -> 留痕，借阅限时有效。 */
@Service
public class ArchiveCirculationService {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_RETURNED = "RETURNED";
    public static final String STATUS_COMPLETED = "COMPLETED";

    private static final int DEFAULT_BORROW_DAYS = 7;
    private static final int DEFAULT_COPIES = 1;

    private final EmrDocumentMapper documentMapper;
    private final EmrBorrowRequestMapper borrowMapper;
    private final EmrCopyRequestMapper copyMapper;
    private final EmrAccessLogMapper accessLogMapper;

    public ArchiveCirculationService(EmrDocumentMapper documentMapper,
                                     EmrBorrowRequestMapper borrowMapper,
                                     EmrCopyRequestMapper copyMapper,
                                     EmrAccessLogMapper accessLogMapper) {
        this.documentMapper = documentMapper;
        this.borrowMapper = borrowMapper;
        this.copyMapper = copyMapper;
        this.accessLogMapper = accessLogMapper;
    }

    @Transactional
    public ArchiveDtos.BorrowView createBorrow(ArchiveDtos.BorrowCreateRequest request, UserPrincipal user) {
        EmrDocument document = requireArchived(request.docId());
        ensureSealedAccessible(document, user);
        boolean pending = borrowMapper.selectCount(Wrappers.<EmrBorrowRequest>lambdaQuery()
                .eq(EmrBorrowRequest::getDocId, document.getId())
                .eq(EmrBorrowRequest::getApplicantId, user.id())
                .eq(EmrBorrowRequest::getStatus, STATUS_PENDING)) > 0;
        if (pending) {
            throw new BizException("BORROW_PENDING", "该病历已有待审批的借阅申请", HttpStatus.CONFLICT);
        }

        EmrBorrowRequest borrow = new EmrBorrowRequest();
        borrow.setDocId(document.getId());
        borrow.setApplicantId(user.id());
        borrow.setApplicantName(user.displayName());
        borrow.setReason(request.reason());
        borrow.setDays(request.days() == null ? DEFAULT_BORROW_DAYS : request.days());
        borrow.setStatus(STATUS_PENDING);
        borrow.setCreatedAt(LocalDateTime.now());
        borrowMapper.insert(borrow);
        logAccess(document.getId(), user, "BORROW_APPLY", request.reason());
        return toBorrowView(borrow, document);
    }

    @Transactional
    public ArchiveDtos.BorrowView approveBorrow(Long id, ArchiveDtos.DecisionRequest request, UserPrincipal user) {
        requirePrivileged(user);
        EmrBorrowRequest borrow = requireBorrow(id);
        if (!STATUS_PENDING.equals(borrow.getStatus())) {
            throw new BizException("INVALID_STATUS", "仅待审批的借阅申请可审批", HttpStatus.CONFLICT);
        }
        EmrDocument document = requireArchived(borrow.getDocId());
        ensureSealedAccessible(document, user);

        LocalDateTime now = LocalDateTime.now();
        borrow.setStatus(STATUS_APPROVED);
        borrow.setApproverId(user.id());
        borrow.setApproverName(user.displayName());
        borrow.setApproveNote(request == null ? null : request.note());
        borrow.setApprovedAt(now);
        borrow.setStartAt(now);
        borrow.setDueAt(now.plusDays(borrow.getDays()));
        borrowMapper.updateById(borrow);
        logAccess(document.getId(), user, "BORROW_APPROVE", borrow.getApproveNote());
        return toBorrowView(borrow, document);
    }

    @Transactional
    public ArchiveDtos.BorrowView rejectBorrow(Long id, ArchiveDtos.RejectRequest request, UserPrincipal user) {
        requirePrivileged(user);
        EmrBorrowRequest borrow = requireBorrow(id);
        if (!STATUS_PENDING.equals(borrow.getStatus())) {
            throw new BizException("INVALID_STATUS", "仅待审批的借阅申请可驳回", HttpStatus.CONFLICT);
        }
        borrow.setStatus(STATUS_REJECTED);
        borrow.setApproverId(user.id());
        borrow.setApproverName(user.displayName());
        borrow.setApproveNote(request.note());
        borrow.setApprovedAt(LocalDateTime.now());
        borrowMapper.updateById(borrow);
        logAccess(borrow.getDocId(), user, "BORROW_REJECT", request.note());
        return toBorrowView(borrow, documentOf(borrow.getDocId()));
    }

    @Transactional
    public ArchiveDtos.BorrowView returnBorrow(Long id, UserPrincipal user) {
        EmrBorrowRequest borrow = requireBorrow(id);
        if (!STATUS_APPROVED.equals(borrow.getStatus())) {
            throw new BizException("INVALID_STATUS", "仅借阅中的申请可归还", HttpStatus.CONFLICT);
        }
        if (!borrow.getApplicantId().equals(user.id()) && !isPrivileged(user)) {
            throw new BizException("FORBIDDEN", "无权归还他人借阅", HttpStatus.FORBIDDEN);
        }
        borrow.setStatus(STATUS_RETURNED);
        borrow.setReturnedAt(LocalDateTime.now());
        borrowMapper.updateById(borrow);
        logAccess(borrow.getDocId(), user, "BORROW_RETURN", null);
        return toBorrowView(borrow, documentOf(borrow.getDocId()));
    }

    public List<ArchiveDtos.BorrowView> listBorrows(String status, int limit, UserPrincipal user) {
        int size = Math.max(1, Math.min(limit, 200));
        List<EmrBorrowRequest> list = borrowMapper.selectList(Wrappers.<EmrBorrowRequest>lambdaQuery()
                .eq(status != null && !status.isBlank(), EmrBorrowRequest::getStatus, status)
                .eq(!isPrivileged(user), EmrBorrowRequest::getApplicantId, user.id())
                .orderByDesc(EmrBorrowRequest::getCreatedAt)
                .last("limit " + size));
        return list.stream().map(borrow -> toBorrowView(borrow, documentOf(borrow.getDocId()))).toList();
    }

    @Transactional
    public ArchiveDtos.CopyView createCopy(ArchiveDtos.CopyCreateRequest request, UserPrincipal user) {
        EmrDocument document = requireArchived(request.docId());
        ensureSealedAccessible(document, user);
        boolean pending = copyMapper.selectCount(Wrappers.<EmrCopyRequest>lambdaQuery()
                .eq(EmrCopyRequest::getDocId, document.getId())
                .eq(EmrCopyRequest::getApplicantId, user.id())
                .eq(EmrCopyRequest::getStatus, STATUS_PENDING)) > 0;
        if (pending) {
            throw new BizException("COPY_PENDING", "该病历已有待审批的复印申请", HttpStatus.CONFLICT);
        }

        EmrCopyRequest copy = new EmrCopyRequest();
        copy.setDocId(document.getId());
        copy.setApplicantId(user.id());
        copy.setApplicantName(user.displayName());
        copy.setPurpose(request.purpose());
        copy.setCopies(request.copies() == null ? DEFAULT_COPIES : request.copies());
        copy.setStatus(STATUS_PENDING);
        copy.setCreatedAt(LocalDateTime.now());
        copyMapper.insert(copy);
        logAccess(document.getId(), user, "COPY_APPLY", request.purpose());
        return toCopyView(copy, document);
    }

    @Transactional
    public ArchiveDtos.CopyView approveCopy(Long id, ArchiveDtos.DecisionRequest request, UserPrincipal user) {
        requirePrivileged(user);
        EmrCopyRequest copy = requireCopy(id);
        if (!STATUS_PENDING.equals(copy.getStatus())) {
            throw new BizException("INVALID_STATUS", "仅待审批的复印申请可审批", HttpStatus.CONFLICT);
        }
        EmrDocument document = requireArchived(copy.getDocId());
        ensureSealedAccessible(document, user);

        copy.setStatus(STATUS_APPROVED);
        copy.setApproverId(user.id());
        copy.setApproverName(user.displayName());
        copy.setApproveNote(request == null ? null : request.note());
        copy.setApprovedAt(LocalDateTime.now());
        copyMapper.updateById(copy);
        logAccess(document.getId(), user, "COPY_APPROVE", copy.getApproveNote());
        return toCopyView(copy, document);
    }

    @Transactional
    public ArchiveDtos.CopyView rejectCopy(Long id, ArchiveDtos.RejectRequest request, UserPrincipal user) {
        requirePrivileged(user);
        EmrCopyRequest copy = requireCopy(id);
        if (!STATUS_PENDING.equals(copy.getStatus())) {
            throw new BizException("INVALID_STATUS", "仅待审批的复印申请可驳回", HttpStatus.CONFLICT);
        }
        copy.setStatus(STATUS_REJECTED);
        copy.setApproverId(user.id());
        copy.setApproverName(user.displayName());
        copy.setApproveNote(request.note());
        copy.setApprovedAt(LocalDateTime.now());
        copyMapper.updateById(copy);
        logAccess(copy.getDocId(), user, "COPY_REJECT", request.note());
        return toCopyView(copy, documentOf(copy.getDocId()));
    }

    /** 病案室完成复印并交付，留痕操作人与时间。 */
    @Transactional
    public ArchiveDtos.CopyView completeCopy(Long id, UserPrincipal user) {
        requirePrivileged(user);
        EmrCopyRequest copy = requireCopy(id);
        if (!STATUS_APPROVED.equals(copy.getStatus())) {
            throw new BizException("INVALID_STATUS", "仅已审批的复印申请可完成", HttpStatus.CONFLICT);
        }
        copy.setStatus(STATUS_COMPLETED);
        copy.setCompletedBy(user.id());
        copy.setCompletedByName(user.displayName());
        copy.setCompletedAt(LocalDateTime.now());
        copyMapper.updateById(copy);
        logAccess(copy.getDocId(), user, "COPY_COMPLETE", copy.getPurpose());
        return toCopyView(copy, documentOf(copy.getDocId()));
    }

    public List<ArchiveDtos.CopyView> listCopies(String status, int limit, UserPrincipal user) {
        int size = Math.max(1, Math.min(limit, 200));
        List<EmrCopyRequest> list = copyMapper.selectList(Wrappers.<EmrCopyRequest>lambdaQuery()
                .eq(status != null && !status.isBlank(), EmrCopyRequest::getStatus, status)
                .eq(!isPrivileged(user), EmrCopyRequest::getApplicantId, user.id())
                .orderByDesc(EmrCopyRequest::getCreatedAt)
                .last("limit " + size));
        return list.stream().map(copy -> toCopyView(copy, documentOf(copy.getDocId()))).toList();
    }

    /** 借阅期内的有效授权，供文档调阅校验使用。 */
    public boolean hasActiveBorrow(Long docId, Long userId) {
        if (docId == null || userId == null) {
            return false;
        }
        return borrowMapper.selectCount(Wrappers.<EmrBorrowRequest>lambdaQuery()
                .eq(EmrBorrowRequest::getDocId, docId)
                .eq(EmrBorrowRequest::getApplicantId, userId)
                .eq(EmrBorrowRequest::getStatus, STATUS_APPROVED)
                .ge(EmrBorrowRequest::getDueAt, LocalDateTime.now())) > 0;
    }

    /** 借阅中的病历 ID 集合，供列表查询放开可见范围。 */
    public Set<Long> activeBorrowedDocIds(Long userId) {
        return borrowMapper.selectList(Wrappers.<EmrBorrowRequest>lambdaQuery()
                        .eq(EmrBorrowRequest::getApplicantId, userId)
                        .eq(EmrBorrowRequest::getStatus, STATUS_APPROVED)
                        .ge(EmrBorrowRequest::getDueAt, LocalDateTime.now()))
                .stream()
                .map(EmrBorrowRequest::getDocId)
                .collect(Collectors.toSet());
    }

    private EmrDocument requireArchived(Long docId) {
        EmrDocument document = documentOf(docId);
        if (document == null) {
            throw new BizException("DOC_NOT_FOUND", "病历不存在", HttpStatus.NOT_FOUND);
        }
        if (!EmrDocumentService.STATUS_ARCHIVED.equals(document.getStatus())) {
            throw new BizException("INVALID_STATUS", "仅已归档病历可申请借阅或复印");
        }
        return document;
    }

    private EmrDocument documentOf(Long docId) {
        return docId == null ? null : documentMapper.selectById(docId);
    }

    private void ensureSealedAccessible(EmrDocument document, UserPrincipal user) {
        if (Boolean.TRUE.equals(document.getSealed()) && !"ADMIN".equals(user.role())) {
            throw new BizException("DOC_SEALED", "病历已封存，仅管理员可申请或审批", HttpStatus.FORBIDDEN);
        }
    }

    private EmrBorrowRequest requireBorrow(Long id) {
        EmrBorrowRequest borrow = borrowMapper.selectById(id);
        if (borrow == null) {
            throw new BizException("BORROW_NOT_FOUND", "借阅申请不存在", HttpStatus.NOT_FOUND);
        }
        return borrow;
    }

    private EmrCopyRequest requireCopy(Long id) {
        EmrCopyRequest copy = copyMapper.selectById(id);
        if (copy == null) {
            throw new BizException("COPY_NOT_FOUND", "复印申请不存在", HttpStatus.NOT_FOUND);
        }
        return copy;
    }

    private void requirePrivileged(UserPrincipal user) {
        if (!isPrivileged(user)) {
            throw new BizException("FORBIDDEN", "仅病案质控或管理员可审批/办理", HttpStatus.FORBIDDEN);
        }
    }

    private boolean isPrivileged(UserPrincipal user) {
        return "ADMIN".equals(user.role()) || "QC".equals(user.role());
    }

    private void logAccess(Long docId, UserPrincipal user, String action, String reason) {
        EmrAccessLog entry = new EmrAccessLog();
        entry.setDocId(docId);
        entry.setOperatorId(user.id());
        entry.setOperatorName(user.displayName());
        entry.setAction(action);
        entry.setReason(reason != null && reason.length() > 200 ? reason.substring(0, 200) : reason);
        entry.setIp(com.his.common.WebUtils.clientIp());
        entry.setCreatedAt(LocalDateTime.now());
        accessLogMapper.insert(entry);
    }

    private ArchiveDtos.BorrowView toBorrowView(EmrBorrowRequest borrow, EmrDocument document) {
        return new ArchiveDtos.BorrowView(
                borrow.getId(),
                borrow.getDocId(),
                document == null ? null : document.getTitle(),
                borrow.getApplicantId(),
                borrow.getApplicantName(),
                borrow.getReason(),
                borrow.getDays(),
                borrow.getStatus(),
                borrow.getApproverName(),
                borrow.getApproveNote(),
                borrow.getApprovedAt(),
                borrow.getStartAt(),
                borrow.getDueAt(),
                borrow.getReturnedAt(),
                borrow.getCreatedAt());
    }

    private ArchiveDtos.CopyView toCopyView(EmrCopyRequest copy, EmrDocument document) {
        return new ArchiveDtos.CopyView(
                copy.getId(),
                copy.getDocId(),
                document == null ? null : document.getTitle(),
                copy.getApplicantId(),
                copy.getApplicantName(),
                copy.getPurpose(),
                copy.getCopies(),
                copy.getStatus(),
                copy.getApproverName(),
                copy.getApproveNote(),
                copy.getApprovedAt(),
                copy.getCompletedByName(),
                copy.getCompletedAt(),
                copy.getCreatedAt());
    }
}

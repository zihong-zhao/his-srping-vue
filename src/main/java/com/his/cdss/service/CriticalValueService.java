package com.his.cdss.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.his.cdss.dto.CdssDtos;
import com.his.cdss.entity.CdssCriticalValue;
import com.his.cdss.mapper.CdssCriticalValueMapper;
import com.his.common.BizException;
import com.his.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** E4-04 危急值闭环：上报 → 确认 → 处理关闭。 */
@Service
public class CriticalValueService {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_CLOSED = "CLOSED";

    private static final Set<String> SEVERITIES = Set.of("HIGH", "LOW");
    private static final Set<String> STATUSES = Set.of(STATUS_PENDING, STATUS_CONFIRMED, STATUS_CLOSED);

    private final CdssCriticalValueMapper criticalValueMapper;

    public CriticalValueService(CdssCriticalValueMapper criticalValueMapper) {
        this.criticalValueMapper = criticalValueMapper;
    }

    @Transactional
    public CdssDtos.CriticalValueView report(CdssDtos.ReportCriticalValueRequest request, UserPrincipal user) {
        if (!SEVERITIES.contains(request.severity())) {
            throw new BizException("INVALID_SEVERITY", "危急值级别仅支持 HIGH 或 LOW");
        }
        LocalDateTime now = LocalDateTime.now();
        CdssCriticalValue value = new CdssCriticalValue();
        value.setPatientId(request.patientId());
        value.setVisitId(request.visitId());
        value.setItemCode(request.itemCode());
        value.setItemName(request.itemName());
        value.setResultValue(request.resultValue());
        value.setUnit(request.unit());
        value.setReferenceRange(request.referenceRange());
        value.setSeverity(request.severity());
        value.setStatus(STATUS_PENDING);
        value.setReportedBy(user.id());
        value.setReportedByName(user.displayName());
        value.setReportedAt(now);
        value.setCreatedAt(now);
        value.setUpdatedAt(now);
        value.setVersion(1);
        criticalValueMapper.insert(value);
        return toView(value);
    }

    /** 医生确认收到危急值通知。 */
    @Transactional
    public CdssDtos.CriticalValueView confirm(Long id, UserPrincipal user) {
        CdssCriticalValue value = requireValue(id);
        if (!STATUS_PENDING.equals(value.getStatus())) {
            throw new BizException("INVALID_STATUS", "仅待确认的危急值可确认，当前状态：" + value.getStatus(),
                    HttpStatus.CONFLICT);
        }
        LocalDateTime now = LocalDateTime.now();
        value.setStatus(STATUS_CONFIRMED);
        value.setConfirmedBy(user.id());
        value.setConfirmedByName(user.displayName());
        value.setConfirmedAt(now);
        value.setUpdatedAt(now);
        if (criticalValueMapper.updateById(value) == 0) {
            throw new BizException("CONFLICT", "危急值已被他人处理，请刷新后重试", HttpStatus.CONFLICT);
        }
        return toView(value);
    }

    /** 记录处理措施并关闭危急值闭环。 */
    @Transactional
    public CdssDtos.CriticalValueView close(Long id, String handlingNote, UserPrincipal user) {
        CdssCriticalValue value = requireValue(id);
        if (!STATUS_CONFIRMED.equals(value.getStatus())) {
            throw new BizException("INVALID_STATUS", "仅已确认的危急值可处理关闭，当前状态：" + value.getStatus(),
                    HttpStatus.CONFLICT);
        }
        LocalDateTime now = LocalDateTime.now();
        value.setStatus(STATUS_CLOSED);
        value.setHandlingNote(handlingNote);
        value.setClosedAt(now);
        value.setUpdatedAt(now);
        if (criticalValueMapper.updateById(value) == 0) {
            throw new BizException("CONFLICT", "危急值已被他人处理，请刷新后重试", HttpStatus.CONFLICT);
        }
        return toView(value);
    }

    public CdssDtos.CriticalValueView get(Long id) {
        return toView(requireValue(id));
    }

    public List<CdssDtos.CriticalValueView> list(Long patientId, String status, int limit) {
        if (patientId == null && (status == null || status.isBlank())) {
            throw new BizException("QUERY_REQUIRED", "请至少按患者或状态查询危急值");
        }
        if (status != null && !status.isBlank() && !STATUSES.contains(status)) {
            throw new BizException("INVALID_STATUS", "不支持的危急值状态：" + status);
        }
        int size = Math.max(1, Math.min(limit, 200));
        return criticalValueMapper.selectList(Wrappers.<CdssCriticalValue>lambdaQuery()
                        .eq(patientId != null, CdssCriticalValue::getPatientId, patientId)
                        .eq(status != null && !status.isBlank(), CdssCriticalValue::getStatus, status)
                        .orderByDesc(CdssCriticalValue::getReportedAt)
                        .last("limit " + size))
                .stream().map(this::toView).toList();
    }

    private CdssCriticalValue requireValue(Long id) {
        CdssCriticalValue value = criticalValueMapper.selectById(id);
        if (value == null) {
            throw new BizException("CRITICAL_VALUE_NOT_FOUND", "危急值记录不存在", HttpStatus.NOT_FOUND);
        }
        return value;
    }

    private CdssDtos.CriticalValueView toView(CdssCriticalValue value) {
        return new CdssDtos.CriticalValueView(
                value.getId(),
                value.getPatientId(),
                value.getVisitId(),
                value.getItemCode(),
                value.getItemName(),
                value.getResultValue(),
                value.getUnit(),
                value.getReferenceRange(),
                value.getSeverity(),
                value.getStatus(),
                value.getReportedByName(),
                value.getReportedAt(),
                value.getConfirmedByName(),
                value.getConfirmedAt(),
                value.getHandlingNote(),
                value.getClosedAt());
    }
}

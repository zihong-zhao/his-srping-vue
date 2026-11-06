package com.his.audit;

import com.his.audit.mapper.AuditLogMapper;
import com.his.common.WebUtils;
import com.his.security.CurrentUser;
import com.his.security.UserPrincipal;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    private final AuditLogMapper auditLogMapper;

    public AuditAspect(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    @Around("@annotation(audited)")
    public Object around(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        long start = System.currentTimeMillis();
        boolean success = true;
        String errorMsg = null;
        try {
            return joinPoint.proceed();
        } catch (Throwable ex) {
            success = false;
            errorMsg = ex.getMessage();
            throw ex;
        } finally {
            writeLog(audited, success, errorMsg, System.currentTimeMillis() - start);
        }
    }

    private void writeLog(Audited audited, boolean success, String errorMsg, long durationMs) {
        try {
            UserPrincipal principal = CurrentUser.require();
            AuditLog entry = new AuditLog();
            entry.setOperatorId(principal.id());
            entry.setOperatorName(principal.displayName());
            entry.setAction(audited.action());
            entry.setResourceType(audited.resourceType());
            entry.setRequestUri(WebUtils.currentUri());
            entry.setHttpMethod(WebUtils.currentMethod());
            entry.setIp(WebUtils.clientIp());
            entry.setSuccess(success);
            entry.setErrorMsg(errorMsg);
            entry.setDurationMs(durationMs);
            entry.setCreatedAt(LocalDateTime.now());
            auditLogMapper.insert(entry);
        } catch (Exception ex) {
            // 审计写入失败不应改变业务结果，但必须留下痕迹供运维排查
            log.error("write audit log failed", ex);
        }
    }
}

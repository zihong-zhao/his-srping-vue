package com.his.audit;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.his.audit.mapper.AuditLogMapper;
import com.his.common.ApiResponse;
import com.his.common.BizException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditLogMapper auditLogMapper;

    public AuditController(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    @GetMapping("/logs")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<AuditLog>> logs(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String action) {
        if (limit < 1 || limit > 200) {
            throw new BizException("INVALID_LIMIT", "limit 取值范围 1-200", HttpStatus.BAD_REQUEST);
        }
        return ApiResponse.ok(auditLogMapper.selectList(
                Wrappers.<AuditLog>lambdaQuery()
                        .eq(action != null && !action.isBlank(), AuditLog::getAction, action)
                        .orderByDesc(AuditLog::getCreatedAt)
                        .last("limit " + limit)));
    }
}

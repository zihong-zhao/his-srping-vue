package com.his.cdss.controller;

import com.his.audit.Audited;
import com.his.cdss.dto.CdssDtos;
import com.his.cdss.service.CriticalValueService;
import com.his.common.ApiResponse;
import com.his.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** E4-04 危急值闭环。 */
@RestController
@RequestMapping("/api/e4/critical-values")
public class CriticalValueController {

    private final CriticalValueService criticalValueService;

    public CriticalValueController(CriticalValueService criticalValueService) {
        this.criticalValueService = criticalValueService;
    }

    @PostMapping
    @Operation(summary = "上报危急值")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','QC')")
    @Audited(action = "CRITICAL_VALUE_REPORT", resourceType = "CDSS_CRITICAL_VALUE")
    public ApiResponse<CdssDtos.CriticalValueView> report(
            @Valid @RequestBody CdssDtos.ReportCriticalValueRequest request) {
        return ApiResponse.ok(criticalValueService.report(request, CurrentUser.require()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "危急值详情")
    public ApiResponse<CdssDtos.CriticalValueView> get(@PathVariable Long id) {
        return ApiResponse.ok(criticalValueService.get(id));
    }

    @GetMapping
    @Operation(summary = "危急值列表")
    public ApiResponse<List<CdssDtos.CriticalValueView>> list(
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(criticalValueService.list(patientId, status, limit));
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "医生确认收到危急值")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','QC')")
    @Audited(action = "CRITICAL_VALUE_CONFIRM", resourceType = "CDSS_CRITICAL_VALUE")
    public ApiResponse<CdssDtos.CriticalValueView> confirm(@PathVariable Long id) {
        return ApiResponse.ok(criticalValueService.confirm(id, CurrentUser.require()));
    }

    @PostMapping("/{id}/close")
    @Operation(summary = "记录处理措施并关闭")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','QC')")
    @Audited(action = "CRITICAL_VALUE_CLOSE", resourceType = "CDSS_CRITICAL_VALUE")
    public ApiResponse<CdssDtos.CriticalValueView> close(
            @PathVariable Long id,
            @Valid @RequestBody CdssDtos.CloseCriticalValueRequest request) {
        return ApiResponse.ok(criticalValueService.close(id, request.handlingNote(), CurrentUser.require()));
    }
}

package com.his.cdss.controller;

import com.his.audit.Audited;
import com.his.cdss.dto.CdssDtos;
import com.his.cdss.service.CdssService;
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

/** E4-01/02 药品知识库与合理用药审查；E4-07 提醒忽略留痕。 */
@RestController
@RequestMapping("/api/e4")
public class CdssController {

    private final CdssService cdssService;

    public CdssController(CdssService cdssService) {
        this.cdssService = cdssService;
    }

    @GetMapping("/drugs")
    @Operation(summary = "检索药品知识库")
    public ApiResponse<List<CdssDtos.DrugView>> drugs(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(cdssService.searchDrugs(keyword, limit));
    }

    @PostMapping("/prescriptions/review")
    @Operation(summary = "开立前合理用药审查")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','QC')")
    @Audited(action = "CDSS_REVIEW", resourceType = "PRESCRIPTION")
    public ApiResponse<CdssDtos.ReviewResult> review(
            @Valid @RequestBody CdssDtos.ReviewRequest request) {
        return ApiResponse.ok(cdssService.review(request, CurrentUser.require()));
    }

    @GetMapping("/alerts")
    @Operation(summary = "查询提醒记录")
    public ApiResponse<List<CdssDtos.AlertView>> alerts(
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) String bizNo,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(cdssService.listAlerts(patientId, bizNo, limit));
    }

    @PostMapping("/alerts/{id}/ignore")
    @Operation(summary = "忽略提醒并填写理由")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','QC')")
    @Audited(action = "CDSS_ALERT_IGNORE", resourceType = "CDSS_ALERT")
    public ApiResponse<CdssDtos.AlertView> ignore(
            @PathVariable Long id,
            @Valid @RequestBody CdssDtos.IgnoreRequest request) {
        return ApiResponse.ok(cdssService.ignoreAlert(id, request.reason(), CurrentUser.require()));
    }
}

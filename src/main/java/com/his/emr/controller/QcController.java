package com.his.emr.controller;

import com.his.audit.Audited;
import com.his.common.ApiResponse;
import com.his.emr.dto.QcDtos;
import com.his.emr.service.QcService;
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

@RestController
@RequestMapping("/api/emr/qc")
public class QcController {

    private final QcService qcService;

    public QcController(QcService qcService) {
        this.qcService = qcService;
    }

    @GetMapping("/rules")
    @Operation(summary = "质控规则清单")
    @PreAuthorize("hasAnyRole('ADMIN','QC')")
    public ApiResponse<List<QcDtos.QcRuleView>> rules() {
        return ApiResponse.ok(qcService.listRules());
    }

    @GetMapping("/issues")
    @Operation(summary = "质控缺陷清单")
    public ApiResponse<List<QcDtos.QcIssueView>> issues(
            @RequestParam(required = false) Long docId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(qcService.listIssues(docId, status, limit, CurrentUser.require()));
    }

    @PostMapping("/issues/{issueId}/close")
    @Operation(summary = "整改并关闭缺陷")
    @Audited(action = "QC_CLOSE_ISSUE", resourceType = "EMR_QC_ISSUE")
    public ApiResponse<QcDtos.QcIssueView> close(
            @PathVariable Long issueId,
            @Valid @RequestBody QcDtos.CloseIssueRequest request) {
        return ApiResponse.ok(qcService.close(issueId, request, CurrentUser.require()));
    }
}

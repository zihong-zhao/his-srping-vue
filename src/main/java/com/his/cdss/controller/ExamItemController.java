package com.his.cdss.controller;

import com.his.audit.Audited;
import com.his.cdss.dto.ExamItemDtos;
import com.his.cdss.service.ExamItemService;
import com.his.common.ApiResponse;
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

/** E4-03 检验检查知识库：项目、适应症与危急值阈值。 */
@RestController
@RequestMapping("/api/e4/exam-items")
public class ExamItemController {

    private final ExamItemService examItemService;

    public ExamItemController(ExamItemService examItemService) {
        this.examItemService = examItemService;
    }

    @GetMapping
    @Operation(summary = "检索检验检查知识库")
    public ApiResponse<List<ExamItemDtos.ExamItemView>> list(
            @RequestParam(required = false) String itemType,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(examItemService.list(itemType, keyword, limit));
    }

    @GetMapping("/{itemCode}")
    @Operation(summary = "检验检查项目详情")
    public ApiResponse<ExamItemDtos.ExamItemView> detail(@PathVariable String itemCode) {
        return ApiResponse.ok(examItemService.get(itemCode));
    }

    @PostMapping("/indication-check")
    @Operation(summary = "按诊断校验检查适应症")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','QC')")
    @Audited(action = "EXAM_INDICATION_CHECK", resourceType = "CDSS_EXAM_ITEM")
    public ApiResponse<ExamItemDtos.IndicationCheckResult> indicationCheck(
            @Valid @RequestBody ExamItemDtos.IndicationCheckRequest request) {
        return ApiResponse.ok(examItemService.checkIndication(request));
    }

    @PostMapping("/critical-check")
    @Operation(summary = "按知识库阈值判定危急值")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','QC')")
    @Audited(action = "EXAM_CRITICAL_CHECK", resourceType = "CDSS_EXAM_ITEM")
    public ApiResponse<ExamItemDtos.CriticalCheckResult> criticalCheck(
            @Valid @RequestBody ExamItemDtos.CriticalCheckRequest request) {
        return ApiResponse.ok(examItemService.checkCritical(request));
    }
}

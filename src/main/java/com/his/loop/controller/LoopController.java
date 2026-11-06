package com.his.loop.controller;

import com.his.audit.Audited;
import com.his.common.ApiResponse;
import com.his.loop.dto.LoopDtos;
import com.his.loop.service.LoopService;
import com.his.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** E5 闭环管理：医嘱、处方、检验、检查四条闭环与闭环率。 */
@RestController
@RequestMapping("/api/e5/loops")
public class LoopController {

    private final LoopService loopService;

    public LoopController(LoopService loopService) {
        this.loopService = loopService;
    }

    @PostMapping
    @Operation(summary = "开立闭环单据")
    @Audited(action = "LOOP_CREATE", resourceType = "LOOP_INSTANCE")
    public ApiResponse<LoopDtos.LoopView> create(@Valid @RequestBody LoopDtos.CreateLoopRequest request) {
        return ApiResponse.ok(loopService.create(request, CurrentUser.require()));
    }

    @PostMapping("/{id}/advance")
    @Operation(summary = "推进到下一节点")
    @Audited(action = "LOOP_ADVANCE", resourceType = "LOOP_INSTANCE")
    public ApiResponse<LoopDtos.LoopView> advance(
            @PathVariable Long id,
            @Valid @RequestBody LoopDtos.AdvanceRequest request) {
        return ApiResponse.ok(loopService.advance(id, request, CurrentUser.require()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "闭环单据详情")
    public ApiResponse<LoopDtos.LoopView> get(@PathVariable Long id) {
        return ApiResponse.ok(loopService.get(id));
    }

    @GetMapping
    @Operation(summary = "闭环单据列表")
    public ApiResponse<List<LoopDtos.LoopSummary>> list(
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) Long visitId,
            @RequestParam(required = false) String loopType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(loopService.list(patientId, visitId, loopType, status, limit));
    }

    @GetMapping("/stats")
    @Operation(summary = "四条闭环闭环率统计")
    @PreAuthorize("hasAnyRole('ADMIN','QC')")
    public ApiResponse<List<LoopDtos.LoopStats>> stats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(loopService.stats(from, to));
    }
}

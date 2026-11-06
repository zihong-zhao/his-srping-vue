package com.his.cdss.controller;

import com.his.audit.Audited;
import com.his.cdss.dto.PathwayDtos;
import com.his.cdss.service.PathwayService;
import com.his.common.ApiResponse;
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

/** E4-05 临床路径：模板、入径、节点执行、变异、退出与执行率。 */
@RestController
@RequestMapping("/api/e4/pathways")
public class PathwayController {

    private final PathwayService pathwayService;

    public PathwayController(PathwayService pathwayService) {
        this.pathwayService = pathwayService;
    }

    @GetMapping
    @Operation(summary = "临床路径模板列表")
    public ApiResponse<List<PathwayDtos.PathwayView>> list() {
        return ApiResponse.ok(pathwayService.listPathways());
    }

    @GetMapping("/stats")
    @Operation(summary = "临床路径执行率统计")
    @PreAuthorize("hasAnyRole('ADMIN','QC')")
    public ApiResponse<List<PathwayDtos.PathwayStats>> stats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(pathwayService.stats(from, to));
    }

    @GetMapping("/instances")
    @Operation(summary = "入径实例列表")
    public ApiResponse<List<PathwayDtos.InstanceView>> instances(
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) Long pathwayId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(pathwayService.listInstances(patientId, pathwayId, status, limit));
    }

    @GetMapping("/instances/{id}")
    @Operation(summary = "入径实例详情")
    public ApiResponse<PathwayDtos.InstanceView> instance(@PathVariable Long id) {
        return ApiResponse.ok(pathwayService.getInstance(id));
    }

    @GetMapping("/{id}")
    @Operation(summary = "临床路径详情")
    public ApiResponse<PathwayDtos.PathwayView> detail(@PathVariable Long id) {
        return ApiResponse.ok(pathwayService.getPathway(id));
    }

    @PostMapping("/{id}/enroll")
    @Operation(summary = "患者入径")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','QC')")
    @Audited(action = "PATHWAY_ENROLL", resourceType = "CLINICAL_PATHWAY")
    public ApiResponse<PathwayDtos.InstanceView> enroll(
            @PathVariable Long id,
            @Valid @RequestBody PathwayDtos.EnrollRequest request) {
        return ApiResponse.ok(pathwayService.enroll(id, request, CurrentUser.require()));
    }

    @PostMapping("/instances/{id}/nodes/{nodeId}/complete")
    @Operation(summary = "完成路径节点（严格按序）")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','QC')")
    @Audited(action = "PATHWAY_NODE_COMPLETE", resourceType = "PATHWAY_INSTANCE")
    public ApiResponse<PathwayDtos.InstanceView> completeNode(
            @PathVariable Long id,
            @PathVariable Long nodeId,
            @RequestBody(required = false) PathwayDtos.CompleteNodeRequest request) {
        String note = request == null ? null : request.note();
        return ApiResponse.ok(pathwayService.completeNode(id, nodeId, note, CurrentUser.require()));
    }

    @PostMapping("/instances/{id}/variances")
    @Operation(summary = "记录路径变异")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','QC')")
    @Audited(action = "PATHWAY_VARIANCE", resourceType = "PATHWAY_INSTANCE")
    public ApiResponse<PathwayDtos.InstanceView> variance(
            @PathVariable Long id,
            @Valid @RequestBody PathwayDtos.VarianceRequest request) {
        return ApiResponse.ok(pathwayService.addVariance(id, request, CurrentUser.require()));
    }

    @PostMapping("/instances/{id}/exit")
    @Operation(summary = "退出路径")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','QC')")
    @Audited(action = "PATHWAY_EXIT", resourceType = "PATHWAY_INSTANCE")
    public ApiResponse<PathwayDtos.InstanceView> exit(
            @PathVariable Long id,
            @Valid @RequestBody PathwayDtos.ExitRequest request) {
        return ApiResponse.ok(pathwayService.exit(id, request.reason(), CurrentUser.require()));
    }
}

package com.his.emr.controller;

import com.his.audit.Audited;
import com.his.common.ApiResponse;
import com.his.emr.dto.ArchiveDtos;
import com.his.emr.service.ArchiveCirculationService;
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

/** E7-04 病案借阅与复印。 */
@RestController
@RequestMapping("/api/emr")
public class ArchiveCirculationController {

    private final ArchiveCirculationService circulationService;

    public ArchiveCirculationController(ArchiveCirculationService circulationService) {
        this.circulationService = circulationService;
    }

    @PostMapping("/borrow-requests")
    @Operation(summary = "提交病案借阅申请")
    @Audited(action = "BORROW_CREATE", resourceType = "EMR_BORROW_REQUEST")
    public ApiResponse<ArchiveDtos.BorrowView> createBorrow(
            @Valid @RequestBody ArchiveDtos.BorrowCreateRequest request) {
        return ApiResponse.ok(circulationService.createBorrow(request, CurrentUser.require()));
    }

    @GetMapping("/borrow-requests")
    @Operation(summary = "借阅申请列表")
    public ApiResponse<List<ArchiveDtos.BorrowView>> borrows(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(circulationService.listBorrows(status, limit, CurrentUser.require()));
    }

    @PostMapping("/borrow-requests/{id}/approve")
    @Operation(summary = "审批通过借阅申请")
    @PreAuthorize("hasAnyRole('ADMIN','QC')")
    @Audited(action = "BORROW_APPROVE", resourceType = "EMR_BORROW_REQUEST")
    public ApiResponse<ArchiveDtos.BorrowView> approveBorrow(
            @PathVariable Long id,
            @RequestBody(required = false) ArchiveDtos.DecisionRequest request) {
        return ApiResponse.ok(circulationService.approveBorrow(id, request, CurrentUser.require()));
    }

    @PostMapping("/borrow-requests/{id}/reject")
    @Operation(summary = "驳回借阅申请")
    @PreAuthorize("hasAnyRole('ADMIN','QC')")
    @Audited(action = "BORROW_REJECT", resourceType = "EMR_BORROW_REQUEST")
    public ApiResponse<ArchiveDtos.BorrowView> rejectBorrow(
            @PathVariable Long id,
            @Valid @RequestBody ArchiveDtos.RejectRequest request) {
        return ApiResponse.ok(circulationService.rejectBorrow(id, request, CurrentUser.require()));
    }

    @PostMapping("/borrow-requests/{id}/return")
    @Operation(summary = "归还借阅病案")
    @Audited(action = "BORROW_RETURN", resourceType = "EMR_BORROW_REQUEST")
    public ApiResponse<ArchiveDtos.BorrowView> returnBorrow(@PathVariable Long id) {
        return ApiResponse.ok(circulationService.returnBorrow(id, CurrentUser.require()));
    }

    @PostMapping("/copy-requests")
    @Operation(summary = "提交病案复印申请")
    @Audited(action = "COPY_CREATE", resourceType = "EMR_COPY_REQUEST")
    public ApiResponse<ArchiveDtos.CopyView> createCopy(
            @Valid @RequestBody ArchiveDtos.CopyCreateRequest request) {
        return ApiResponse.ok(circulationService.createCopy(request, CurrentUser.require()));
    }

    @GetMapping("/copy-requests")
    @Operation(summary = "复印申请列表")
    public ApiResponse<List<ArchiveDtos.CopyView>> copies(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(circulationService.listCopies(status, limit, CurrentUser.require()));
    }

    @PostMapping("/copy-requests/{id}/approve")
    @Operation(summary = "审批通过复印申请")
    @PreAuthorize("hasAnyRole('ADMIN','QC')")
    @Audited(action = "COPY_APPROVE", resourceType = "EMR_COPY_REQUEST")
    public ApiResponse<ArchiveDtos.CopyView> approveCopy(
            @PathVariable Long id,
            @RequestBody(required = false) ArchiveDtos.DecisionRequest request) {
        return ApiResponse.ok(circulationService.approveCopy(id, request, CurrentUser.require()));
    }

    @PostMapping("/copy-requests/{id}/reject")
    @Operation(summary = "驳回复印申请")
    @PreAuthorize("hasAnyRole('ADMIN','QC')")
    @Audited(action = "COPY_REJECT", resourceType = "EMR_COPY_REQUEST")
    public ApiResponse<ArchiveDtos.CopyView> rejectCopy(
            @PathVariable Long id,
            @Valid @RequestBody ArchiveDtos.RejectRequest request) {
        return ApiResponse.ok(circulationService.rejectCopy(id, request, CurrentUser.require()));
    }

    @PostMapping("/copy-requests/{id}/complete")
    @Operation(summary = "完成复印并交付")
    @PreAuthorize("hasAnyRole('ADMIN','QC')")
    @Audited(action = "COPY_COMPLETE", resourceType = "EMR_COPY_REQUEST")
    public ApiResponse<ArchiveDtos.CopyView> completeCopy(@PathVariable Long id) {
        return ApiResponse.ok(circulationService.completeCopy(id, CurrentUser.require()));
    }
}

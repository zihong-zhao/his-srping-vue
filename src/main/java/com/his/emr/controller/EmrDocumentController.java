package com.his.emr.controller;

import com.his.audit.Audited;
import com.his.common.ApiResponse;
import com.his.emr.dto.EmrDtos;
import com.his.emr.dto.QcDtos;
import com.his.emr.service.EmrDocumentService;
import com.his.emr.service.QcService;
import com.his.security.CurrentUser;
import com.his.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/emr/documents")
public class EmrDocumentController {

    private final EmrDocumentService documentService;
    private final QcService qcService;

    public EmrDocumentController(EmrDocumentService documentService, QcService qcService) {
        this.documentService = documentService;
        this.qcService = qcService;
    }

    @PostMapping
    @Audited(action = "CREATE_DOCUMENT", resourceType = "EMR_DOCUMENT")
    public ApiResponse<EmrDtos.DocumentView> create(
            @Valid @RequestBody EmrDtos.CreateDocumentRequest request) {
        return ApiResponse.ok(documentService.create(request, CurrentUser.require()));
    }

    @GetMapping
    public ApiResponse<List<EmrDtos.DocumentSummary>> list(
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) Long visitId,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(documentService.list(patientId, visitId, status, CurrentUser.require()));
    }

    @GetMapping("/{id}")
    public ApiResponse<EmrDtos.DocumentView> get(
            @PathVariable Long id,
            @RequestParam(required = false) String reason) {
        return ApiResponse.ok(documentService.get(id, reason, CurrentUser.require()));
    }

    @PutMapping("/{id}")
    @Audited(action = "SAVE_DOCUMENT", resourceType = "EMR_DOCUMENT")
    public ApiResponse<EmrDtos.DocumentView> save(
            @PathVariable Long id,
            @Valid @RequestBody EmrDtos.SaveDocumentRequest request) {
        return ApiResponse.ok(documentService.save(id, request, CurrentUser.require()));
    }

    @PostMapping("/{id}/submit")
    @Audited(action = "SUBMIT_DOCUMENT", resourceType = "EMR_DOCUMENT")
    public ApiResponse<EmrDtos.DocumentView> submit(@PathVariable Long id) {
        return ApiResponse.ok(documentService.submit(id, CurrentUser.require()));
    }

    @PostMapping("/{id}/sign")
    @Audited(action = "SIGN_DOCUMENT", resourceType = "EMR_DOCUMENT")
    public ApiResponse<EmrDtos.DocumentView> sign(@PathVariable Long id) {
        return ApiResponse.ok(documentService.sign(id, CurrentUser.require()));
    }

    @PostMapping("/{id}/archive")
    @Audited(action = "ARCHIVE_DOCUMENT", resourceType = "EMR_DOCUMENT")
    public ApiResponse<EmrDtos.DocumentView> archive(@PathVariable Long id) {
        return ApiResponse.ok(documentService.archive(id, CurrentUser.require()));
    }

    @PostMapping("/{id}/seal")
    @Audited(action = "SEAL_DOCUMENT", resourceType = "EMR_DOCUMENT")
    public ApiResponse<EmrDtos.DocumentView> seal(
            @PathVariable Long id,
            @Valid @RequestBody EmrDtos.SealRequest request) {
        return ApiResponse.ok(documentService.seal(id, request, CurrentUser.require()));
    }

    @PostMapping("/{id}/unseal")
    @Audited(action = "UNSEAL_DOCUMENT", resourceType = "EMR_DOCUMENT")
    public ApiResponse<EmrDtos.DocumentView> unseal(
            @PathVariable Long id,
            @Valid @RequestBody EmrDtos.SealRequest request) {
        return ApiResponse.ok(documentService.unseal(id, request, CurrentUser.require()));
    }

    @GetMapping("/{id}/versions")
    public ApiResponse<List<EmrDtos.VersionView>> versions(@PathVariable Long id) {
        return ApiResponse.ok(documentService.versions(id, CurrentUser.require()));
    }

    @GetMapping("/{id}/versions/diff")
    public ApiResponse<EmrDtos.DiffResult> diff(
            @PathVariable Long id,
            @RequestParam Integer from,
            @RequestParam Integer to) {
        return ApiResponse.ok(documentService.diff(id, from, to, CurrentUser.require()));
    }

    @PostMapping("/{id}/qc/run")
    @Audited(action = "QC_RUN", resourceType = "EMR_DOCUMENT")
    public ApiResponse<QcDtos.QcRunResult> runQc(@PathVariable Long id) {
        return ApiResponse.ok(qcService.run(id, CurrentUser.require()));
    }

    @GetMapping("/{id}/verify")
    public ApiResponse<EmrDtos.VerifyResult> verify(
            @PathVariable Long id,
            @RequestParam(required = false) Integer versionNo) {
        return ApiResponse.ok(documentService.verify(id, versionNo, CurrentUser.require()));
    }
}

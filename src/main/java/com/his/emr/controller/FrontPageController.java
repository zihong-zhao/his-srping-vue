package com.his.emr.controller;

import com.his.audit.Audited;
import com.his.common.ApiResponse;
import com.his.emr.dto.FrontPageDtos;
import com.his.emr.service.FrontPageService;
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

/** E7-01 病案首页与 E7-05 ICD 编码核对。 */
@RestController
@RequestMapping("/api/emr")
public class FrontPageController {

    private final FrontPageService frontPageService;

    public FrontPageController(FrontPageService frontPageService) {
        this.frontPageService = frontPageService;
    }

    @GetMapping("/icd/codes")
    @Operation(summary = "检索 ICD 编码字典")
    public ApiResponse<List<FrontPageDtos.IcdCodeView>> icdCodes(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(frontPageService.searchIcd(type, keyword, limit));
    }

    @PostMapping("/front-pages")
    @Operation(summary = "保存病案首页（按就诊 ID 新增或更新）")
    @PreAuthorize("hasAnyRole('ADMIN','QC')")
    @Audited(action = "FRONT_PAGE_SAVE", resourceType = "EMR_FRONT_PAGE")
    public ApiResponse<FrontPageDtos.FrontPageView> save(
            @Valid @RequestBody FrontPageDtos.SaveFrontPageRequest request) {
        return ApiResponse.ok(frontPageService.save(request, CurrentUser.require()));
    }

    @GetMapping("/front-pages/{id}")
    @Operation(summary = "病案首页详情")
    public ApiResponse<FrontPageDtos.FrontPageView> get(@PathVariable Long id) {
        return ApiResponse.ok(frontPageService.get(id));
    }

    @GetMapping("/front-pages")
    @Operation(summary = "病案首页列表")
    public ApiResponse<List<FrontPageDtos.FrontPageView>> list(
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) Long visitId,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(frontPageService.list(patientId, visitId, limit));
    }

    @PostMapping("/front-pages/{id}/verify")
    @Operation(summary = "ICD 编码核对并置为已核对")
    @PreAuthorize("hasAnyRole('ADMIN','QC')")
    @Audited(action = "FRONT_PAGE_VERIFY", resourceType = "EMR_FRONT_PAGE")
    public ApiResponse<FrontPageDtos.VerifyResult> verify(@PathVariable Long id) {
        return ApiResponse.ok(frontPageService.verify(id, CurrentUser.require()));
    }
}

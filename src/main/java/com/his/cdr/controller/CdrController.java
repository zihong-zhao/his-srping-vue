package com.his.cdr.controller;

import com.his.audit.Audited;
import com.his.cdr.dto.CdrDtos;
import com.his.cdr.service.CdrService;
import com.his.common.ApiResponse;
import com.his.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cdr/patients")
public class CdrController {

    private final CdrService cdrService;

    public CdrController(CdrService cdrService) {
        this.cdrService = cdrService;
    }

    @GetMapping("/{patientId}/timeline")
    @Operation(summary = "患者全流程时间轴（E3 最小实现）")
    @Audited(action = "CDR_VIEW", resourceType = "PATIENT")
    public ApiResponse<CdrDtos.PatientTimeline> timeline(@PathVariable Long patientId) {
        return ApiResponse.ok(cdrService.timeline(patientId, CurrentUser.require()));
    }
}

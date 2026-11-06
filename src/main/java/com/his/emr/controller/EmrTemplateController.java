package com.his.emr.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.his.common.ApiResponse;
import com.his.emr.entity.EmrTemplate;
import com.his.emr.mapper.EmrTemplateMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/emr/templates")
public class EmrTemplateController {

    private final EmrTemplateMapper templateMapper;

    public EmrTemplateController(EmrTemplateMapper templateMapper) {
        this.templateMapper = templateMapper;
    }

    @GetMapping
    public ApiResponse<List<EmrTemplate>> list(
            @RequestParam(required = false) String docType) {
        return ApiResponse.ok(templateMapper.selectList(
                Wrappers.<EmrTemplate>lambdaQuery()
                        .eq(EmrTemplate::getEnabled, true)
                        .eq(docType != null && !docType.isBlank(), EmrTemplate::getDocType, docType)
                        .orderByAsc(EmrTemplate::getId)));
    }
}

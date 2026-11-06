package com.his.emr.qc;

import com.his.emr.entity.EmrQcRule;

import java.util.List;

/**
 * 单类质控规则的校验器。
 * 规则表只负责开关与严重级别，具体判定逻辑由 Java 校验器实现（不引入规则 DSL）。
 */
public interface QcChecker {

    boolean supports(EmrQcRule rule);

    List<String> check(EmrQcRule rule, QcCheckContext context);
}

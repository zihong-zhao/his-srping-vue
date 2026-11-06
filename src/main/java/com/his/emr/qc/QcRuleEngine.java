package com.his.emr.qc;

import com.his.emr.entity.EmrQcRule;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** 按规则表顺序执行校验器，汇总缺陷。 */
@Component
public class QcRuleEngine {

    private final List<QcChecker> checkers;

    public QcRuleEngine(List<QcChecker> checkers) {
        this.checkers = checkers;
    }

    public List<Defect> evaluate(List<EmrQcRule> rules, QcCheckContext context) {
        List<Defect> defects = new ArrayList<>();
        for (EmrQcRule rule : rules) {
            QcChecker checker = checkers.stream()
                    .filter(candidate -> candidate.supports(rule))
                    .findFirst()
                    .orElse(null);
            if (checker == null) {
                continue;
            }
            for (String desc : checker.check(rule, context)) {
                defects.add(new Defect(rule, desc));
            }
        }
        return defects;
    }

    public record Defect(EmrQcRule rule, String desc) {
    }
}

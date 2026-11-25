package com.campusplan.dto;

import java.math.BigDecimal;

/** 졸업 규칙 하나의 진단 결과. 값은 DB View GraduationCheck 결과 그대로입니다. */
public class GraduationRuleResult {

    private final int ruleId;
    private final String ruleName;
    private final String ruleType;
    private final BigDecimal currentValue;
    private final BigDecimal requiredValue;
    private final boolean isMet;
    private final boolean isVerified;

    public GraduationRuleResult(int ruleId, String ruleName, String ruleType, BigDecimal currentValue,
            BigDecimal requiredValue, boolean isMet, boolean isVerified) {
        this.ruleId = ruleId;
        this.ruleName = ruleName;
        this.ruleType = ruleType;
        this.currentValue = currentValue;
        this.requiredValue = requiredValue;
        this.isMet = isMet;
        this.isVerified = isVerified;
    }

    public boolean isMet() {
        return isMet;
    }
}

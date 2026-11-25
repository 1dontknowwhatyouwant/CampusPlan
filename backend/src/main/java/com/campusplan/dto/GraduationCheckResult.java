package com.campusplan.dto;

import java.util.List;

/**
 * GET /api/me/graduation-check 응답.
 * allMet 은 rules 의 isMet 을 모은 값이며 새 판정 기준이 아닙니다. 규칙이 없으면 판정할 수 없으므로 null 입니다.
 */
public class GraduationCheckResult {

    private final int studentId;
    private final List<GraduationRuleResult> rules;
    private final Boolean allMet;

    public GraduationCheckResult(int studentId, List<GraduationRuleResult> rules) {
        this.studentId = studentId;
        this.rules = rules;
        this.allMet = rules.isEmpty() ? null : rules.stream().allMatch(GraduationRuleResult::isMet);
    }
}

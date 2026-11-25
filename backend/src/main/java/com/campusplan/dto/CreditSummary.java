package com.campusplan.dto;

/** GET /api/me/credits 응답. 값은 DB View CreditSummary 결과 그대로입니다. */
public class CreditSummary {

    private final int studentId;
    private final int totalCredits;
    private final int majorCredits;
    private final int generalCredits;

    public CreditSummary(int studentId, int totalCredits, int majorCredits, int generalCredits) {
        this.studentId = studentId;
        this.totalCredits = totalCredits;
        this.majorCredits = majorCredits;
        this.generalCredits = generalCredits;
    }
}

package com.campusplan.dto;

import java.math.BigDecimal;

/** POST /api/admin/students/{studentId}/volunteers/approve-all 응답 (프로시저 ApproveAllPendingVolunteer 의 OUT 값) */
public class ApproveAllResult {

    private final int approvedCount;
    private final BigDecimal totalApprovedHours;

    public ApproveAllResult(int approvedCount, BigDecimal totalApprovedHours) {
        this.approvedCount = approvedCount;
        this.totalApprovedHours = totalApprovedHours;
    }
}

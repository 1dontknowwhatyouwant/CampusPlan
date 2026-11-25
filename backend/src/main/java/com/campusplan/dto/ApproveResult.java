package com.campusplan.dto;

/** POST /api/admin/volunteers/{recordId}/approve 응답 */
public class ApproveResult {

    private final int recordId;
    private final String status;

    public ApproveResult(int recordId, String status) {
        this.recordId = recordId;
        this.status = status;
    }
}

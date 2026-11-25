package com.campusplan.dto;

import java.math.BigDecimal;

/** 봉사 내역 한 건. activityDate 는 yyyy-MM-dd, approvedAt 은 yyyy-MM-ddTHH:mm:ss (승인 전 null) */
public class VolunteerItem {

    private final int recordId;
    private final String activityName;
    private final String activityDate;
    private final BigDecimal hours;
    private final String status;
    private final String approvedAt;

    public VolunteerItem(int recordId, String activityName, String activityDate, BigDecimal hours,
            String status, String approvedAt) {
        this.recordId = recordId;
        this.activityName = activityName;
        this.activityDate = activityDate;
        this.hours = hours;
        this.status = status;
        this.approvedAt = approvedAt;
    }
}

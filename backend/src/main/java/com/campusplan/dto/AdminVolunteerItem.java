package com.campusplan.dto;

import java.math.BigDecimal;

/** 관리자용 봉사 내역 한 건: VolunteerItem 에 학생 정보가 더해집니다. */
public class AdminVolunteerItem {

    private final int recordId;
    private final int studentId;
    private final String studentName;
    private final String activityName;
    private final String activityDate;
    private final BigDecimal hours;
    private final String status;
    private final String approvedAt;

    public AdminVolunteerItem(int recordId, int studentId, String studentName, String activityName,
            String activityDate, BigDecimal hours, String status, String approvedAt) {
        this.recordId = recordId;
        this.studentId = studentId;
        this.studentName = studentName;
        this.activityName = activityName;
        this.activityDate = activityDate;
        this.hours = hours;
        this.status = status;
        this.approvedAt = approvedAt;
    }
}

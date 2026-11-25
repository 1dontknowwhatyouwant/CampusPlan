package com.campusplan.dto;

/**
 * GET /api/me/dashboard 응답. 각 항목은 해당 조회 API 의 응답과 같으며 별도 계산은 없습니다.
 * 재학 중인 학기가 없으면 timetable 은 null 입니다.
 */
public class Dashboard {

    private final StudentInfo student;
    private final Timetable timetable;
    private final CreditSummary credits;
    private final GraduationCheckResult graduationCheck;

    public Dashboard(StudentInfo student, Timetable timetable, CreditSummary credits,
            GraduationCheckResult graduationCheck) {
        this.student = student;
        this.timetable = timetable;
        this.credits = credits;
        this.graduationCheck = graduationCheck;
    }
}

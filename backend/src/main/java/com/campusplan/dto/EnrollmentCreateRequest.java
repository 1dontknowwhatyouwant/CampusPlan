package com.campusplan.dto;

/** POST /api/me/enrollments 요청 본문. completed 는 트리거가 정하므로 받지 않습니다. */
public class EnrollmentCreateRequest {

    public Integer semesterId;
    public Integer courseId;
    public String grade;
}

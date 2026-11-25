package com.campusplan.dto;

/** 수강 이력 한 건. completed 는 트리거가 grade 로부터 계산한 DB 값입니다. */
public class EnrollmentItem {

    private final int enrollmentId;
    private final int semesterId;
    private final int year;
    private final int term;
    private final int courseId;
    private final String courseCode;
    private final String courseName;
    private final int credits;
    private final String category;
    private final String grade;
    private final boolean completed;

    public EnrollmentItem(int enrollmentId, int semesterId, int year, int term, int courseId, String courseCode,
            String courseName, int credits, String category, String grade, boolean completed) {
        this.enrollmentId = enrollmentId;
        this.semesterId = semesterId;
        this.year = year;
        this.term = term;
        this.courseId = courseId;
        this.courseCode = courseCode;
        this.courseName = courseName;
        this.credits = credits;
        this.category = category;
        this.grade = grade;
        this.completed = completed;
    }
}

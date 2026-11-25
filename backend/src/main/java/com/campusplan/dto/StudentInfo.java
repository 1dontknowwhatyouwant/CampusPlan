package com.campusplan.dto;

/** GET /api/me 응답: 학생 기본 정보와 소속 */
public class StudentInfo {

    private final int studentId;
    private final String name;
    private final int admissionYear;
    private final String college;
    private final String departmentName;

    public StudentInfo(int studentId, String name, int admissionYear, String college, String departmentName) {
        this.studentId = studentId;
        this.name = name;
        this.admissionYear = admissionYear;
        this.college = college;
        this.departmentName = departmentName;
    }
}

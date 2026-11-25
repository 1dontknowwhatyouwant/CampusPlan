package com.campusplan.dto;

public class CourseItem {

    private final int courseId;
    private final String code;
    private final String name;
    private final int credits;
    private final String category;

    public CourseItem(int courseId, String code, String name, int credits, String category) {
        this.courseId = courseId;
        this.code = code;
        this.name = name;
        this.credits = credits;
        this.category = category;
    }
}

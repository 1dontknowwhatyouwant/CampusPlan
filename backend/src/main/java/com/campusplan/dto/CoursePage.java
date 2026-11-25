package com.campusplan.dto;

import java.util.List;

/** GET /api/courses 응답 */
public class CoursePage {

    private final List<CourseItem> items;
    private final int page;
    private final int size;
    private final long totalCount;

    public CoursePage(List<CourseItem> items, int page, int size, long totalCount) {
        this.items = items;
        this.page = page;
        this.size = size;
        this.totalCount = totalCount;
    }
}

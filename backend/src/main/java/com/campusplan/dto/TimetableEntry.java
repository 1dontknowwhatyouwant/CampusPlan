package com.campusplan.dto;

import java.util.ArrayList;
import java.util.List;

/** 시간표의 수강 과목 한 건과 그 과목의 요일·교시 칸들 */
public class TimetableEntry {

    private final int enrollmentId;
    private final int courseId;
    private final String courseName;
    private final int credits;
    private final List<TimetableSlot> slots = new ArrayList<>();

    public TimetableEntry(int enrollmentId, int courseId, String courseName, int credits) {
        this.enrollmentId = enrollmentId;
        this.courseId = courseId;
        this.courseName = courseName;
        this.credits = credits;
    }

    public void addSlot(TimetableSlot slot) {
        slots.add(slot);
    }
}

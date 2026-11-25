package com.campusplan.dto;

import java.util.List;

/** GET /api/me/timetable 응답 */
public class Timetable {

    private final SemesterItem semester;
    private final List<TimetableEntry> entries;

    public Timetable(SemesterItem semester, List<TimetableEntry> entries) {
        this.semester = semester;
        this.entries = entries;
    }
}

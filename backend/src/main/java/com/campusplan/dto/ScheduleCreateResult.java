package com.campusplan.dto;

import java.util.List;

/** POST /api/enrollments/{enrollmentId}/schedules 응답: 생성된 시간표 칸 */
public class ScheduleCreateResult {

    private final List<TimetableSlot> slots;

    public ScheduleCreateResult(List<TimetableSlot> slots) {
        this.slots = slots;
    }
}

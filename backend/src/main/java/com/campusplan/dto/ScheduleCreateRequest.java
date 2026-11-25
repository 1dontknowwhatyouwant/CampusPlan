package com.campusplan.dto;

import java.util.List;

/** POST /api/enrollments/{enrollmentId}/schedules 요청 본문 */
public class ScheduleCreateRequest {

    public List<Slot> slots;

    public static class Slot {
        public String day;
        public Integer period;
        public String room;
    }
}

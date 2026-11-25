package com.campusplan.dto;

public class TimetableSlot {

    private final int scheduleId;
    private final String day;
    private final int period;
    private final String room;

    public TimetableSlot(int scheduleId, String day, int period, String room) {
        this.scheduleId = scheduleId;
        this.day = day;
        this.period = period;
        this.room = room;
    }
}

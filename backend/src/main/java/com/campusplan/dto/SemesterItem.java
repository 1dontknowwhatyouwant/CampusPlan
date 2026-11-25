package com.campusplan.dto;

public class SemesterItem {

    private final int semesterId;
    private final int year;
    private final int term;
    private final String status;

    public SemesterItem(int semesterId, int year, int term, String status) {
        this.semesterId = semesterId;
        this.year = year;
        this.term = term;
        this.status = status;
    }

    public int semesterId() {
        return semesterId;
    }
}

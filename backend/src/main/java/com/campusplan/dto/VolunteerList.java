package com.campusplan.dto;

import java.math.BigDecimal;
import java.util.List;

/** GET /api/me/volunteers 응답. approvedHours 는 View VolunteerSummary 값이며 status 필터와 관계없습니다. */
public class VolunteerList {

    private final BigDecimal approvedHours;
    private final List<VolunteerItem> records;

    public VolunteerList(BigDecimal approvedHours, List<VolunteerItem> records) {
        this.approvedHours = approvedHours;
        this.records = records;
    }
}

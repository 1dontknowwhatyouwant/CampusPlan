package com.campusplan.dto;

import java.math.BigDecimal;

/** POST /api/me/volunteers 요청 본문. 상태는 요청으로 받지 않고 항상 '대기'로 만듭니다. */
public class VolunteerCreateRequest {

    public String activityName;
    public String activityDate;
    public BigDecimal hours;
}

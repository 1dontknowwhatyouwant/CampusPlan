package com.campusplan.dto;

import java.io.Serializable;

/** 로그인한 계정 정보. 세션에 저장합니다. 비밀번호 해시는 담지 않습니다. */
public record AuthUser(int accountId, String loginId, String role, Integer studentId, String name)
        implements Serializable {

    public static final String STUDENT = "STUDENT";
    public static final String ADMIN = "ADMIN";
}

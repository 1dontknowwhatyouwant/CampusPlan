package com.campusplan.dto;

/** 로그인·내 정보 응답. 관리자 계정은 studentId, name 이 null 입니다. */
public class AuthResponse {

    private final String loginId;
    private final String role;
    private final Integer studentId;
    private final String name;

    public AuthResponse(AuthUser user) {
        this.loginId = user.loginId();
        this.role = user.role();
        this.studentId = user.studentId();
        this.name = user.name();
    }
}

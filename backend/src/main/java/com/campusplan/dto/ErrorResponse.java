package com.campusplan.dto;

/** API 명세의 오류 응답: {"error": {"code": "...", "message": "..."}} */
public class ErrorResponse {

    private final Error error;

    public ErrorResponse(String code, String message) {
        this.error = new Error(code, message);
    }

    private static class Error {
        private final String code;
        private final String message;

        Error(String code, String message) {
            this.code = code;
            this.message = message;
        }
    }
}

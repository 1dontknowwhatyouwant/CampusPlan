package com.campusplan.util;

/** HTTP 상태 코드와 오류 코드를 함께 전달하는 예외. ApiServlet 이 JSON 오류 응답으로 바꿉니다. */
public class ApiException extends RuntimeException {

    private final int status;
    private final String code;

    public ApiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public ApiException(int status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public static ApiException badRequest(String message) {
        return new ApiException(400, "INVALID_REQUEST", message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(404, "NOT_FOUND", message);
    }
}

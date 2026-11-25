package com.campusplan.servlet;

import com.campusplan.dto.ErrorResponse;
import com.campusplan.util.JsonUtil;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 서블릿이 매핑되지 않은 경로(404)와 허용되지 않은 메서드(405) 등 컨테이너가 만드는 오류를
 * Tomcat 기본 HTML 대신 API 명세의 JSON 오류 형식으로 바꿉니다. web.xml 의 error-page 가 이 서블릿으로 보냅니다.
 */
@WebServlet("/_error")
public class ErrorServlet extends HttpServlet {

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Object attribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = attribute instanceof Integer ? (Integer) attribute : 500;

        String code;
        String message;
        switch (status) {
            case 404 -> {
                code = "NOT_FOUND";
                message = "요청한 경로를 찾을 수 없습니다.";
            }
            case 405 -> {
                code = "METHOD_NOT_ALLOWED";
                message = "허용되지 않은 요청 방식입니다.";
            }
            case 400 -> {
                code = "INVALID_REQUEST";
                message = "잘못된 요청입니다.";
            }
            default -> {
                status = 500;
                code = "INTERNAL_ERROR";
                message = "서버 오류가 발생했습니다.";
            }
        }
        JsonUtil.write(response, status, new ErrorResponse(code, message));
    }
}

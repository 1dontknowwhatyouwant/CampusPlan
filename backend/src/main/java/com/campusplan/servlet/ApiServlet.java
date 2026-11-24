package com.campusplan.servlet;

import com.campusplan.dto.ErrorResponse;
import com.campusplan.util.ApiException;
import com.campusplan.util.JsonUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/** 모든 API 서블릿의 기반 클래스: 예외를 API 명세의 JSON 오류 응답으로 바꿉니다. */
public abstract class ApiServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(ApiServlet.class.getName());

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            super.service(request, response);
        } catch (ApiException e) {
            if (e.getStatus() >= 500) {
                LOG.log(Level.SEVERE, "API 오류", e);
            }
            JsonUtil.write(response, e.getStatus(), new ErrorResponse(e.getCode(), e.getMessage()));
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "처리되지 않은 오류", e);
            JsonUtil.write(response, 500, new ErrorResponse("INTERNAL_ERROR", "서버 오류가 발생했습니다."));
        }
    }
}

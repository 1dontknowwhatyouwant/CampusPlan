package com.campusplan.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** 프론트엔드(Next.js 개발 서버, http://localhost:3000)의 요청만 허용합니다. */
@WebFilter("/api/*")
public class CorsFilter implements Filter {

    private static final String ALLOWED_ORIGIN = "http://localhost:3000";

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        if (ALLOWED_ORIGIN.equals(request.getHeader("Origin"))) {
            response.setHeader("Access-Control-Allow-Origin", ALLOWED_ORIGIN);
            response.setHeader("Vary", "Origin");
            response.setHeader("Access-Control-Allow-Methods", "GET, POST, PATCH, DELETE, OPTIONS");
            response.setHeader("Access-Control-Allow-Headers", "Content-Type");
        }
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }
        chain.doFilter(req, res);
    }
}

package com.campusplan.config;

import com.campusplan.dto.ErrorResponse;
import com.campusplan.util.JsonUtil;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * 프론트엔드(Next.js 개발 서버, http://localhost:3000)의 요청만 쿠키 포함으로 허용합니다. (등록과 실행 순서는 web.xml)
 *
 * CSRF 방어: 상태를 바꾸는 요청(POST/PUT/PATCH/DELETE)에 Origin 헤더가 있고 허용 출처가 아니면 403 으로 거부합니다.
 * SameSite=Lax 쿠키는 다른 사이트의 요청만 막고, 같은 사이트로 취급되는 localhost 의 다른 포트 등에서는 쿠키가 전송되기 때문입니다.
 * Origin 헤더가 없는 요청(curl 등 브라우저가 아닌 클라이언트)은 통과시킵니다.
 */
public class CorsFilter implements Filter {

    private static final String ALLOWED_ORIGIN = "http://localhost:3000";
    private static final Set<String> UNSAFE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        String origin = request.getHeader("Origin");
        if (origin != null && !ALLOWED_ORIGIN.equals(origin)
                && UNSAFE_METHODS.contains(request.getMethod().toUpperCase(Locale.ROOT))) {
            JsonUtil.write(response, 403, new ErrorResponse("FORBIDDEN", "허용되지 않은 출처의 요청입니다."));
            return;
        }

        if (ALLOWED_ORIGIN.equals(request.getHeader("Origin"))) {
            response.setHeader("Access-Control-Allow-Origin", ALLOWED_ORIGIN);
            response.setHeader("Access-Control-Allow-Credentials", "true");
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

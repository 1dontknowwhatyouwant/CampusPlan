package com.campusplan.config;

import com.campusplan.dto.AuthUser;
import com.campusplan.dto.ErrorResponse;
import com.campusplan.util.JsonUtil;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Set;

/**
 * /api/* 요청의 로그인 여부와 역할을 검사합니다. (등록과 실행 순서는 web.xml, CorsFilter 다음)
 *
 * <ul>
 *   <li>로그인 불필요: GET /api/health, POST /api/auth/login</li>
 *   <li>STUDENT 전용: /api/me, /api/me/**, /api/enrollments, /api/enrollments/**, /api/schedules, /api/schedules/**</li>
 *   <li>ADMIN 전용: /api/admin/**</li>
 *   <li>그 외: 로그인한 모든 계정</li>
 * </ul>
 *
 * 수강·시간표 항목이 본인 것인지(소유권)는 각 API 가 확인합니다.
 */
public class AuthFilter implements Filter {

    public static final String SESSION_USER = "authUser";

    private static final Set<String> PUBLIC_PATHS = Set.of("/api/health", "/api/auth/login");

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        String path = request.getServletPath() + (request.getPathInfo() == null ? "" : request.getPathInfo());
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || PUBLIC_PATHS.contains(path)) {
            chain.doFilter(req, res);
            return;
        }

        AuthUser user = sessionUser(request);
        if (user == null) {
            JsonUtil.write(response, 401,
                    new ErrorResponse("UNAUTHENTICATED", "로그인이 필요합니다."));
            return;
        }

        String requiredRole = requiredRole(path);
        if (requiredRole != null && !requiredRole.equals(user.role())) {
            JsonUtil.write(response, 403,
                    new ErrorResponse("FORBIDDEN", "이 요청을 할 수 있는 권한이 없습니다."));
            return;
        }
        chain.doFilter(req, res);
    }

    /** 경로에 필요한 역할. null 이면 로그인만 하면 됩니다. */
    static String requiredRole(String path) {
        if (path.equals("/api/admin") || path.startsWith("/api/admin/")) {
            return AuthUser.ADMIN;
        }
        if (path.equals("/api/me") || path.startsWith("/api/me/")
                || path.equals("/api/enrollments") || path.startsWith("/api/enrollments/")
                || path.equals("/api/schedules") || path.startsWith("/api/schedules/")) {
            return AuthUser.STUDENT;
        }
        return null;
    }

    private static AuthUser sessionUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(SESSION_USER);
        return value instanceof AuthUser ? (AuthUser) value : null;
    }

    /** 필터를 통과한 요청에서 로그인한 계정을 꺼냅니다. */
    public static AuthUser currentUser(HttpServletRequest request) {
        AuthUser user = sessionUser(request);
        if (user == null) {
            throw new IllegalStateException("AuthFilter 를 거치지 않은 요청입니다.");
        }
        return user;
    }
}

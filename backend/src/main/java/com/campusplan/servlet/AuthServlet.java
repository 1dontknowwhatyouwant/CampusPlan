package com.campusplan.servlet;

import com.campusplan.dao.AccountDao;
import com.campusplan.dto.AuthResponse;
import com.campusplan.dto.AuthUser;
import com.campusplan.dto.LoginRequest;
import com.campusplan.config.AuthFilter;
import com.campusplan.util.ApiException;
import com.campusplan.util.JsonUtil;
import com.campusplan.util.PasswordHasher;
import com.campusplan.util.Validator;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;

/** /api/auth/login, /api/auth/logout, /api/auth/me */
@WebServlet("/api/auth/*")
public class AuthServlet extends ApiServlet {

    private static final int MAX_LOGIN_ID = 50;
    private static final int MAX_PASSWORD = 200;

    private final AccountDao accountDao = new AccountDao();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = request.getPathInfo();
        if ("/login".equals(path)) {
            login(request, response);
        } else if ("/logout".equals(path)) {
            logout(request, response);
        } else {
            throw ApiException.notFound("존재하지 않는 경로입니다.");
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!"/me".equals(request.getPathInfo())) {
            throw ApiException.notFound("존재하지 않는 경로입니다.");
        }
        AuthUser user = AuthFilter.currentUser(request);
        JsonUtil.write(response, 200, new AuthResponse(user));
    }

    private void login(HttpServletRequest request, HttpServletResponse response) throws IOException {
        LoginRequest body = JsonUtil.read(request, LoginRequest.class);
        String loginId = Validator.requireText(body.loginId, "loginId", MAX_LOGIN_ID);
        Validator.requireText(body.password, "password", MAX_PASSWORD);

        var credentials = accountDao.findByLoginId(loginId);
        boolean ok = credentials.isPresent()
                ? PasswordHasher.verify(credentials.get().passwordHash(), body.password)
                : PasswordHasher.verifyDummy(body.password);
        if (!ok) {
            // 아이디가 없는 경우와 비밀번호가 틀린 경우를 구분하지 않는다.
            throw new ApiException(401, "INVALID_CREDENTIALS", "아이디 또는 비밀번호가 올바르지 않습니다.");
        }

        // 세션 고정 공격을 막기 위해 기존 세션을 버리고 새 세션을 만든다.
        HttpSession old = request.getSession(false);
        if (old != null) {
            old.invalidate();
        }
        AuthUser user = credentials.get().user();
        request.getSession(true).setAttribute(AuthFilter.SESSION_USER, user);
        JsonUtil.write(response, 200, new AuthResponse(user));
    }

    private void logout(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        JsonUtil.write(response, 204, null);
    }
}

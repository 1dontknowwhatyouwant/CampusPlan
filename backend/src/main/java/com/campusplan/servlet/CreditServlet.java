package com.campusplan.servlet;

import com.campusplan.config.AuthFilter;
import com.campusplan.dao.GraduationDao;
import com.campusplan.dto.CreditSummary;
import com.campusplan.util.ApiException;
import com.campusplan.util.JsonUtil;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** GET /api/me/credits : 로그인한 학생의 학점 집계 (View CreditSummary) */
@WebServlet("/api/me/credits")
public class CreditServlet extends ApiServlet {

    private final GraduationDao graduationDao = new GraduationDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int studentId = AuthFilter.currentUser(request).studentId();
        CreditSummary credits = graduationDao.findCredits(studentId)
                .orElseThrow(() -> ApiException.notFound("학생 정보를 찾을 수 없습니다."));
        JsonUtil.write(response, 200, credits);
    }
}

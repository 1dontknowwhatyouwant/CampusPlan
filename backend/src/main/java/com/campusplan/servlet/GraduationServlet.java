package com.campusplan.servlet;

import com.campusplan.config.AuthFilter;
import com.campusplan.dao.GraduationDao;
import com.campusplan.dto.GraduationCheckResult;
import com.campusplan.util.JsonUtil;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** GET /api/me/graduation-check : 로그인한 학생의 졸업요건 진단 (View GraduationCheck) */
@WebServlet("/api/me/graduation-check")
public class GraduationServlet extends ApiServlet {

    private final GraduationDao graduationDao = new GraduationDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int studentId = AuthFilter.currentUser(request).studentId();
        JsonUtil.write(response, 200, new GraduationCheckResult(studentId, graduationDao.findChecks(studentId)));
    }
}

package com.campusplan.servlet;

import com.campusplan.config.AuthFilter;
import com.campusplan.dao.SemesterDao;
import com.campusplan.util.JsonUtil;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** GET /api/me/semesters : 로그인한 학생의 학기 목록 (최신순) */
@WebServlet("/api/me/semesters")
public class SemesterServlet extends ApiServlet {

    private final SemesterDao semesterDao = new SemesterDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int studentId = AuthFilter.currentUser(request).studentId();
        JsonUtil.write(response, 200, semesterDao.findByStudent(studentId));
    }
}

package com.campusplan.servlet;

import com.campusplan.dao.VolunteerDao;
import com.campusplan.util.ApiException;
import com.campusplan.util.JsonUtil;
import com.campusplan.util.Validator;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * POST /api/admin/students/{studentId}/volunteers/approve-all : 학생 한 명의 대기 봉사 내역 일괄 승인
 * (역할 검사는 AuthFilter 가 /api/admin/** 에 적용한다)
 */
@WebServlet("/api/admin/students/*")
public class AdminStudentServlet extends ApiServlet {

    private final VolunteerDao volunteerDao = new VolunteerDao();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // pathInfo: /{studentId}/volunteers/approve-all
        String[] parts = request.getPathInfo() == null ? new String[0] : request.getPathInfo().substring(1).split("/");
        if (parts.length != 3 || !"volunteers".equals(parts[1]) || !"approve-all".equals(parts[2])) {
            throw ApiException.notFound("존재하지 않는 경로입니다.");
        }
        int studentId = Validator.parseId(parts[0], "studentId");
        JsonUtil.write(response, 200, volunteerDao.approveAllPending(studentId));
    }
}

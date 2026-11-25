package com.campusplan.servlet;

import com.campusplan.config.AuthFilter;
import com.campusplan.dao.VolunteerDao;
import com.campusplan.dto.VolunteerList;
import com.campusplan.util.JsonUtil;
import com.campusplan.util.Validator;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/** GET /api/me/volunteers?status= : 로그인한 학생의 봉사 내역과 승인된 누적 시간 */
@WebServlet("/api/me/volunteers")
public class VolunteerServlet extends ApiServlet {

    private static final List<String> STATUSES = List.of("대기", "승인", "반려");

    private final VolunteerDao volunteerDao = new VolunteerDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int studentId = AuthFilter.currentUser(request).studentId();
        String raw = request.getParameter("status");
        String status = (raw == null || raw.isEmpty()) ? null : Validator.requireOneOf(raw, STATUSES, "status");
        JsonUtil.write(response, 200, new VolunteerList(volunteerDao.findApprovedHours(studentId),
                volunteerDao.findByStudent(studentId, status)));
    }
}

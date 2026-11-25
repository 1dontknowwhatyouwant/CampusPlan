package com.campusplan.servlet;

import com.campusplan.dao.VolunteerDao;
import com.campusplan.dto.ApproveResult;
import com.campusplan.util.ApiException;
import com.campusplan.util.JsonUtil;
import com.campusplan.util.Validator;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * 관리자 봉사 API (역할 검사는 AuthFilter 가 /api/admin/** 에 적용한다)
 *  GET  /api/admin/volunteers?status=         모든 학생의 봉사 내역
 *  POST /api/admin/volunteers/{recordId}/approve  봉사 내역 1건 승인
 */
@WebServlet("/api/admin/volunteers/*")
public class AdminVolunteerServlet extends ApiServlet {

    private static final List<String> STATUSES = List.of("대기", "승인", "반려");

    private final VolunteerDao volunteerDao = new VolunteerDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = request.getPathInfo();
        if (path != null && !"/".equals(path)) {
            if (path.endsWith("/approve")) {
                // 존재하는 경로이지만 POST 전용이므로 405 (기본 구현이 응답한다)
                super.doGet(request, response);
                return;
            }
            throw ApiException.notFound("존재하지 않는 경로입니다.");
        }
        String raw = request.getParameter("status");
        String status = (raw == null || raw.isEmpty()) ? null : Validator.requireOneOf(raw, STATUSES, "status");
        JsonUtil.write(response, 200, volunteerDao.findAll(status));
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // pathInfo: /{recordId}/approve
        String[] parts = request.getPathInfo() == null ? new String[0] : request.getPathInfo().substring(1).split("/");
        if (parts.length != 2 || !"approve".equals(parts[1])) {
            throw ApiException.notFound("존재하지 않는 경로입니다.");
        }
        int recordId = Validator.parseId(parts[0], "recordId");
        volunteerDao.approve(recordId);
        JsonUtil.write(response, 200, new ApproveResult(recordId, "승인"));
    }
}

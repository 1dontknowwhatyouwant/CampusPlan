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
        if (isListPath(request)) {
            String raw = request.getParameter("status");
            String status = (raw == null || raw.isEmpty()) ? null : Validator.requireOneOf(raw, STATUSES, "status");
            JsonUtil.write(response, 200, volunteerDao.findAll(status));
        } else if (approvePathParts(request) != null) {
            super.doGet(request, response); // POST 전용 경로 -> 405 (기본 구현이 응답한다)
        } else {
            throw ApiException.notFound("존재하지 않는 경로입니다.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String[] parts = approvePathParts(request);
        if (parts != null) {
            int recordId = Validator.parseId(parts[0], "recordId");
            volunteerDao.approve(recordId);
            JsonUtil.write(response, 200, new ApproveResult(recordId, "승인"));
        } else if (isListPath(request)) {
            super.doPost(request, response); // GET 전용 경로 -> 405
        } else {
            throw ApiException.notFound("존재하지 않는 경로입니다.");
        }
    }

    /** /api/admin/volunteers (목록) */
    private static boolean isListPath(HttpServletRequest request) {
        String path = request.getPathInfo();
        return path == null || "/".equals(path);
    }

    /** /api/admin/volunteers/{recordId}/approve 이면 [recordId, "approve"], 아니면 null */
    private static String[] approvePathParts(HttpServletRequest request) {
        String path = request.getPathInfo();
        if (path == null) {
            return null;
        }
        String[] parts = path.substring(1).split("/");
        return parts.length == 2 && !parts[0].isEmpty() && "approve".equals(parts[1]) ? parts : null;
    }
}

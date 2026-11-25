package com.campusplan.servlet;

import com.campusplan.config.AuthFilter;
import com.campusplan.dao.EnrollmentDao;
import com.campusplan.util.ApiException;
import com.campusplan.util.Validator;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** DELETE /api/schedules/{scheduleId} : 시간표 한 칸 삭제 (본인 항목만, 수강 과목은 지우지 않는다) */
@WebServlet("/api/schedules/*")
public class ScheduleServlet extends ApiServlet {

    private final EnrollmentDao enrollmentDao = new EnrollmentDao();

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = request.getPathInfo();
        if (path == null || path.length() <= 1 || path.substring(1).contains("/")) {
            throw ApiException.notFound("존재하지 않는 경로입니다.");
        }
        int scheduleId = Validator.parseId(path.substring(1), "scheduleId");
        enrollmentDao.deleteSlot(AuthFilter.currentUser(request).studentId(), scheduleId);
        response.setStatus(204);
    }
}

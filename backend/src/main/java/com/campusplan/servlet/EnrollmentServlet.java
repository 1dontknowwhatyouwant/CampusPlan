package com.campusplan.servlet;

import com.campusplan.config.AuthFilter;
import com.campusplan.dao.EnrollmentDao;
import com.campusplan.dao.SemesterDao;
import com.campusplan.util.ApiException;
import com.campusplan.util.JsonUtil;
import com.campusplan.util.Validator;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** GET /api/me/enrollments?semesterId= : 로그인한 학생의 수강 이력 */
@WebServlet("/api/me/enrollments")
public class EnrollmentServlet extends ApiServlet {

    private final EnrollmentDao enrollmentDao = new EnrollmentDao();
    private final SemesterDao semesterDao = new SemesterDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int studentId = AuthFilter.currentUser(request).studentId();
        String raw = request.getParameter("semesterId");
        Integer semesterId = null;
        if (raw != null && !raw.isEmpty()) {
            semesterId = Validator.parseId(raw, "semesterId");
            // 다른 학생의 학기는 없는 학기와 같게 처리한다.
            if (semesterDao.findOwned(studentId, semesterId).isEmpty()) {
                throw ApiException.notFound("학기를 찾을 수 없습니다.");
            }
        }
        JsonUtil.write(response, 200, enrollmentDao.findByStudent(studentId, semesterId));
    }
}

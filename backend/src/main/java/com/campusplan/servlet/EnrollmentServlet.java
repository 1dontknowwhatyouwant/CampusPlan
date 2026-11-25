package com.campusplan.servlet;

import com.campusplan.config.AuthFilter;
import com.campusplan.dao.EnrollmentDao;
import com.campusplan.dao.SemesterDao;
import com.campusplan.dto.EnrollmentCreateRequest;
import com.campusplan.util.ApiException;
import com.campusplan.util.JsonUtil;
import com.campusplan.util.Validator;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * GET  /api/me/enrollments?semesterId= : 로그인한 학생의 수강 이력
 * POST /api/me/enrollments             : 수강 등록
 */
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

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int studentId = AuthFilter.currentUser(request).studentId();
        EnrollmentCreateRequest body = JsonUtil.read(request, EnrollmentCreateRequest.class);
        int semesterId = Validator.requirePositive(body.semesterId, "semesterId");
        int courseId = Validator.requirePositive(body.courseId, "courseId");
        String grade = body.grade == null ? null : Validator.requireOneOf(body.grade, Validator.GRADES, "grade");
        // 없는 학기와 다른 학생의 학기는 구분하지 않고 400 으로 처리한다. (API 명세)
        if (semesterDao.findOwned(studentId, semesterId).isEmpty()) {
            throw ApiException.badRequest("semesterId 에 해당하는 본인의 학기가 없습니다.");
        }
        JsonUtil.write(response, 201, enrollmentDao.insert(studentId, semesterId, courseId, grade));
    }
}

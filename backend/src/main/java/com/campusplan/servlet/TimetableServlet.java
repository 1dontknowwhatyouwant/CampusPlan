package com.campusplan.servlet;

import com.campusplan.config.AuthFilter;
import com.campusplan.dao.EnrollmentDao;
import com.campusplan.dao.SemesterDao;
import com.campusplan.dto.SemesterItem;
import com.campusplan.dto.Timetable;
import com.campusplan.util.ApiException;
import com.campusplan.util.JsonUtil;
import com.campusplan.util.Validator;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;

/**
 * GET /api/me/timetable?semesterId= : 학기의 주간 시간표.
 * semesterId 를 생략하면 상태가 '재학'인 가장 최근 학기를 사용하고, 없으면 404 입니다.
 */
@WebServlet("/api/me/timetable")
public class TimetableServlet extends ApiServlet {

    private final SemesterDao semesterDao = new SemesterDao();
    private final EnrollmentDao enrollmentDao = new EnrollmentDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int studentId = AuthFilter.currentUser(request).studentId();
        String raw = request.getParameter("semesterId");
        Optional<SemesterItem> semester = (raw == null || raw.isEmpty())
                ? semesterDao.findCurrent(studentId)
                : semesterDao.findOwned(studentId, Validator.parseId(raw, "semesterId"));
        SemesterItem found = semester.orElseThrow(() -> ApiException.notFound("학기를 찾을 수 없습니다."));
        JsonUtil.write(response, 200,
                new Timetable(found, enrollmentDao.findTimetable(studentId, found.semesterId())));
    }
}

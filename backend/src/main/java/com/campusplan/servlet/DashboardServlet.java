package com.campusplan.servlet;

import com.campusplan.config.AuthFilter;
import com.campusplan.dao.EnrollmentDao;
import com.campusplan.dao.GraduationDao;
import com.campusplan.dao.SemesterDao;
import com.campusplan.dao.StudentDao;
import com.campusplan.dto.Dashboard;
import com.campusplan.dto.GraduationCheckResult;
import com.campusplan.dto.Timetable;
import com.campusplan.util.ApiException;
import com.campusplan.util.JsonUtil;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * GET /api/me/dashboard : 학생 정보, 현재 학기 시간표, 학점 요약, 졸업 진단을 한 번에 반환합니다.
 * 각 값은 개별 조회 API 와 같은 DAO 를 사용하며 새로 계산하지 않습니다.
 * 재학 중인 학기가 없으면 시간표 API 는 404 지만 여기서는 timetable 만 null 입니다.
 */
@WebServlet("/api/me/dashboard")
public class DashboardServlet extends ApiServlet {

    private final StudentDao studentDao = new StudentDao();
    private final SemesterDao semesterDao = new SemesterDao();
    private final EnrollmentDao enrollmentDao = new EnrollmentDao();
    private final GraduationDao graduationDao = new GraduationDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int studentId = AuthFilter.currentUser(request).studentId();
        var student = studentDao.findById(studentId)
                .orElseThrow(() -> ApiException.notFound("학생 정보를 찾을 수 없습니다."));
        var credits = graduationDao.findCredits(studentId)
                .orElseThrow(() -> ApiException.notFound("학생 정보를 찾을 수 없습니다."));
        Timetable timetable = semesterDao.findCurrent(studentId)
                .map(semester -> new Timetable(semester, enrollmentDao.findTimetable(studentId, semester.semesterId())))
                .orElse(null);
        JsonUtil.write(response, 200, new Dashboard(student, timetable, credits,
                new GraduationCheckResult(studentId, graduationDao.findChecks(studentId))));
    }
}

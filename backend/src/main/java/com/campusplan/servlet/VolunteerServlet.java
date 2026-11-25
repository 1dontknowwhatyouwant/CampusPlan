package com.campusplan.servlet;

import com.campusplan.config.AuthFilter;
import com.campusplan.dao.VolunteerDao;
import com.campusplan.dto.VolunteerCreateRequest;
import com.campusplan.dto.VolunteerList;
import com.campusplan.util.JsonUtil;
import com.campusplan.util.Validator;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** /api/me/volunteers : GET 은 로그인한 학생의 봉사 내역과 승인된 누적 시간, POST 는 봉사 내역 등록 */
@WebServlet("/api/me/volunteers")
public class VolunteerServlet extends ApiServlet {

    private static final List<String> STATUSES = List.of("대기", "승인", "반려");
    private static final int MAX_ACTIVITY_NAME = 100;
    // VolunteerRecord.hours 는 DECIMAL(5,1) 이라 9999.9 가 최대
    private static final BigDecimal MAX_HOURS = new BigDecimal("9999.9");

    private final VolunteerDao volunteerDao = new VolunteerDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int studentId = AuthFilter.currentUser(request).studentId();
        String raw = request.getParameter("status");
        String status = (raw == null || raw.isEmpty()) ? null : Validator.requireOneOf(raw, STATUSES, "status");
        JsonUtil.write(response, 200, new VolunteerList(volunteerDao.findApprovedHours(studentId),
                volunteerDao.findByStudent(studentId, status)));
    }

    /** POST /api/me/volunteers : 봉사 내역 등록. 상태는 항상 '대기'로 만들어진다. */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int studentId = AuthFilter.currentUser(request).studentId();
        VolunteerCreateRequest body = JsonUtil.read(request, VolunteerCreateRequest.class);
        String activityName = Validator.requireText(body.activityName, "activityName", MAX_ACTIVITY_NAME);
        LocalDate activityDate = Validator.parseDate(body.activityDate, "activityDate");
        BigDecimal hours = Validator.requirePositiveOneDecimal(body.hours, "hours", MAX_HOURS);
        JsonUtil.write(response, 201, volunteerDao.insert(studentId, activityName, activityDate, hours));
    }
}

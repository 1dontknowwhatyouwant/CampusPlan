package com.campusplan.servlet;

import com.campusplan.config.AuthFilter;
import com.campusplan.dao.EnrollmentDao;
import com.campusplan.dto.ScheduleCreateRequest;
import com.campusplan.dto.ScheduleCreateResult;
import com.campusplan.util.ApiException;
import com.campusplan.util.JsonUtil;
import com.campusplan.util.Validator;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 수강 이력 한 건에 대한 API (본인 항목만, 다른 학생의 것은 404)
 *  PATCH  /api/enrollments/{enrollmentId}            성적 입력·수정
 *  DELETE /api/enrollments/{enrollmentId}            수강 취소
 *  POST   /api/enrollments/{enrollmentId}/schedules  시간표 칸 등록
 */
@WebServlet("/api/enrollments/*")
public class EnrollmentItemServlet extends ApiServlet {

    private static final int MAX_SLOTS = 30;
    private static final int MAX_ROOM_LENGTH = 50;
    // 교시 상한. 학교의 실제 교시 수를 확인하지 못한 임시값이며, 화면 시간표가 감당할 수 있는 범위로 제한한다.
    private static final int MAX_PERIOD = 15;

    private final EnrollmentDao enrollmentDao = new EnrollmentDao();

    @Override
    protected void doPatch(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String[] parts = pathParts(request);
        if (parts.length != 1) {
            throw ApiException.notFound("존재하지 않는 경로입니다.");
        }
        int enrollmentId = Validator.parseId(parts[0], "enrollmentId");
        // grade 가 null 인 요청(성적 삭제)과 grade 가 없는 요청을 구분하려고 JsonObject 로 읽는다.
        JsonObject body = JsonUtil.read(request, JsonObject.class);
        if (!body.has("grade")) {
            throw ApiException.badRequest("grade 필드가 필요합니다. 성적을 지우려면 null 을 보냅니다.");
        }
        JsonElement element = body.get("grade");
        String grade = null;
        if (!element.isJsonNull()) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw ApiException.badRequest("grade 는 문자열 또는 null 이어야 합니다.");
            }
            grade = Validator.requireOneOf(element.getAsString(), Validator.GRADES, "grade");
        }
        int studentId = AuthFilter.currentUser(request).studentId();
        JsonUtil.write(response, 200, enrollmentDao.updateGrade(studentId, enrollmentId, grade));
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String[] parts = pathParts(request);
        if (parts.length != 1) {
            throw ApiException.notFound("존재하지 않는 경로입니다.");
        }
        int enrollmentId = Validator.parseId(parts[0], "enrollmentId");
        enrollmentDao.delete(AuthFilter.currentUser(request).studentId(), enrollmentId);
        response.setStatus(204);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String[] parts = pathParts(request);
        if (parts.length != 2 || !"schedules".equals(parts[1])) {
            throw ApiException.notFound("존재하지 않는 경로입니다.");
        }
        int enrollmentId = Validator.parseId(parts[0], "enrollmentId");
        ScheduleCreateRequest body = JsonUtil.read(request, ScheduleCreateRequest.class);
        if (body.slots == null || body.slots.isEmpty()) {
            throw ApiException.badRequest("slots 에 1개 이상의 시간표 칸이 필요합니다.");
        }
        if (body.slots.size() > MAX_SLOTS) {
            throw ApiException.badRequest("slots 는 한 번에 " + MAX_SLOTS + "개까지 등록할 수 있습니다.");
        }
        for (ScheduleCreateRequest.Slot slot : body.slots) {
            if (slot == null) {
                throw ApiException.badRequest("slots 에 빈 항목이 있습니다.");
            }
            slot.day = Validator.requireOneOf(slot.day, Validator.DAYS, "day");
            if (slot.period == null || slot.period < 1 || slot.period > MAX_PERIOD) {
                throw ApiException.badRequest("period 는 1 이상 " + MAX_PERIOD + " 이하의 정수여야 합니다.");
            }
            slot.room = slot.room == null || slot.room.isBlank()
                    ? null : Validator.requireText(slot.room, "room", MAX_ROOM_LENGTH);
        }
        int studentId = AuthFilter.currentUser(request).studentId();
        JsonUtil.write(response, 201,
                new ScheduleCreateResult(enrollmentDao.insertSlots(studentId, enrollmentId, body.slots)));
    }

    /** /api/enrollments/ 뒤의 경로 조각. 경로가 없으면 빈 배열 */
    private static String[] pathParts(HttpServletRequest request) {
        String path = request.getPathInfo();
        if (path == null || path.length() <= 1) {
            return new String[0];
        }
        return path.substring(1).split("/");
    }
}

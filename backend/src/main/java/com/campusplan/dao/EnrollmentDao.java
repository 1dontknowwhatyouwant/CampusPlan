package com.campusplan.dao;

import com.campusplan.config.DBConnectionManager;
import com.campusplan.dto.EnrollmentItem;
import com.campusplan.dto.ScheduleCreateRequest;
import com.campusplan.dto.TimetableEntry;
import com.campusplan.dto.TimetableSlot;
import com.campusplan.util.ApiException;
import com.campusplan.util.SqlErrorMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EnrollmentDao {

    private static final String LIST =
            "SELECT e.enrollment_id, e.semester_id, sm.year, sm.term, c.course_id, c.code, c.name, "
          + "c.credits, c.category, e.grade, e.completed "
          + "FROM Enrollment e "
          + "JOIN Semester sm ON sm.semester_id = e.semester_id "
          + "JOIN Course c ON c.course_id = e.course_id "
          + "WHERE e.student_id = ? ";

    private static final String ORDER = "ORDER BY sm.year DESC, sm.term DESC, c.name";

    /** 수강 이력. semesterId 가 null 이면 전체 학기 (최신 학기부터) */
    public List<EnrollmentItem> findByStudent(int studentId, Integer semesterId) {
        String sql = LIST + (semesterId == null ? "" : "AND e.semester_id = ? ") + ORDER;
        try (Connection connection = DBConnectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, studentId);
            if (semesterId != null) {
                statement.setInt(2, semesterId);
            }
            List<EnrollmentItem> items = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    items.add(new EnrollmentItem(rs.getInt("enrollment_id"), rs.getInt("semester_id"),
                            rs.getInt("year"), rs.getInt("term"), rs.getInt("course_id"), rs.getString("code"),
                            rs.getString("name"), rs.getInt("credits"), rs.getString("category"),
                            rs.getString("grade"), rs.getBoolean("completed")));
                }
            }
            return items;
        } catch (SQLException e) {
            throw SqlErrorMapper.toApiException(e);
        }
    }

    private static final String TIMETABLE =
            "SELECT e.enrollment_id, c.course_id, c.name, c.credits, "
          + "s.schedule_id, s.day, s.period, s.room "
          + "FROM Enrollment e "
          + "JOIN Course c ON c.course_id = e.course_id "
          + "LEFT JOIN Schedule s ON s.enrollment_id = e.enrollment_id "
          + "WHERE e.student_id = ? AND e.semester_id = ? "
          + "ORDER BY c.name, e.enrollment_id, FIELD(s.day, '월', '화', '수', '목', '금', '토'), s.period";

    /** 학기의 수강 과목별 시간표 칸. 시간표가 없는 과목은 slots 가 빈 배열입니다. */
    public List<TimetableEntry> findTimetable(int studentId, int semesterId) {
        try (Connection connection = DBConnectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(TIMETABLE)) {
            statement.setInt(1, studentId);
            statement.setInt(2, semesterId);
            Map<Integer, TimetableEntry> entries = new LinkedHashMap<>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    int enrollmentId = rs.getInt("enrollment_id");
                    TimetableEntry entry = entries.get(enrollmentId);
                    if (entry == null) {
                        entry = new TimetableEntry(enrollmentId, rs.getInt("course_id"),
                                rs.getString("name"), rs.getInt("credits"));
                        entries.put(enrollmentId, entry);
                    }
                    int scheduleId = rs.getInt("schedule_id");
                    if (!rs.wasNull()) {
                        entry.addSlot(new TimetableSlot(scheduleId, rs.getString("day"),
                                rs.getInt("period"), rs.getString("room")));
                    }
                }
            }
            return new ArrayList<>(entries.values());
        } catch (SQLException e) {
            throw SqlErrorMapper.toApiException(e);
        }
    }

    // ---- 쓰기 ----

    private static EnrollmentItem toItem(ResultSet rs) throws SQLException {
        return new EnrollmentItem(rs.getInt("enrollment_id"), rs.getInt("semester_id"),
                rs.getInt("year"), rs.getInt("term"), rs.getInt("course_id"), rs.getString("code"),
                rs.getString("name"), rs.getInt("credits"), rs.getString("category"),
                rs.getString("grade"), rs.getBoolean("completed"));
    }

    private static EnrollmentItem selectOne(Connection connection, int studentId, int enrollmentId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LIST + "AND e.enrollment_id = ?")) {
            statement.setInt(1, studentId);
            statement.setInt(2, enrollmentId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? toItem(rs) : null;
            }
        }
    }

    /** 본인의 수강 이력이 아니면 404. 다른 학생의 것은 없는 것과 같게 처리한다. */
    private static EnrollmentItem requireOwned(Connection connection, int studentId, int enrollmentId)
            throws SQLException {
        EnrollmentItem item = selectOne(connection, studentId, enrollmentId);
        if (item == null) {
            throw ApiException.notFound("수강 이력을 찾을 수 없습니다.");
        }
        return item;
    }

    /** 수강 등록. 과목이 없으면 404, 같은 학기에 같은 과목이 있으면 409(DUPLICATE). completed 는 트리거가 정한다. */
    public EnrollmentItem insert(int studentId, int semesterId, int courseId, String grade) {
        try (Connection connection = DBConnectionManager.getConnection()) {
            try (PreparedStatement check = connection.prepareStatement("SELECT 1 FROM Course WHERE course_id = ?")) {
                check.setInt(1, courseId);
                try (ResultSet rs = check.executeQuery()) {
                    if (!rs.next()) {
                        throw ApiException.notFound("과목을 찾을 수 없습니다.");
                    }
                }
            }
            int id;
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO Enrollment (student_id, semester_id, course_id, grade) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                statement.setInt(1, studentId);
                statement.setInt(2, semesterId);
                statement.setInt(3, courseId);
                statement.setString(4, grade);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    keys.next();
                    id = keys.getInt(1);
                }
            }
            return selectOne(connection, studentId, id);
        } catch (SQLException e) {
            throw SqlErrorMapper.toApiException(e);
        }
    }

    /** 성적 입력·수정(null 이면 삭제). 본인의 수강 이력이 아니면 404. */
    public EnrollmentItem updateGrade(int studentId, int enrollmentId, String grade) {
        try (Connection connection = DBConnectionManager.getConnection()) {
            requireOwned(connection, studentId, enrollmentId);
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE Enrollment SET grade = ? WHERE enrollment_id = ? AND student_id = ?")) {
                statement.setString(1, grade);
                statement.setInt(2, enrollmentId);
                statement.setInt(3, studentId);
                statement.executeUpdate();
            }
            return selectOne(connection, studentId, enrollmentId);
        } catch (SQLException e) {
            throw SqlErrorMapper.toApiException(e);
        }
    }

    /** 수강 취소. 시간표 칸을 먼저 지우고 수강 이력을 지우며, 한 트랜잭션으로 처리한다. */
    public void delete(int studentId, int enrollmentId) {
        try (Connection connection = DBConnectionManager.getConnection()) {
            connection.setAutoCommit(false);
            try {
                requireOwned(connection, studentId, enrollmentId);
                try (PreparedStatement schedules = connection.prepareStatement(
                        "DELETE FROM Schedule WHERE enrollment_id = ?")) {
                    schedules.setInt(1, enrollmentId);
                    schedules.executeUpdate();
                }
                try (PreparedStatement enrollment = connection.prepareStatement(
                        "DELETE FROM Enrollment WHERE enrollment_id = ? AND student_id = ?")) {
                    enrollment.setInt(1, enrollmentId);
                    enrollment.setInt(2, studentId);
                    enrollment.executeUpdate();
                }
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw SqlErrorMapper.toApiException(e);
        }
    }

    /** 교착 상태로 트랜잭션이 취소됐을 때 다시 시도하는 횟수 */
    private static final int DEADLOCK_RETRIES = 2;

    /**
     * 수강 과목에 시간표 칸을 한 번에 등록한다. 하나라도 실패하면 전체를 취소한다.
     * 충돌 트리거(SQLSTATE 45000)는 409 SCHEDULE_CONFLICT, 같은 칸 중복은 409 DUPLICATE.
     *
     * 같은 학생이 같은 칸을 동시에 등록하면 트리거의 조회와 INSERT 의 잠금이 엇갈려 교착 상태(1213)가 날 수 있다.
     * MySQL 이 트랜잭션 전체를 취소하므로 처음부터 다시 실행한다. 다시 실행하면 먼저 끝난 요청의 칸이 보여 409 가 된다.
     */
    public List<TimetableSlot> insertSlots(int studentId, int enrollmentId, List<ScheduleCreateRequest.Slot> slots) {
        for (int attempt = 0; ; attempt++) {
            try {
                return insertSlotsOnce(studentId, enrollmentId, slots);
            } catch (ApiException e) {
                if (attempt >= DEADLOCK_RETRIES || !SqlErrorMapper.isDeadlock(e.getCause())) {
                    throw e;
                }
            }
        }
    }

    private List<TimetableSlot> insertSlotsOnce(int studentId, int enrollmentId,
            List<ScheduleCreateRequest.Slot> slots) {
        try (Connection connection = DBConnectionManager.getConnection()) {
            connection.setAutoCommit(false);
            try {
                requireOwned(connection, studentId, enrollmentId);
                List<TimetableSlot> created = new ArrayList<>();
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO Schedule (enrollment_id, day, period, room) VALUES (?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    for (ScheduleCreateRequest.Slot slot : slots) {
                        statement.setInt(1, enrollmentId);
                        statement.setString(2, slot.day);
                        statement.setInt(3, slot.period);
                        statement.setString(4, slot.room);
                        statement.executeUpdate();
                        try (ResultSet keys = statement.getGeneratedKeys()) {
                            keys.next();
                            created.add(new TimetableSlot(keys.getInt(1), slot.day, slot.period, slot.room));
                        }
                    }
                }
                connection.commit();
                return created;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            if ("45000".equals(e.getSQLState())) {
                throw new ApiException(409, "SCHEDULE_CONFLICT", "같은 학기에 요일/교시가 겹치는 수업이 이미 있습니다.", e);
            }
            throw SqlErrorMapper.toApiException(e);
        }
    }

    /** 시간표 한 칸 삭제. 본인의 수강 과목 칸이 아니면 404. */
    public void deleteSlot(int studentId, int scheduleId) {
        String sql = "DELETE s FROM Schedule s JOIN Enrollment e ON e.enrollment_id = s.enrollment_id "
                   + "WHERE s.schedule_id = ? AND e.student_id = ?";
        try (Connection connection = DBConnectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, scheduleId);
            statement.setInt(2, studentId);
            if (statement.executeUpdate() == 0) {
                throw ApiException.notFound("시간표 칸을 찾을 수 없습니다.");
            }
        } catch (SQLException e) {
            throw SqlErrorMapper.toApiException(e);
        }
    }
}

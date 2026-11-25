package com.campusplan.dao;

import com.campusplan.config.DBConnectionManager;
import com.campusplan.dto.EnrollmentItem;
import com.campusplan.dto.TimetableEntry;
import com.campusplan.dto.TimetableSlot;
import com.campusplan.util.SqlErrorMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
}

package com.campusplan.dao;

import com.campusplan.config.DBConnectionManager;
import com.campusplan.dto.VolunteerItem;
import com.campusplan.util.SqlErrorMapper;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class VolunteerDao {

    /** API 명세의 일시 형식. LocalDateTime.toString() 은 0초일 때 초를 생략하므로 형식을 고정한다. */
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private static final String APPROVED_HOURS =
            "SELECT approved_hours FROM VolunteerSummary WHERE student_id = ?";

    private static final String LIST =
            "SELECT record_id, activity_name, activity_date, hours, status, approved_at "
          + "FROM VolunteerRecord WHERE student_id = ? ";

    /** 승인된 누적 봉사시간 (View VolunteerSummary) */
    public BigDecimal findApprovedHours(int studentId) {
        try (Connection connection = DBConnectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(APPROVED_HOURS)) {
            statement.setInt(1, studentId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getBigDecimal("approved_hours") : BigDecimal.ZERO;
            }
        } catch (SQLException e) {
            throw SqlErrorMapper.toApiException(e);
        }
    }

    /** 봉사 내역 (활동일 최신순). status 가 null 이면 전체 */
    public List<VolunteerItem> findByStudent(int studentId, String status) {
        String sql = LIST + (status == null ? "" : "AND status = ? ") + "ORDER BY activity_date DESC, record_id DESC";
        try (Connection connection = DBConnectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, studentId);
            if (status != null) {
                statement.setString(2, status);
            }
            List<VolunteerItem> items = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    // DATE/DATETIME 을 java.time 으로 읽어 JVM 시간대에 따른 변환 없이 DB 값 그대로 쓴다.
                    // (getTimestamp 는 JVM 시간대가 UTC 인 서버에서 9시간 밀린 값을 돌려준다)
                    LocalDateTime approvedAt = rs.getObject("approved_at", LocalDateTime.class);
                    items.add(new VolunteerItem(rs.getInt("record_id"), rs.getString("activity_name"),
                            rs.getObject("activity_date", LocalDate.class).toString(), rs.getBigDecimal("hours"),
                            rs.getString("status"),
                            approvedAt == null ? null : approvedAt.format(DATE_TIME)));
                }
            }
            return items;
        } catch (SQLException e) {
            throw SqlErrorMapper.toApiException(e);
        }
    }
}

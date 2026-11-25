package com.campusplan.dao;

import com.campusplan.config.DBConnectionManager;
import com.campusplan.dto.AdminVolunteerItem;
import com.campusplan.dto.ApproveAllResult;
import com.campusplan.dto.VolunteerItem;
import com.campusplan.util.ApiException;
import com.campusplan.util.SqlErrorMapper;
import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
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
                    items.add(toItem(rs));
                }
            }
            return items;
        } catch (SQLException e) {
            throw SqlErrorMapper.toApiException(e);
        }
    }

    /**
     * DATE/DATETIME 을 java.time 으로 읽어 JVM 시간대에 따른 변환 없이 DB 값 그대로 쓴다.
     * (getTimestamp 는 JVM 시간대가 UTC 인 서버에서 9시간 밀린 값을 돌려준다)
     */
    private static VolunteerItem toItem(ResultSet rs) throws SQLException {
        return new VolunteerItem(rs.getInt("record_id"), rs.getString("activity_name"),
                rs.getObject("activity_date", LocalDate.class).toString(), rs.getBigDecimal("hours"),
                rs.getString("status"), formatApprovedAt(rs));
    }

    private static String formatApprovedAt(ResultSet rs) throws SQLException {
        LocalDateTime approvedAt = rs.getObject("approved_at", LocalDateTime.class);
        return approvedAt == null ? null : approvedAt.format(DATE_TIME);
    }

    private static final String INSERT =
            "INSERT INTO VolunteerRecord (student_id, activity_name, activity_date, hours) VALUES (?, ?, ?, ?)";

    private static final String FIND_ONE =
            "SELECT record_id, activity_name, activity_date, hours, status, approved_at "
          + "FROM VolunteerRecord WHERE record_id = ?";

    /** 봉사 내역 등록. status 는 컬럼 기본값('대기')으로 만들어진다. */
    public VolunteerItem insert(int studentId, String activityName, LocalDate activityDate, BigDecimal hours) {
        try (Connection connection = DBConnectionManager.getConnection();
             PreparedStatement insert = connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            insert.setInt(1, studentId);
            insert.setString(2, activityName);
            insert.setObject(3, activityDate);
            insert.setBigDecimal(4, hours);
            insert.executeUpdate();
            int recordId;
            try (ResultSet keys = insert.getGeneratedKeys()) {
                keys.next();
                recordId = keys.getInt(1);
            }
            try (PreparedStatement select = connection.prepareStatement(FIND_ONE)) {
                select.setInt(1, recordId);
                try (ResultSet rs = select.executeQuery()) {
                    rs.next();
                    return toItem(rs);
                }
            }
        } catch (SQLException e) {
            throw SqlErrorMapper.toApiException(e);
        }
    }

    private static final String ADMIN_LIST =
            "SELECT v.record_id, v.student_id, s.name AS student_name, v.activity_name, v.activity_date, "
          + "v.hours, v.status, v.approved_at "
          + "FROM VolunteerRecord v JOIN Student s ON s.student_id = v.student_id ";

    /** 모든 학생의 봉사 내역 (활동일 최신순). status 가 null 이면 전체 */
    public List<AdminVolunteerItem> findAll(String status) {
        String sql = ADMIN_LIST + (status == null ? "" : "WHERE v.status = ? ")
                + "ORDER BY v.activity_date DESC, v.record_id DESC";
        try (Connection connection = DBConnectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (status != null) {
                statement.setString(1, status);
            }
            List<AdminVolunteerItem> items = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    items.add(new AdminVolunteerItem(rs.getInt("record_id"), rs.getInt("student_id"),
                            rs.getString("student_name"), rs.getString("activity_name"),
                            rs.getObject("activity_date", LocalDate.class).toString(), rs.getBigDecimal("hours"),
                            rs.getString("status"), formatApprovedAt(rs)));
                }
            }
            return items;
        } catch (SQLException e) {
            throw SqlErrorMapper.toApiException(e);
        }
    }

    /**
     * 봉사 내역 1건 승인 (프로시저 ApproveVolunteer). 대기 상태일 때만 승인된다.
     * 없는 내역이면 404, 이미 처리된 내역이면 409 INVALID_STATE.
     */
    public void approve(int recordId) {
        try (Connection connection = DBConnectionManager.getConnection();
             CallableStatement call = connection.prepareCall("{CALL ApproveVolunteer(?, ?)}")) {
            call.setInt(1, recordId);
            call.registerOutParameter(2, Types.INTEGER);
            call.execute();
            if (call.getInt(2) == 0) {
                // 프로시저는 '없음'과 '이미 처리됨'을 구분하지 않으므로 존재 여부를 따로 확인한다.
                try (PreparedStatement select = connection.prepareStatement(FIND_ONE)) {
                    select.setInt(1, recordId);
                    try (ResultSet rs = select.executeQuery()) {
                        if (!rs.next()) {
                            throw ApiException.notFound("봉사 내역을 찾을 수 없습니다.");
                        }
                    }
                }
                throw new ApiException(409, "INVALID_STATE", "이미 처리된 봉사 내역입니다.");
            }
        } catch (SQLException e) {
            throw SqlErrorMapper.toApiException(e);
        }
    }

    /**
     * 학생 한 명의 대기 내역 일괄 승인 (프로시저 ApproveAllPendingVolunteer).
     * 프로시저가 직접 트랜잭션을 시작·커밋하므로 연결은 자동 커밋 상태 그대로 둔다.
     */
    public ApproveAllResult approveAllPending(int studentId) {
        try (Connection connection = DBConnectionManager.getConnection();
             CallableStatement call = connection.prepareCall("{CALL ApproveAllPendingVolunteer(?, ?, ?)}")) {
            call.setInt(1, studentId);
            call.registerOutParameter(2, Types.INTEGER);
            call.registerOutParameter(3, Types.DECIMAL);
            call.execute();
            return new ApproveAllResult(call.getInt(2), call.getBigDecimal(3));
        } catch (SQLException e) {
            // SQLSTATE 45000 은 이 프로시저에서는 '존재하지 않는 학생'을 뜻한다.
            if ("45000".equals(e.getSQLState())) {
                throw ApiException.notFound("학생을 찾을 수 없습니다.");
            }
            throw SqlErrorMapper.toApiException(e);
        }
    }
}

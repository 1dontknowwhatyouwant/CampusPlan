package com.campusplan.dao;

import com.campusplan.config.DBConnectionManager;
import com.campusplan.dto.SemesterItem;
import com.campusplan.util.SqlErrorMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SemesterDao {

    private static final String COLUMNS = "SELECT semester_id, year, term, status FROM Semester ";

    /** 학생의 학기 목록 (최신순) */
    public List<SemesterItem> findByStudent(int studentId) {
        return query(COLUMNS + "WHERE student_id = ? ORDER BY year DESC, term DESC", studentId);
    }

    /** 학생 본인의 학기 한 건. 다른 학생의 학기면 비어 있습니다. */
    public Optional<SemesterItem> findOwned(int studentId, int semesterId) {
        return query(COLUMNS + "WHERE student_id = ? AND semester_id = ?", studentId, semesterId)
                .stream().findFirst();
    }

    /** 상태가 '재학'인 가장 최근 학기 */
    public Optional<SemesterItem> findCurrent(int studentId) {
        return query(COLUMNS + "WHERE student_id = ? AND status = '재학' ORDER BY year DESC, term DESC LIMIT 1",
                studentId).stream().findFirst();
    }

    private List<SemesterItem> query(String sql, int... params) {
        try (Connection connection = DBConnectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setInt(i + 1, params[i]);
            }
            List<SemesterItem> items = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    items.add(new SemesterItem(rs.getInt("semester_id"), rs.getInt("year"),
                            rs.getInt("term"), rs.getString("status")));
                }
            }
            return items;
        } catch (SQLException e) {
            throw SqlErrorMapper.toApiException(e);
        }
    }
}

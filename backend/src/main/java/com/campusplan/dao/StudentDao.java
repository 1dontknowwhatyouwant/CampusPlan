package com.campusplan.dao;

import com.campusplan.config.DBConnectionManager;
import com.campusplan.dto.StudentInfo;
import com.campusplan.util.SqlErrorMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class StudentDao {

    private static final String FIND_BY_ID =
            "SELECT s.student_id, s.name, s.admission_year, d.college, d.department_name "
          + "FROM Student s JOIN Department d ON d.department_id = s.department_id "
          + "WHERE s.student_id = ?";

    public Optional<StudentInfo> findById(int studentId) {
        try (Connection connection = DBConnectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_BY_ID)) {
            statement.setInt(1, studentId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new StudentInfo(rs.getInt("student_id"), rs.getString("name"),
                        rs.getInt("admission_year"), rs.getString("college"),
                        rs.getString("department_name")));
            }
        } catch (SQLException e) {
            throw SqlErrorMapper.toApiException(e);
        }
    }
}

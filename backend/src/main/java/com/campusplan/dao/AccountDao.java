package com.campusplan.dao;

import com.campusplan.config.DBConnectionManager;
import com.campusplan.dto.AuthUser;
import com.campusplan.util.SqlErrorMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class AccountDao {

    /** 로그인 검증에 쓰는 조회 결과. 비밀번호 해시는 이 안에서만 다룹니다. */
    public record Credentials(AuthUser user, String passwordHash) {
    }

    private static final String FIND_BY_LOGIN_ID =
            "SELECT a.account_id, a.login_id, a.password_hash, a.role, a.student_id, s.name "
          + "FROM Account a LEFT JOIN Student s ON s.student_id = a.student_id "
          + "WHERE a.login_id = ?";

    public Optional<Credentials> findByLoginId(String loginId) {
        try (Connection connection = DBConnectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_BY_LOGIN_ID)) {
            statement.setString(1, loginId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                int studentId = rs.getInt("student_id");
                Integer studentIdOrNull = rs.wasNull() ? null : studentId;
                AuthUser user = new AuthUser(rs.getInt("account_id"), rs.getString("login_id"),
                        rs.getString("role"), studentIdOrNull, rs.getString("name"));
                return Optional.of(new Credentials(user, rs.getString("password_hash")));
            }
        } catch (SQLException e) {
            throw SqlErrorMapper.toApiException(e);
        }
    }
}

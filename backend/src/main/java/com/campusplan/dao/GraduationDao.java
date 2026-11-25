package com.campusplan.dao;

import com.campusplan.config.DBConnectionManager;
import com.campusplan.dto.CreditSummary;
import com.campusplan.dto.GraduationRuleResult;
import com.campusplan.util.SqlErrorMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 학점 집계·졸업 진단 조회. 계산은 DB View(CreditSummary, GraduationCheck)가 하고 여기서는 다시 계산하지 않습니다. */
public class GraduationDao {

    private static final String CREDITS =
            "SELECT student_id, total_credits, major_credits, general_credits "
          + "FROM CreditSummary WHERE student_id = ?";

    private static final String CHECK =
            "SELECT rule_id, rule_name, rule_type, current_value, required_value, is_met, is_verified "
          + "FROM GraduationCheck WHERE student_id = ? ORDER BY rule_id";

    public Optional<CreditSummary> findCredits(int studentId) {
        try (Connection connection = DBConnectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(CREDITS)) {
            statement.setInt(1, studentId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new CreditSummary(rs.getInt("student_id"), rs.getInt("total_credits"),
                        rs.getInt("major_credits"), rs.getInt("general_credits")));
            }
        } catch (SQLException e) {
            throw SqlErrorMapper.toApiException(e);
        }
    }

    public List<GraduationRuleResult> findChecks(int studentId) {
        try (Connection connection = DBConnectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(CHECK)) {
            statement.setInt(1, studentId);
            List<GraduationRuleResult> rules = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rules.add(new GraduationRuleResult(rs.getInt("rule_id"), rs.getString("rule_name"),
                            rs.getString("rule_type"), rs.getBigDecimal("current_value"),
                            rs.getBigDecimal("required_value"), rs.getInt("is_met") == 1,
                            rs.getBoolean("is_verified")));
                }
            }
            return rules;
        } catch (SQLException e) {
            throw SqlErrorMapper.toApiException(e);
        }
    }
}

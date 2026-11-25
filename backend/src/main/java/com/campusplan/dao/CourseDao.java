package com.campusplan.dao;

import com.campusplan.config.DBConnectionManager;
import com.campusplan.dto.CourseItem;
import com.campusplan.dto.CoursePage;
import com.campusplan.util.SqlErrorMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class CourseDao {

    /** LIKE 의 이스케이프 문자. 검색어의 %, _ 가 와일드카드로 동작하지 않게 합니다. */
    private static final char ESCAPE = '!';

    /**
     * 과목을 검색합니다.
     *
     * @param query    과목명 또는 과목코드에 포함된 문자열 (null 이면 조건 없음)
     * @param category 과목 구분 (null 이면 조건 없음)
     * @param page     0부터 시작
     */
    public CoursePage search(String query, String category, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<String> params = new ArrayList<>();
        if (query != null) {
            where.append(" AND (name LIKE ? ESCAPE '").append(ESCAPE).append("' OR code LIKE ? ESCAPE '")
                    .append(ESCAPE).append("')");
            String pattern = "%" + escapeLike(query) + "%";
            params.add(pattern);
            params.add(pattern);
        }
        if (category != null) {
            where.append(" AND category = ?");
            params.add(category);
        }

        String countSql = "SELECT COUNT(*) FROM Course" + where;
        String listSql = "SELECT course_id, code, name, credits, category FROM Course" + where
                + " ORDER BY name, code LIMIT ? OFFSET ?";

        try (Connection connection = DBConnectionManager.getConnection()) {
            long total;
            try (PreparedStatement statement = connection.prepareStatement(countSql)) {
                bind(statement, params);
                try (ResultSet rs = statement.executeQuery()) {
                    rs.next();
                    total = rs.getLong(1);
                }
            }

            List<CourseItem> items = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(listSql)) {
                int next = bind(statement, params);
                statement.setInt(next++, size);
                statement.setLong(next, (long) page * size);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        items.add(new CourseItem(rs.getInt("course_id"), rs.getString("code"),
                                rs.getString("name"), rs.getInt("credits"), rs.getString("category")));
                    }
                }
            }
            return new CoursePage(items, page, size, total);
        } catch (SQLException e) {
            throw SqlErrorMapper.toApiException(e);
        }
    }

    /** 문자열 파라미터를 1번부터 순서대로 바인딩하고 다음 위치를 반환합니다. */
    private static int bind(PreparedStatement statement, List<String> params) throws SQLException {
        int index = 1;
        for (String value : params) {
            statement.setString(index++, value);
        }
        return index;
    }

    private static String escapeLike(String value) {
        return value.replace("" + ESCAPE, "" + ESCAPE + ESCAPE)
                .replace("%", ESCAPE + "%")
                .replace("_", ESCAPE + "_");
    }
}

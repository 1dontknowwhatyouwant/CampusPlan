package com.campusplan.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * 환경 변수(DB_URL, DB_USER, DB_PASSWORD)로 MySQL 연결을 만듭니다.
 * 접속 정보는 코드나 저장소에 두지 않습니다.
 */
public final class DBConnectionManager {

    private DBConnectionManager() {
    }

    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL 드라이버를 찾을 수 없습니다.", e);
        }
        return DriverManager.getConnection(
                requireEnv("DB_URL"), requireEnv("DB_USER"), requireEnv("DB_PASSWORD"));
    }

    private static String requireEnv(String name) throws SQLException {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new SQLException("환경 변수 " + name + " 이(가) 설정되지 않았습니다.");
        }
        return value;
    }
}

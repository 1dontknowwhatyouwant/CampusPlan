package com.campusplan.servlet;

import com.campusplan.config.DBConnectionManager;
import com.campusplan.util.JsonUtil;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/** GET /api/health : 서버와 DB 연결 상태를 확인합니다. DB 에 연결할 수 없으면 503 입니다. */
@WebServlet("/api/health")
public class HealthServlet extends ApiServlet {

    private static final Logger LOG = Logger.getLogger(HealthServlet.class.getName());

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        boolean dbUp = checkDatabase();
        Map<String, String> body = new LinkedHashMap<>();
        body.put("status", dbUp ? "ok" : "degraded");
        body.put("database", dbUp ? "up" : "down");
        JsonUtil.write(response, dbUp ? 200 : 503, body);
    }

    private boolean checkDatabase() {
        try (Connection connection = DBConnectionManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("SELECT 1");
            return true;
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "DB 연결 확인 실패", e);
            return false;
        }
    }
}

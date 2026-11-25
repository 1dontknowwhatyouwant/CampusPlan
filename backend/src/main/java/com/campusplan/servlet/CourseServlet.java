package com.campusplan.servlet;

import com.campusplan.dao.CourseDao;
import com.campusplan.util.JsonUtil;
import com.campusplan.util.Validator;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/** GET /api/courses?q=&category=&page=&size= : 과목 검색 (로그인한 모든 계정) */
@WebServlet("/api/courses")
public class CourseServlet extends ApiServlet {

    private static final List<String> CATEGORIES = List.of("전공", "교양필수", "교양선택");
    private static final int MAX_QUERY_LENGTH = 100;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final CourseDao courseDao = new CourseDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String q = request.getParameter("q");
        String query = (q == null || q.isBlank()) ? null : Validator.requireText(q, "q", MAX_QUERY_LENGTH);

        String c = request.getParameter("category");
        String category = (c == null || c.isEmpty()) ? null : Validator.requireOneOf(c, CATEGORIES, "category");

        int page = Validator.optionalInt(request.getParameter("page"), "page", 0, 0, Integer.MAX_VALUE);
        int size = Validator.optionalInt(request.getParameter("size"), "size", DEFAULT_SIZE, 1, MAX_SIZE);

        JsonUtil.write(response, 200, courseDao.search(query, category, page, size));
    }
}

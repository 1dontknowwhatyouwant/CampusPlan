package com.campusplan.servlet;

import com.campusplan.config.AuthFilter;
import com.campusplan.dao.StudentDao;
import com.campusplan.dto.StudentInfo;
import com.campusplan.util.ApiException;
import com.campusplan.util.JsonUtil;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** GET /api/me : 로그인한 학생의 기본 정보와 소속 (STUDENT 전용은 AuthFilter 가 검사) */
@WebServlet("/api/me")
public class StudentServlet extends ApiServlet {

    private final StudentDao studentDao = new StudentDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int studentId = AuthFilter.currentUser(request).studentId();
        StudentInfo student = studentDao.findById(studentId)
                .orElseThrow(() -> ApiException.notFound("학생 정보를 찾을 수 없습니다."));
        JsonUtil.write(response, 200, student);
    }
}

package com.campusplan.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

public final class JsonUtil {

    // API 명세대로 null 값도 응답에 포함한다. (예: 관리자 계정의 studentId)
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private JsonUtil() {
    }

    public static void write(HttpServletResponse response, int status, Object body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        if (body != null) {
            PrintWriter writer = response.getWriter();
            writer.write(GSON.toJson(body));
        }
    }

    /** 요청 본문 JSON 을 type 으로 읽습니다. 본문이 없거나 JSON 이 아니면 400 입니다. */
    public static <T> T read(HttpServletRequest request, Class<T> type) throws IOException {
        request.setCharacterEncoding("UTF-8");
        try {
            T value = GSON.fromJson(request.getReader(), type);
            if (value == null) {
                throw ApiException.badRequest("요청 본문이 필요합니다.");
            }
            return value;
        } catch (JsonSyntaxException e) {
            throw ApiException.badRequest("요청 본문이 올바른 JSON 이 아닙니다.");
        }
    }
}

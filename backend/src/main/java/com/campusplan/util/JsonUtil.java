package com.campusplan.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Locale;

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

    /**
     * 요청 본문 JSON 을 type 으로 읽습니다. Content-Type 이 application/json 이 아니거나,
     * 본문이 없거나 JSON 이 아니면 400 입니다.
     * (Content-Type 을 강제하면 브라우저가 다른 출처 요청에 preflight 를 거치게 되어, form 으로 위조한 요청을 막는다)
     */
    public static <T> T read(HttpServletRequest request, Class<T> type) throws IOException {
        String contentType = request.getContentType();
        if (contentType == null
                || !contentType.toLowerCase(Locale.ROOT).startsWith("application/json")) {
            throw ApiException.badRequest("Content-Type 은 application/json 이어야 합니다.");
        }
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

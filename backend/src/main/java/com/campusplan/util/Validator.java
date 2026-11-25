package com.campusplan.util;

import java.util.Set;

/** 요청 값 검증. 실패하면 400 INVALID_REQUEST 를 던집니다. DB 의 CHECK 는 마지막 방어선입니다. */
public final class Validator {

    public static final Set<String> GRADES =
            Set.of("A+", "A", "B+", "B", "C+", "C", "D+", "D", "F", "P", "NP");
    public static final Set<String> DAYS = Set.of("월", "화", "수", "목", "금", "토");
    public static final Set<String> SEMESTER_STATUSES = Set.of("예정", "재학", "이수");

    private Validator() {
    }

    public static String requireText(String value, String name, int maxLength) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest(name + " 값이 필요합니다.");
        }
        if (value.length() > maxLength) {
            throw ApiException.badRequest(name + " 은(는) " + maxLength + "자 이하여야 합니다.");
        }
        return value.trim();
    }

    public static <T> T requireNonNull(T value, String name) {
        if (value == null) {
            throw ApiException.badRequest(name + " 값이 필요합니다.");
        }
        return value;
    }

    public static int requirePositive(Integer value, String name) {
        if (value == null || value < 1) {
            throw ApiException.badRequest(name + " 은(는) 1 이상의 정수여야 합니다.");
        }
        return value;
    }

    public static String requireOneOf(String value, Set<String> allowed, String name) {
        if (value == null || !allowed.contains(value)) {
            throw ApiException.badRequest(name + " 값이 올바르지 않습니다. 허용 값: " + allowed);
        }
        return value;
    }

    /** 경로·쿼리 문자열을 정수로 변환합니다. */
    public static int parseId(String raw, String name) {
        try {
            return requirePositive(Integer.valueOf(raw), name);
        } catch (NumberFormatException e) {
            throw ApiException.badRequest(name + " 은(는) 1 이상의 정수여야 합니다.");
        }
    }

    /** 선택 정수 파라미터. 없으면 기본값, 형식이 틀리거나 범위를 벗어나면 400 입니다. */
    public static int optionalInt(String raw, String name, int defaultValue, int min, int max) {
        if (raw == null || raw.isEmpty()) {
            return defaultValue;
        }
        try {
            int value = Integer.parseInt(raw);
            if (value < min || value > max) {
                // 상한이 없는 값(max 가 Integer.MAX_VALUE)은 상한을 메시지에 드러내지 않는다.
                String range = max == Integer.MAX_VALUE
                        ? min + " 이상의 정수여야 합니다."
                        : min + " 이상 " + max + " 이하여야 합니다.";
                throw ApiException.badRequest(name + " 은(는) " + range);
            }
            return value;
        } catch (NumberFormatException e) {
            throw ApiException.badRequest(name + " 은(는) 정수여야 합니다.");
        }
    }
}

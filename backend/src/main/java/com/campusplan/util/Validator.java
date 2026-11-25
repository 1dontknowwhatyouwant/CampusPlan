package com.campusplan.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/** 요청 값 검증. 실패하면 400 INVALID_REQUEST 를 던집니다. DB 의 CHECK 는 마지막 방어선입니다. */
public final class Validator {

    // 허용 값은 순서가 있는 List 로 둔다. 오류 메시지에 항상 같은 순서로 나오게 하기 위해서다.
    public static final List<String> GRADES =
            List.of("A+", "A", "B+", "B", "C+", "C", "D+", "D", "F", "P", "NP");
    public static final List<String> DAYS = List.of("월", "화", "수", "목", "금", "토");
    public static final List<String> SEMESTER_STATUSES = List.of("예정", "재학", "이수");

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

    public static String requireOneOf(String value, List<String> allowed, String name) {
        if (value == null || !allowed.contains(value)) {
            throw ApiException.badRequest(name + " 값이 올바르지 않습니다. 허용 값: " + allowed);
        }
        return value;
    }

    /**
     * yyyy-MM-dd 형식의 날짜 문자열을 읽습니다. 연도는 MySQL DATE 의 지원 범위인 1000~9999 만 받습니다.
     * (LocalDate.parse 는 "+10000-01-01" 같은 5자리 연도도 받아들여, 거르지 않으면 DB 저장 단계에서 500 이 된다)
     */
    public static LocalDate parseDate(String value, String name) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest(name + " 값이 필요합니다.");
        }
        LocalDate date;
        try {
            date = LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest(name + " 은(는) yyyy-MM-dd 형식의 올바른 날짜여야 합니다.");
        }
        if (date.getYear() < 1000 || date.getYear() > 9999) {
            throw ApiException.badRequest(name + " 의 연도는 1000~9999 사이여야 합니다.");
        }
        return date;
    }

    /** 0 보다 크고 소수 첫째 자리까지, max 이하인 수 (DB 의 DECIMAL(n,1) 범위를 넘지 않게 먼저 거른다). */
    public static BigDecimal requirePositiveOneDecimal(BigDecimal value, String name, BigDecimal max) {
        if (value == null) {
            throw ApiException.badRequest(name + " 값이 필요합니다.");
        }
        if (value.signum() <= 0) {
            throw ApiException.badRequest(name + " 은(는) 0보다 커야 합니다.");
        }
        if (value.stripTrailingZeros().scale() > 1) {
            throw ApiException.badRequest(name + " 은(는) 소수 첫째 자리까지만 입력할 수 있습니다.");
        }
        if (value.compareTo(max) > 0) {
            throw ApiException.badRequest(name + " 은(는) " + max.toPlainString() + " 이하여야 합니다.");
        }
        return value.setScale(1);
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

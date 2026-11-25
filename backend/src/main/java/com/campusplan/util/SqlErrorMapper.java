package com.campusplan.util;

import java.sql.SQLException;

/**
 * DB 오류를 API 오류로 바꿉니다. DB 오류 메시지는 응답에 싣지 않습니다.
 *
 * SQLSTATE 45000 은 시간표 충돌(409)과 존재하지 않는 학생(404)에 모두 쓰이므로
 * 여기서 처리하지 않습니다. 해당 엔드포인트의 DAO/서블릿이 직접 변환합니다.
 */
public final class SqlErrorMapper {

    private static final int DUPLICATE_ENTRY = 1062;
    private static final int FOREIGN_KEY_FAIL = 1452;
    private static final int CHECK_VIOLATED = 3819;
    private static final int DEADLOCK = 1213;

    private SqlErrorMapper() {
    }

    /** MySQL 교착 상태 오류(1213)인지. ApiException 의 cause 를 넘겨 확인한다. */
    public static boolean isDeadlock(Throwable cause) {
        return cause instanceof SQLException && ((SQLException) cause).getErrorCode() == DEADLOCK;
    }

    public static ApiException toApiException(SQLException e) {
        switch (e.getErrorCode()) {
            case DUPLICATE_ENTRY:
                return new ApiException(409, "DUPLICATE", "이미 등록된 값입니다.", e);
            case FOREIGN_KEY_FAIL:
                return new ApiException(400, "INVALID_REQUEST", "참조하는 값이 존재하지 않습니다.", e);
            case CHECK_VIOLATED:
                return new ApiException(400, "INVALID_REQUEST", "허용되지 않는 값입니다.", e);
            case DEADLOCK:
                // 동시에 처리된 다른 요청과 잠금이 엇갈려 MySQL 이 이 트랜잭션을 취소한 경우. 다시 보내면 처리될 수 있다.
                return new ApiException(409, "CONCURRENT_UPDATE",
                        "동시에 처리된 다른 요청과 충돌했습니다. 다시 시도해 주세요.", e);
            default:
                return new ApiException(500, "INTERNAL_ERROR", "서버 오류가 발생했습니다.", e);
        }
    }
}

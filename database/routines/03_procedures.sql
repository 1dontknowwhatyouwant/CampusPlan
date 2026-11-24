-- Stored Procedure 정의 (재실행 가능)
-- 관련 테이블: VolunteerRecord
-- DELIMITER 는 MySQL Workbench / mysql CLI 용 명령입니다.
--
-- "누가 승인했는가"는 기록하지 않습니다.
-- 승인 권한 검사(ADMIN 계정만)는 프로시저가 아니라 백엔드의 로그인·역할 검사에서 합니다.

USE campusplan;

DROP PROCEDURE IF EXISTS ApproveVolunteer;
DROP PROCEDURE IF EXISTS ApproveAllPendingVolunteer;

DELIMITER $$

-- 1. 봉사활동 한 건 승인 (대기 상태인 경우만)
--    p_updated: 실제로 승인된 건수 (0 이면 없는 기록이거나 이미 처리된 기록)
CREATE PROCEDURE ApproveVolunteer(
    IN  p_record_id INT,
    OUT p_updated   INT
)
BEGIN
    UPDATE VolunteerRecord
    SET status = '승인',
        approved_at = NOW()
    WHERE record_id = p_record_id
      AND status = '대기';

    SET p_updated = ROW_COUNT();
END$$

-- 2. 학생의 대기 중 봉사활동 일괄 승인 + 승인 누적 시간 반환
--    p_approved_count : 이번에 승인된 건수
--    p_total_hours    : 승인 후 해당 학생의 승인 봉사시간 합계
CREATE PROCEDURE ApproveAllPendingVolunteer(
    IN  p_student_id     INT,
    OUT p_approved_count INT,
    OUT p_total_hours    DECIMAL(7,1)
)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    IF NOT EXISTS (SELECT 1 FROM Student WHERE student_id = p_student_id) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '존재하지 않는 학생입니다.';
    END IF;

    START TRANSACTION;

    UPDATE VolunteerRecord
    SET status = '승인',
        approved_at = NOW()
    WHERE student_id = p_student_id
      AND status = '대기';

    SET p_approved_count = ROW_COUNT();

    SELECT COALESCE(SUM(hours), 0)
      INTO p_total_hours
      FROM VolunteerRecord
     WHERE student_id = p_student_id
       AND status = '승인';

    COMMIT;
END$$

DELIMITER ;

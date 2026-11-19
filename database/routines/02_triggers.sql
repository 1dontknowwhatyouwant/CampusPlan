-- Trigger 정의 (재실행 가능: DROP TRIGGER IF EXISTS 후 재생성)
-- 관련 테이블: Enrollment, Schedule
-- DELIMITER 는 MySQL Workbench / mysql CLI 용 명령입니다. (JDBC 에서는 사용할 수 없음)
--
-- 이수 기준(임시 가정): A+~D, P 는 이수 / F, NP, 성적 미입력은 미이수.
-- 공식 이수 기준이 확인되면 fn_is_completed_grade 의 목록만 수정합니다.

USE campusplan;

DROP TRIGGER IF EXISTS trg_enrollment_insert;
DROP TRIGGER IF EXISTS trg_enrollment_update;
DROP TRIGGER IF EXISTS trg_schedule_conflict;
DROP FUNCTION IF EXISTS fn_is_completed_grade;

DELIMITER $$

-- 성적 → 이수 여부 판정 규칙을 한 곳에서 관리
CREATE FUNCTION fn_is_completed_grade(p_grade VARCHAR(5))
RETURNS BOOLEAN
DETERMINISTIC
NO SQL
BEGIN
    RETURN COALESCE(
        p_grade IN ('A+', 'A', 'B+', 'B', 'C+', 'C', 'D+', 'D', 'P'),
        FALSE
    );
END$$

-- 1. 수강 정보 추가 시 이수 여부 자동 설정
CREATE TRIGGER trg_enrollment_insert
BEFORE INSERT ON Enrollment
FOR EACH ROW
BEGIN
    SET NEW.completed = fn_is_completed_grade(NEW.grade);
END$$

-- 2. 성적 수정 시 이수 여부 자동 변경
CREATE TRIGGER trg_enrollment_update
BEFORE UPDATE ON Enrollment
FOR EACH ROW
BEGIN
    SET NEW.completed = fn_is_completed_grade(NEW.grade);
END$$

-- 3. 같은 학생·같은 학기에서 요일/교시가 겹치는 시간표 등록 방지
CREATE TRIGGER trg_schedule_conflict
BEFORE INSERT ON Schedule
FOR EACH ROW
BEGIN
    IF EXISTS (
        SELECT 1
        FROM Enrollment AS new_e
        JOIN Enrollment AS other_e
            ON other_e.student_id  = new_e.student_id
           AND other_e.semester_id = new_e.semester_id
           AND other_e.enrollment_id <> new_e.enrollment_id
        JOIN Schedule AS s
            ON s.enrollment_id = other_e.enrollment_id
        WHERE new_e.enrollment_id = NEW.enrollment_id
          AND s.day    = NEW.day
          AND s.period = NEW.period
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '같은 학기에 요일/교시가 겹치는 수업이 이미 있습니다.';
    END IF;
END$$

DELIMITER ;

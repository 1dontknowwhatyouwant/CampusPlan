USE campusplan;

-- 기존 트리거가 있다면 삭제
DROP TRIGGER IF EXISTS trg_enrollment_insert;
DROP TRIGGER IF EXISTS trg_enrollment_update;

-- 1. 수강 정보 추가 시 이수 여부 자동 설정
CREATE TRIGGER trg_enrollment_insert
BEFORE INSERT ON Enrollment
FOR EACH ROW
SET NEW.completed =
    CASE
        WHEN NEW.grade IN (
    'A+', 'A', 'B+', 'B',
    'C+', 'C', 'D+', 'D', 'P'
)
        THEN TRUE
        ELSE FALSE
    END;

-- 2. 성적 수정 시 이수 여부 자동 변경
CREATE TRIGGER trg_enrollment_update
BEFORE UPDATE ON Enrollment
FOR EACH ROW
SET NEW.completed =
    CASE
        WHEN NEW.grade IN (
    'A+', 'A', 'B+', 'B',
    'C+', 'C', 'D+', 'D', 'P'
)
        THEN TRUE
        ELSE FALSE
    END;

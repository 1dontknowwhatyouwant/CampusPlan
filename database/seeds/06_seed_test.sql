-- ============================================================================
-- [테스트 데이터] 개발·시연용 합성 데이터입니다. 실제 학생·학사 자료가 아닙니다.
--   * 학생, 과목, 성적, 봉사 내역, 졸업 규칙의 기준값은 모두 임의로 만든 값입니다.
--   * 아래 GraduationRule 의 기준 학점/시간은 시연용 임시값이며 실제 졸업 기준이 아닙니다.
--     (is_verified = FALSE 로 표시)
--   * 초기화 전용 스크립트입니다. 같은 DB 에 두 번 실행하면 키 중복 오류가 납니다.
--   * 선행 조건: 01~04 실행 완료, 05_seed_master.sql 로 Department 데이터 입력 완료
--   * Enrollment.completed 는 트리거가 grade 로부터 계산하므로 입력하지 않습니다.
-- ============================================================================

USE campusplan;

-- 1. 학생 테스트 데이터 (소속은 Department 기준 데이터에서 조회)
INSERT INTO Student (student_id, name, department_id, admission_year)
VALUES
(1, '김학생',
    (SELECT department_id FROM Department
      WHERE admission_year = 2024 AND department_name = '항공소프트웨어공학과'),
    2024),
(2, '이학생',
    (SELECT department_id FROM Department
      WHERE admission_year = 2023 AND department_name = '항공소프트웨어공학과'),
    2023);

-- 2. 학기 테스트 데이터
INSERT INTO Semester (semester_id, student_id, year, term, status)
VALUES
(1, 1, 2026, 1, '이수'),
(2, 1, 2026, 2, '재학'),
(3, 2, 2026, 1, '이수'),
(4, 1, 2025, 2, '이수');

-- 3. 과목 테스트 데이터 (실제 개설 과목이 아님)
--    course_id 는 05_seed_master.sql 의 실제 과목과 겹치지 않도록 자동 증가에 맡기고,
--    아래 수강 이력·졸업 규칙에서는 과목코드(TEST-*)로 조회합니다.
INSERT INTO Course (code, name, credits, category)
VALUES
('TEST-DB101', '[테스트] 데이터베이스',     3, '전공'),
('TEST-SW201', '[테스트] 소프트웨어공학',   3, '전공'),
('TEST-GE101', '[테스트] 지역사회와봉사',   2, '교양필수'),
('TEST-GE102', '[테스트] 인간과사회',       3, '교양선택'),
('TEST-SW301', '[테스트] 웹프로그래밍',     3, '전공');

-- 4. 수강 이력 테스트 데이터
--    (1, 소프트웨어공학): 2025-2 에 F → 2026-1 에 재수강 B  (재수강 시 학점 중복 집계 방지 확인용)
INSERT INTO Enrollment (enrollment_id, student_id, semester_id, course_id, grade)
VALUES
(1, 1, 4,
    (SELECT course_id FROM Course WHERE code = 'TEST-SW201'), 'F'),
(2, 1, 1,
    (SELECT course_id FROM Course WHERE code = 'TEST-DB101'), 'A'),
(3, 1, 1,
    (SELECT course_id FROM Course WHERE code = 'TEST-SW201'), 'B'),
(4, 1, 1,
    (SELECT course_id FROM Course WHERE code = 'TEST-GE101'), 'A'),
(5, 1, 2,
    (SELECT course_id FROM Course WHERE code = 'TEST-GE102'), NULL),
(6, 1, 2,
    (SELECT course_id FROM Course WHERE code = 'TEST-SW301'), NULL),
(7, 2, 3,
    (SELECT course_id FROM Course WHERE code = 'TEST-DB101'), 'B');

-- 5. 시간표 테스트 데이터 (현재 학기 수강 과목)
--    같은 학생·학기의 같은 요일/교시를 추가로 넣으면 trg_schedule_conflict 가 거부합니다.
INSERT INTO Schedule (enrollment_id, day, period, room)
VALUES
(5, '월', 1, 'TEST-101'),
(5, '월', 2, 'TEST-101'),
(6, '화', 3, 'TEST-202'),
(6, '화', 4, 'TEST-202');

-- 6. 봉사활동 테스트 데이터
INSERT INTO VolunteerRecord (student_id, activity_name, activity_date, hours, status, approved_at)
VALUES
(1, '지역사회 환경정화', '2026-09-10', 5.0, '승인', '2026-09-12 10:00:00'),
(1, '도서관 봉사',       '2026-10-15', 4.0, '대기', NULL),
(1, '복지관 봉사',       '2026-11-02', 6.0, '승인', '2026-11-04 10:00:00'),
(2, '환경보호 캠페인',   '2026-10-20', 3.0, '승인', '2026-10-22 10:00:00');

-- 7. 졸업 규칙 테스트 데이터 (시연용 임시값, 실제 졸업 기준 아님)
INSERT INTO GraduationRule
    (name, rule_type, required_value, required_course_id,
     department_name, admission_year_from, admission_year_to, is_verified, source)
VALUES
('[테스트] 총 이수학점',     'TOTAL_CREDITS',   10.0, NULL, NULL, 2023, NULL, FALSE, '시연용 임시값'),
('[테스트] 전공학점',        'MAJOR_CREDITS',    6.0, NULL, '항공소프트웨어공학과', 2023, NULL, FALSE, '시연용 임시값'),
('[테스트] 교양학점',        'GENERAL_CREDITS',  5.0, NULL, NULL, 2023, NULL, FALSE, '시연용 임시값'),
('[테스트] 봉사시간',        'VOLUNTEER_HOURS', 15.0, NULL, NULL, 2023, NULL, FALSE, '시연용 임시값'),
('[테스트] 필수과목 소프트웨어공학', 'REQUIRED_COURSE', NULL,
    (SELECT course_id FROM Course WHERE code = 'TEST-SW201'), '항공소프트웨어공학과', 2023, NULL, FALSE, '시연용 임시값');

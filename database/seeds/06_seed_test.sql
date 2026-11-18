USE campusplan;

-- 1. 학생 테스트 데이터
INSERT INTO Student
(student_id, name, department, admission_year)
VALUES
(1, '김학생', '항공소프트웨어공학과', 2024),
(2, '이학생', '항공소프트웨어공학과', 2023);

-- 2. 학기 테스트 데이터
INSERT INTO Semester
(semester_id, student_id, year, term, status)
VALUES
(1, 1, 2026, 1, '이수'),
(2, 1, 2026, 2, '재학'),
(3, 2, 2026, 1, '이수');

-- 3. 과목 테스트 데이터
INSERT INTO Course
(course_id, code, name, credits, category)
VALUES
(1, 'DB101', '데이터베이스', 3, '전공'),
(2, 'SW201', '소프트웨어공학', 3, '전공'),
(3, 'GE101', '지역사회와봉사', 2, '교양필수'),
(4, 'GE102', '인간과사회', 3, '교양선택');

-- 4. 수강 이력 테스트 데이터
INSERT INTO Enrollment
(student_id, semester_id, course_id, grade, completed)
VALUES
(1, 1, 1, 'A', TRUE),
(1, 1, 2, 'B', TRUE),
(1, 1, 3, 'A', TRUE),
(1, 2, 4, NULL, FALSE),
(2, 3, 1, 'B', TRUE);

-- 5. 봉사활동 테스트 데이터
INSERT INTO VolunteerRecord
(student_id, activity_name, activity_date, hours, status)
VALUES
(1, '지역사회 환경정화', '2026-09-10', 5.0, '승인'),
(1, '도서관 봉사', '2026-10-15', 4.0, '대기'),
(1, '복지관 봉사', '2026-11-02', 6.0, '승인'),
(2, '환경보호 캠페인', '2026-10-20', 3.0, '승인');
-- CampusPlan 스키마 (설계 초안 v2)
--
-- 적용 순서: 01_schema → 02_triggers → 03_procedures → 04_views → 05_seed_master → 06_seed_test
-- 이 파일은 IF NOT EXISTS 로 작성되어 있어, 이미 이전 버전 테이블이 있는 DB에서는
-- 구조가 바뀌지 않습니다. 개발용 로컬 DB라면 DROP DATABASE campusplan; 후 다시 실행하세요.
-- (운영 데이터가 있는 DB에는 실행하지 않습니다.)
--
-- 성적 척도(A+~F, P/NP)와 과목 구분(category) 값은 공식 확인 전의 임시 가정입니다.
-- 공식 자료로 확정되면 CHECK 조건을 함께 수정합니다.

CREATE DATABASE IF NOT EXISTS campusplan
    CHARACTER SET utf8mb4;
USE campusplan;

-- 1. 학부 및 학과 정보 (입학년도별 소속, 출처 URL 기록)
CREATE TABLE IF NOT EXISTS Department (
    department_id   INT AUTO_INCREMENT PRIMARY KEY,
    admission_year  INT NOT NULL,
    college         VARCHAR(100) NOT NULL,
    department_name VARCHAR(100) NOT NULL,
    source_url      VARCHAR(500),

    UNIQUE (admission_year, college, department_name)
);

-- 2. 학생 정보
CREATE TABLE IF NOT EXISTS Student (
    student_id     INT AUTO_INCREMENT PRIMARY KEY,
    name           VARCHAR(50) NOT NULL,
    department_id  INT NOT NULL,
    admission_year INT NOT NULL,

    FOREIGN KEY (department_id)
        REFERENCES Department(department_id)
);

-- 3. 학기 정보
CREATE TABLE IF NOT EXISTS Semester (
    semester_id INT AUTO_INCREMENT PRIMARY KEY,
    student_id  INT NOT NULL,
    year        INT NOT NULL,
    term        INT NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT '재학',

    FOREIGN KEY (student_id)
        REFERENCES Student(student_id),

    CHECK (term IN (1, 2)),
    CHECK (status IN ('예정', '재학', '이수')),

    UNIQUE (student_id, year, term),
    -- Enrollment 가 (semester_id, student_id) 쌍으로 참조할 수 있게 함
    UNIQUE (semester_id, student_id)
);

-- 4. 과목 정보
CREATE TABLE IF NOT EXISTS Course (
    course_id INT AUTO_INCREMENT PRIMARY KEY,
    code      VARCHAR(30) NOT NULL UNIQUE,
    name      VARCHAR(100) NOT NULL,
    credits   INT NOT NULL,
    category  VARCHAR(30) NOT NULL,

    CHECK (credits >= 0),
    CHECK (category IN ('전공필수', '전공선택', '교양필수', '교양선택', '일반선택'))
);

-- 5. 수강 이력
--    completed 는 트리거(02_triggers.sql)가 grade 로부터 자동 계산합니다.
--    재수강은 다른 학기에 같은 과목을 다시 등록하는 방식이며,
--    학점 집계(04_views.sql)에서는 과목당 한 번만 인정합니다.
CREATE TABLE IF NOT EXISTS Enrollment (
    enrollment_id INT AUTO_INCREMENT PRIMARY KEY,
    student_id    INT NOT NULL,
    semester_id   INT NOT NULL,
    course_id     INT NOT NULL,
    grade         VARCHAR(5),
    completed     BOOLEAN NOT NULL DEFAULT FALSE,

    -- 학기와 학생이 서로 다른 학생을 가리키지 못하게 하는 복합 외래 키
    FOREIGN KEY (semester_id, student_id)
        REFERENCES Semester(semester_id, student_id),

    FOREIGN KEY (course_id)
        REFERENCES Course(course_id),

    CHECK (grade IS NULL OR grade IN
        ('A+', 'A', 'B+', 'B', 'C+', 'C', 'D+', 'D', 'F', 'P', 'NP')),

    -- 같은 학기에 같은 과목을 두 번 등록할 수 없음
    UNIQUE (semester_id, course_id)
);

-- 6. 시간표 (충돌 검사는 트리거 trg_schedule_conflict)
CREATE TABLE IF NOT EXISTS Schedule (
    schedule_id   INT AUTO_INCREMENT PRIMARY KEY,
    enrollment_id INT NOT NULL,
    day           VARCHAR(10) NOT NULL,
    period        INT NOT NULL,
    room          VARCHAR(50),

    FOREIGN KEY (enrollment_id)
        REFERENCES Enrollment(enrollment_id),

    CHECK (day IN ('월', '화', '수', '목', '금', '토')),
    CHECK (period >= 1),

    UNIQUE (enrollment_id, day, period)
);

-- 7. 졸업 요건
--    rule_type 별 사용 컬럼:
--      TOTAL_CREDITS / MAJOR_CREDITS / GENERAL_CREDITS : required_value = 필요 학점
--      VOLUNTEER_HOURS                                 : required_value = 필요 봉사시간
--      REQUIRED_COURSE                                 : required_course_id = 필수 과목
--    department_name 이 NULL 이면 전체 학과 공통 규칙입니다. Department 는 입학년도별
--    행이므로 학과명으로 매칭하고, 적용 입학년도는 admission_year_from~to 로 지정합니다.
--    is_verified 가 FALSE 인 규칙은 공식 자료로 확인되지 않은 임시/테스트 규칙입니다.
CREATE TABLE IF NOT EXISTS GraduationRule (
    rule_id            INT AUTO_INCREMENT PRIMARY KEY,
    name               VARCHAR(100) NOT NULL,
    rule_type          VARCHAR(30) NOT NULL,
    required_value     DECIMAL(6,1),
    required_course_id INT,
    department_name    VARCHAR(100),
    admission_year_from INT NOT NULL,
    admission_year_to   INT,
    is_verified        BOOLEAN NOT NULL DEFAULT FALSE,
    source             VARCHAR(500),

    FOREIGN KEY (required_course_id)
        REFERENCES Course(course_id),

    CHECK (rule_type IN ('TOTAL_CREDITS', 'MAJOR_CREDITS', 'GENERAL_CREDITS',
                         'VOLUNTEER_HOURS', 'REQUIRED_COURSE')),
    CHECK ((rule_type = 'REQUIRED_COURSE' AND required_course_id IS NOT NULL
            AND required_value IS NULL)
        OR (rule_type <> 'REQUIRED_COURSE' AND required_course_id IS NULL
            AND required_value IS NOT NULL AND required_value >= 0)),
    CHECK (admission_year_to IS NULL OR admission_year_to >= admission_year_from)
);

-- 8. 봉사활동 내역
CREATE TABLE IF NOT EXISTS VolunteerRecord (
    record_id     INT AUTO_INCREMENT PRIMARY KEY,
    student_id    INT NOT NULL,
    activity_name VARCHAR(100) NOT NULL,
    activity_date DATE NOT NULL,
    hours         DECIMAL(5,1) NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT '대기',
    approved_at   DATETIME NULL,

    FOREIGN KEY (student_id)
        REFERENCES Student(student_id),

    CHECK (hours > 0),
    CHECK (status IN ('대기', '승인', '반려'))
);

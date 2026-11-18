
-- CampusPlan 데이터베이스 생성
CREATE DATABASE IF NOT EXISTS campusplan
CHARACTER SET utf8mb4;
USE campusplan;

-- 1. 학생 정보
CREATE TABLE IF NOT EXISTS Student (
    student_id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    department VARCHAR(100) NOT NULL,
    admission_year INT NOT NULL
);

-- 2. 학기 정보
CREATE TABLE IF NOT EXISTS Semester (
    semester_id INT AUTO_INCREMENT PRIMARY KEY,
    student_id INT NOT NULL,
    year INT NOT NULL,
    term INT NOT NULL,
    status VARCHAR(20),

    FOREIGN KEY (student_id)
        REFERENCES Student(student_id),

    CHECK (term IN (1, 2))
);

-- 3. 과목 정보
CREATE TABLE IF NOT EXISTS Course (
    course_id INT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    credits INT NOT NULL,
    category VARCHAR(30) NOT NULL,

    CHECK (credits >= 0)
);

-- 4. 수강 이력
CREATE TABLE IF NOT EXISTS Enrollment (
    enrollment_id INT AUTO_INCREMENT PRIMARY KEY,
    student_id INT NOT NULL,
    semester_id INT NOT NULL,
    course_id INT NOT NULL,
    grade VARCHAR(5),
    completed BOOLEAN DEFAULT FALSE,

    FOREIGN KEY (student_id)
        REFERENCES Student(student_id),

    FOREIGN KEY (semester_id)
        REFERENCES Semester(semester_id),

    FOREIGN KEY (course_id)
        REFERENCES Course(course_id)
);

-- 5. 시간표
CREATE TABLE IF NOT EXISTS Schedule (
    schedule_id INT AUTO_INCREMENT PRIMARY KEY,
    enrollment_id INT NOT NULL,
    day VARCHAR(10) NOT NULL,
    period INT NOT NULL,
    room VARCHAR(50),

    FOREIGN KEY (enrollment_id)
        REFERENCES Enrollment(enrollment_id)
);

-- 6. 졸업 요건
CREATE TABLE IF NOT EXISTS GraduationRule (
    rule_id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    category VARCHAR(30) NOT NULL,
    required_credits INT,
    required_course_code VARCHAR(30)
);

-- 7. 봉사활동 내역
CREATE TABLE IF NOT EXISTS VolunteerRecord (
    record_id INT AUTO_INCREMENT PRIMARY KEY,
    student_id INT NOT NULL,
    activity_name VARCHAR(100) NOT NULL,
    activity_date DATE NOT NULL,
    hours DECIMAL(5,1) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT '대기',

    FOREIGN KEY (student_id)
        REFERENCES Student(student_id),

    CHECK (hours > 0),
    CHECK (status IN ('대기', '승인', '반려'))
);


-- 학부 및 학과 정보 테이블
CREATE TABLE IF NOT EXISTS Department (
    department_id INT AUTO_INCREMENT PRIMARY KEY,
    admission_year INT NOT NULL,
    college VARCHAR(100) NOT NULL,
    department_name VARCHAR(100) NOT NULL,
    source_url VARCHAR(500),
    UNIQUE (admission_year, college, department_name)
);

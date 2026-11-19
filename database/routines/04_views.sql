-- View 정의 (재실행 가능: CREATE OR REPLACE)
-- 관련 테이블: Student, Department, Enrollment, Course, VolunteerRecord, GraduationRule
-- 의존 순서: CreditSummary, VolunteerSummary → GraduationCheck
--
-- 학점 집계 기준:
--   * completed = 1 인 수강만 인정
--   * 재수강 등으로 같은 과목 이력이 여러 개면 과목당 한 번만 인정

USE campusplan;

-- 1. 학생별 학점 집계
CREATE OR REPLACE VIEW campusplan.CreditSummary AS
SELECT
    s.student_id,
    s.name,
    COALESCE(SUM(CASE WHEN ce.completed = 1
                      THEN c.credits ELSE 0 END), 0) AS total_credits,
    COALESCE(SUM(CASE WHEN ce.completed = 1 AND c.category LIKE '전공%'
                      THEN c.credits ELSE 0 END), 0) AS major_credits,
    COALESCE(SUM(CASE WHEN ce.completed = 1 AND c.category LIKE '교양%'
                      THEN c.credits ELSE 0 END), 0) AS general_credits
FROM campusplan.Student s
LEFT JOIN (
    SELECT student_id, course_id, MAX(completed) AS completed
    FROM campusplan.Enrollment
    GROUP BY student_id, course_id
) ce ON ce.student_id = s.student_id
LEFT JOIN campusplan.Course c
    ON c.course_id = ce.course_id
GROUP BY s.student_id, s.name;

-- 2. 학생별 승인 봉사시간 합계
CREATE OR REPLACE VIEW campusplan.VolunteerSummary AS
SELECT
    s.student_id,
    s.name,
    COALESCE(SUM(CASE WHEN v.status = '승인' THEN v.hours ELSE 0 END), 0) AS approved_hours
FROM campusplan.Student s
LEFT JOIN campusplan.VolunteerRecord v
    ON v.student_id = s.student_id
GROUP BY s.student_id, s.name;

-- 3. 졸업요건 진단: 학생 × 적용되는 졸업 규칙
--    current_value : 현재 달성값 (학점, 봉사시간, 필수과목은 이수 시 1 / 미이수 시 0)
--    required_value: 기준값 (필수과목은 1)
--    is_met        : 1 충족 / 0 미충족
--    is_verified   : 0 이면 공식 확인 전 임시·테스트 규칙 (화면에서 구분 표시 필요)
CREATE OR REPLACE VIEW campusplan.GraduationCheck AS
SELECT
    t.student_id,
    t.name,
    t.rule_id,
    t.rule_name,
    t.rule_type,
    t.current_value,
    t.required_value,
    t.is_verified,
    CASE WHEN t.current_value >= t.required_value THEN 1 ELSE 0 END AS is_met
FROM (
    SELECT
        s.student_id,
        s.name,
        r.rule_id,
        r.name AS rule_name,
        r.rule_type,
        r.is_verified,
        CASE r.rule_type
            WHEN 'TOTAL_CREDITS'   THEN cs.total_credits
            WHEN 'MAJOR_CREDITS'   THEN cs.major_credits
            WHEN 'GENERAL_CREDITS' THEN cs.general_credits
            WHEN 'VOLUNTEER_HOURS' THEN vs.approved_hours
            WHEN 'REQUIRED_COURSE' THEN
                CASE WHEN EXISTS (
                    SELECT 1
                    FROM campusplan.Enrollment e
                    WHERE e.student_id = s.student_id
                      AND e.course_id  = r.required_course_id
                      AND e.completed  = 1
                ) THEN 1 ELSE 0 END
        END AS current_value,
        CASE r.rule_type
            WHEN 'REQUIRED_COURSE' THEN 1
            ELSE r.required_value
        END AS required_value
    FROM campusplan.Student s
    JOIN campusplan.Department d
        ON d.department_id = s.department_id
    JOIN campusplan.GraduationRule r
        ON (r.department_name IS NULL OR r.department_name = d.department_name)
       AND s.admission_year >= r.admission_year_from
       AND (r.admission_year_to IS NULL OR s.admission_year <= r.admission_year_to)
    JOIN campusplan.CreditSummary cs
        ON cs.student_id = s.student_id
    JOIN campusplan.VolunteerSummary vs
        ON vs.student_id = s.student_id
) AS t;

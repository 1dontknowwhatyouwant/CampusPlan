USE campusplan;
CREATE OR REPLACE VIEW campusplan.CreditSummary AS
SELECT
    s.student_id,
    s.name,
    COALESCE(SUM(
        CASE
            WHEN e.completed = 1
            THEN c.credits
            ELSE 0
        END
    ), 0) AS total_credits,
    COALESCE(SUM(
        CASE
            WHEN e.completed = 1
                 AND c.category = '전공'
            THEN c.credits
            ELSE 0
        END
    ), 0) AS major_credits,
    COALESCE(SUM(
        CASE
            WHEN e.completed = 1
                 AND c.category LIKE '교양%'
            THEN c.credits
            ELSE 0
        END
    ), 0) AS general_credits
FROM campusplan.Student s
LEFT JOIN campusplan.Enrollment e
    ON s.student_id = e.student_id
LEFT JOIN campusplan.Course c
    ON e.course_id = c.course_id
GROUP BY s.student_id, s.name;


CREATE OR REPLACE VIEW campusplan.VolunteerSummary AS
SELECT
    s.student_id,
    s.name,
    COALESCE(SUM(
        CASE
            WHEN v.status = '승인'
            THEN v.hours
            ELSE 0
        END
    ), 0) AS approved_hours
FROM campusplan.Student s
LEFT JOIN campusplan.VolunteerRecord v
    ON s.student_id = v.student_id
GROUP BY s.student_id, s.name;

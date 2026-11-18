USE campusplan;
DROP PROCEDURE IF EXISTS ApproveVolunteer;

CREATE PROCEDURE ApproveVolunteer(
    IN p_record_id INT
)
UPDATE VolunteerRecord
SET status = '승인'
WHERE record_id = p_record_id
  AND status = '대기';
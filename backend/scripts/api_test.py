#!/usr/bin/env python3
"""CampusPlan API 시나리오 테스트.

실행 중인 백엔드(backend/scripts/dev.sh)와 개발용 MySQL 컨테이너를 대상으로
API 응답을 DB 직접 조회 결과와 대조합니다. 표준 라이브러리만 사용합니다.

조회 테스트는 DB를 바꾸지 않습니다. 쓰기 테스트는 테스트용 행을 만들어 API로 처리한 뒤
만든 행만 지우고, 시작 때와 같은 상태로 돌아왔는지 확인합니다 (자동 증가 번호는 되돌리지 않음).

사용법 (저장소 루트에서):
    python3 backend/scripts/api_test.py              # 조회 + 쓰기 테스트
    python3 backend/scripts/api_test.py --read-only  # 조회 테스트만 (DB 쓰기 없음)

환경 변수 (선택):
    API_BASE        기본 http://localhost:8080
    TEST_PASSWORD   테스트 계정 비밀번호 (기본값: database/seeds/06_seed_test.sql 주석의 테스트 비밀번호)
    MYSQL_CONTAINER 기본 campusplan-mysql
    MYSQL_ROOT_PASSWORD 없으면 저장소 루트 .env 에서 읽음

DB 조회 결과를 기대값으로 쓰므로 테스트 데이터(06_seed_test.sql)가 바뀌어도 대부분 그대로 동작합니다.
"""
import http.cookiejar
import json
import os
import re
import subprocess
import sys
import threading
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
API_BASE = os.environ.get("API_BASE", "http://localhost:8080")
TEST_PASSWORD = os.environ.get("TEST_PASSWORD", "campusplan-test")
CONTAINER = os.environ.get("MYSQL_CONTAINER", "campusplan-mysql")
FRONT_ORIGIN = "http://localhost:3000"

results = {"pass": 0, "fail": 0}


def check(name, condition, detail=""):
    if condition:
        results["pass"] += 1
        print(f"  PASS  {name}")
    else:
        results["fail"] += 1
        print(f"  FAIL  {name}" + (f"  ({detail})" if detail else ""))


# ---------------------------------------------------------------- DB

def mysql_password():
    if os.environ.get("MYSQL_ROOT_PASSWORD"):
        return os.environ["MYSQL_ROOT_PASSWORD"]
    env = ROOT / ".env"
    if env.exists():
        for line in env.read_text(encoding="utf-8").splitlines():
            if line.startswith("MYSQL_ROOT_PASSWORD="):
                return line.split("=", 1)[1].strip().strip("'\"")
    sys.exit("MYSQL_ROOT_PASSWORD 를 찾을 수 없습니다 (.env 또는 환경 변수)")


DB_PASSWORD = mysql_password()


def db(sql, *params):
    """SELECT 결과를 행 목록(문자열, NULL 은 None)으로 반환. params 는 정수만 허용."""
    for p in params:
        if not isinstance(p, int):
            raise TypeError("db() 파라미터는 정수만 허용합니다")
    sql = sql % params if params else sql
    out = subprocess.run(
        ["docker", "exec", "-e", f"MYSQL_PWD={DB_PASSWORD}", CONTAINER,
         "mysql", "-uroot", "-N", "-B", "--default-character-set=utf8mb4", "campusplan", "-e", sql],
        capture_output=True, text=True, check=True).stdout
    rows = []
    for line in out.splitlines():
        rows.append([None if v == "NULL" else v for v in line.split("\t")])
    return rows


# ---------------------------------------------------------------- HTTP

class Client:
    def __init__(self):
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))

    def call(self, method, path, body=None, headers=None):
        data = json.dumps(body).encode() if body is not None else None
        req = urllib.request.Request(API_BASE + path, data=data, method=method, headers=headers or {})
        if data is not None and "Content-Type" not in (headers or {}):
            req.add_header("Content-Type", "application/json")
        try:
            with self.opener.open(req) as res:
                return res.status, res.headers, self._json(res.read())
        except urllib.error.HTTPError as e:
            return e.code, e.headers, self._json(e.read())

    @staticmethod
    def _json(raw):
        if not raw:
            return None
        try:
            return json.loads(raw)
        except ValueError:
            return raw.decode(errors="replace")

    def login(self, login_id, password=TEST_PASSWORD):
        return self.call("POST", "/api/auth/login", {"loginId": login_id, "password": password})


def error_code(body):
    return body.get("error", {}).get("code") if isinstance(body, dict) else None


def q(text):
    return urllib.parse.quote(text)


# ---------------------------------------------------------------- 시나리오

def test_public_and_auth():
    print("[공통·인증]")
    c = Client()
    status, _, body = c.call("GET", "/api/health")
    check("health 200, DB up", status == 200 and body == {"status": "ok", "database": "up"}, body)

    for path in ["/api/auth/me", "/api/me", "/api/courses", "/api/me/credits"]:
        status, _, body = c.call("GET", path)
        check(f"미로그인 {path} -> 401 UNAUTHENTICATED", status == 401 and error_code(body) == "UNAUTHENTICATED")

    status, _, body = c.login("student1", "wrong-password")
    check("비밀번호 오류 -> 401 INVALID_CREDENTIALS", status == 401 and error_code(body) == "INVALID_CREDENTIALS")
    wrong_msg = body
    status, _, body = c.login("no-such-user", "wrong-password")
    check("없는 아이디 -> 같은 401 응답", status == 401 and body == wrong_msg)
    status, _, body = c.call("POST", "/api/auth/login", {"loginId": "student1"})
    check("password 누락 -> 400", status == 400 and error_code(body) == "INVALID_REQUEST")

    status, headers, body = c.login("student1")
    cookie = headers.get("Set-Cookie", "")
    check("학생 로그인 200, role STUDENT", status == 200 and body.get("role") == "STUDENT")
    check("세션 쿠키 HttpOnly, SameSite=Lax", "HttpOnly" in cookie and "SameSite=Lax" in cookie, cookie)
    check("로그인 응답에 비밀번호·해시 없음", "password" not in json.dumps(body).lower() and "pbkdf2" not in json.dumps(body))

    status, _, body = c.call("GET", "/api/admin/volunteers")
    check("학생 -> /api/admin/* 403", status == 403 and error_code(body) == "FORBIDDEN")
    status, _, body = c.call("GET", "/api/no-such-path")
    check("없는 경로 -> 404 JSON", status == 404 and error_code(body) == "NOT_FOUND")
    status, _, body = c.call("POST", "/api/courses", {})
    check("지원하지 않는 메서드 -> 405 JSON", status == 405 and error_code(body) == "METHOD_NOT_ALLOWED")

    status, _, _ = c.call("POST", "/api/auth/logout")
    check("로그아웃 204", status == 204)
    status, _, _ = c.call("GET", "/api/auth/me")
    check("로그아웃 후 me 401", status == 401)

    admin = Client()
    status, _, body = admin.login("admin")
    check("관리자 로그인, studentId null", status == 200 and body.get("role") == "ADMIN" and body.get("studentId") is None)
    for path in ["/api/me", "/api/me/credits", "/api/enrollments/1"]:
        status, _, _ = admin.call("GET", path)
        check(f"관리자 -> {path} 403", status == 403)
    status, _, _ = admin.call("GET", "/api/courses")
    check("관리자 -> /api/courses 200", status == 200)

    # 세션 고정 방지: 같은 쿠키로 다시 로그인하면 이전 세션 ID 는 무효
    old_id = next(ck.value for ck in admin.jar if ck.name == "JSESSIONID")
    admin.login("admin")
    new_id = next(ck.value for ck in admin.jar if ck.name == "JSESSIONID")
    status, _, _ = Client().call("GET", "/api/auth/me", headers={"Cookie": f"JSESSIONID={old_id}"})
    check("재로그인 후 이전 세션 무효", old_id != new_id and status == 401)
    status, _, _ = Client().call("GET", "/api/auth/me", headers={"Cookie": f"JSESSIONID={new_id}"})
    check("재로그인 후 새 세션 유효", status == 200)


def test_cors():
    print("[CORS]")
    c = Client()
    status, headers, _ = c.call("OPTIONS", "/api/auth/login",
                                headers={"Origin": FRONT_ORIGIN, "Access-Control-Request-Method": "POST"})
    check("허용 origin preflight 204 + 쿠키 허용",
          status == 204 and headers.get("Access-Control-Allow-Origin") == FRONT_ORIGIN
          and headers.get("Access-Control-Allow-Credentials") == "true")
    status, headers, _ = c.call("GET", "/api/auth/me", headers={"Origin": FRONT_ORIGIN})
    check("401 응답에도 CORS 헤더", status == 401 and headers.get("Access-Control-Allow-Origin") == FRONT_ORIGIN)
    _, headers, _ = c.call("GET", "/api/auth/me", headers={"Origin": "http://evil.example"})
    check("허용되지 않은 origin 에는 CORS 헤더 없음", headers.get("Access-Control-Allow-Origin") is None)


def test_courses():
    print("[과목]")
    c = Client()
    c.login("student1")
    total = int(db("SELECT COUNT(*) FROM Course")[0][0])
    status, _, body = c.call("GET", "/api/courses")
    check("기본 조회 totalCount = DB 전체", status == 200 and body["totalCount"] == total, body.get("totalCount"))
    check("기본 size 20", len(body["items"]) == min(20, total))

    for category in ["전공", "교양필수", "교양선택"]:
        expected = int(db(f"SELECT COUNT(*) FROM Course WHERE category = '{category}'")[0][0])
        _, _, body = c.call("GET", f"/api/courses?category={q(category)}&size=1")
        check(f"category={category} totalCount = DB {expected}", body["totalCount"] == expected, body["totalCount"])

    literal = int(db("SELECT COUNT(*) FROM Course WHERE name LIKE '%!%%' ESCAPE '!' OR code LIKE '%!%%' ESCAPE '!'")[0][0])
    _, _, body = c.call("GET", f"/api/courses?q={q('%')}")
    check(f"q=% 는 문자 그대로 검색 ({literal}건)", body["totalCount"] == literal, body["totalCount"])
    _, _, body = c.call("GET", f"/api/courses?q={q(chr(39) + ' OR 1=1 --')}")
    check("SQL 주입 문자열 0건", body["totalCount"] == 0)

    for query, label in [("category=x", "잘못된 category"), ("size=0", "size 0"), ("size=101", "size 101"),
                         ("page=-1", "page -1"), ("page=abc", "page 문자"), ("q=" + "a" * 101, "101자 q")]:
        status, _, body = c.call("GET", f"/api/courses?{query}")
        check(f"{label} -> 400", status == 400 and error_code(body) == "INVALID_REQUEST")
    _, _, body = c.call("GET", "/api/me/volunteers?status=x")
    message = body["error"]["message"] if isinstance(body, dict) and "error" in body else ""
    check("허용 값이 정의 순서로 표시", "[대기, 승인, 반려]" in message, message)


def test_student(login_id):
    student_id = int(db(f"SELECT student_id FROM Account WHERE login_id = '{login_id}'")[0][0])
    print(f"[학생 {login_id} (student_id={student_id})]")
    c = Client()
    c.login(login_id)

    # 학생 정보
    row = db("SELECT s.name, s.admission_year, d.college, d.department_name FROM Student s "
             "JOIN Department d ON d.department_id = s.department_id WHERE s.student_id = %d", student_id)[0]
    _, _, body = c.call("GET", "/api/me")
    check("/api/me = DB", body == {"studentId": student_id, "name": row[0], "admissionYear": int(row[1]),
                                    "college": row[2], "departmentName": row[3]}, body)

    # 학점
    row = db("SELECT total_credits, major_credits, general_credits FROM CreditSummary WHERE student_id = %d",
             student_id)[0]
    _, _, body = c.call("GET", "/api/me/credits")
    check("/api/me/credits = CreditSummary",
          [body["totalCredits"], body["majorCredits"], body["generalCredits"]] == [int(v) for v in row], body)

    # 졸업 진단
    rows = db("SELECT rule_id, current_value, required_value, is_met, is_verified FROM GraduationCheck "
              "WHERE student_id = %d ORDER BY rule_id", student_id)
    _, _, body = c.call("GET", "/api/me/graduation-check")
    api_rules = [(r["ruleId"], r["currentValue"], r["requiredValue"], r["isMet"], r["isVerified"]) for r in body["rules"]]
    db_rules = [(int(r[0]), float(r[1]), float(r[2]), r[3] == "1", r[4] == "1") for r in rows]
    check(f"graduation-check 규칙 {len(db_rules)}개 = GraduationCheck", api_rules == db_rules)
    expected_all = None if not db_rules else all(r[3] for r in db_rules)
    check(f"allMet = {expected_all}", body["allMet"] == expected_all)

    # 학기
    rows = db("SELECT semester_id, year, term, status FROM Semester WHERE student_id = %d "
              "ORDER BY year DESC, term DESC", student_id)
    _, _, body = c.call("GET", "/api/me/semesters")
    check("semesters = DB (최신순)", [(s["semesterId"], s["year"], s["term"], s["status"]) for s in body]
          == [(int(r[0]), int(r[1]), int(r[2]), r[3]) for r in rows])

    # 수강 이력
    rows = db("SELECT e.enrollment_id, e.grade, e.completed FROM Enrollment e WHERE e.student_id = %d", student_id)
    _, _, body = c.call("GET", "/api/me/enrollments")
    check(f"enrollments {len(rows)}건, 성적·이수 = DB",
          sorted((e["enrollmentId"], e["grade"], e["completed"]) for e in body)
          == sorted((int(r[0]), r[1], r[2] == "1") for r in rows))
    other = db("SELECT semester_id FROM Semester WHERE student_id <> %d LIMIT 1", student_id)
    if other:
        status, _, _ = c.call("GET", f"/api/me/enrollments?semesterId={int(other[0][0])}")
        check("다른 학생의 학기 -> 404", status == 404)
    status, _, _ = c.call("GET", "/api/me/enrollments?semesterId=abc")
    check("semesterId 문자 -> 400", status == 400)

    # 시간표
    current = db("SELECT semester_id FROM Semester WHERE student_id = %d AND status = '재학' "
                 "ORDER BY year DESC, term DESC LIMIT 1", student_id)
    status, _, body = c.call("GET", "/api/me/timetable")
    if not current:
        check("재학 학기 없음 -> timetable 404", status == 404)
    else:
        semester_id = int(current[0][0])
        slots = int(db("SELECT COUNT(*) FROM Schedule s JOIN Enrollment e ON e.enrollment_id = s.enrollment_id "
                       "WHERE e.student_id = %d AND e.semester_id = %d", student_id, semester_id)[0][0])
        enrollments = int(db("SELECT COUNT(*) FROM Enrollment WHERE student_id = %d AND semester_id = %d",
                             student_id, semester_id)[0][0])
        api_slots = sum(len(e["slots"]) for e in body["entries"])
        check(f"timetable 재학 학기 {semester_id}, 과목 {enrollments}개·칸 {slots}개 = DB",
              status == 200 and body["semester"]["semesterId"] == semester_id
              and len(body["entries"]) == enrollments and api_slots == slots)

    # 봉사
    approved = float(db("SELECT approved_hours FROM VolunteerSummary WHERE student_id = %d", student_id)[0][0])
    count = int(db("SELECT COUNT(*) FROM VolunteerRecord WHERE student_id = %d", student_id)[0][0])
    _, _, body = c.call("GET", "/api/me/volunteers")
    check(f"volunteers 승인 {approved}시간, {count}건 = DB",
          body["approvedHours"] == approved and len(body["records"]) == count)
    fmt = re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$")
    check("approvedAt 형식 yyyy-MM-ddTHH:mm:ss (승인 전 null)",
          all((r["approvedAt"] is None) == (r["status"] != "승인") and (r["approvedAt"] is None or fmt.match(r["approvedAt"]))
              for r in body["records"]))
    db_times = sorted(r[0] for r in db("SELECT DATE_FORMAT(approved_at, '%%Y-%%m-%%dT%%H:%%i:%%s') FROM VolunteerRecord "
                                       "WHERE student_id = %d AND approved_at IS NOT NULL", student_id))
    check("approvedAt = DB 값 그대로 (시간대 변환 없음)",
          sorted(r["approvedAt"] for r in body["records"] if r["approvedAt"]) == db_times)


def test_volunteer_writes():
    """봉사 쓰기·관리자 API. 시작 때 없던 행은 끝에서 지우고, 테이블이 시작 때와 같은지 확인한다.

    정리는 응답으로 받은 ID 가 아니라 시작 전 스냅샷을 기준으로 한다. 테스트가 실패해 예상과 다른
    요청이 성공한 경우에도 테스트가 만든 행이 남지 않게 하기 위해서다.
    """
    print("[봉사 쓰기·관리자]")
    snapshot = "SELECT * FROM VolunteerRecord ORDER BY record_id"
    before = db(snapshot)
    before_ids = {int(row[0]) for row in before}

    try:
        student1, student2, admin, anon = Client(), Client(), Client(), Client()
        student1.login("student1")
        student2.login("student2")
        admin.login("admin")
        sid1 = int(db("SELECT student_id FROM Account WHERE login_id = 'student1'")[0][0])
        sid2 = int(db("SELECT student_id FROM Account WHERE login_id = 'student2'")[0][0])

        # 등록
        good = {"activityName": "[테스트] 봉사 A", "activityDate": "2026-11-20", "hours": 2.5}
        status, _, body = student1.call("POST", "/api/me/volunteers", good)
        check("등록 201", status == 201 and body.get("status") == "대기" and body.get("approvedAt") is None, body)
        check("등록 응답 값", body.get("activityName") == good["activityName"]
              and body.get("activityDate") == "2026-11-20" and body.get("hours") == 2.5, body)
        rid = body.get("recordId")
        row = db("SELECT student_id, activity_name, hours, status FROM VolunteerRecord WHERE record_id = %d", rid or 0)
        check("DB에 본인(student1) 대기 상태로 저장", row == [[str(sid1), good["activityName"], "2.5", "대기"]], row)

        status, _, body = student1.call("POST", "/api/me/volunteers", {**good, "status": "승인", "studentId": sid2})
        row = db("SELECT student_id, status FROM VolunteerRecord WHERE record_id = %d", body.get("recordId") or 0)
        check("요청의 status·studentId 는 무시됨", status == 201 and row == [[str(sid1), "대기"]], (body, row))

        invalid = {
            "hours 0": {**good, "hours": 0},
            "hours 음수": {**good, "hours": -1},
            "hours 소수 둘째 자리": {**good, "hours": 1.25},
            "hours 범위 초과": {**good, "hours": 10000},
            "hours 문자열": {**good, "hours": "abc"},
            "hours 누락": {"activityName": "x", "activityDate": "2026-11-20"},
            "날짜 형식 오류": {**good, "activityDate": "2026-13-40"},
            "날짜 5자리 연도": {**good, "activityDate": "+10000-01-01"},
            "날짜 연도 1000 미만": {**good, "activityDate": "0999-12-31"},
            "날짜 누락": {"activityName": "x", "hours": 1},
            "이름 누락": {"activityDate": "2026-11-20", "hours": 1},
            "이름 빈 문자열": {**good, "activityName": "   "},
            "이름 101자": {**good, "activityName": "가" * 101},
        }
        for label, payload in invalid.items():
            status, _, body = student1.call("POST", "/api/me/volunteers", payload)
            check(f"등록 거부: {label} -> 400", status == 400 and error_code(body) == "INVALID_REQUEST", (status, body))
        status, _, body = student1.call("POST", "/api/me/volunteers")
        check("본문 없음 -> 400", status == 400 and error_code(body) == "INVALID_REQUEST", (status, body))
        status, _, body = student1.call("POST", "/api/me/volunteers", good, headers={"Content-Type": "text/plain"})
        check("Content-Type text/plain -> 400", status == 400 and error_code(body) == "INVALID_REQUEST", (status, body))

        # CSRF: 상태를 바꾸는 요청에 허용되지 않은 Origin 이 있으면 403, 허용 출처(프론트엔드)는 통과
        count = db("SELECT COUNT(*) FROM VolunteerRecord")
        for origin in ("http://localhost:5173", "null"):
            status, _, body = student1.call("POST", "/api/me/volunteers", good, headers={"Origin": origin})
            check(f"Origin {origin} 등록 -> 403", status == 403 and error_code(body) == "FORBIDDEN", (status, body))
        check("거부된 등록은 저장되지 않음", db("SELECT COUNT(*) FROM VolunteerRecord") == count)
        status, _, body = student1.call("POST", "/api/me/volunteers", good, headers={"Origin": FRONT_ORIGIN})
        check("프론트엔드 Origin 등록 -> 201", status == 201, (status, body))
        status, _, body = Client().call("POST", "/api/auth/login", {"loginId": "student1", "password": TEST_PASSWORD},
                                        headers={"Origin": "http://localhost:5173"})
        check("허용되지 않은 Origin 로그인 -> 403", status == 403 and error_code(body) == "FORBIDDEN", (status, body))
        status, _, _ = student1.call("GET", "/api/me/volunteers", headers={"Origin": "http://localhost:5173"})
        check("조회(GET)는 Origin 과 관계없이 200", status == 200, status)

        status, _, body = admin.call("POST", "/api/me/volunteers", good)
        check("관리자가 학생 API -> 403", status == 403 and error_code(body) == "FORBIDDEN")
        status, _, body = anon.call("POST", "/api/me/volunteers", good)
        check("미로그인 등록 -> 401", status == 401 and error_code(body) == "UNAUTHENTICATED")

        # 관리자 목록
        status, _, body = admin.call("GET", "/api/admin/volunteers")
        expected = int(db("SELECT COUNT(*) FROM VolunteerRecord")[0][0])
        check("관리자 목록 전체", status == 200 and len(body) == expected, (status, expected))
        first = next((r for r in body if r["recordId"] == rid), None)
        check("관리자 목록에 학생 이름 포함", first is not None
              and first["studentId"] == sid1 and first["studentName"] == db(
                  "SELECT name FROM Student WHERE student_id = %d", sid1)[0][0], first)
        status, _, body = admin.call("GET", "/api/admin/volunteers?status=" + q("대기"))
        expected = int(db("SELECT COUNT(*) FROM VolunteerRecord WHERE status = '대기'")[0][0])
        check("관리자 목록 status 필터", status == 200 and len(body) == expected
              and all(r["status"] == "대기" for r in body), (status, expected))
        status, _, body = admin.call("GET", "/api/admin/volunteers?status=x")
        check("관리자 목록 잘못된 status -> 400", status == 400 and error_code(body) == "INVALID_REQUEST")
        status, _, body = student1.call("GET", "/api/admin/volunteers")
        check("학생이 관리자 목록 -> 403", status == 403 and error_code(body) == "FORBIDDEN")

        # 개별 승인
        status, _, body = student1.call("POST", f"/api/admin/volunteers/{rid}/approve")
        check("학생이 승인 -> 403", status == 403 and error_code(body) == "FORBIDDEN")
        row = db("SELECT status FROM VolunteerRecord WHERE record_id = %d", rid)
        check("403 이면 상태 그대로", row == [["대기"]], row)
        hours_before = student1.call("GET", "/api/me/volunteers")[2]["approvedHours"]
        status, _, body = admin.call("POST", f"/api/admin/volunteers/{rid}/approve",
                                     headers={"Origin": "http://localhost:5173"})
        check("허용되지 않은 Origin 승인 -> 403", status == 403 and error_code(body) == "FORBIDDEN", (status, body))
        row = db("SELECT status FROM VolunteerRecord WHERE record_id = %d", rid)
        check("거부된 승인은 상태 그대로", row == [["대기"]], row)
        status, _, body = admin.call("POST", f"/api/admin/volunteers/{rid}/approve")
        check("승인 200", status == 200 and body == {"recordId": rid, "status": "승인"}, (status, body))
        row = db("SELECT status, approved_at IS NOT NULL FROM VolunteerRecord WHERE record_id = %d", rid)
        check("DB 승인 상태·승인 시각", row == [["승인", "1"]], row)
        hours_after = student1.call("GET", "/api/me/volunteers")[2]["approvedHours"]
        check("승인 시간 합계가 승인 건만큼 증가 (View)", round(hours_after - hours_before, 1) == 2.5,
              (hours_before, hours_after))
        status, _, body = admin.call("POST", f"/api/admin/volunteers/{rid}/approve")
        check("재승인 -> 409 INVALID_STATE", status == 409 and error_code(body) == "INVALID_STATE", (status, body))
        status, _, body = admin.call("POST", "/api/admin/volunteers/999999/approve")
        check("없는 내역 승인 -> 404", status == 404 and error_code(body) == "NOT_FOUND", (status, body))
        status, _, body = admin.call("POST", "/api/admin/volunteers/abc/approve")
        check("id 형식 오류 -> 400", status == 400 and error_code(body) == "INVALID_REQUEST", (status, body))
        status, _, body = admin.call("GET", f"/api/admin/volunteers/{rid}/approve")
        check("승인 경로에 GET -> 405", status == 405 and error_code(body) == "METHOD_NOT_ALLOWED", (status, body))
        status, _, body = admin.call("POST", f"/api/admin/volunteers/{rid}/reject")
        check("없는 하위 경로 -> 404", status == 404 and error_code(body) == "NOT_FOUND", (status, body))
        status, _, body = admin.call("POST", "/api/admin/volunteers")
        check("목록 경로에 POST -> 405", status == 405 and error_code(body) == "METHOD_NOT_ALLOWED", (status, body))
        for path in ("/api/admin/volunteers/approve", "/api/admin/volunteers//approve"):
            status, _, body = admin.call("GET", path)
            check(f"GET {path} (ID 없음) -> 404", status == 404 and error_code(body) == "NOT_FOUND", (status, body))

        # 일괄 승인: 시드의 대기 내역을 건드리지 않도록, 대기 내역이 없는 학생으로만 시험한다.
        pending2 = int(db("SELECT COUNT(*) FROM VolunteerRecord WHERE student_id = %d AND status = '대기'", sid2)[0][0])
        if pending2 == 0:
            for hours in (1.5, 2):
                status, _, body = student2.call("POST", "/api/me/volunteers", {**good, "hours": hours})
            total = db("SELECT COALESCE(SUM(hours), 0) FROM VolunteerRecord WHERE student_id = %d "
                       "AND (status = '승인' OR status = '대기')", sid2)[0][0]
            status, _, body = admin.call("POST", f"/api/admin/students/{sid2}/volunteers/approve-all")
            check("일괄 승인 200: 건수·누적 시간", status == 200 and body.get("approvedCount") == 2
                  and float(body.get("totalApprovedHours")) == float(total), (body, total))
            row = db("SELECT COUNT(*) FROM VolunteerRecord WHERE student_id = %d AND status = '대기'", sid2)
            check("일괄 승인 후 대기 0건", row == [["0"]], row)
            status, _, body = admin.call("POST", f"/api/admin/students/{sid2}/volunteers/approve-all")
            check("대기 없으면 0건 정상 응답", status == 200 and body.get("approvedCount") == 0, (status, body))
            status, _, body = student2.call("POST", f"/api/admin/students/{sid2}/volunteers/approve-all")
            check("학생이 일괄 승인 -> 403", status == 403 and error_code(body) == "FORBIDDEN")
        else:
            print(f"  SKIP  일괄 승인: student2 에 대기 내역 {pending2}건이 있어 시드 데이터를 바꾸지 않으려고 건너뜀")
        status, _, body = admin.call("POST", "/api/admin/students/999999/volunteers/approve-all")
        check("없는 학생 일괄 승인 -> 404", status == 404 and error_code(body) == "NOT_FOUND", (status, body))
        status, _, body = admin.call("POST", f"/api/admin/students/{sid2}/volunteers/x")
        check("일괄 승인 잘못된 경로 -> 404", status == 404 and error_code(body) == "NOT_FOUND", (status, body))
    finally:
        # 시작 때 없던 행만 지운다. (시드 행이 승인 등으로 바뀌었다면 지우지 않고 아래 비교에서 실패로 드러난다)
        new_ids = [int(row[0]) for row in db("SELECT record_id FROM VolunteerRecord") if int(row[0]) not in before_ids]
        if new_ids:
            db("DELETE FROM VolunteerRecord WHERE record_id IN (%s)" % ",".join(str(i) for i in new_ids))
        after = db(snapshot)
        check("테스트 후 VolunteerRecord 가 시작 때와 같음", after == before,
              f"{len(before)}행 -> {len(after)}행")


def test_enrollment_writes():
    """수강 등록·성적 수정·시간표 쓰기 API. 시작 때 없던 행은 끝에서 지우고 테이블이 시작 때와 같은지 확인한다.

    기존(시드) 수강·시간표 행은 바꾸지 않고, 테스트가 만든 수강 과목에서만 쓰기를 확인한다.
    """
    print("[수강·시간표 쓰기]")
    enroll_sql = "SELECT * FROM Enrollment ORDER BY enrollment_id"
    sched_sql = "SELECT * FROM Schedule ORDER BY schedule_id"
    before_enroll, before_sched = db(enroll_sql), db(sched_sql)
    enroll_ids = {int(r[0]) for r in before_enroll}
    sched_ids = {int(r[0]) for r in before_sched}

    try:
        student1, student2, admin, anon = Client(), Client(), Client(), Client()
        student1.login("student1")
        student2.login("student2")
        admin.login("admin")
        sid1 = int(db("SELECT student_id FROM Account WHERE login_id = 'student1'")[0][0])
        # student1 의 '재학' 학기와 student2 의 학기, 두 학생의 시드 수강·시간표
        sem1 = int(db("SELECT semester_id FROM Semester WHERE student_id = %d AND status = '재학' "
                      "ORDER BY year DESC, term DESC LIMIT 1", sid1)[0][0])
        sem2 = int(db("SELECT semester_id FROM Semester WHERE student_id <> %d LIMIT 1", sid1)[0][0])
        other_enroll = int(db("SELECT enrollment_id FROM Enrollment WHERE student_id <> %d ORDER BY enrollment_id LIMIT 1",
                              sid1)[0][0])
        other_sched = db("SELECT s.schedule_id FROM Schedule s JOIN Enrollment e ON e.enrollment_id = s.enrollment_id "
                         "WHERE e.student_id <> %d LIMIT 1", sid1)
        # 시드에 없는 과목 두 개 (이미 등록된 학기 과목과 겹치지 않게)
        free = [int(r[0]) for r in db("SELECT course_id FROM Course WHERE course_id NOT IN "
                                      "(SELECT course_id FROM Enrollment WHERE semester_id = %d) "
                                      "ORDER BY course_id LIMIT 3", sem1)]
        c1, c2, c3 = free
        # 시드에서 쓰이는 요일·교시를 피해 빈 칸을 고른다
        used = {(r[0], int(r[1])) for r in db("SELECT s.day, s.period FROM Schedule s JOIN Enrollment e "
                                              "ON e.enrollment_id = s.enrollment_id WHERE e.student_id = %d "
                                              "AND e.semester_id = %d", sid1, sem1)}
        seed_slot = next(iter(sorted(used)), None)

        # --- 수강 등록
        status, _, body = student1.call("POST", "/api/me/enrollments", {"semesterId": sem1, "courseId": c1})
        check("수강 등록 201", status == 201 and body.get("courseId") == c1 and body.get("grade") is None
              and body.get("completed") is False and body.get("semesterId") == sem1, (status, body))
        e1 = body.get("enrollmentId") or 0
        row = db("SELECT student_id, semester_id, course_id, grade, completed FROM Enrollment WHERE enrollment_id = %d", e1)
        check("DB에 본인 학기로 저장", row == [[str(sid1), str(sem1), str(c1), None, "0"]], row)
        check("응답이 목록 조회 항목과 같음",
              body in (student1.call("GET", f"/api/me/enrollments?semesterId={sem1}")[2] or []))

        status, _, body = student1.call("POST", "/api/me/enrollments", {"semesterId": sem1, "courseId": c2, "grade": "A"})
        e2 = body.get("enrollmentId") or 0
        check("성적과 함께 등록하면 completed 는 트리거가 true 로 정함", status == 201 and body.get("completed") is True
              and db("SELECT completed FROM Enrollment WHERE enrollment_id = %d", e2) == [["1"]], (status, body))
        status, _, body = student1.call("POST", "/api/me/enrollments",
                                        {"semesterId": sem1, "courseId": c3, "grade": "F", "completed": True})
        e3 = body.get("enrollmentId") or 0
        check("요청의 completed 는 무시됨(F 는 미이수)", status == 201 and body.get("completed") is False, (status, body))

        status, _, body = student1.call("POST", "/api/me/enrollments", {"semesterId": sem1, "courseId": c1})
        check("같은 학기 같은 과목 -> 409 DUPLICATE", status == 409 and error_code(body) == "DUPLICATE", (status, body))
        status, _, body = student1.call("POST", "/api/me/enrollments", {"semesterId": sem1, "courseId": 99999999})
        check("없는 과목 -> 404", status == 404 and error_code(body) == "NOT_FOUND", (status, body))
        status, _, body = student1.call("POST", "/api/me/enrollments", {"semesterId": sem2, "courseId": c1})
        check("다른 학생의 학기 -> 400", status == 400 and error_code(body) == "INVALID_REQUEST", (status, body))
        status, _, body = student1.call("POST", "/api/me/enrollments", {"semesterId": 99999999, "courseId": c1})
        check("없는 학기 -> 400", status == 400 and error_code(body) == "INVALID_REQUEST", (status, body))
        invalid = {
            "semesterId 누락": {"courseId": c1},
            "courseId 누락": {"semesterId": sem1},
            "semesterId 0": {"semesterId": 0, "courseId": c1},
            "courseId 문자열": {"semesterId": sem1, "courseId": "abc"},
            "grade 허용 외 값": {"semesterId": sem1, "courseId": c1, "grade": "Z"},
            "grade 소문자": {"semesterId": sem1, "courseId": c1, "grade": "a"},
        }
        for label, payload in invalid.items():
            status, _, body = student1.call("POST", "/api/me/enrollments", payload)
            check(f"수강 등록 거부: {label} -> 400", status == 400 and error_code(body) == "INVALID_REQUEST", (status, body))
        status, _, body = student1.call("POST", "/api/me/enrollments")
        check("수강 등록 본문 없음 -> 400", status == 400 and error_code(body) == "INVALID_REQUEST", (status, body))
        count = db("SELECT COUNT(*) FROM Enrollment")
        status, _, body = student1.call("POST", "/api/me/enrollments", {"semesterId": sem1, "courseId": c1},
                                        headers={"Origin": "http://localhost:5173"})
        check("허용되지 않은 Origin 수강 등록 -> 403", status == 403 and error_code(body) == "FORBIDDEN", (status, body))
        check("거부된 요청은 저장되지 않음", db("SELECT COUNT(*) FROM Enrollment") == count)
        status, _, body = admin.call("POST", "/api/me/enrollments", {"semesterId": sem1, "courseId": c1})
        check("관리자가 수강 등록 -> 403", status == 403 and error_code(body) == "FORBIDDEN")
        status, _, body = anon.call("POST", "/api/me/enrollments", {"semesterId": sem1, "courseId": c1})
        check("미로그인 수강 등록 -> 401", status == 401 and error_code(body) == "UNAUTHENTICATED")

        # --- 성적 수정
        status, _, body = student1.call("PATCH", f"/api/enrollments/{e1}", {"grade": "B+"})
        check("성적 입력 200 + completed true", status == 200 and body.get("grade") == "B+"
              and body.get("completed") is True and body.get("enrollmentId") == e1, (status, body))
        check("DB 성적 반영", db("SELECT grade, completed FROM Enrollment WHERE enrollment_id = %d", e1) == [["B+", "1"]])
        status, _, body = student1.call("PATCH", f"/api/enrollments/{e1}", {"grade": "F"})
        check("F 로 수정하면 completed false", status == 200 and body.get("completed") is False, (status, body))
        status, _, body = student1.call("PATCH", f"/api/enrollments/{e1}", {"grade": "P"})
        check("P 는 이수", status == 200 and body.get("completed") is True, (status, body))
        status, _, body = student1.call("PATCH", f"/api/enrollments/{e1}", {"grade": None})
        check("grade null 로 성적 삭제", status == 200 and body.get("grade") is None and body.get("completed") is False
              and db("SELECT grade, completed FROM Enrollment WHERE enrollment_id = %d", e1) == [[None, "0"]], (status, body))
        for label, payload in {"grade 누락": {}, "허용 외 값": {"grade": "Z"}, "숫자": {"grade": 5},
                               "객체": {"grade": {"a": 1}}}.items():
            status, _, body = student1.call("PATCH", f"/api/enrollments/{e1}", payload)
            check(f"성적 수정 거부: {label} -> 400", status == 400 and error_code(body) == "INVALID_REQUEST", (status, body))
        check("거부된 수정은 반영되지 않음", db("SELECT grade FROM Enrollment WHERE enrollment_id = %d", e1) == [[None]])
        before_other = db("SELECT * FROM Enrollment WHERE enrollment_id = %d", other_enroll)
        status, _, body = student1.call("PATCH", f"/api/enrollments/{other_enroll}", {"grade": "A"})
        check("다른 학생의 수강 성적 수정 -> 404", status == 404 and error_code(body) == "NOT_FOUND", (status, body))
        check("다른 학생의 수강은 그대로", db("SELECT * FROM Enrollment WHERE enrollment_id = %d", other_enroll) == before_other)
        status, _, body = student1.call("PATCH", "/api/enrollments/99999999", {"grade": "A"})
        check("없는 수강 성적 수정 -> 404", status == 404 and error_code(body) == "NOT_FOUND", (status, body))
        status, _, body = student1.call("PATCH", "/api/enrollments/abc", {"grade": "A"})
        check("수강 ID 형식 오류 -> 400", status == 400 and error_code(body) == "INVALID_REQUEST", (status, body))
        status, _, body = student1.call("PATCH", f"/api/enrollments/{e1}", {"grade": "A"},
                                        headers={"Origin": "http://localhost:5173"})
        check("허용되지 않은 Origin 성적 수정 -> 403", status == 403 and error_code(body) == "FORBIDDEN", (status, body))
        status, _, body = admin.call("PATCH", f"/api/enrollments/{e1}", {"grade": "A"})
        check("관리자가 성적 수정 -> 403", status == 403 and error_code(body) == "FORBIDDEN")
        status, _, _ = student1.call("PATCH", "/api/me/enrollments", {"grade": "A"})
        check("PATCH 미지원 경로 -> 405", status == 405, status)

        # --- 시간표 등록
        slots = {"slots": [{"day": "수", "period": 5, "room": "T-1"}, {"day": "수", "period": 6}]}
        status, _, body = student1.call("POST", f"/api/enrollments/{e1}/schedules", slots)
        got = body.get("slots") if isinstance(body, dict) else None
        check("시간표 칸 2개 등록 201", status == 201 and isinstance(got, list) and len(got) == 2
              and [(g["day"], g["period"], g["room"]) for g in got] == [("수", 5, "T-1"), ("수", 6, None)], (status, body))
        rows = db("SELECT schedule_id, day, period, room FROM Schedule WHERE enrollment_id = %d ORDER BY period", e1)
        check("DB에 두 칸 저장, 응답 scheduleId 와 일치", [r[0] for r in rows] == [str(g["scheduleId"]) for g in (got or [])]
              and [(r[1], r[2], r[3]) for r in rows] == [("수", "5", "T-1"), ("수", "6", None)], rows)
        status, _, body = student1.call("GET", f"/api/me/timetable?semesterId={sem1}")
        entry = next((e for e in (body or {}).get("entries", []) if e["enrollmentId"] == e1), None)
        check("시간표 조회에 반영", status == 200 and entry is not None and len(entry["slots"]) == 2, (status, entry))

        before_sched_now = db(sched_sql)
        status, _, body = student1.call("POST", f"/api/enrollments/{e2}/schedules", {"slots": [{"day": "수", "period": 5}]})
        check("다른 수강 과목과 같은 요일·교시 -> 409 SCHEDULE_CONFLICT",
              status == 409 and error_code(body) == "SCHEDULE_CONFLICT", (status, body))
        status, _, body = student1.call("POST", f"/api/enrollments/{e2}/schedules",
                                        {"slots": [{"day": "금", "period": 7}, {"day": "수", "period": 6}]})
        check("여러 칸 중 하나가 충돌하면 409", status == 409 and error_code(body) == "SCHEDULE_CONFLICT", (status, body))
        check("충돌 시 앞선 칸도 저장되지 않음(전체 취소)", db(sched_sql) == before_sched_now)
        if seed_slot is not None:
            status, _, body = student1.call("POST", f"/api/enrollments/{e2}/schedules",
                                            {"slots": [{"day": seed_slot[0], "period": seed_slot[1]}]})
            check("시드 시간표와 충돌 -> 409 SCHEDULE_CONFLICT",
                  status == 409 and error_code(body) == "SCHEDULE_CONFLICT", (status, body))
        status, _, body = student1.call("POST", f"/api/enrollments/{e1}/schedules", {"slots": [{"day": "수", "period": 5}]})
        check("같은 수강 과목의 같은 칸 -> 409 DUPLICATE", status == 409 and error_code(body) == "DUPLICATE", (status, body))
        status, _, body = student1.call("POST", f"/api/enrollments/{e1}/schedules",
                                        {"slots": [{"day": "금", "period": 7}, {"day": "금", "period": 7}]})
        check("요청 안에서 같은 칸 반복 -> 409 DUPLICATE 이고 전체 취소",
              status == 409 and error_code(body) == "DUPLICATE" and db(sched_sql) == before_sched_now, (status, body))
        invalid = {
            "slots 누락": {},
            "slots 빈 배열": {"slots": []},
            "slots 31개": {"slots": [{"day": "금", "period": 1 + i % 9} for i in range(31)]},
            "slots 에 null": {"slots": [None]},
            "day 허용 외": {"slots": [{"day": "일", "period": 1}]},
            "day 누락": {"slots": [{"period": 1}]},
            "period 0": {"slots": [{"day": "금", "period": 0}]},
            "period 16 (상한 15)": {"slots": [{"day": "금", "period": 16}]},
            "period int 최대값": {"slots": [{"day": "금", "period": 2147483647}]},
            "period 누락": {"slots": [{"day": "금"}]},
            "period 소수": {"slots": [{"day": "금", "period": 1.5}]},
            "room 51자": {"slots": [{"day": "금", "period": 1, "room": "가" * 51}]},
        }
        for label, payload in invalid.items():
            status, _, body = student1.call("POST", f"/api/enrollments/{e1}/schedules", payload)
            check(f"시간표 등록 거부: {label} -> 400", status == 400 and error_code(body) == "INVALID_REQUEST", (status, body))
        check("거부된 요청은 저장되지 않음", db(sched_sql) == before_sched_now)
        status, _, body = student1.call("POST", f"/api/enrollments/{other_enroll}/schedules", {"slots": [{"day": "금", "period": 8}]})
        check("다른 학생의 수강에 시간표 등록 -> 404", status == 404 and error_code(body) == "NOT_FOUND"
              and db(sched_sql) == before_sched_now, (status, body))
        status, _, body = student1.call("POST", "/api/enrollments/99999999/schedules", {"slots": [{"day": "금", "period": 8}]})
        check("없는 수강에 시간표 등록 -> 404", status == 404 and error_code(body) == "NOT_FOUND", (status, body))
        status, _, body = student1.call("POST", f"/api/enrollments/{e1}/x", {"slots": [{"day": "금", "period": 8}]})
        check("잘못된 경로 -> 404", status == 404 and error_code(body) == "NOT_FOUND", (status, body))
        status, _, body = student1.call("POST", f"/api/enrollments/{e1}/schedules", {"slots": [{"day": "금", "period": 8}]},
                                        headers={"Origin": "http://localhost:5173"})
        check("허용되지 않은 Origin 시간표 등록 -> 403", status == 403 and error_code(body) == "FORBIDDEN", (status, body))
        status, _, body = admin.call("POST", f"/api/enrollments/{e1}/schedules", {"slots": [{"day": "금", "period": 8}]})
        check("관리자가 시간표 등록 -> 403", status == 403 and error_code(body) == "FORBIDDEN")
        status, _, _ = student1.call("GET", f"/api/enrollments/{e1}")
        check("GET 미지원 경로 -> 405", status == 405, status)
        for method, path in (("POST", "/api/enrollments"), ("PATCH", "/api/enrollments"),
                             ("DELETE", "/api/enrollments"), ("DELETE", "/api/schedules")):
            status, _, body = admin.call(method, path, {"grade": "A"} if method != "DELETE" else None)
            check(f"관리자 {method} {path}(끝 / 없음) -> 403", status == 403 and error_code(body) == "FORBIDDEN", (status, body))
        status, _, body = student1.call("POST", f"/api/enrollments/{e1}/schedules", {"slots": [{"day": "토", "period": 15}]})
        check("period 15(상한) 등록 201", status == 201, (status, body))

        # 같은 칸을 두 수강 과목에 동시에 등록: 하나만 저장되고 다른 하나는 409 (교착 상태로 500 이 나지 않음)
        def concurrent_post(eid, out):
            c = Client()
            c.login("student1")
            st, _, b = c.call("POST", f"/api/enrollments/{eid}/schedules", {"slots": [{"day": "토", "period": 14}]})
            out.append((st, error_code(b)))
        outcomes, stored = [], []
        for _ in range(5):
            out = []
            threads = [threading.Thread(target=concurrent_post, args=(eid, out)) for eid in (e1, e2)]
            for t in threads:
                t.start()
            for t in threads:
                t.join()
            outcomes.append(sorted(out, key=lambda x: x[0]))
            stored.append(db("SELECT COUNT(*) FROM Schedule s JOIN Enrollment e ON e.enrollment_id = s.enrollment_id "
                             "WHERE e.student_id = %d AND s.day = '토' AND s.period = 14", sid1))
            db("DELETE s FROM Schedule s JOIN Enrollment e ON e.enrollment_id = s.enrollment_id "
               "WHERE e.enrollment_id IN (%d, %d) AND s.day = '토' AND s.period = 14", e1, e2)
        check("동시 등록 5회: 매번 201 하나와 409 하나", all(o[0][0] == 201 and o[1][0] == 409
              and o[1][1] in ("SCHEDULE_CONFLICT", "CONCURRENT_UPDATE") for o in outcomes), outcomes)
        check("동시 등록 5회: 매번 한 칸만 저장", all(r == [["1"]] for r in stored), stored)

        # --- 시간표 한 칸 삭제
        first = got[0]["scheduleId"] if got else 0
        if other_sched:
            sid_other = int(other_sched[0][0])
            status, _, body = student1.call("DELETE", f"/api/schedules/{sid_other}")
            check("다른 학생의 시간표 삭제 -> 404", status == 404 and error_code(body) == "NOT_FOUND"
                  and db("SELECT COUNT(*) FROM Schedule WHERE schedule_id = %d", sid_other) == [["1"]], (status, body))
        status, _, body = student1.call("DELETE", f"/api/schedules/{first}", headers={"Origin": "http://localhost:5173"})
        check("허용되지 않은 Origin 시간표 삭제 -> 403", status == 403 and error_code(body) == "FORBIDDEN", (status, body))
        status, _, body = admin.call("DELETE", f"/api/schedules/{first}")
        check("관리자가 시간표 삭제 -> 403", status == 403 and error_code(body) == "FORBIDDEN")
        status, _, body = student1.call("DELETE", f"/api/schedules/{first}")
        check("시간표 한 칸 삭제 204", status == 204 and body is None
              and db("SELECT COUNT(*) FROM Schedule WHERE schedule_id = %d", first) == [["0"]], (status, body))
        check("수강 과목은 남아 있음", db("SELECT COUNT(*) FROM Enrollment WHERE enrollment_id = %d", e1) == [["1"]])
        status, _, body = student1.call("DELETE", f"/api/schedules/{first}")
        check("이미 지운 칸 삭제 -> 404", status == 404 and error_code(body) == "NOT_FOUND", (status, body))
        status, _, body = student1.call("DELETE", "/api/schedules/abc")
        check("시간표 ID 형식 오류 -> 400", status == 400 and error_code(body) == "INVALID_REQUEST", (status, body))
        status, _, body = student1.call("DELETE", "/api/schedules/")
        check("시간표 ID 없음 -> 404", status == 404 and error_code(body) == "NOT_FOUND", (status, body))
        status, _, body = student1.call("POST", f"/api/enrollments/{e1}/schedules", {"slots": [{"day": "수", "period": 5}]})
        check("지운 칸은 다시 등록할 수 있음", status == 201, (status, body))

        # --- 수강 취소 (시간표 칸도 함께 삭제)
        before_other = db("SELECT * FROM Enrollment WHERE enrollment_id = %d", other_enroll)
        status, _, body = student1.call("DELETE", f"/api/enrollments/{other_enroll}")
        check("다른 학생의 수강 취소 -> 404", status == 404 and error_code(body) == "NOT_FOUND"
              and db("SELECT * FROM Enrollment WHERE enrollment_id = %d", other_enroll) == before_other, (status, body))
        status, _, body = student1.call("DELETE", f"/api/enrollments/{e1}", headers={"Origin": "http://localhost:5173"})
        check("허용되지 않은 Origin 수강 취소 -> 403", status == 403 and error_code(body) == "FORBIDDEN", (status, body))
        status, _, body = admin.call("DELETE", f"/api/enrollments/{e1}")
        check("관리자가 수강 취소 -> 403", status == 403 and error_code(body) == "FORBIDDEN")
        check("거부된 요청 후에도 수강·시간표가 남아 있음",
              db("SELECT COUNT(*) FROM Enrollment WHERE enrollment_id = %d", e1) == [["1"]]
              and db("SELECT COUNT(*) FROM Schedule WHERE enrollment_id = %d", e1) != [["0"]])
        status, _, body = student1.call("DELETE", f"/api/enrollments/{e1}")
        check("시간표가 있는 수강 취소 204", status == 204 and body is None, (status, body))
        check("수강과 그 시간표 칸이 함께 삭제됨",
              db("SELECT COUNT(*) FROM Enrollment WHERE enrollment_id = %d", e1) == [["0"]]
              and db("SELECT COUNT(*) FROM Schedule WHERE enrollment_id = %d", e1) == [["0"]])
        status, _, body = student1.call("DELETE", f"/api/enrollments/{e2}")
        check("시간표 없는(성적 입력된) 수강 취소 204", status == 204, (status, body))
        status, _, body = student1.call("DELETE", f"/api/enrollments/{e1}")
        check("이미 취소한 수강 -> 404", status == 404 and error_code(body) == "NOT_FOUND", (status, body))
        status, _, body = student1.call("DELETE", "/api/enrollments/abc")
        check("수강 ID 형식 오류(DELETE) -> 400", status == 400 and error_code(body) == "INVALID_REQUEST", (status, body))
        status, _, body = student1.call("DELETE", "/api/enrollments/")
        check("수강 ID 없음(DELETE) -> 404", status == 404 and error_code(body) == "NOT_FOUND", (status, body))
    finally:
        # 시작 때 없던 행만 지운다. 시간표 칸을 먼저 지워야 수강 이력을 지울 수 있다.
        new_sched = [r[0] for r in db("SELECT schedule_id FROM Schedule") if int(r[0]) not in sched_ids]
        if new_sched:
            db("DELETE FROM Schedule WHERE schedule_id IN (%s)" % ",".join(new_sched))
        new_enroll = [r[0] for r in db("SELECT enrollment_id FROM Enrollment") if int(r[0]) not in enroll_ids]
        if new_enroll:
            db("DELETE FROM Enrollment WHERE enrollment_id IN (%s)" % ",".join(new_enroll))
        check("테스트 후 Enrollment 가 시작 때와 같음", db(enroll_sql) == before_enroll)
        check("테스트 후 Schedule 이 시작 때와 같음", db(sched_sql) == before_sched)


def main():
    print(f"대상: {API_BASE}, DB 컨테이너: {CONTAINER}\n")
    try:
        urllib.request.urlopen(API_BASE + "/api/health", timeout=3)
    except Exception as e:  # noqa: BLE001 - 서버 미실행 안내
        sys.exit(f"백엔드에 연결할 수 없습니다: {e}\n먼저 backend/scripts/dev.sh 로 서버를 실행하세요.")

    test_public_and_auth()
    test_cors()
    test_courses()
    for (login_id,) in db("SELECT login_id FROM Account WHERE role = 'STUDENT' ORDER BY login_id"):
        test_student(login_id)
    if "--read-only" in sys.argv[1:]:
        print("\n[쓰기 테스트 생략] --read-only")
    else:
        test_volunteer_writes()
        test_enrollment_writes()

    print(f"\n결과: {results['pass']} 통과, {results['fail']} 실패")
    sys.exit(1 if results["fail"] else 0)


if __name__ == "__main__":
    main()

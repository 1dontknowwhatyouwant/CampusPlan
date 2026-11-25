#!/usr/bin/env python3
"""CampusPlan API 시나리오 테스트 (조회 전용, DB를 바꾸지 않음).

실행 중인 백엔드(backend/scripts/dev.sh)와 개발용 MySQL 컨테이너를 대상으로
API 응답을 DB 직접 조회 결과와 대조합니다. 표준 라이브러리만 사용합니다.

사용법 (저장소 루트에서):
    python3 backend/scripts/api_test.py

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
        if data is not None:
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

    print(f"\n결과: {results['pass']} 통과, {results['fail']} 실패")
    sys.exit(1 if results["fail"] else 0)


if __name__ == "__main__":
    main()

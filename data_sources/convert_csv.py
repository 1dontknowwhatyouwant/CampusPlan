"""공식 자료 CSV를 database/seeds/05_seed_master.sql 로 변환합니다.

입력 (이 파일과 같은 폴더 기준)
  - 입학년도별_학부_학과_매핑.csv        -> Department
  - official/학번별_졸업이수학점.csv      -> GraduationRule (학부 단위 학점 기준)
  - official/2026학년도 2학기 교양강좌 강의시간표.csv -> Course (교양필수·교양선택)
  - official/2026학년도 2학기 전공강좌 강의시간표.csv -> Course (전공)

실행: 어느 위치에서든 `python3 data_sources/convert_csv.py`
"""
import csv
import re
import sys
from collections import defaultdict
from pathlib import Path

BASE = Path(__file__).resolve().parent
DEPT_CSV = BASE / "입학년도별_학부_학과_매핑.csv"
GRAD_CSV = BASE / "official" / "학번별_졸업이수학점.csv"
GE_CSV = BASE / "official" / "2026학년도 2학기 교양강좌 강의시간표.csv"
MAJOR_CSV = BASE / "official" / "2026학년도 2학기 전공강좌 강의시간표.csv"
SQL_PATH = BASE.parent / "database" / "seeds" / "05_seed_master.sql"

GRAD_SOURCE = "official/학번별_졸업이수학점.csv (공식 졸업학점표 PDF에서 추출)"

# 졸업학점 CSV의 학부명 -> Department.college 로 쓰인 이름 (기준은 Department.college).
# 두 자료가 같은 학부를 다르게 부르는 경우만 적습니다.
COLLEGE_ALIASES = {
    "스포츠학부(해양·스포츠학부)": ["스포츠학부", "해양·스포츠학부"],
    "스포츠학부": ["스포츠학부", "해양·스포츠학부"],
    "융합교양학부": ["융합교양학부", "융합학부"],
}

# 졸업학점 CSV의 '충남 RISE융합학부(학과...)' 행: 학과명을 Department 기준으로 맞춥니다.
# 졸업학점표의 '첨단항공공학과'는 모집자료(Department)에서 '첨단항공학과'입니다.
RISE_DEPARTMENT_ALIASES = {
    "첨단항공공학과": "첨단항공학과",
}

# CSV 열 -> GraduationRule 규칙
RULE_COLUMNS = [
    ("졸업학점_최소학점", "TOTAL_CREDITS", "총 이수학점"),
    ("전공_최소학점", "MAJOR_CREDITS", "전공학점"),
    ("교양소계_최소학점", "GENERAL_CREDITS", "교양학점"),
]


def sql_value(value):
    if value is None or value == "":
        return "NULL"
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


def read_rows(path):
    with path.open("r", encoding="utf-8-sig", newline="") as file:
        return list(csv.DictReader(file))


def parse_years(label):
    """'2018-2022' -> (2018, 2022), '2023' -> (2023, 2023)"""
    match = re.fullmatch(r"(\d{4})(?:-(\d{4}))?", label)
    if not match:
        raise ValueError(f"알 수 없는 학번 구간: {label}")
    start = int(match.group(1))
    return start, int(match.group(2) or start)


def parse_college(label):
    """'충남 RISE융합학부(학과·학과)' -> (학부, [학과...]) / 그 외 -> (학부명, None)"""
    match = re.fullmatch(r"(충남 RISE융합학부)\((.+)\)", label)
    if not match:
        return label, None
    departments = [RISE_DEPARTMENT_ALIASES.get(name, name)
                   for name in re.split(r"[·,]", match.group(2))]
    return match.group(1), departments


def build_department_sql(rows):
    departments = {}
    for row in rows:
        key = (row["입학년도"], row["학부"], row["학과"])
        departments[key] = row["출처URL"]

    lines = []
    for (year, college, department), url in departments.items():
        lines.append(
            "INSERT INTO Department "
            "(admission_year, college, department_name, source_url) "
            f"VALUES ({int(year)}, {sql_value(college)}, "
            f"{sql_value(department)}, {sql_value(url)}) "
            "ON DUPLICATE KEY UPDATE "
            "source_url = VALUES(source_url);"
        )
    return lines, departments


def build_rule_rows(grad_rows, departments):
    """졸업학점 CSV를 GraduationRule 행으로 바꿉니다. 이름이 맞지 않으면 오류로 멈춥니다."""
    known = defaultdict(set)  # (학부) -> {입학년도}
    known_dept = defaultdict(set)  # (학부, 학과) -> {입학년도}
    for year, college, department in departments:
        known[college].add(int(year))
        known_dept[(college, department)].add(int(year))

    rules = []
    for row in grad_rows:
        year_from, year_to = parse_years(row["학번"])
        college_label, rise_departments = parse_college(row["학부"])
        candidates = COLLEGE_ALIASES.get(college_label, [college_label])
        # 해당 학번 구간에 실제로 존재하는 Department.college 만 사용
        colleges = [c for c in candidates
                    if any(year_from <= y <= year_to for y in known.get(c, ()))]
        if not colleges:
            raise ValueError(
                f"Department 에 없는 학부: {row['학번']} {row['학부']} "
                "(COLLEGE_ALIASES 확인)")

        for college in colleges:
            targets = rise_departments or [None]
            for department in targets:
                if department and not known_dept.get((college, department)):
                    raise ValueError(
                        f"Department 에 없는 학과: {college} {department}")
                for column, rule_type, label in RULE_COLUMNS:
                    value = row[column].strip()
                    if not value:
                        raise ValueError(f"{row['학번']} {row['학부']}: {column} 값이 없습니다")
                    scope = f"{college}" + (f" {department}" if department else "")
                    rules.append({
                        "name": f"{label} ({scope})",
                        "rule_type": rule_type,
                        "value": float(value),
                        "college": college,
                        "department": department,
                        "year_from": year_from,
                        "year_to": year_to,
                    })
    return rules


def build_rule_sql(rules):
    lines = [
        "INSERT INTO GraduationRule",
        "    (name, rule_type, required_value, required_course_id, college,",
        "     department_name, admission_year_from, admission_year_to, is_verified, source)",
        "VALUES",
    ]
    values = []
    for rule in rules:
        values.append(
            f"({sql_value(rule['name'])}, '{rule['rule_type']}', {rule['value']:.1f}, NULL, "
            f"{sql_value(rule['college'])}, {sql_value(rule['department'])}, "
            f"{rule['year_from']}, {rule['year_to']}, TRUE, {sql_value(GRAD_SOURCE)})"
        )
    lines.append(",\n".join(values) + ";")
    return lines


def parse_general_courses():
    """교양 강의시간표: '▶ 교양필수' / '▶ 교양선택' 구역별로 과목(코드 기준 1건)을 읽습니다."""
    courses = {}
    category = None
    with GE_CSV.open("r", encoding="utf-8-sig", newline="") as file:
        for row in csv.reader(file):
            row += [""] * (10 - len(row))
            if "▶" in row[2]:
                category = row[2].replace("▶", "").strip()
                continue
            code = row[0].strip()
            if not re.fullmatch(r"\d{7}", code):
                continue  # 과목명 헤더, 열 이름, 안내 문구 행
            if category not in ("교양필수", "교양선택"):
                raise ValueError(f"교양 구분을 알 수 없습니다: {code} ({category})")
            add_course(courses, code, row[2], row[4], category, GE_CSV.name)
    return courses


def parse_major_courses():
    """전공 강의시간표: 학과별 표의 데이터 행(학년 열이 '1학년'~'4학년'·'공통')을 읽습니다."""
    courses = {}
    with MAJOR_CSV.open("r", encoding="utf-8-sig", newline="") as file:
        for row in csv.reader(file):
            if len(row) >= 5 and row[0] in ("1학년", "2학년", "3학년", "4학년", "공통"):
                add_course(courses, row[2], row[1], row[4], "전공", MAJOR_CSV.name)
    return courses


def add_course(courses, code, name, credits, category, source):
    code, name, credits = code.strip(), name.strip(), credits.strip()
    if not code or not name or not credits.isdigit():
        raise ValueError(f"{source}: 과목 행을 읽을 수 없습니다: {code!r}, {name!r}, {credits!r}")
    value = (name, int(credits), category)
    if code in courses and courses[code] != value:
        raise ValueError(f"{source}: 과목코드 {code} 의 내용이 서로 다릅니다: {courses[code]} / {value}")
    courses[code] = value


def build_course_sql(courses):
    lines = [
        "INSERT INTO Course (code, name, credits, category)",
        "VALUES",
    ]
    values = [
        f"({sql_value(code)}, {sql_value(name)}, {credits}, {sql_value(category)})"
        for code, (name, credits, category) in courses.items()
    ]
    lines.append(",\n".join(values))
    lines.append("ON DUPLICATE KEY UPDATE name = VALUES(name), credits = VALUES(credits),"
                 " category = VALUES(category);")
    return lines


def check_coverage(rules, departments):
    """모든 Department(학부·학과·입학년도)가 규칙 종류별로 정확히 1개씩 적용되는지 확인합니다."""
    problems = []
    for year, college, department in departments:
        year = int(year)
        for _, rule_type, _ in RULE_COLUMNS:
            matched = [
                r for r in rules
                if r["rule_type"] == rule_type
                and r["college"] == college
                and r["department"] in (None, department)
                and r["year_from"] <= year <= (r["year_to"] or 9999)
            ]
            if len(matched) != 1:
                problems.append((year, college, department, rule_type, len(matched)))
    return problems


def main():
    dept_rows = read_rows(DEPT_CSV)
    grad_rows = read_rows(GRAD_CSV)

    dept_lines, departments = build_department_sql(dept_rows)
    rules = build_rule_rows(grad_rows, departments)

    problems = check_coverage(rules, departments)
    if problems:
        for problem in problems[:20]:
            print("규칙 적용 불일치(연도, 학부, 학과, 규칙, 개수):", problem, file=sys.stderr)
        print(f"전체 {len(problems)}건. 파일을 쓰지 않고 종료합니다.", file=sys.stderr)
        sys.exit(1)

    lines = ["USE campusplan;", ""]
    lines += dept_lines
    lines += ["", "-- 졸업 규칙: 공식 졸업학점표(학부 x 학번 구간) 기준. 교양소계는 GENERAL_CREDITS 로 입력합니다."]
    lines += build_rule_sql(rules)

    general = parse_general_courses()
    major = parse_major_courses()
    overlap = set(general) & set(major)
    if overlap:
        print(f"교양·전공에 같은 과목코드가 있습니다: {sorted(overlap)[:10]}", file=sys.stderr)
        sys.exit(1)
    courses = {**general, **major}
    lines += ["", "-- 과목: 2026학년도 2학기 강의시간표(교양·전공) 기준. 교양은 필수/선택으로 구분하고 전공은 '전공'으로 입력합니다."]
    lines += build_course_sql(courses)

    SQL_PATH.parent.mkdir(parents=True, exist_ok=True)
    SQL_PATH.write_text("\n".join(lines) + "\n", encoding="utf-8")

    print(f"학과 CSV: {len(dept_rows)}행 -> Department {len(departments)}건")
    print(f"졸업학점 CSV: {len(grad_rows)}행 -> GraduationRule {len(rules)}건")
    print(f"강의시간표 CSV: 교양 {len(general)}건, 전공 {len(major)}건 -> Course {len(courses)}건")
    print(f"저장 위치: {SQL_PATH}")


if __name__ == "__main__":
    main()

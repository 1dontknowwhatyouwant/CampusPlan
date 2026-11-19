import csv
from pathlib import Path

BASE = Path(__file__).resolve().parent
csv_path = BASE / "입학년도별_학부_학과_매핑.csv"
sql_path = BASE.parent / "database" / "seeds" / "05_seed_master.sql"

def sql_value(value):
    if not value:
        return "NULL"
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"

with csv_path.open("r", encoding="utf-8-sig", newline="") as file:
    rows = list(csv.DictReader(file))

departments = {}
for row in rows:
    key = (row["입학년도"], row["학부"], row["학과"])
    departments[key] = row["출처URL"]
lines = ["USE campusplan;", ""]

for (year, college, department), url in departments.items():
    lines.append(
        "INSERT INTO Department "
        "(admission_year, college, department_name, source_url) "
        f"VALUES ({int(year)}, {sql_value(college)}, "
        f"{sql_value(department)}, {sql_value(url)}) "
        "ON DUPLICATE KEY UPDATE "
        "source_url = VALUES(source_url);"
    )

sql_path.parent.mkdir(parents=True, exist_ok=True)
sql_path.write_text("\n".join(lines) + "\n", encoding="utf-8")

print(f"원본 CSV: {len(rows)}행")
print(f"변환 완료: {len(departments)}건")
print(f"저장 위치: {sql_path}")
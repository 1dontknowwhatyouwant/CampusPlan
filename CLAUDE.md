# CampusPlan

@AGENTS.md

위 `AGENTS.md`가 프로젝트 맥락, 개발 원칙, 데이터 취급, 검증·보고 규칙의 기준입니다. 이 문서는 현재 진행 상태와 작업 시 알아야 할 사항만 추가로 기록합니다. 내용이 겹치면 `AGENTS.md`를 따릅니다.

## 현재 진행 상태 (2026-11-19 기준)

폭포수 6단계 중 **4단계(구현)가 진행 중이며, 그중 DB 구축만 끝난 상태**입니다. 작업 전에 실제 파일 상태를 다시 확인합니다.

| 단계 | 상태 |
|---|---|
| 1. 요구사항·범위 | 완료 (계획서는 로컬 보관, 저장소에 올리지 않음) |
| 2. 데이터 조사·정제 | 대부분 완료. 졸업 학점 기준은 공식 자료 반영, 그 외 조사 내용은 확인 전 |
| 3. DB·API·화면 설계 | DB 설계 완료. ERD·API 명세 문서는 `docs/`에 아직 없음 |
| 4. 구현 | DB만 진행. 백엔드·프론트엔드는 시작 전 |
| 5. 통합 확인 | 시작 전 |
| 6. 문서·시연 정리 | 시작 전 |

### 영역별 현황

- `database/`: `schema/01_schema.sql`, `routines/02~04`, `seeds/05~06` 작성됨. MySQL 8.0(Docker)에서 순서대로 실행해 확인했다고 `database/README.md`에 기록되어 있음.
- `backend/`: `pom.xml`과 README만 있고 `src/`가 없음. README의 서블릿·DAO 구조는 계획이며 구현된 것이 아님.
- `frontend/`: `create-next-app` 기본 화면 그대로. 화면 구현 없음.
- `docs/`: 협업 규칙 문서만 있음(브랜치·커밋·이슈/PR 컨벤션).

> 단계가 바뀌면 이 절을 갱신합니다. 이 절의 내용이 저장소 실제 상태와 다르면 실제 상태를 따르고 사용자에게 알립니다.

## 확정되지 않은 사항

- 성적 척도(A+~F, P/NP), 이수 기준, 과목 구분, 학기 상태값은 임시 가정입니다.
- `05_seed_master.sql`의 졸업 규칙(학점 기준)은 공식 졸업학점표에서 만든 값(`is_verified = TRUE`)입니다. `06_seed_test.sql`의 졸업 규칙은 시연용 임시값이며 `is_verified = FALSE`입니다. 봉사 시간과 필수 과목 규칙은 아직 공식 값이 없습니다.
- `05_seed_master.sql`의 과목은 2026학년도 2학기 강의시간표 기준 813건입니다. 교양은 필수/선택으로 구분하고, 전공은 구분 없이 `category = '전공'`입니다.
- 졸업요건 조사 내용(`data_sources/graduation_requirements.md`)은 공식 원문과 최신 개정 여부가 확인되기 전까지 확정 기준이 아닙니다.
- 입학년도 2026 학과 데이터의 출처 URL이 `admission2028.pdf`를 가리킵니다. 연도·출처 확인이 필요합니다.
- 인증 방식과 배포 방식은 정해지지 않았습니다. 인증·권한이 구현되기 전에는 안전하다고 표현하지 않습니다.

## 알려진 문제

- `02_triggers.sql`, `03_procedures.sql`은 `DELIMITER`를 사용하므로 JDBC가 아닌 MySQL Workbench 또는 `mysql` CLI로 실행해야 합니다.
- `06_seed_test.sql`은 초기화 전용이라 같은 DB에 두 번 실행하면 키 중복 오류가 납니다. 스키마가 바뀌면 개발용 DB를 `DROP DATABASE campusplan;` 후 다시 구축합니다.

## 작업 시 참고

- 학점 집계, 봉사시간, 졸업 진단의 계산 기준은 DB의 View·Trigger·Procedure(`CreditSummary`, `VolunteerSummary`, `GraduationCheck`, `ApproveAllPendingVolunteer` 등)에 있습니다. 백엔드·프론트엔드에 같은 규칙을 다시 구현하지 않고, 백엔드는 요청 검증 → DB 호출 → JSON 응답만 담당합니다.
- DB 스키마를 바꾸면 `database/README.md`와 관련 문서를 함께 갱신합니다.
- `data_sources/official/`은 공식 자료 원본, `database/seeds/06_seed_test.sql`은 합성 테스트 데이터입니다. 두 가지를 섞지 않습니다.
- 브랜치·커밋·이슈/PR 규칙은 `docs/`의 컨벤션 문서를 따릅니다. 기능 작업은 `develop`으로 PR을 올리고, `develop`을 `main`으로 PR해 반영합니다. 상시 브랜치는 `main`과 `develop`입니다.
- `계획서/` 폴더는 로컬 참고 자료이며 저장소에 추가하지 않습니다.
- `frontend/AGENTS.md`와 `.agents/skills/`가 있으므로 프론트엔드 작업 전에 해당 지침을 확인합니다.

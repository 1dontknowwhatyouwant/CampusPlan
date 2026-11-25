# CampusPlan

@AGENTS.md

위 `AGENTS.md`가 프로젝트 맥락, 개발 원칙, 데이터 취급, 검증·보고 규칙의 기준입니다. 이 문서는 현재 진행 상태와 작업 시 알아야 할 사항만 추가로 기록합니다. 내용이 겹치면 `AGENTS.md`를 따릅니다.

## 현재 진행 상태 (2026-11-24 기준)

폭포수 6단계 중 **4단계(구현)가 진행 중이며, DB 구축과 백엔드 공통 기반(`/api/health`까지)이 끝난 상태**입니다. 로그인은 구현됐고, 도메인 API와 프론트엔드는 시작 전입니다. 작업 전에 실제 파일 상태를 다시 확인합니다.

| 단계 | 상태 |
|---|---|
| 1. 요구사항·범위 | 완료 (`docs/REQUIREMENTS.md`, 계획서는 로컬 보관, 저장소에 올리지 않음) |
| 2. 데이터 조사·정제 | 대부분 완료. 졸업 학점 기준은 공식 자료 반영, 그 외 조사 내용은 확인 전 |
| 3. DB·API·화면 설계 | DB 설계 완료. API 명세 초안은 `docs/API.md`에 있음(로그인 방식 반영). ERD 문서는 아직 없음(팀원 작업), 화면 설계 문서는 없음 |
| 4. 구현 | DB 완료, 백엔드는 공통 기반, 로그인·권한 검사, `GET /api/me`·`GET /api/courses`·학점·졸업진단·학기·수강 이력·시간표·봉사 조회 API까지 구현(쓰기 API, 관리자 API, 대시보드 구현 전), 프론트엔드는 시작 전 |
| 5. 통합 확인 | 시작 전 |
| 6. 문서·시연 정리 | 시작 전 |

### 영역별 현황

- `database/`: `schema/01_schema.sql`(테이블 9개, 로그인 계정 `Account` 포함), `routines/02~04`, `seeds/05~06` 작성됨. MySQL 8.0(Docker)에서 순서대로 실행해 확인했다고 `database/README.md`에 기록되어 있음.
- `backend/`: 공통 기반만 구현됨(`DBConnectionManager`, `CorsFilter`, `ApiServlet`·`ApiException`·`SqlErrorMapper`·`Validator`·`JsonUtil`, `HealthServlet`)과 로그인(`AuthServlet`, `AuthFilter`, `AccountDao`, `PasswordHasher`)이 구현됨. `GET /api/me`(`StudentServlet`), `GET /api/courses`(`CourseServlet`), `GET /api/me/credits`(`CreditServlet`), `GET /api/me/graduation-check`(`GraduationServlet`), `GET /api/me/semesters`·`enrollments`·`timetable`·`volunteers`가 구현됨. 나머지 도메인 서블릿·DAO는 아직 없고, README의 해당 구조는 계획입니다.
- `frontend/`: `create-next-app` 기본 화면 그대로. 화면 구현 없음.
- `docs/`: 요구사항 정의(`REQUIREMENTS.md`), API 명세 초안(`API.md`), 협업 규칙 문서(브랜치·커밋·이슈/PR 컨벤션)가 있음. ERD는 없음.

> 단계가 바뀌면 이 절을 갱신합니다. 이 절의 내용이 저장소 실제 상태와 다르면 실제 상태를 따르고 사용자에게 알립니다.

## 확정되지 않은 사항

- 성적 척도(A+~F, P/NP), 이수 기준, 과목 구분, 학기 상태값은 임시 가정입니다.
- `05_seed_master.sql`의 졸업 규칙(학점 기준)은 공식 졸업학점표에서 만든 값(`is_verified = TRUE`)입니다. `06_seed_test.sql`의 졸업 규칙은 시연용 임시값이며 `is_verified = FALSE`입니다. 봉사 시간과 필수 과목 규칙은 아직 공식 값이 없습니다.
- `05_seed_master.sql`의 과목은 2026학년도 2학기 강의시간표 기준 813건입니다. 교양은 필수/선택으로 구분하고, 전공은 구분 없이 `category = '전공'`입니다.
- 졸업요건 조사 내용(`data_sources/graduation_requirements.md`)은 공식 원문과 최신 개정 여부가 확인되기 전까지 확정 기준이 아닙니다.
- 입학년도 2026 학과 데이터의 출처 URL이 `admission2028.pdf`를 가리킵니다. 연도·출처 확인이 필요합니다.
- 로그인은 서버 세션과 쿠키, 별도 `Account` 테이블(`STUDENT`/`ADMIN`), 봉사 승인은 관리자만 하는 것으로 설계가 정해졌고(`docs/API.md`), 로그인·로그아웃·역할 검사는 구현됐습니다. 로그인 시도 제한, CSRF 토큰, 세션 만료 시간, 계정 발급 방식은 정해지지 않았습니다. 배포 방식도 정해지지 않았습니다. 인증·권한이 구현되기 전에는 안전하다고 표현하지 않습니다.
- `06_seed_test.sql`의 로그인 계정(`student1`, `student2`, `admin`)은 개발·시연용 테스트 계정이며 테스트 비밀번호는 그 파일 주석에 있습니다. 실제 계정이 아닙니다.

## 알려진 문제

- `02_triggers.sql`, `03_procedures.sql`은 `DELIMITER`를 사용하므로 JDBC가 아닌 MySQL Workbench 또는 `mysql` CLI로 실행해야 합니다.
- `06_seed_test.sql`은 초기화 전용이라 같은 DB에 두 번 실행하면 키 중복 오류가 납니다. 스키마가 바뀌면 개발용 DB를 `DROP DATABASE campusplan;` 후 다시 구축합니다.
- 백엔드 테스트는 조회 전용 API 시나리오 스크립트(`backend/scripts/api_test.py`, 응답을 DB 직접 조회와 대조)뿐입니다. 쓰기 API 테스트와 JUnit 단위 테스트는 없습니다. 오류 변환(`SqlErrorMapper`)의 중복 키·FK·CHECK 처리는 쓰기 API 구현 때 처음 실행됩니다.
- 백엔드 코드를 바꾸면 PR 전에 `python3 backend/scripts/api_test.py`를 실행합니다.
- 로그인 시도 횟수 제한이 없고 세션 만료 시간은 Tomcat 기본값입니다.
- DB 연결 풀이 없어 요청마다 연결을 새로 엽니다(요청당 1~2번). 시연 규모에서는 문제가 없어 보류했고, 배포하거나 느려지면 `DBConnectionManager`에 HikariCP를 도입합니다(DAO는 바꾸지 않음).

## 작업 시 참고

- 학점 집계, 봉사시간, 졸업 진단의 계산 기준은 DB의 View·Trigger·Procedure(`CreditSummary`, `VolunteerSummary`, `GraduationCheck`, `ApproveAllPendingVolunteer` 등)에 있습니다. 백엔드·프론트엔드에 같은 규칙을 다시 구현하지 않고, 백엔드는 요청 검증 → DB 호출 → JSON 응답만 담당합니다.
- DB 스키마를 바꾸면 `database/README.md`와 관련 문서를 함께 갱신합니다.
- DB 시간대는 한국 시간(`+09:00`)입니다. 로컬 MySQL 컨테이너는 `--default-time-zone=+09:00`으로 실행합니다(`database/README.md`). 백엔드는 DATE/DATETIME을 `getObject(..., LocalDate/LocalDateTime.class)`로 읽어 JVM 시간대에 영향받지 않게 합니다.
- 백엔드 실행: JDK 17, Tomcat 10.1, Maven이 필요하고(`brew install openjdk@17 tomcat@10 maven`), DB 접속 정보는 저장소 루트 `.env`(`.env.example` 참고, Git에 올리지 않음)에 둡니다. `backend/scripts/dev.sh`가 JDK 17로 빌드하고 프로젝트 전용 Tomcat에서 실행합니다. 이 PC의 기본 `java`는 17이 아닐 수 있으므로 스크립트를 사용합니다.
- API는 `docs/API.md`가 기준입니다. 응답은 래퍼 없이 데이터를 그대로 반환하고, 오류는 `{"error": {"code", "message"}}`입니다. 학생 데이터 경로는 로그인 세션 기준의 `/api/me/...`입니다.
- `data_sources/official/`은 공식 자료 원본, `database/seeds/06_seed_test.sql`은 합성 테스트 데이터입니다. 두 가지를 섞지 않습니다.
- 브랜치·커밋·이슈/PR 규칙은 `docs/`의 컨벤션 문서를 따릅니다. 기능 작업은 `develop`으로 PR을 올리고, `develop`을 `main`으로 PR해 반영합니다. 상시 브랜치는 `main`과 `develop`입니다.
- `계획서/` 폴더는 로컬 참고 자료이며 저장소에 추가하지 않습니다.
- `frontend/AGENTS.md`와 `.agents/skills/`가 있으므로 프론트엔드 작업 전에 해당 지침을 확인합니다.

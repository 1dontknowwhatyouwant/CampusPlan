# CampusPlan Backend

한서대학교 학업 및 졸업 준비 관리 시스템 **CampusPlan**의 Java Servlet REST API 서버입니다.  
프로젝트의 핵심 계산 로직(학점 집계, 승인 봉사시간 합산, 졸업요건 진단)은 **MySQL(View, Trigger, Stored Procedure)**에서 일관되게 관리하며, 백엔드는 **요청 검증 → DB 호출 → JSON 응답 반환** 역할을 담당합니다.

---

## 1. 기술 스택

- **Language**: Java (JDK 17 권장)
- **Web / WAS**: Java Servlet, Apache Tomcat (10.1.x 기준 `jakarta.servlet.*`)
- **Database**: MySQL 8.x
- **Build Tool**: Maven (`pom.xml`, WAR 패키징)
- **IDE**: Visual Studio Code (`Extension Pack for Java`, `Community Server Connectors`)

---

## 2. 디렉터리 구조

```text
backend/
├── pom.xml                                  # Maven 설정 및 의존성 관리
├── README.md                                # 백엔드 안내 및 구현 순서 문서
├── scripts/
│   └── dev.sh                               # JDK 17 빌드 + 프로젝트 전용 Tomcat 실행
└── src/
    └── main/
        ├── java/com/campusplan/
        │   ├── config/
        │   │   ├── DBConnectionManager.java # 환경변수 기반 MySQL 커넥션 관리
        │   │   └── CorsFilter.java          # 프론트엔드(Next.js) 연동용 CORS 필터
        │   ├── servlet/                     # 도메인별 REST API 엔드포인트
        │   │   ├── ApiServlet.java          # 공통 기반 클래스: 예외 → JSON 오류 응답 변환
        │   │   ├── HealthServlet.java       # 서버 및 DB 연결 상태 확인 (/api/health)
        │   │   ├── CourseServlet.java       # 과목 조회 (/api/courses)
        │   │   ├── StudentServlet.java      # 학생 정보 및 대시보드 요약 (/api/students)
        │   │   ├── EnrollmentServlet.java   # 수강 이력 및 주간 시간표 (/api/enrollments)
        │   │   ├── VolunteerServlet.java    # 봉사활동 내역 관리 (/api/volunteers)
        │   │   └── GraduationServlet.java   # 졸업요건 진단 조회 (/api/graduation)
        │   ├── dao/                         # DB 접근 계층 (PreparedStatement / CallableStatement)
        │   ├── dto/                         # 요청/응답 JSON 데이터 객체 (ErrorResponse 등)
        │   └── util/                        # JsonUtil, Validator, ApiException, SqlErrorMapper
        └── webapp/
            └── WEB-INF/
                └── web.xml                  # 서블릿 및 필터 설정
```

---

## 3. 로컬 실행

사전 준비: `brew install openjdk@17 tomcat@10 maven`, DB 컨테이너 실행, 저장소 루트의 `.env`(`.env.example` 참고).

```bash
backend/scripts/dev.sh        # 빌드 후 Tomcat 실행 (http://localhost:8080/api/health)
backend/scripts/dev.sh build  # 빌드만
```

- 스크립트는 JDK 17로 빌드하고, `backend/.tomcat/`(Git 제외)에 프로젝트 전용 Tomcat 인스턴스를 만들어 `ROOT.war`로 배포합니다. 전역 Java 설정은 바꾸지 않습니다.
- DB 접속 정보는 `.env`를 읽어 환경 변수로 전달합니다. 코드나 저장소에는 두지 않습니다.
- 확인된 동작: `/api/health`가 DB 연결 시 200, DB가 꺼져 있으면 503을 반환합니다. 로그인·로그아웃·`/api/auth/me`와 역할별 접근 제한(401/403)이 동작합니다. 구현된 API 목록은 [API 명세](../docs/API.md) 첫 문단에 있습니다.
- 테스트 계정(`student1`, `student2`, `admin`)은 `database/seeds/06_seed_test.sql`에 있고 테스트 비밀번호는 그 파일 주석에 있습니다.
- 매핑되지 않은 경로(404)와 허용되지 않은 메서드(405)는 `web.xml`의 error-page가 `ErrorServlet`(`/_error`)으로 보내 JSON 오류 형식으로 응답합니다. 이 응답에는 `CorsFilter`가 적용되지 않아 브라우저(origin 3000)에서는 본문을 읽지 못할 수 있습니다.

### API 시나리오 테스트

서버(`dev.sh`)와 DB 컨테이너가 실행 중인 상태에서 저장소 루트에서 실행합니다. 표준 라이브러리만 쓰므로 설치할 것이 없습니다.

```bash
python3 backend/scripts/api_test.py
```

- 조회 테스트는 로그인 세션만 만들고 DB 데이터를 바꾸지 않습니다. 쓰기 테스트는 테스트용 행을 만들어 API로 처리한 뒤 **만든 행만 지우고**, 끝에서 테이블이 시작 때와 같은지 확인합니다(자동 증가 번호는 되돌리지 않음). DB를 바꾸지 않으려면 `python3 backend/scripts/api_test.py --read-only`를 씁니다.
- 기대값을 하드코딩하지 않고 **DB 직접 조회 결과와 API 응답을 대조**합니다. 테스트 데이터가 바뀌어도 대부분 그대로 동작합니다.
- 검사 항목: 인증(401/403, 로그인 실패 응답 통일, 세션 쿠키 속성, 세션 고정 방지), CORS, 404/405 JSON, 과목 검색·검증, 학생별 학생 정보·학점·졸업진단·학기·수강 이력·시간표·봉사 값과 시각 형식.
- 하나라도 실패하면 종료 코드 1입니다. 코드를 바꾼 뒤 PR 전에 실행합니다.
- 쓰기 테스트는 봉사 API(등록·관리자 목록·개별·일괄 승인)만 있습니다. 수강·시간표·성적 쓰기 API를 구현할 때 같은 방식(만든 행만 삭제 + 복원 확인)으로 추가합니다. 일괄 승인은 시드의 대기 내역을 건드리지 않도록 대기 내역이 없는 학생(student2)으로만 시험합니다.

## 4. 백엔드 구현 순서

### Step 1. 개발 환경 및 프로젝트 초기 세팅
- [x] 로컬 PC에 JDK 17, Apache Tomcat 10.1, Maven, MySQL(Docker) 설치 및 실행 확인
- [ ] VS Code 확장 프로그램 설치 (`Extension Pack for Java`, `Community Server Connectors`)
- [x] `backend/pom.xml` 작성 및 Maven 의존성 다운로드 완료 (`jakarta.servlet-api` 6.0.0, `mysql-connector-j` 9.7.0, `gson` 2.15.0; Java 17 컴파일 기준)
- [x] 패키지 디렉터리(`config`, `servlet`, `dto`, `util`) 및 `web.xml` 생성 (`dao`는 도메인 API 구현 시 추가)

### Step 2. DB 연결 및 공통 기반 구현
- [x] `DBConnectionManager`: 환경변수(`DB_URL`, `DB_USER`, `DB_PASSWORD`) 기반 MySQL 연결 구현
- [x] `CorsFilter`: `http://localhost:3000` 요청 허용 설정
- [x] `JsonUtil`, `ApiException`, `ApiServlet`, `Validator`, `SqlErrorMapper`: 공통 응답·오류 처리·입력 검증. 응답 규격은 [API 명세](../docs/API.md)를 따릅니다. 성공 시 데이터를 그대로 반환하고, 오류는 `{"error": {"code", "message"}}`입니다.
- [x] `HealthServlet` (`GET /api/health`) 작성 후 로컬 Tomcat에서 DB 연결 정상 여부 테스트

### Step 2-1. 로그인·권한
- [x] `PasswordHasher`(PBKDF2-HMAC-SHA256), `AccountDao`: `Account` 조회와 비밀번호 검증. 계정이 없을 때도 같은 계산 비용을 쓰고 실패 응답은 같은 메시지로 통일
- [x] `AuthServlet`: `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/me`. 로그인 시 기존 세션을 버리고 새 세션 생성(세션 고정 방지)
- [x] `AuthFilter`: 로그인 여부와 역할 검사(`/api/me/**`, `/api/enrollments/**`, `/api/schedules/**`는 `STUDENT`, `/api/admin/**`는 `ADMIN`). 필터 순서(CORS → 인증)와 세션 쿠키 설정(`HttpOnly`, `SameSite=Lax`)은 `web.xml`
- [x] `CorsFilter`: `http://localhost:3000`에 쿠키 포함 요청 허용. 상태 변경 요청(POST/PUT/PATCH/DELETE)에 다른 `Origin`이 있으면 403 (CSRF 방어). 요청 본문은 `JsonUtil.read`가 `application/json`만 받음
- [ ] 수강·시간표 항목의 소유권 검사는 해당 API 구현 시 각 API에서 처리

### Step 3. 선행 산출물 동기화 (DB & API 명세)
- [x] `database/` 폴더의 테이블 DDL, View, Trigger, Stored Procedure가 로컬 MySQL에 반영되었는지 확인
- [ ] `docs/` API 명세서와 JSON 필드명·자료형·HTTP 상태 코드 일치 확인

### Step 4. 도메인별 API 순차 구현
*(의존성이 낮은 기준 데이터부터 구현하며, 모든 SQL은 `PreparedStatement` 파라미터 바인딩을 사용합니다.)*
- [x] **과목 API (`CourseServlet`, `CourseDao`)**: `GET /api/courses` 검색·페이지 (`Course`는 2026학년도 2학기 개설분만 담고 있어 학기별 개설 과목 조회는 만들지 않음)
- [ ] **학생 · 대시보드 API (`StudentServlet`, `StudentDao`)**: `GET /api/me`(학생 기본 정보)는 구현됨. 학점/봉사 요약 View 조회와 대시보드는 아직 없음
- [ ] **수강 · 시간표 API (`EnrollmentServlet`, `EnrollmentDao`)**: 조회는 구현됨(`GET /api/me/semesters`, `GET /api/me/enrollments`, `GET /api/me/timetable`). 수강 등록·수정·삭제와 시간표 칸 등록·삭제는 아직 없음
- [x] **봉사활동 API (`VolunteerServlet`, `VolunteerDao`)**: 구현됨: `GET /api/me/volunteers`(승인 시간은 View `VolunteerSummary`), `POST /api/me/volunteers`(상태는 항상 `대기`), 관리자 API(`AdminVolunteerServlet`, `AdminStudentServlet`): 전체 내역 조회, 프로시저 `ApproveVolunteer`·`ApproveAllPendingVolunteer` 호출
- [x] **학점·졸업요건 진단 API (`CreditServlet`, `GraduationServlet`, `GraduationDao`)**: `GET /api/me/credits`(View `CreditSummary`), `GET /api/me/graduation-check`(View `GraduationCheck`) 결과를 그대로 반환

### Step 5. 예외 처리 검증 및 문서 갱신
- [ ] 필수 파라미터 누락, 잘못된 값 입력, 미존재 데이터 조회 시 오류 응답(`400`, `404` 등) 검증
- [ ] 변경된 API 스펙이 있을 경우 `docs/` API 문서 동기화 후 `develop` 브랜치로 PR 생성

---

## 5. 구현 원칙 및 주의사항

1. **계산 로직 중복 금지**: 학점 평균(GPA), 이수구분별 학점 합계, 승인 봉사시간, 졸업 충족 여부는 자바 코드에서 별도로 계산하지 않고 `database/`에 정의된 View 및 Stored Procedure 결과를 조회합니다.
2. **보안 및 비밀값 관리**: DB 계정 정보나 비밀번호는 절대 코드에 직접 작성하거나 Git에 커밋하지 않습니다.
3. **로컬 DB 동기화**: `database/` 폴더의 SQL 파일이 변경되면 팀원에게 공유하여 각자 로컬 MySQL 환경을 동일하게 유지합니다.

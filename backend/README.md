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
- 확인된 동작: `/api/health`가 DB 연결 시 200, DB가 꺼져 있으면 503을 반환합니다. 도메인 API는 아직 없습니다.
- 알려진 한계: 서블릿이 매핑되지 않은 경로(404)나 허용되지 않은 메서드(405)는 Tomcat 기본 HTML 오류 페이지로 응답합니다. JSON 오류 형식으로 통일하는 작업은 남아 있습니다.

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

### Step 3. 선행 산출물 동기화 (DB & API 명세)
- [x] `database/` 폴더의 테이블 DDL, View, Trigger, Stored Procedure가 로컬 MySQL에 반영되었는지 확인
- [ ] `docs/` API 명세서와 JSON 필드명·자료형·HTTP 상태 코드 일치 확인

### Step 4. 도메인별 API 순차 구현
*(의존성이 낮은 기준 데이터부터 구현하며, 모든 SQL은 `PreparedStatement` 파라미터 바인딩을 사용합니다.)*
- [ ] **과목 API (`CourseServlet`, `CourseDao`)**: 전체/학기별 개설 과목 조회
- [ ] **학생 · 대시보드 API (`StudentServlet`, `StudentDao`)**: 학생 기본 정보 및 학점/봉사 요약 View 조회
- [ ] **수강 · 시간표 API (`EnrollmentServlet`, `EnrollmentDao`)**: 수강 내역 등록·수정·삭제 및 학기별 주간 시간표 조회
- [ ] **봉사활동 API (`VolunteerServlet`, `VolunteerDao`)**: 봉사 내역 등록·조회 및 승인 봉사시간 집계 View 조회
- [ ] **졸업요건 진단 API (`GraduationServlet`, `GraduationDao`)**: DB 졸업 진단 Stored Procedure / View 호출 및 결과 반환

### Step 5. 예외 처리 검증 및 문서 갱신
- [ ] 필수 파라미터 누락, 잘못된 값 입력, 미존재 데이터 조회 시 오류 응답(`400`, `404` 등) 검증
- [ ] 변경된 API 스펙이 있을 경우 `docs/` API 문서 동기화 후 `develop` 브랜치로 PR 생성

---

## 5. 구현 원칙 및 주의사항

1. **계산 로직 중복 금지**: 학점 평균(GPA), 이수구분별 학점 합계, 승인 봉사시간, 졸업 충족 여부는 자바 코드에서 별도로 계산하지 않고 `database/`에 정의된 View 및 Stored Procedure 결과를 조회합니다.
2. **보안 및 비밀값 관리**: DB 계정 정보나 비밀번호는 절대 코드에 직접 작성하거나 Git에 커밋하지 않습니다.
3. **로컬 DB 동기화**: `database/` 폴더의 SQL 파일이 변경되면 팀원에게 공유하여 각자 로컬 MySQL 환경을 동일하게 유지합니다.

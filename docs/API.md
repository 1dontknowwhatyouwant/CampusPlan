# API 명세 (초안)

CampusPlan 백엔드(Java Servlet REST API)의 엔드포인트 초안입니다. [요구사항 정의](REQUIREMENTS.md)의 기능 번호(FR-xx)를 기준으로 하고, 필드는 `database/`의 스키마·View·Procedure와 맞췄습니다. 설계 문서이며, 구현된 API는 `GET /api/health`, 인증(`/api/auth/login`, `/api/auth/logout`, `/api/auth/me`), `GET /api/me`, `GET /api/courses`, `GET /api/me/credits`, `GET /api/me/graduation-check`, `GET /api/me/semesters`, `GET /api/me/enrollments`, `GET /api/me/timetable`, `GET /api/me/volunteers`, `POST /api/me/volunteers`, 관리자 봉사 API(`GET /api/admin/volunteers`, `POST /api/admin/volunteers/{recordId}/approve`, `POST /api/admin/students/{studentId}/volunteers/approve-all`)이고 나머지(수강·시간표·성적 쓰기 API, 학기 생성, 대시보드)는 구현 전입니다.

## 1. 공통 규칙

### 인증과 권한

로그인한 계정(`Account`)으로 사용자를 식별합니다. 학생 번호를 URL로 받지 않고, 서버가 세션에서 로그인한 학생을 찾습니다.

| 항목 | 내용 |
| --- | --- |
| 로그인 유지 | 서버 세션(`HttpSession`)과 쿠키(`JSESSIONID`). 쿠키는 `HttpOnly`, `SameSite=Lax`로 설정합니다. |
| 역할 | `STUDENT`(학생 한 명에 연결), `ADMIN`(학생에 연결되지 않음) |
| 비밀번호 | 평문을 저장하지 않고 `pbkdf2_sha256$반복횟수$salt$hash` 형식 해시로 저장·비교합니다. |
| 프론트엔드 | 요청에 쿠키를 포함해야 합니다(`fetch`의 `credentials: "include"`). 백엔드는 `http://localhost:3000`에서 온 요청만 쿠키 포함 요청으로 허용합니다. |
| 출처 검사 (CSRF) | 상태를 바꾸는 요청(POST/PUT/PATCH/DELETE)에 `Origin` 헤더가 있고 `http://localhost:3000`이 아니면 403 `FORBIDDEN`입니다. `SameSite=Lax`는 다른 사이트의 요청만 막고, `localhost`의 다른 포트처럼 같은 사이트로 취급되는 출처에서는 쿠키가 전송되기 때문입니다. `Origin`이 없는 요청(curl 등)은 통과합니다. |
| 요청 본문 | 본문이 있는 요청은 `Content-Type: application/json`이어야 하며, 아니면 400 `INVALID_REQUEST`입니다. |
| 인증이 필요 없는 API | `GET /api/health`, `POST /api/auth/login` |

| 구분 | 경로 | 접근 가능한 역할 |
| --- | --- | --- |
| 학생 본인 데이터 | `/api/me/**` | `STUDENT` |
| 수강·시간표 개별 항목 | `/api/enrollments/{id}/**`, `/api/schedules/{id}` | `STUDENT` (본인 항목만) |
| 과목 조회 | `/api/courses` | `STUDENT`, `ADMIN` |
| 관리자 기능 | `/api/admin/**` | `ADMIN` |
| 로그인 상태 확인·로그아웃 | `/api/auth/me`, `/api/auth/logout` | 로그인한 모든 계정 |

- **소유권 검사**: 경로에 ID가 있는 수강·시간표 API는 해당 항목이 로그인한 학생의 것인지 확인합니다. 남의 항목이거나 없는 항목이면 구분 없이 404 `NOT_FOUND`를 반환해 존재 여부를 드러내지 않습니다.
- **봉사 승인**: 승인은 `ADMIN`만 할 수 있고, 학생은 내역을 등록만 합니다.
- **계정 발급**: 회원가입 기능은 없습니다. 계정은 시드 데이터로 만듭니다.

### 기본 형식

| 항목 | 규칙 |
| --- | --- |
| 기본 경로 | `/api` |
| 형식 | 요청·응답 모두 `application/json; charset=UTF-8` |
| 필드명 | JSON은 camelCase (`studentId`), DB 컬럼은 snake_case (`student_id`) |
| 날짜 | `yyyy-MM-dd` |
| 숫자 | 학점·봉사시간은 JSON 숫자 (DB `DECIMAL`은 소수 1자리까지) |
| 불리언 | DB의 0/1은 `true`/`false`로 변환 |
| 계산 | 학점 합계·이수 여부·충족 여부는 DB(View·Trigger·Procedure)가 계산한 값을 그대로 전달합니다. 백엔드·프론트엔드에서 다시 계산하지 않습니다. |

### 오류 응답

모든 오류는 같은 형식으로 반환합니다.

```json
{
  "error": {
    "code": "SCHEDULE_CONFLICT",
    "message": "같은 학기에 요일/교시가 겹치는 수업이 이미 있습니다."
  }
}
```

| HTTP | code | 상황 |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | 필수값 누락, 형식 오류, 허용되지 않는 값(성적·요일·학기 등), `Content-Type`이 `application/json`이 아닌 본문, DB `CHECK` 위반 |
| 401 | `UNAUTHENTICATED` | 로그인하지 않았거나 세션이 만료됨 |
| 401 | `INVALID_CREDENTIALS` | 로그인 실패 (아이디가 없거나 비밀번호가 틀림. 어느 쪽인지 알려 주지 않음) |
| 403 | `FORBIDDEN` | 로그인했지만 이 API를 쓸 수 없는 역할 (학생이 관리자 API 호출, 관리자가 학생 API 호출), 허용되지 않은 출처(`Origin`)의 상태 변경 요청 |
| 404 | `NOT_FOUND` | 학생·학기·과목·수강·봉사 내역이 없거나 본인의 것이 아님, 존재하지 않는 경로 |
| 405 | `METHOD_NOT_ALLOWED` | 해당 경로에서 지원하지 않는 HTTP 메서드 |
| 409 | `SCHEDULE_CONFLICT` | 시간표 충돌 (트리거 `trg_schedule_conflict`의 `SQLSTATE 45000`) |
| 409 | `DUPLICATE` | 유니크 제약 위반 (같은 학기에 같은 과목 중복 등록 등) |
| 409 | `INVALID_STATE` | 이미 처리된 봉사 내역을 다시 승인하는 등 현재 상태에서 할 수 없는 요청 |
| 500 | `INTERNAL_ERROR` | 그 외 서버 오류 (DB 오류 메시지는 응답에 그대로 싣지 않음) |

### 입력 검증

SQL은 파라미터 바인딩만 사용합니다. 서버는 아래 값을 먼저 검증하고, DB의 `CHECK`는 마지막 방어선으로 둡니다.

| 값 | 허용 |
| --- | --- |
| `grade` | `A+`, `A`, `B+`, `B`, `C+`, `C`, `D+`, `D`, `F`, `P`, `NP`, `null` (임시 가정) |
| `term` | `1`, `2` |
| `status` (학기) | `예정`, `재학`, `이수` (임시 가정) |
| `day` | `월`, `화`, `수`, `목`, `금`, `토` |
| `period` | 1 이상의 정수 |
| `hours` | 0보다 크고 소수 첫째 자리까지, 9999.9 이하 (DB `DECIMAL(5,1)`) |
| `activityDate` | `yyyy-MM-dd` 형식의 실제 있는 날짜 |
| `activityName` | 비어 있지 않은 문자열, 100자 이하 |

## 2. 엔드포인트 요약

| 기능 | 메서드 | 경로 | 역할 | 호출 DB 객체 |
| --- | --- | --- | --- | --- |
| 공통 | GET | `/api/health` | 없음 | (DB 연결 확인) |
| 인증 | POST | `/api/auth/login` | 없음 | Account |
| 인증 | POST | `/api/auth/logout` | 로그인 | (세션 종료) |
| 인증 | GET | `/api/auth/me` | 로그인 | Account |
| FR-01 | GET | `/api/me` | STUDENT | Student, Department |
| FR-02 | GET | `/api/courses` | 로그인 | Course |
| FR-02 | GET | `/api/me/semesters` | STUDENT | Semester |
| FR-02 | POST | `/api/me/semesters` | STUDENT | Semester (선택 기능) |
| FR-03 | GET | `/api/me/timetable` | STUDENT | Schedule, Enrollment, Course |
| FR-03 | POST | `/api/me/enrollments` | STUDENT | Enrollment, `trg_enrollment_insert` |
| FR-03 | POST | `/api/enrollments/{enrollmentId}/schedules` | STUDENT | Schedule, `trg_schedule_conflict` |
| FR-03 | DELETE | `/api/schedules/{scheduleId}` | STUDENT | Schedule |
| FR-03 | DELETE | `/api/enrollments/{enrollmentId}` | STUDENT | Enrollment, Schedule |
| FR-04 | GET | `/api/me/enrollments` | STUDENT | Enrollment, Course |
| FR-04 | PATCH | `/api/enrollments/{enrollmentId}` | STUDENT | `trg_enrollment_update` |
| FR-04 | GET | `/api/me/credits` | STUDENT | `CreditSummary` |
| FR-05 | GET | `/api/me/volunteers` | STUDENT | VolunteerRecord, `VolunteerSummary` |
| FR-05 | POST | `/api/me/volunteers` | STUDENT | VolunteerRecord |
| FR-05 | GET | `/api/admin/volunteers` | ADMIN | VolunteerRecord, Student |
| FR-05 | POST | `/api/admin/students/{studentId}/volunteers/approve-all` | ADMIN | `ApproveAllPendingVolunteer` |
| FR-05 | POST | `/api/admin/volunteers/{recordId}/approve` | ADMIN | `ApproveVolunteer` |
| FR-06 | GET | `/api/me/graduation-check` | STUDENT | `GraduationCheck` |
| FR-07 | GET | `/api/me/dashboard` | STUDENT | 위 조회를 묶어서 반환 |

## 3. 엔드포인트 상세

### 공통

#### `GET /api/health`

서버와 DB 연결 상태를 확인합니다. 로그인이 필요 없습니다.

```json
{ "status": "ok", "database": "up" }
```

- DB에 연결할 수 없으면 `503`과 `{ "status": "degraded", "database": "down" }`을 반환합니다. 이 응답만 오류 형식(`error`)을 쓰지 않습니다.

### 인증

#### `POST /api/auth/login`

아이디와 비밀번호로 로그인합니다. 성공하면 세션을 만들고 `Set-Cookie`로 `JSESSIONID`를 내려줍니다. 이미 로그인한 세션에서 다시 호출하면 새 세션으로 바꿉니다.

```json
{ "loginId": "student1", "password": "..." }
```

```json
{ "loginId": "student1", "role": "STUDENT", "studentId": 1, "name": "김학생" }
```

- 관리자 계정은 `studentId`와 `name`이 `null`입니다.
- 400 `INVALID_REQUEST`: `loginId` 또는 `password` 누락
- 401 `INVALID_CREDENTIALS`: 아이디가 없거나 비밀번호가 틀림 (두 경우의 응답과 메시지를 같게 합니다)

#### `POST /api/auth/logout`

세션을 종료합니다. 응답은 `204`입니다. 로그인하지 않은 상태에서 호출하면 401 `UNAUTHENTICATED`입니다.

#### `GET /api/auth/me`

현재 로그인한 계정을 반환합니다. 화면이 시작될 때 로그인 여부를 확인하는 데 씁니다. 응답 형식은 로그인 응답과 같고, 로그인하지 않았으면 401 `UNAUTHENTICATED`입니다.

### FR-01 학생

#### `GET /api/me`

로그인한 학생의 기본 정보와 소속을 반환합니다. 학생 등록·수정 API와 학생 목록 API는 만들지 않습니다.

```json
{
  "studentId": 1,
  "name": "홍길동",
  "admissionYear": 2023,
  "college": "항공학부",
  "departmentName": "항공소프트웨어공학과"
}
```

- 403 `FORBIDDEN`: 관리자 계정

### FR-02 과목·학기

#### `GET /api/courses`

과목 목록을 반환합니다. 2026학년도 2학기 개설분 813건이 들어 있으므로 검색과 페이지를 지원합니다.

| 쿼리 | 설명 | 기본값 |
| --- | --- | --- |
| `q` | 과목명 또는 과목코드 부분 일치 | (없음) |
| `category` | `전공`, `교양필수`, `교양선택` 중 하나 | (없음) |
| `page` | 0부터 시작 | `0` |
| `size` | 1~100 | `20` |

```json
{
  "items": [
    { "courseId": 12, "code": "1234567", "name": "데이터베이스", "credits": 3, "category": "전공" }
  ],
  "page": 0,
  "size": 20,
  "totalCount": 813
}
```

- 담당교수, 요일·교시는 `Course`에 없으므로 제공하지 않습니다.
- 과목명, 과목코드 순으로 정렬합니다. 검색어의 `%`, `_`는 와일드카드가 아니라 문자 그대로 검색합니다. 테스트 과목(`TEST-*`, `[테스트] ...`)도 목록에 포함되며 과목명 정렬상 앞쪽에 나옵니다.
- `page`, `size`, `category` 값이 올바르지 않으면 400 `INVALID_REQUEST`입니다.

#### `GET /api/me/semesters`

학생의 학기 목록을 최신순으로 반환합니다.

```json
[
  { "semesterId": 3, "year": 2026, "term": 2, "status": "재학" }
]
```

#### `POST /api/me/semesters` (선택)

학기 생성 화면을 만들 때만 구현합니다.

```json
{ "year": 2026, "term": 2, "status": "재학" }
```

- 응답: `201` + 생성된 학기 (위 목록 항목과 같은 형식)
- 409 `DUPLICATE`: 같은 학생의 같은 연도·학기가 이미 있음

### FR-03 시간표

#### `GET /api/me/timetable?semesterId={semesterId}`

학기의 주간 시간표를 수강 과목 단위로 묶어서 반환합니다. `semesterId`를 생략하면 상태가 `재학`인 가장 최근 학기를 사용합니다. `재학` 학기가 없으면 404 `NOT_FOUND`입니다.

```json
{
  "semester": { "semesterId": 3, "year": 2026, "term": 2, "status": "재학" },
  "entries": [
    {
      "enrollmentId": 21,
      "courseId": 12,
      "courseName": "데이터베이스",
      "credits": 3,
      "slots": [
        { "scheduleId": 40, "day": "월", "period": 3, "room": "A101" },
        { "scheduleId": 41, "day": "월", "period": 4, "room": "A101" }
      ]
    }
  ]
}
```

- 시간표가 등록되지 않은 수강 과목은 `slots`가 빈 배열입니다.
- `semesterId`가 없는 학기이거나 다른 학생의 학기면 404 `NOT_FOUND`입니다.
- `entries`는 과목명 순, `slots`는 요일(월~토)·교시 순입니다.

#### `POST /api/me/enrollments`

학기에 과목을 수강 등록합니다. 성적은 선택이며, `completed`는 트리거가 정합니다.

```json
{ "semesterId": 3, "courseId": 12, "grade": null }
```

- 응답: `201` + 수강 항목 (`GET .../enrollments`의 항목 형식)
- 400 `INVALID_REQUEST`: 학기가 없거나 로그인한 학생의 것이 아님 (복합 외래 키 위반은 두 경우를 구분할 수 없어 하나로 처리)
- 404 `NOT_FOUND`: 과목이 없음
- 409 `DUPLICATE`: 같은 학기에 같은 과목이 이미 등록됨

#### `POST /api/enrollments/{enrollmentId}/schedules`

수강 과목에 요일·교시·강의실을 등록합니다. 한 번에 여러 칸을 등록할 수 있고, 하나라도 실패하면 전체를 취소합니다.

```json
{
  "slots": [
    { "day": "월", "period": 3, "room": "A101" },
    { "day": "월", "period": 4, "room": "A101" }
  ]
}
```

- 응답: `201` + 생성된 `slots` (`scheduleId` 포함)
- 409 `SCHEDULE_CONFLICT`: 같은 학생·학기에 같은 요일·교시 수업이 이미 있음
- 409 `DUPLICATE`: 같은 수강 과목에 같은 요일·교시가 이미 있음
- 강의 시간은 `Course`에 저장되어 있지 않아 사용자가 직접 입력합니다.

#### `DELETE /api/schedules/{scheduleId}`

시간표 한 칸을 삭제합니다. 응답은 `204`입니다. 수강 과목 자체는 삭제하지 않습니다.

#### `DELETE /api/enrollments/{enrollmentId}`

수강 등록을 취소합니다. 해당 수강 과목의 시간표 칸(`Schedule`)을 먼저 지우고 수강 이력을 지우며, 두 삭제는 한 트랜잭션으로 처리합니다. 응답은 `204`입니다.

- 404 `NOT_FOUND`: 수강 이력이 없거나 본인의 것이 아님
- 성적이 입력된 수강도 삭제할 수 있으므로 화면에서 확인을 거칩니다.

### FR-04 학점·성적

#### `GET /api/me/enrollments?semesterId={semesterId}`

수강 이력을 반환합니다. `semesterId`를 생략하면 전체 학기입니다. 최신 학기부터, 같은 학기 안에서는 과목명 순입니다. `semesterId`가 없는 학기이거나 다른 학생의 학기면 404 `NOT_FOUND`, 정수가 아니면 400 `INVALID_REQUEST`입니다.

```json
[
  {
    "enrollmentId": 21,
    "semesterId": 3,
    "year": 2026,
    "term": 2,
    "courseId": 12,
    "courseCode": "1234567",
    "courseName": "데이터베이스",
    "credits": 3,
    "category": "전공",
    "grade": "A+",
    "completed": true
  }
]
```

#### `PATCH /api/enrollments/{enrollmentId}`

성적을 입력·수정합니다. `completed`는 트리거(`trg_enrollment_update`)가 자동으로 바꾸므로 요청에 받지 않습니다. 응답에는 갱신된 수강 항목을 반환합니다.

```json
{ "grade": "B+" }
```

- 성적을 지우려면 `{ "grade": null }`
- 400 `INVALID_REQUEST`: 허용되지 않는 성적 값, `grade` 필드 누락
- 404 `NOT_FOUND`: 수강 이력이 없거나 본인의 것이 아님

#### `GET /api/me/credits`

`CreditSummary` 결과입니다. 이수 완료된 수강만 집계하며, 같은 과목을 재수강한 경우 한 번만 인정합니다.

```json
{
  "studentId": 1,
  "totalCredits": 45,
  "majorCredits": 21,
  "generalCredits": 24
}
```

### FR-05 봉사시간

#### `GET /api/me/volunteers?status={status}`

봉사 내역과 승인된 누적 시간(`VolunteerSummary`)을 반환합니다. `status`는 `대기`, `승인`, `반려`이며 생략하면 전체입니다. 승인된 시간만 `approvedHours`에 반영됩니다.

```json
{
  "approvedHours": 12.0,
  "records": [
    {
      "recordId": 7,
      "activityName": "교내 환경 정화",
      "activityDate": "2026-10-03",
      "hours": 4.0,
      "status": "승인",
      "approvedAt": "2026-10-10T14:20:00"
    }
  ]
}
```

- `approvedAt`은 승인 전에는 `null`입니다.
- `approvedHours`는 `status` 필터와 관계없이 승인된 전체 시간입니다. `records`는 활동일 최신순입니다.
- `status`가 허용 값이 아니면 400 `INVALID_REQUEST`입니다.

#### `POST /api/me/volunteers`

로그인한 학생의 봉사 내역을 등록합니다. 상태는 항상 `대기`로 만들어지며 요청으로 정할 수 없습니다.

```json
{ "activityName": "교내 환경 정화", "activityDate": "2026-10-03", "hours": 4.0 }
```

- 응답: `201` + 등록된 내역
- 400 `INVALID_REQUEST`: 시간이 0 이하, 이름·날짜 누락

#### `GET /api/admin/volunteers?status={status}` (관리자)

모든 학생의 봉사 내역을 반환합니다. `status`는 `대기`, `승인`, `반려`이며 생략하면 전체입니다. 승인 화면에서 대기 내역을 확인하는 데 씁니다.

```json
[
  {
    "recordId": 8,
    "studentId": 1,
    "studentName": "김학생",
    "activityName": "도서관 봉사",
    "activityDate": "2026-10-15",
    "hours": 4.0,
    "status": "대기",
    "approvedAt": null
  }
]
```

- 403 `FORBIDDEN`: 학생 계정

#### `POST /api/admin/students/{studentId}/volunteers/approve-all` (관리자)

학생 한 명의 대기 중인 봉사 내역을 한꺼번에 승인합니다 (`ApproveAllPendingVolunteer`). 요청 본문은 없습니다.

```json
{ "approvedCount": 3, "totalApprovedHours": 15.0 }
```

- 대기 내역이 없으면 `approvedCount`가 `0`인 정상 응답(200)입니다.
- 403 `FORBIDDEN`: 학생 계정
- 404 `NOT_FOUND`: 학생이 없음 (프로시저가 `SQLSTATE 45000`으로 알림)

#### `POST /api/admin/volunteers/{recordId}/approve` (관리자)

봉사 내역 1건을 승인합니다 (`ApproveVolunteer`). 요청 본문은 없습니다.

```json
{ "recordId": 7, "status": "승인" }
```

- 403 `FORBIDDEN`: 학생 계정
- 404 `NOT_FOUND`: 내역이 없음
- 409 `INVALID_STATE`: 이미 승인·반려된 내역 (프로시저의 `p_updated`가 0)
- `반려` 처리 기능은 만들지 않습니다.
- 누가 승인했는지는 DB에 기록하지 않습니다 (`VolunteerRecord`에 승인자 컬럼이 없음).

### FR-06 졸업요건 진단

#### `GET /api/me/graduation-check`

학생에게 적용되는 졸업 규칙별 현재 값과 기준 값, 충족 여부를 반환합니다 (`GraduationCheck`).

```json
{
  "studentId": 1,
  "rules": [
    {
      "ruleId": 5,
      "ruleName": "총 이수학점 (항공학부)",
      "ruleType": "TOTAL_CREDITS",
      "currentValue": 45.0,
      "requiredValue": 130.0,
      "isMet": false,
      "isVerified": true
    }
  ],
  "allMet": false
}
```

| 필드 | 설명 |
| --- | --- |
| `ruleType` | `TOTAL_CREDITS`, `MAJOR_CREDITS`, `GENERAL_CREDITS`, `VOLUNTEER_HOURS`, `REQUIRED_COURSE` |
| `currentValue` / `requiredValue` | 학점·봉사시간 (소수 1자리 숫자, 예: `8.0`). `REQUIRED_COURSE`는 이수 시 `1.0`/미이수 시 `0.0`, 기준값은 `1.0` |
| `isMet` | DB가 계산한 충족 여부 |
| `isVerified` | `false`면 공식 확인 전 임시·테스트 규칙입니다. 화면에서 구분해 표시해야 합니다. |
| `allMet` | 모든 `rules`가 충족이면 `true`, 하나라도 미충족이면 `false`. `isMet`을 모은 값이며 새 판정 기준이 아닙니다. 규칙이 없으면 판정할 수 없으므로 `null`입니다. |

- 규칙은 `ruleId` 순서이며, 공식 규칙과 테스트 규칙(`isVerified: false`)이 함께 나옵니다.
- 학생에게 적용되는 규칙이 없으면 `rules`는 빈 배열이고 `allMet`은 `null`입니다. 화면은 "졸업 불가"가 아니라 "판정할 규칙 없음"으로 표시해야 합니다.

### FR-07 대시보드

#### `GET /api/me/dashboard`

대시보드 한 화면에 필요한 값을 한 번에 반환합니다. 아래 API의 응답을 그대로 묶은 것이며 별도 계산은 없습니다.

```json
{
  "student": { },
  "timetable": { },
  "credits": { },
  "graduationCheck": { }
}
```

| 필드 | 내용 |
| --- | --- |
| `student` | `GET /api/me` 응답 |
| `timetable` | `GET .../timetable` 응답 (학기 생략 시와 같은 기준) |
| `credits` | `GET .../credits` 응답 |
| `graduationCheck` | `GET .../graduation-check` 응답 |

- 재학 중인 학기가 없으면 `timetable`은 `null`이고 나머지는 정상 반환합니다. 시간표 전용 API는 같은 경우에 404이므로 화면에서 두 경우를 따로 처리합니다.

## 4. 구현 시 확인할 점

- `02_triggers.sql`, `03_procedures.sql`로 만든 객체를 JDBC로 **호출**하는 것은 가능합니다(`CALL ApproveAllPendingVolunteer(?, ?, ?)`). 제한은 객체를 **만드는** SQL 파일의 `DELIMITER`에만 있습니다.
- `ApproveAllPendingVolunteer`는 내부에서 트랜잭션을 시작하고 커밋하므로, 자동 커밋 상태인 새 연결로 호출합니다(구현됨). 호출 전에 다른 트랜잭션을 열어 두지 않습니다.
- `GET .../timetable`처럼 여러 테이블을 묶는 응답은 서버에서 JOIN 결과를 그룹핑해 만듭니다. 학점·충족 여부는 DB 값을 쓰고 서버에서 다시 계산하지 않습니다.
- DB 오류를 HTTP 오류로 바꾸는 규칙(SQLSTATE `45000`, 중복 키, 외래 키, `CHECK` 위반)은 한 곳에서 처리합니다. 다만 `45000`은 시간표 충돌(409)과 존재하지 않는 학생(404)에 모두 쓰이므로 SQLSTATE만으로 구분할 수 없습니다. SQLSTATE만으로는 구분할 수 없어 엔드포인트별 DAO가 직접 변환합니다. 일괄 승인은 `45000`을 404로 바꾸며 구현됐고, 시간표 등록의 `45000`(409)은 해당 API 구현 때 처리합니다.

- 로그인 응답과 쿠키를 프론트엔드가 쓰려면 CORS 응답에 `Access-Control-Allow-Credentials: true`가 있어야 하고 `Access-Control-Allow-Origin`은 `*`가 아닌 특정 출처여야 합니다. 백엔드의 `CorsFilter`가 `http://localhost:3000`에 대해 이를 설정합니다. 로그인·권한 검사를 하는 `AuthFilter`는 CORS 필터 다음에 실행되어, 401·403 응답에도 CORS 헤더가 붙습니다. 두 필터의 순서는 `web.xml`에서 정합니다.
- 비밀번호 비교는 `PBKDF2WithHmacSHA256`(JDK 기본 제공)으로 하고, 해시 문자열의 반복 횟수와 salt를 읽어 같은 방식으로 계산한 값과 비교합니다. 시드의 테스트 해시가 JDK 17에서 같은 값으로 검증되는 것을 확인했습니다.

## 5. 확정된 결정과 미확정 사항

### 확정

| 항목 | 결정 |
| --- | --- |
| 로그인 유지 | 서버 세션과 쿠키 |
| 계정 저장 | 별도 `Account` 테이블 (`STUDENT`/`ADMIN`) |
| 봉사 승인 | `ADMIN` 계정만 |

### 미확정

| 항목 | 내용 |
| --- | --- |
| 로그인 보안 보강 | 로그인 시도 횟수 제한, CSRF 토큰, 세션 만료 시간, 비밀번호 변경은 정해지지 않았습니다. CSRF는 `SameSite=Lax` 쿠키, 상태 변경 요청의 `Origin` 검사, `application/json` 본문 강제로 방어하며 토큰 방식은 쓰지 않습니다. 배포 시 허용 출처를 바꿔야 합니다. 상용 수준의 인증은 범위 밖입니다. |
| 계정 발급 | 회원가입·계정 생성 화면은 없고 시드 데이터로 만듭니다. 실제 학생 계정을 어떻게 만들지는 정해지지 않았습니다. |
| 승인자 기록 | 누가 승인했는지 `VolunteerRecord`에 저장하지 않습니다. 필요하면 승인자 컬럼과 프로시저 수정이 필요합니다. |
| 배포 | 배포 방식과 주소가 정해지지 않았습니다. 배포 시 쿠키의 `Secure` 설정과 CORS 허용 출처를 다시 정해야 합니다. |
| 학기 상태값 | `예정`/`재학`/`이수`는 임시 가정입니다. "현재 학기" 선택 기준이 바뀌면 시간표·대시보드 기본값도 바뀝니다. |
| 교양 세부 기준 | 교양필수·교양선택 세부 학점, 필수과목 규칙은 DB에 없어 `graduation-check`에 나오지 않습니다. |

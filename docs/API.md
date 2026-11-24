# API 명세 (초안)

CampusPlan 백엔드(Java Servlet REST API)의 엔드포인트 초안입니다. [요구사항 정의](REQUIREMENTS.md)의 기능 번호(FR-xx)를 기준으로 하고, 필드는 `database/`의 스키마·View·Procedure와 맞췄습니다. 구현 전 설계 문서이며 구현된 API가 아닙니다.

## 1. 공통 규칙

### 가정 (미확정 사항)

- **인증**: 정해지지 않았습니다. 이 초안은 인증 없이 URL의 `studentId`로 학생을 식별한다고 가정합니다. 이 방식은 인증·권한이 없으므로 시연 환경 전용이며 안전하다고 볼 수 없습니다. 인증 방식이 정해지면 경로와 권한 검사를 함께 고칩니다.
- **소유권 검사 없음**: `PATCH`·`DELETE /api/enrollments/{id}`, `DELETE /api/schedules/{id}`, `POST /api/volunteers/{id}/approve`는 경로에 학생이 없어 다른 학생의 데이터도 바꿀 수 있습니다. 인증이 정해지기 전에는 검사하지 않습니다.
- **없는 학생**: `studentId`가 들어가는 모든 API는 해당 학생이 없으면 404 `NOT_FOUND`를 반환합니다.
- **봉사 승인 주체**: 정해지지 않았습니다. 이 초안은 호출자를 구분하지 않고 누구나 승인 API를 호출할 수 있다고 가정합니다.

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
| 400 | `INVALID_REQUEST` | 필수값 누락, 형식 오류, 허용되지 않는 값(성적·요일·학기 등), DB `CHECK` 위반 |
| 404 | `NOT_FOUND` | 학생·학기·과목·수강·봉사 내역이 없음 |
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
| `hours` | 0보다 큰 수 |
| `activityName` | 비어 있지 않은 문자열, 100자 이하 |

## 2. 엔드포인트 요약

| 기능 | 메서드 | 경로 | 호출 DB 객체 |
| --- | --- | --- | --- |
| FR-01 | GET | `/api/students` | Student, Department |
| FR-01 | GET | `/api/students/{studentId}` | Student, Department |
| FR-02 | GET | `/api/courses` | Course |
| FR-02 | GET | `/api/students/{studentId}/semesters` | Semester |
| FR-02 | POST | `/api/students/{studentId}/semesters` | Semester (선택 기능) |
| FR-03 | GET | `/api/students/{studentId}/timetable` | Schedule, Enrollment, Course |
| FR-03 | POST | `/api/students/{studentId}/enrollments` | Enrollment, `trg_enrollment_insert` |
| FR-03 | POST | `/api/enrollments/{enrollmentId}/schedules` | Schedule, `trg_schedule_conflict` |
| FR-03 | DELETE | `/api/schedules/{scheduleId}` | Schedule |
| FR-04 | GET | `/api/students/{studentId}/enrollments` | Enrollment, Course |
| FR-04 | PATCH | `/api/enrollments/{enrollmentId}` | `trg_enrollment_update` |
| FR-03 | DELETE | `/api/enrollments/{enrollmentId}` | Enrollment, Schedule |
| FR-04 | GET | `/api/students/{studentId}/credits` | `CreditSummary` |
| FR-05 | GET | `/api/students/{studentId}/volunteers` | VolunteerRecord, `VolunteerSummary` |
| FR-05 | POST | `/api/students/{studentId}/volunteers` | VolunteerRecord |
| FR-05 | POST | `/api/students/{studentId}/volunteers/approve-all` | `ApproveAllPendingVolunteer` |
| FR-05 | POST | `/api/volunteers/{recordId}/approve` | `ApproveVolunteer` |
| FR-06 | GET | `/api/students/{studentId}/graduation-check` | `GraduationCheck` |
| FR-07 | GET | `/api/students/{studentId}/dashboard` | 위 조회를 묶어서 반환 |

## 3. 엔드포인트 상세

### FR-01 학생

#### `GET /api/students`

학생 목록을 반환합니다. 인증이 없는 시연 환경에서 화면이 대상 학생을 고르는 데 씁니다. 학생 등록·수정 API는 만들지 않습니다.

```json
[
  {
    "studentId": 1,
    "name": "홍길동",
    "admissionYear": 2023,
    "college": "항공학부",
    "departmentName": "항공소프트웨어공학과"
  }
]
```

#### `GET /api/students/{studentId}`

학생 한 명의 기본 정보와 소속을 반환합니다. 응답 형식은 위 목록의 항목과 같습니다.

```json
{
  "studentId": 1,
  "name": "홍길동",
  "admissionYear": 2023,
  "college": "항공학부",
  "departmentName": "항공소프트웨어공학과"
}
```

- 404 `NOT_FOUND`: 학생이 없음

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

#### `GET /api/students/{studentId}/semesters`

학생의 학기 목록을 최신순으로 반환합니다.

```json
[
  { "semesterId": 3, "year": 2026, "term": 2, "status": "재학" }
]
```

#### `POST /api/students/{studentId}/semesters` (선택)

학기 생성 화면을 만들 때만 구현합니다.

```json
{ "year": 2026, "term": 2, "status": "재학" }
```

- 응답: `201` + 생성된 학기 (위 목록 항목과 같은 형식)
- 409 `DUPLICATE`: 같은 학생의 같은 연도·학기가 이미 있음

### FR-03 시간표

#### `GET /api/students/{studentId}/timetable?semesterId={semesterId}`

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

#### `POST /api/students/{studentId}/enrollments`

학기에 과목을 수강 등록합니다. 성적은 선택이며, `completed`는 트리거가 정합니다.

```json
{ "semesterId": 3, "courseId": 12, "grade": null }
```

- 응답: `201` + 수강 항목 (`GET .../enrollments`의 항목 형식)
- 400 `INVALID_REQUEST`: 학기가 없거나 해당 학생의 것이 아님 (복합 외래 키 위반은 두 경우를 구분할 수 없어 하나로 처리)
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

- 404 `NOT_FOUND`: 수강 이력이 없음
- 성적이 입력된 수강도 삭제할 수 있으므로 화면에서 확인을 거칩니다.

### FR-04 학점·성적

#### `GET /api/students/{studentId}/enrollments?semesterId={semesterId}`

수강 이력을 반환합니다. `semesterId`를 생략하면 전체 학기입니다.

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
- 404 `NOT_FOUND`: 수강 이력이 없음

#### `GET /api/students/{studentId}/credits`

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

#### `GET /api/students/{studentId}/volunteers?status={status}`

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

#### `POST /api/students/{studentId}/volunteers`

봉사 내역을 등록합니다. 상태는 항상 `대기`로 만들어지며 요청으로 정할 수 없습니다.

```json
{ "activityName": "교내 환경 정화", "activityDate": "2026-10-03", "hours": 4.0 }
```

- 응답: `201` + 등록된 내역
- 400 `INVALID_REQUEST`: 시간이 0 이하, 이름·날짜 누락

#### `POST /api/students/{studentId}/volunteers/approve-all`

대기 중인 봉사 내역을 학생 단위로 한꺼번에 승인합니다 (`ApproveAllPendingVolunteer`). 요청 본문은 없습니다.

```json
{ "approvedCount": 3, "totalApprovedHours": 15.0 }
```

- 대기 내역이 없으면 `approvedCount`가 `0`인 정상 응답(200)입니다.
- 404 `NOT_FOUND`: 학생이 없음 (프로시저가 `SQLSTATE 45000`으로 알림)

#### `POST /api/volunteers/{recordId}/approve`

봉사 내역 1건을 승인합니다 (`ApproveVolunteer`). 요청 본문은 없습니다.

```json
{ "recordId": 7, "status": "승인" }
```

- 404 `NOT_FOUND`: 내역이 없음
- 409 `INVALID_STATE`: 이미 승인·반려된 내역 (프로시저의 `p_updated`가 0)
- `반려` 처리 기능은 만들지 않습니다.

### FR-06 졸업요건 진단

#### `GET /api/students/{studentId}/graduation-check`

학생에게 적용되는 졸업 규칙별 현재 값과 기준 값, 충족 여부를 반환합니다 (`GraduationCheck`).

```json
{
  "studentId": 1,
  "rules": [
    {
      "ruleId": 5,
      "ruleName": "총 이수학점 (항공학부)",
      "ruleType": "TOTAL_CREDITS",
      "currentValue": 45,
      "requiredValue": 130,
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
| `currentValue` / `requiredValue` | 학점·봉사시간. `REQUIRED_COURSE`는 이수 시 `1`/미이수 시 `0`, 기준값은 `1` |
| `isMet` | DB가 계산한 충족 여부 |
| `isVerified` | `false`면 공식 확인 전 임시·테스트 규칙입니다. 화면에서 구분해 표시해야 합니다. |
| `allMet` | 모든 `rules`가 충족이면 `true`, 하나라도 미충족이면 `false`. `isMet`을 모은 값이며 새 판정 기준이 아닙니다. 규칙이 없으면 판정할 수 없으므로 `null`입니다. |

- 학생에게 적용되는 규칙이 없으면 `rules`는 빈 배열이고 `allMet`은 `null`입니다. 화면은 "졸업 불가"가 아니라 "판정할 규칙 없음"으로 표시해야 합니다.

### FR-07 대시보드

#### `GET /api/students/{studentId}/dashboard`

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
| `student` | `GET /api/students/{studentId}` 응답 |
| `timetable` | `GET .../timetable` 응답 (학기 생략 시와 같은 기준) |
| `credits` | `GET .../credits` 응답 |
| `graduationCheck` | `GET .../graduation-check` 응답 |

- 재학 중인 학기가 없으면 `timetable`은 `null`이고 나머지는 정상 반환합니다. 시간표 전용 API는 같은 경우에 404이므로 화면에서 두 경우를 따로 처리합니다.

## 4. 구현 시 확인할 점

- `02_triggers.sql`, `03_procedures.sql`로 만든 객체를 JDBC로 **호출**하는 것은 가능합니다(`CALL ApproveAllPendingVolunteer(?, ?, ?)`). 제한은 객체를 **만드는** SQL 파일의 `DELIMITER`에만 있습니다.
- `ApproveAllPendingVolunteer`는 내부에서 트랜잭션을 시작하고 커밋하므로, 호출 전에 JDBC 연결의 자동 커밋 설정과 이미 열린 트랜잭션이 없는지 확인해야 합니다.
- `GET .../timetable`처럼 여러 테이블을 묶는 응답은 서버에서 JOIN 결과를 그룹핑해 만듭니다. 학점·충족 여부는 DB 값을 쓰고 서버에서 다시 계산하지 않습니다.
- DB 오류를 HTTP 오류로 바꾸는 규칙(SQLSTATE `45000`, 중복 키, 외래 키, `CHECK` 위반)은 한 곳에서 처리합니다. 다만 `45000`은 시간표 충돌(409)과 존재하지 않는 학생(404)에 모두 쓰이므로 SQLSTATE만으로 구분할 수 없습니다. 엔드포인트별로 처리하거나 메시지 문구로 구분해야 하며, 구현 전에 방식을 정해야 합니다.

## 5. 미확정 사항

| 항목 | 내용 |
| --- | --- |
| 인증·권한 | 정해지지 않아 `studentId`를 URL로 받는 가정을 썼습니다. 정해지면 경로와 권한 검사를 고칩니다. |
| 봉사 승인 주체 | 학생 본인 또는 관리자 여부가 정해지지 않았습니다. 정해지면 승인 API의 접근 제어를 추가합니다. |
| CORS·배포 | 프론트엔드(Next.js)와 Tomcat의 주소 관계가 정해지지 않았습니다. |
| 학기 상태값 | `예정`/`재학`/`이수`는 임시 가정입니다. "현재 학기" 선택 기준이 바뀌면 시간표·대시보드 기본값도 바뀝니다. |
| 교양 세부 기준 | 교양필수·교양선택 세부 학점, 필수과목 규칙은 DB에 없어 `graduation-check`에 나오지 않습니다. |

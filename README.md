# CampusPlan

대학생 통합 학업 관리 시스템을 만드는 데이터베이스 캡스톤디자인 프로젝트입니다.

## 프로젝트 구조

```text
CampusPlan/
├── frontend/       # Next.js 화면
├── backend/        # Java Servlet REST API
├── database/       # MySQL 스키마, SQL 객체, 샘플 데이터
└── docs/           # 요구사항, ERD, API 및 회의 기록
```

프로젝트 계획서 원본은 로컬에 보관하며 저장소에는 올리지 않았습니다. 필요 시 추후에 올릴 예정입니다.

## 기술 스택

- Frontend: Node.js 24.14.0, Next.js 16.4.6, React / React DOM 19.3.0, Tailwind CSS 4.3.3, TypeScript 5.9.3, ESLint 9.39.5
- Backend: JDK 17, Maven 3.10.1, Jakarta Servlet API 6.0.0, Apache Tomcat 10.1.x, MySQL Connector/J 9.7.0, Gson 2.15.0
- Database: MySQL Server 8.x, MySQL Workbench 8.x (관리 도구)

프론트엔드의 직접·전이 의존성 버전은 `frontend/package-lock.json`, 백엔드 의존성 버전은 `backend/pom.xml`에서 고정합니다. 현재 확인된 로컬 런타임은 Node.js 24.14.0과 JDK 27이며, Maven 3.10.1은 이번 의존성 다운로드에 임시 경로에서 사용했습니다. JDK 17, Tomcat, MySQL Server, Workbench는 별도 설치 프로그램이며 이번 작업에서 설치하지 않았습니다.

팀원이 각자 백엔드를 로컬에서 개발·실행할 경우 **JDK 17과 Apache Tomcat 10.1.x를 각자 설치**합니다. Tomcat을 공용 개발 서버에서만 실행하는 경우에는 해당 서버에 설치하면 됩니다. 팀원 모두 같은 JDK 주 버전과 Tomcat 마이너 버전을 사용합니다.

## 개발 시작

프론트엔드 Next.js 초기 설정과 백엔드 Maven 의존성 선언을 완료했습니다. 각 영역의 설치 및 실행 방법은 해당 폴더의 README를 참고해주세요. Tomcat과 MySQL Server는 별도로 설치하고 실행해야 합니다.

## 졸업요건 자료

공통 졸업요건과 항공소프트웨어공학과 자격 규정의 사용자 제공 내용은 [졸업요건 정리](data_sources/graduation_requirements.md)에 기록합니다. 공식 원문, 최신 개정 여부, 학생별 적용 범위가 확인되기 전까지 해당 내용은 조사 자료이며 확정된 시스템 기준값으로 사용하지 않습니다.

## 협업 방식

작업은 이슈 단위로 나누고 기능 브랜치에서 작업합니다. 기능 브랜치는 Pull Request로 `develop`에 통합하고, 안정된 변경은 검토 후 `main`에 반영합니다.

### 협업 규칙

- [브랜치 컨벤션](docs/BRANCH_CONVENTION.md)
- [커밋 메시지 컨벤션](docs/GIT_CONVENTION.md)
- [이슈 · PR 컨벤션](docs/ISSUE_PR_CONVENTION.md)

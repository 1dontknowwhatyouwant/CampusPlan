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

- Frontend: Next.js, React, Tailwind CSS
- Backend: Java Servlet, Apache Tomcat
- Database: MySQL

## 개발 시작

각 영역의 구동 방법과 준비 상태는 해당 폴더의 README를 참고해주세요. 현재는 프로젝트 구조를 먼저 마련한 상태이며, 실행 가능한 앱 코드는 각 영역의 초기 설정 단계에서 추가합니다.

## 협업 방식

작업은 이슈 단위로 나누고 기능 브랜치에서 작업합니다. 기능 브랜치는 Pull Request로 `develop`에 통합하고, 안정된 변경은 검토 후 `main`에 반영합니다.

### 협업 규칙

- [브랜치 컨벤션](docs/BRANCH_CONVENTION.md)
- [커밋 메시지 컨벤션](docs/GIT_CONVENTION.md)
- [이슈 · PR 컨벤션](docs/ISSUE_PR_CONVENTION.md)

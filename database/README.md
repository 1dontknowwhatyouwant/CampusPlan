# Database

MySQL 데이터베이스 설계와 초기화 파일을 관리합니다.

권장 구성:

```text
database/
├── schema/       # 테이블과 제약 조건
├── routines/     # Trigger, Stored Procedure, View
├── seeds/        # 테스트용 초기 데이터
└── README.md
```

실제 학사 자료와 테스트 데이터는 파일과 문서에서 구분합니다. 비밀번호, 접속 문자열 등 비밀 정보는 커밋하지 않습니다.

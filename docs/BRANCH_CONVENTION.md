# 브랜치 컨벤션

이 문서는 CampusPlan의 이슈별 작업 브랜치 생성, Pull Request, 통합 및 배포 브랜치 흐름을 정합니다.

## 브랜치 구성

| 브랜치 | 용도 | 작업 방식 |
| --- | --- | --- |
| `main` | 제출·시연 가능한 안정된 코드 | 기능 브랜치에서 직접 병합하지 않고, 검토된 `develop`을 PR로 반영 |
| `develop` | 완료된 기능을 통합하는 개발 브랜치 | 기능 브랜치에서 PR을 받아 통합 |
| `feat/#이슈번호` | 이슈 하나를 개발하는 작업 브랜치 | 최신 `develop`에서 생성하고 작업 후 `develop`으로 PR |

예를 들어 이슈 번호가 1이면 브랜치 이름은 `feat/#1`입니다. 브랜치 이름은 작은따옴표로 감싸 명령에서 `#`가 안전하게 처리되도록 합니다.

## 최초 설정

현재 저장소의 기본 브랜치는 `main`입니다. 팀에서 처음 이 흐름을 적용할 때 `develop` 브랜치를 `main`에서 한 번 생성해 원격에 올립니다.

```bash
git switch main
git pull --ff-only origin main
git switch -c develop
git push -u origin develop
```

이후 팀원은 원격 `develop`을 기준으로 작업합니다.

## 새 작업 시작

먼저 이슈를 만들고 번호를 확인합니다. 그 다음 최신 `develop`에서 작업 브랜치를 만듭니다.

```bash
git fetch origin
git switch develop
git pull --ff-only origin develop
git switch -c 'feat/#1'
git branch --show-current
```

`git branch --show-current` 결과가 이번 작업 브랜치인지 확인한 뒤 코드를 수정합니다. 팀원이 처음 저장소를 받을 때 로컬 `develop`이 아직 없다면 다음 명령으로 원격 브랜치를 추적하도록 설정합니다.

```bash
git fetch origin
git switch --track origin/develop
```

## 작업 중 및 PR 생성

작업은 이슈의 범위에 맞춰 진행하고, 한 커밋에는 한 가지 변경 목적만 담습니다. 커밋 메시지는 [커밋 컨벤션](GIT_CONVENTION.md)을 따릅니다.

```bash
git add <변경한 파일>
git commit -m "feat: 시간표 조회 화면 추가"
git push -u origin 'feat/#1'
```

GitHub에서 다음 방향으로 Pull Request를 생성합니다.

```text
feat/#1 → develop
```

PR에는 관련 이슈를 연결하고, 변경 내용과 확인 방법을 적습니다. 팀원의 검토와 필요한 수정이 끝나면 PR을 병합합니다. 병합 방법은 저장소에서 정한 설정을 따릅니다. 병합 후 작업 브랜치는 삭제해도 됩니다.

## 안정 버전 반영

`develop`의 통합 내용이 제출 또는 시연 가능한 상태가 되면 다음 방향으로 PR을 생성합니다.

```text
develop → main
```

검토 후 병합된 `main`만 안정된 코드로 유지합니다. `main`과 `develop`에 직접 작업 내용을 커밋하거나 푸시하지 않습니다.

## 작업 전 확인 목록

- [ ] 작업할 이슈를 만들고 번호를 확인했나요?
- [ ] `origin`에서 최신 변경을 가져왔나요?
- [ ] `develop`에서 새 작업 브랜치를 만들었나요?
- [ ] 현재 브랜치 이름이 `feat/#이슈번호`와 일치하나요?
- [ ] PR 대상이 `develop`인가요?

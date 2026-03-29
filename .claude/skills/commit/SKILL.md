---
name: commit
description: 현재 변경사항을 Conventional Commits 형식으로 커밋한다. 이슈 번호를 받아 Closes #N을 본문에 자동 포함.
argument-hint: <이슈번호>
---

현재 변경사항을 이슈 번호($ARGUMENTS)와 연결하여 Conventional Commits 형식으로 커밋하라.

## Step 1 — 변경사항 파악

```bash
git status
git diff
git diff --staged
```

스테이징 여부와 무관하게 전체 변경사항을 파악하라.

## Step 2 — 커밋 타입 결정

변경 내용에 맞는 타입을 선택하라:

| 타입 | 사용 상황 |
|------|----------|
| `feat` | 새로운 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 기능 변경 없는 코드 개선 |
| `test` | 테스트 코드 추가/수정 |
| `chore` | 빌드 설정, 의존성, 기타 |
| `docs` | 문서 수정 |

## Step 3 — 파일 스테이징

변경된 파일을 확인하고 커밋에 포함할 파일을 스테이징하라.
민감 정보가 포함된 파일(`application*.yaml`, `.env` 등)은 절대 스테이징하지 않는다.

```bash
git add [파일 경로들]
```

## Step 4 — 커밋 메시지 작성 및 커밋

형식: `[타입]: [변경사항 요약]`

커밋 메시지 규칙:
- 제목은 50자 이내, 명령형으로 작성
- 본문에 `Closes #$ARGUMENTS` 반드시 포함

```bash
git commit -m "$(cat <<'EOF'
[타입]: [변경사항 요약]

Closes #$ARGUMENTS
EOF
)"
```

커밋 후 `git log --oneline -3` 으로 결과를 확인하라.

---
name: pr
description: 이슈 번호로 main 브랜치 대상 PR을 생성한다. 이슈 내용과 커밋 목록을 읽어 PR 본문을 자동 작성.
argument-hint: <이슈번호>
---

이슈 번호($ARGUMENTS)에 대한 PR을 main 브랜치로 생성하라.

**repo:** yuhaeni/carrot-settle
**주의:** main에 직접 push 금지. 반드시 PR을 통해 머지한다.

## Step 1 — 컨텍스트 수집

```bash
gh issue view $ARGUMENTS --repo yuhaeni/carrot-settle
git log main..HEAD --oneline
git diff main..HEAD --stat
```

이슈 내용, 커밋 목록, 변경된 파일 목록을 파악하라.

## Step 2 — 현재 브랜치 확인

```bash
git branch --show-current
```

브랜치가 `main`이면 PR 생성을 중단하고 올바른 feature 브랜치로 전환하도록 안내하라.

## Step 3 — 원격 브랜치 push

```bash
git push -u origin $(git branch --show-current)
```

## Step 4 — PR 생성

아래 템플릿을 기반으로 PR 본문을 작성하고 생성하라:

```bash
gh pr create --base main --repo yuhaeni/carrot-settle \
  --title "[타입]: [변경사항 요약] (#$ARGUMENTS)" \
  --body "$(cat <<'EOF'
## 📝 변경사항

<!-- 이 PR에서 변경된 내용을 간단히 설명 -->

### 주요 변경사항
-

### 상세 설명
<!-- 구현 방식, 기술적 결정 등 -->

## 🔍 변경된 파일

<!-- git diff --stat 결과 기반으로 작성 -->

## 🧪 테스트

- [ ] `./gradlew test` 전체 테스트 통과
- [ ] 관련 기능 수동 동작 확인

### 테스트 방법
\`\`\`bash
./gradlew test
\`\`\`

## ✅ 체크리스트

- [ ] Conventional Commits 형식 준수
- [ ] 민감 정보 미포함 확인
- [ ] 관련 이슈 체크리스트 업데이트 완료

## 📚 관련 이슈

Closes #$ARGUMENTS
EOF
)"
```

PR 생성 후 URL을 출력하라.

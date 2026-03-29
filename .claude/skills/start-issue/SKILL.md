---
name: start-issue
description: GitHub 이슈 번호로 feature/fix/chore 브랜치를 생성한다. 이슈 제목을 읽어 브랜치명을 kebab-case로 제안하고 생성.
argument-hint: <이슈번호>
---

주어진 GitHub 이슈 번호($ARGUMENTS)로 작업 브랜치를 생성하라.

**repo:** yuhaeni/carrot-settle

## Step 1 — 이슈 내용 확인

```bash
gh issue view $ARGUMENTS --repo yuhaeni/carrot-settle
```

이슈 제목과 내용을 읽어라.

## Step 2 — 브랜치명 결정

이슈 제목을 기반으로 브랜치명을 결정하라:

- 일반 기능: `feature/$ARGUMENTS-[간단-설명-kebab-case]`
- 버그 수정: `fix/$ARGUMENTS-[간단-설명-kebab-case]`
- 설정/도구: `chore/$ARGUMENTS-[간단-설명-kebab-case]`

브랜치명 규칙:
- 소문자 영어와 숫자, 하이픈만 사용
- 설명 부분은 3단어 이내로 간결하게
- 예: `feature/2-domain-entity-setup`

결정한 브랜치명을 사용자에게 보여주고 확인받아라.

## Step 3 — 브랜치 생성

반드시 `main` 브랜치에서 checkout하여 브랜치를 생성하라:

```bash
git checkout main && git pull origin main && git checkout -b [브랜치명]
```

생성 후 현재 브랜치를 확인하라:

```bash
git branch --show-current
```

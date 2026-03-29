---
name: issue-done
description: GitHub 이슈의 체크리스트를 검증하고 업데이트한다. 모든 항목 완료 시 이슈를 닫고 완료 근거를 코멘트로 남김.
argument-hint: <이슈번호>
---

주어진 GitHub 이슈 번호($ARGUMENTS)에 대해 아래 프로세스를 순서대로 실행하라.

**repo:** yuhaeni/carrot-settle

## Step 1 — 이슈 현황 파악

```bash
gh issue view $ARGUMENTS --repo yuhaeni/carrot-settle
```

이슈 본문의 Acceptance Criteria(AC)와 Tasks 체크리스트를 읽고 현재 체크 상태를 파악하라.

## Step 2 — 완료 여부 검증

각 항목을 코드/파일 실제 존재 여부 기준으로 판단하라:

- **AC (Given-When-Then)**: 관련 테스트 또는 실제 코드 동작으로 확인
- **Tasks**: 코드/파일이 실제로 존재하거나 동작이 확인된 항목만 체크
- 확인이 어렵거나 불확실한 항목은 체크하지 않는다

## Step 3 — 체크리스트 업데이트

변경이 필요한 항목이 있으면 이슈 본문 전체를 업데이트하라:

```bash
gh issue edit $ARGUMENTS --repo yuhaeni/carrot-settle --body "[업데이트된 본문 전체]"
```

## Step 4 — 완료 시 이슈 닫기

모든 AC + Tasks가 체크된 경우에만 이슈를 닫아라:

```bash
gh issue close $ARGUMENTS --repo yuhaeni/carrot-settle --comment "완료 근거: [체크된 항목 요약]"
```

미완료 항목이 남아있으면 이슈를 닫지 않고, 미완료 항목 목록을 보고하라.

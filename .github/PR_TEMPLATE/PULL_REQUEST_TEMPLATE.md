## 📝 변경사항

<!-- 이 PR에서 변경된 내용을 간단히 설명해주세요 -->

### 주요 변경사항
- [ ] 새로운 기능 추가
- [ ] 기존 기능 수정
- [ ] 버그 수정
- [ ] 리팩토링
- [ ] 테스트 추가/수정
- [ ] 설정/인프라 변경
- [ ] 기타

### 상세 설명
<!-- 구현 방식, 기술적 결정, 트레이드오프 등을 설명해주세요 -->

## 🔍 변경된 파일

<!-- 변경된 파일 목록을 작성해주세요 -->

## 🧪 테스트

- [ ] `./gradlew test` 전체 테스트 통과
- [ ] 관련 도메인 로직 단위 테스트 확인
- [ ] Testcontainers 통합 테스트 확인 (Docker 실행 필요)

### 테스트 방법
```bash
# 전체 테스트 실행
./gradlew test

# 특정 테스트 클래스 실행
./gradlew test --tests "com.haeni.carrot.settle.TargetTest"
```

## ✅ 체크리스트

- [ ] Conventional Commits 형식 준수 (`feat:`, `fix:`, `chore:` 등)
- [ ] 금액 계산에 `BigDecimal` + `RoundingMode.HALF_UP` 사용
- [ ] Entity 변경 시 DB 마이그레이션 파일 생성 완료
- [ ] 민감 정보 미포함 확인 (`password`, `api_key`, `secret` 등)
- [ ] 관련 GitHub 이슈 체크리스트 업데이트 완료

## 📚 관련 이슈

Closes #(이슈 번호)

## 💡 추가 정보

<!-- 리뷰어에게 전달할 추가 정보가 있다면 작성해주세요 -->

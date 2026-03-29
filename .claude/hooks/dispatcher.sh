#!/bin/bash

# ~/.claude/hooks/pre-tool-use.sh

echo "🚀 디스패처 실행됨!" >&2

# JSON stdin에서 데이터 읽기
INPUT=$(cat)

# jq로 파싱 (없으면 grep 사용)
if command -v jq &> /dev/null; then
    TOOL_NAME=$(echo "$INPUT" | jq -r '.tool_name // "Unknown"')
    FILE_PATH=$(echo "$INPUT" | jq -r '.tool_input.path // .tool_input.file_path // ""')
    CONTENT=$(echo "$INPUT" | jq -r '.tool_input.content // ""')
else
    TOOL_NAME=$(echo "$INPUT" | grep -o '"tool_name":"[^"]*' | sed 's/"tool_name":"//')
    FILE_PATH=$(echo "$INPUT" | grep -o '"path":"[^"]*' | sed 's/"path":"//' | head -1)
    CONTENT=""
fi

echo "🔧 Tool: $TOOL_NAME, 📁 File: $FILE_PATH" >&2

# ============================================
# 규칙 1: application 설정 파일 보안
# ============================================
# 모든 application*.yml, application*.properties 차단
if [[ "$FILE_PATH" =~ application.*\.ya?ml$ ]] || \
   [[ "$FILE_PATH" =~ application.*\.properties$ ]]; then
    if [[ "$TOOL_NAME" == "Read" || "$TOOL_NAME" == "Grep" ]]; then
        echo "❌ 보안 규칙 위반: application 설정 파일은 읽을 수 없습니다." >&2
        exit 2
    fi
fi

# ============================================
# 규칙 2: Flyway/Liquibase 마이그레이션 파일 수정 제어
# ============================================
if [[ "$FILE_PATH" == *"src/main/resources/db/migration/"* ]] || \
   [[ "$FILE_PATH" == *"src/main/resources/db/changelog/"* ]]; then
    if [[ "$TOOL_NAME" =~ ^(Edit|Write|MultiEdit)$ ]]; then
        echo "❌ 데이터 불변성 규칙 위반: 마이그레이션 파일은 수정할 수 없습니다." >&2
        echo "💡 새 마이그레이션 파일을 생성하세요 (예: V2__add_column.sql)" >&2
        exit 2
    fi
fi

# ============================================
# 규칙 3: Service 클래스 문서화 정책
# ============================================
if [[ "$FILE_PATH" =~ /service/.*\.kt$ ]] || \
   [[ "$FILE_PATH" =~ /application/.*Service\.kt$ ]]; then
    if [[ "$TOOL_NAME" == "Write" ]]; then
        if [[ ! "$CONTENT" == *"/**"* ]] || [[ ! "$CONTENT" == *"@author"* ]]; then
            echo "❌ 문서화 규칙 위반: Service 클래스에는 KDoc 문서화가 필요합니다." >&2
            echo "💡 클래스 상단에 /** @author 이름 */ 형식의 KDoc을 추가하세요." >&2
            exit 2
        fi
    fi
fi

# ============================================
# 규칙 4: Entity 클래스 수정 주의
# ============================================
if [[ "$FILE_PATH" =~ /(entity|domain)/.*\.kt$ ]]; then
    if [[ "$TOOL_NAME" =~ ^(Edit|Write|MultiEdit)$ ]]; then
        echo "⚠️  주의: Entity 수정 시 DB 마이그레이션 파일도 함께 생성해야 합니다." >&2
    fi
fi

# ============================================
# 규칙 5: 테스트 파일 네이밍 규칙
# ============================================
if [[ "$FILE_PATH" == *"src/test/kotlin/"* ]]; then
    if [[ "$TOOL_NAME" == "Write" ]]; then
        FILENAME=$(basename "$FILE_PATH")
        if [[ "$FILENAME" == *.kt ]] && \
           [[ ! "$FILENAME" == *Test.kt ]] && \
           [[ ! "$FILENAME" == *Tests.kt ]] && \
           [[ ! "$FILENAME" == *Spec.kt ]]; then
            echo "❌ 네이밍 규칙 위반: 테스트 파일은 Test.kt, Tests.kt, 또는 Spec.kt로 끝나야 합니다." >&2
            echo "💡 현재 파일명: $FILENAME" >&2
            exit 2
        fi
    fi
fi

# ============================================
# 규칙 6: build.gradle.kts 의존성 추가 시 버전 명시 권장
# ============================================
if [[ "$FILE_PATH" == *"build.gradle.kts" ]] || [[ "$FILE_PATH" == *"build.gradle" ]]; then
    if [[ "$TOOL_NAME" =~ ^(Edit|Write)$ ]]; then
        if echo "$CONTENT" | grep -qE 'implementation\s*\(\s*["\047][^:]+:[^:]+["\047]\s*\)'; then
            echo "⚠️  권장: 의존성 추가 시 버전을 명시하거나 버전 카탈로그를 사용하세요." >&2
        fi
    fi
fi

# ============================================
# 규칙 7: Controller에 @Valid 어노테이션 권장
# ============================================
if [[ "$FILE_PATH" =~ /(controller|api)/.*Controller\.kt$ ]]; then
    if [[ "$TOOL_NAME" == "Write" ]]; then
        if [[ "$CONTENT" == *"@RequestBody"* ]] && [[ ! "$CONTENT" == *"@Valid"* ]]; then
            echo "⚠️  권장: @RequestBody 파라미터에 @Valid 어노테이션 추가를 고려하세요." >&2
        fi
    fi
fi


# ============================================
# 멘토 규칙 1: Controller 파일 수정 시 멘토링
# ============================================
if [[ "$FILE_PATH" =~ /(controller|api)/.*Controller\.kt$ ]]; then
    if [[ "$TOOL_NAME" =~ ^(Edit|Write|MultiEdit)$ ]]; then
        FILENAME=$(basename "$FILE_PATH")
        ENTITY_NAME=$(echo "$FILENAME" | sed 's/Controller\.kt$//')

        echo "🎯 [컨트롤러 수정 감지] ${ENTITY_NAME} API 컨트롤러를 수정하려고 합니다." >&2
        echo "📚 아키텍트 멘토의 조언: 컨트롤러 수정 전에 다음을 확인하세요:" >&2
        echo "   - src/main/kotlin/.../domain/${ENTITY_NAME}.kt (엔티티)" >&2
        echo "   - src/main/kotlin/.../service/${ENTITY_NAME}Service.kt (비즈니스 로직)" >&2
        echo "   - src/main/kotlin/.../dto/${ENTITY_NAME}Request.kt, ${ENTITY_NAME}Response.kt (DTO)" >&2
        echo "   - 기존 API 패턴과의 일관성" >&2
        echo "💡 먼저 관련 파일들을 읽고 계획을 세워주세요!" >&2
        exit 2
    fi
fi

# ============================================
# 멘토 규칙 2: Entity/Domain 파일 수정 시 영향도 경고
# ============================================
if [[ "$FILE_PATH" =~ /(entity|domain)/.*\.kt$ ]]; then
    if [[ "$TOOL_NAME" =~ ^(Edit|Write|MultiEdit)$ ]]; then
        echo "🗃️ [엔티티 수정 경고] 데이터 모델 변경은 신중해야 합니다!" >&2
        echo "📋 체크리스트:" >&2
        echo "   □ 기존 데이터 호환성 확인" >&2
        echo "   □ API 응답(DTO) 변경 필요 여부" >&2
        echo "   □ Flyway/Liquibase 마이그레이션 파일 생성 필요" >&2
        echo "   □ Repository 쿼리 수정 필요 여부" >&2
        echo "💡 영향도를 분석한 후 계획을 세워주세요." >&2
        exit 2
    fi
fi

echo "✅ 모든 규칙 통과" >&2
exit 0
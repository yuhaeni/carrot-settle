#!/bin/bash

# JSON stdin에서 데이터 읽기
JSON_INPUT=$(cat)

echo "🔥 PostToolUse 실행!" >&2

# 파일 경로 추출
if command -v jq &> /dev/null; then
    FILE_PATH=$(echo "$JSON_INPUT" | jq -r '.tool_input.file_path // .tool_input.path // .file_path // .path // empty')
    TOOL_NAME=$(echo "$JSON_INPUT" | jq -r '.tool_name // "Unknown"')
else
    FILE_PATH=$(echo "$JSON_INPUT" | grep -o '"file_path":"[^"]*' | sed 's/"file_path":"//' | head -1)
fi

echo "🔧 Tool: $TOOL_NAME" >&2
echo "📁 File: $FILE_PATH" >&2

# Java/Kotlin/Spring 관련 파일 포맷팅
if [[ -n "$FILE_PATH" && -f "$FILE_PATH" ]]; then

    # Java/Kotlin 파일 → google-java-format 또는 ktlint
    if [[ "$FILE_PATH" =~ \.(java)$ ]]; then
        echo "📝 Java 포맷팅 시작: $FILE_PATH" >&2

        if command -v google-java-format &> /dev/null; then
            google-java-format --replace "$FILE_PATH" && echo "✅ $FILE_PATH 포맷팅 완료 (google-java-format)" >&2
        elif [ -f "./gradlew" ] && ./gradlew tasks --all 2>/dev/null | grep -q "spotlessApply"; then
            ./gradlew spotlessApply -PspotlessFiles="$FILE_PATH" 2>/dev/null && echo "✅ $FILE_PATH 포맷팅 완료 (Spotless)" >&2
        elif [ -f "./mvnw" ] && grep -q "spotless" pom.xml 2>/dev/null; then
            ./mvnw spotless:apply 2>/dev/null && echo "✅ 포맷팅 완료 (Maven Spotless)" >&2
        else
            echo "⚠️  Java 포맷터를 찾을 수 없음" >&2
            echo "💡 google-java-format 또는 Spotless 플러그인 설치 권장" >&2
        fi

    # Kotlin 파일 → ktlint 또는 ktfmt
    elif [[ "$FILE_PATH" =~ \.(kt|kts)$ ]]; then
        echo "📝 Kotlin 포맷팅 시작: $FILE_PATH" >&2

        if command -v ktlint &> /dev/null; then
            ktlint --format "$FILE_PATH" && echo "✅ $FILE_PATH 포맷팅 완료 (ktlint)" >&2
        elif command -v ktfmt &> /dev/null; then
            ktfmt --kotlinlang-style "$FILE_PATH" && echo "✅ $FILE_PATH 포맷팅 완료 (ktfmt)" >&2
        elif [ -f "./gradlew" ] && ./gradlew tasks --all 2>/dev/null | grep -q "ktlintFormat"; then
            ./gradlew ktlintFormat 2>/dev/null && echo "✅ 포맷팅 완료 (Gradle ktlint)" >&2
        else
            echo "⚠️  Kotlin 포맷터를 찾을 수 없음" >&2
            echo "💡 ktlint 또는 ktfmt 설치 권장" >&2
        fi

    # XML 파일 (pom.xml, application context 등)
    elif [[ "$FILE_PATH" =~ \.(xml)$ ]]; then
        echo "📝 XML 포맷팅 시작: $FILE_PATH" >&2

        if command -v xmllint &> /dev/null; then
            xmllint --format "$FILE_PATH" --output "$FILE_PATH" && echo "✅ $FILE_PATH 포맷팅 완료 (xmllint)" >&2
        else
            echo "⚠️  xmllint를 찾을 수 없음 (libxml2 설치 필요)" >&2
        fi

    # YAML/YML 파일 (application.yml 등)
    elif [[ "$FILE_PATH" =~ \.(ya?ml)$ ]]; then
        echo "📝 YAML 포맷팅 시작: $FILE_PATH" >&2

        if command -v yamlfmt &> /dev/null; then
            yamlfmt "$FILE_PATH" && echo "✅ $FILE_PATH 포맷팅 완료 (yamlfmt)" >&2
        elif command -v prettier &> /dev/null; then
            prettier --write "$FILE_PATH" && echo "✅ $FILE_PATH 포맷팅 완료 (prettier)" >&2
        else
            echo "⚠️  YAML 포맷터를 찾을 수 없음" >&2
        fi

    # Properties 파일
    elif [[ "$FILE_PATH" =~ \.(properties)$ ]]; then
        echo "ℹ️  Properties 파일은 포맷팅 건너뜀: $FILE_PATH" >&2

    # SQL 파일
    elif [[ "$FILE_PATH" =~ \.(sql)$ ]]; then
        echo "📝 SQL 포맷팅 시작: $FILE_PATH" >&2

        if command -v sql-formatter &> /dev/null; then
            sql-formatter "$FILE_PATH" -o "$FILE_PATH" && echo "✅ $FILE_PATH 포맷팅 완료 (sql-formatter)" >&2
        elif command -v sqlfluff &> /dev/null; then
            sqlfluff fix "$FILE_PATH" && echo "✅ $FILE_PATH 포맷팅 완료 (sqlfluff)" >&2
        else
            echo "⚠️  SQL 포맷터를 찾을 수 없음" >&2
        fi

    # JSON 파일
    elif [[ "$FILE_PATH" =~ \.(json)$ ]]; then
        echo "📝 JSON 포맷팅 시작: $FILE_PATH" >&2

        if command -v jq &> /dev/null; then
            jq '.' "$FILE_PATH" > "$FILE_PATH.tmp" && mv "$FILE_PATH.tmp" "$FILE_PATH" && echo "✅ $FILE_PATH 포맷팅 완료 (jq)" >&2
        elif command -v prettier &> /dev/null; then
            prettier --write "$FILE_PATH" && echo "✅ $FILE_PATH 포맷팅 완료 (prettier)" >&2
        else
            echo "⚠️  JSON 포맷터를 찾을 수 없음" >&2
        fi

    # Gradle 빌드 파일
    elif [[ "$FILE_PATH" =~ (build\.gradle|settings\.gradle)$ ]]; then
        echo "📝 Gradle 파일 포맷팅 시작: $FILE_PATH" >&2

        if [ -f "./gradlew" ] && ./gradlew tasks --all 2>/dev/null | grep -q "spotlessApply"; then
            ./gradlew spotlessApply 2>/dev/null && echo "✅ 포맷팅 완료 (Spotless)" >&2
        else
            echo "ℹ️  Gradle 파일 포맷팅은 Spotless 플러그인 필요" >&2
        fi

    else
        echo "ℹ️  포맷팅 대상 아님: $FILE_PATH" >&2
    fi
else
    echo "ℹ️  파일 없음 또는 경로 없음: $FILE_PATH" >&2
fi

# 로그 기록
echo "$(date): PostToolUse - Tool: $TOOL_NAME, File: $FILE_PATH" >> /tmp/claude-hooks.log

echo '{"continue": true}'
exit 0
# ビルド用ベースイメージとしてOpenJDK 17を使用
FROM openjdk:17-jdk-slim

WORKDIR /workspace

# Gradle Wrapperをコピーして実行権限を付与
COPY gradlew ./
COPY gradlew.bat ./
COPY gradle/ ./gradle/
RUN chmod +x gradlew

# デフォルトのエントリポイントをGradle Wrapperに設定
ENTRYPOINT ["./gradlew"]

# デフォルトのコマンドをbuildタスクに設定
CMD ["build", "--no-daemon"] 
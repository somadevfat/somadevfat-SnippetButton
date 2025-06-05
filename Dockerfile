# ビルド用ベースイメージとしてOpenJDK 17を使用
FROM openjdk:17-jdk-slim

# ネイティブパッケージ生成に必要なツールをインストール
RUN apt-get update && apt-get install -y --no-install-recommends \
    fakeroot \
    dpkg-dev \
    && rm -rf /var/lib/apt/lists/*

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
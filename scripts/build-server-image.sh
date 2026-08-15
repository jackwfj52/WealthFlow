#!/usr/bin/env bash
# 构建内嵌后端镜像：mvnw 打 fat jar → jpackage app-image（精简 JRE + jar）
# 产物复制到 frontend/src-tauri/resources/server/，由 Tauri 打包进桌面安装包。
# 产物目录已加入 .gitignore，不入库。
set -euo pipefail

cd "$(dirname "$0")/.."

export JAVA_HOME="${JAVA_HOME:-D:/develop/Java/jdk-17}"

# git-bash 的 PATH 用 Unix 风格路径，这里直接以全路径调用 jpackage
JPACKAGE="$JAVA_HOME/bin/jpackage"

echo "==> 1/3 构建后端 fat jar"
./mvnw -q -DskipTests package

JAR="target/WealthFlow-0.0.1-SNAPSHOT.jar"
[ -f "$JAR" ] || { echo "错误：找不到 $JAR"; exit 1; }

STAGING="target/jpackage-staging"
IMAGE_DIR="target/wealthflow-server-image"
OUT_DIR="frontend/src-tauri/resources/server"

echo "==> 2/3 jpackage 生成 app-image"
rm -rf "$STAGING" "$IMAGE_DIR" "$OUT_DIR"
mkdir -p "$STAGING"
cp "$JAR" "$STAGING/wealthflow.jar"

"$JPACKAGE" \
  --type app-image \
  --input "$STAGING" \
  --dest "$IMAGE_DIR" \
  --name WealthFlowServer \
  --main-jar wealthflow.jar \
  --main-class org.springframework.boot.loader.launch.JarLauncher \
  --app-version 2.0.0 \
  --java-options "-Dfile.encoding=UTF-8"

echo "==> 3/3 复制镜像到 Tauri 资源目录"
mkdir -p "frontend/src-tauri/resources"
mv "$IMAGE_DIR/WealthFlowServer" "$OUT_DIR"
rm -rf "$STAGING"

echo "完成：$OUT_DIR"

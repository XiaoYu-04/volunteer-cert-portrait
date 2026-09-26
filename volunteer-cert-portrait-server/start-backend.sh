#!/usr/bin/env bash
# 用途（2026-09-26 起）：演示库口令已明文写在 application.yml（`123456`），平时直接
# `java -jar` 即可；本脚本用于**临时换口令 / 换账号**：交互输入，只注入本次进程、不落盘。
# 也会把工作目录切到脚本所在目录（application.yml 的 import 路径与 uploads/ 都相对它）。
#
#   ./start-backend.sh                      # 交互输入口令（不回显）
#   VCP_DB_PASSWORD=... ./start-backend.sh  # 或先设环境变量
set -euo pipefail

cd "$(dirname "$0")"

JAR="${JAR:-vcp-boot/target/vcp-boot-1.0.0.jar}"
if [ ! -f "$JAR" ]; then
  echo "找不到 $JAR —— 先构建：mvn package -DskipTests" >&2
  exit 1
fi

: "${VCP_DB_USERNAME:=postgres}"

if [ -z "${VCP_DB_PASSWORD:-}" ]; then
  read -r -s -p "请输入数据库口令（输入时不回显）：" VCP_DB_PASSWORD
  echo
fi

if [ -z "${VCP_DB_PASSWORD}" ]; then
  echo "口令为空，已取消。" >&2
  exit 1
fi

export VCP_DB_USERNAME VCP_DB_PASSWORD

echo "启动后端：http://127.0.0.1:8080 （口令只注入本次进程，未写入任何文件）"
exec java -jar "$JAR"

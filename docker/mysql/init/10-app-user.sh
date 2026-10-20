#!/bin/bash
# =====================================================================
# MySQL Docker 首次初始化：创建最小权限应用账号 drv_app
#
# 仅在 MySQL 官方镜像数据目录为空的首次初始化时，由
# /docker-entrypoint-initdb.d 机制执行；已有数据卷不会自动重跑本脚本。
# 应用账号只获得当前 MYSQL_DATABASE 的 SELECT/INSERT/UPDATE/DELETE，
# 不授予 CREATE/DROP/ALTER/GRANT/FILE 等管理权限。
# 密码来自 APP_DB_PASSWORD 环境变量，不写入仓库。
# =====================================================================
set -euo pipefail

: "${MYSQL_DATABASE:?MYSQL_DATABASE must be set}"
: "${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD must be set}"
: "${APP_DB_USER:?APP_DB_USER must be set}"
: "${APP_DB_PASSWORD:?APP_DB_PASSWORD must be set}"

# 标识符只允许字母、数字和下划线，避免 SQL 注入。
if [[ ! "$MYSQL_DATABASE" =~ ^[A-Za-z0-9_]+$ ]]; then
  echo "ERROR: MYSQL_DATABASE contains unsupported characters" >&2
  exit 1
fi
if [[ ! "$APP_DB_USER" =~ ^[A-Za-z0-9_]+$ ]]; then
  echo "ERROR: APP_DB_USER contains unsupported characters" >&2
  exit 1
fi
if [[ "$APP_DB_PASSWORD" == *$'\n'* || "$APP_DB_PASSWORD" == *$'\r'* ]]; then
  echo "ERROR: APP_DB_PASSWORD must not contain newline characters" >&2
  exit 1
fi

# 转义 MySQL 字符串中的反斜杠和单引号；不打印密码。
escaped_password=${APP_DB_PASSWORD//\\/\\\\}
escaped_password=${escaped_password//\'/\'\'}

# 使用 MYSQL_PWD 避免把 root 口令放在命令行参数中。
MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --protocol=socket -uroot <<SQL
CREATE USER IF NOT EXISTS '${APP_DB_USER}'@'%' IDENTIFIED BY '${escaped_password}';
GRANT SELECT, INSERT, UPDATE, DELETE ON \`${MYSQL_DATABASE}\`.* TO '${APP_DB_USER}'@'%';
FLUSH PRIVILEGES;
SQL

echo "应用数据库账号已准备：${APP_DB_USER}@%（仅 ${MYSQL_DATABASE} 的 DML 权限）"

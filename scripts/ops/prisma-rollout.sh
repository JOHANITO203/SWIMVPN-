#!/usr/bin/env bash
set -euo pipefail

COMPOSE_DIR="${COMPOSE_DIR:-$(pwd)}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}"
COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-}"
ENV_FILE="${ENV_FILE:-}"
RUN_BASELINE="${RUN_BASELINE:-0}"
SKIP_BACKUP="${SKIP_BACKUP:-0}"
PRISMA_BASELINE_MIGRATION="${PRISMA_BASELINE_MIGRATION:-202604230001_init_schema}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --compose-dir) COMPOSE_DIR="$2"; shift 2 ;;
    --compose-file) COMPOSE_FILE="$2"; shift 2 ;;
    --project) COMPOSE_PROJECT_NAME="$2"; shift 2 ;;
    --env-file) ENV_FILE="$2"; shift 2 ;;
    --baseline) RUN_BASELINE=1; shift ;;
    --skip-backup) SKIP_BACKUP=1; shift ;;
    --baseline-migration) PRISMA_BASELINE_MIGRATION="$2"; shift 2 ;;
    *) echo "Unknown argument: $1"; exit 1 ;;
  esac
done

cd "$COMPOSE_DIR"

compose_cmd=(docker compose -f "$COMPOSE_FILE")
if [[ -n "$COMPOSE_PROJECT_NAME" ]]; then
  compose_cmd+=( -p "$COMPOSE_PROJECT_NAME" )
fi
if [[ -n "$ENV_FILE" ]]; then
  compose_cmd+=( --env-file "$ENV_FILE" )
fi

echo "[1/5] Validate compose config"
"${compose_cmd[@]}" config > /dev/null

echo "[2/5] Start PostgreSQL"
"${compose_cmd[@]}" up -d db

if [[ "$SKIP_BACKUP" != "1" ]]; then
  echo "[3/5] Backup database"
  backup_cmd=("$(dirname "$0")/backup-db.sh" --compose-dir "$COMPOSE_DIR" --compose-file "$COMPOSE_FILE")
  if [[ -n "$COMPOSE_PROJECT_NAME" ]]; then
    backup_cmd+=( --project "$COMPOSE_PROJECT_NAME" )
  fi
  if [[ -n "$ENV_FILE" ]]; then
    backup_cmd+=( --env-file "$ENV_FILE" )
  fi
  "${backup_cmd[@]}"
else
  echo "[3/5] Backup skipped"
fi

if [[ "$RUN_BASELINE" == "1" ]]; then
  echo "[4/5] Baseline existing production schema as ${PRISMA_BASELINE_MIGRATION}"
  "${compose_cmd[@]}" run --rm prisma-migrate sh -lc "npx prisma migrate resolve --applied ${PRISMA_BASELINE_MIGRATION}"
else
  echo "[4/5] Baseline skipped"
fi

echo "[5/5] Run Prisma migrate + seed"
"${compose_cmd[@]}" run --rm prisma-migrate
"${compose_cmd[@]}" run --rm prisma-seed

echo "Prisma rollout completed successfully."

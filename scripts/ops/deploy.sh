#!/usr/bin/env bash
set -euo pipefail

COMPOSE_DIR="${COMPOSE_DIR:-$(pwd)}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}"
COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-}"
ENV_FILE="${ENV_FILE:-}"
BRANCH="${BRANCH:-main}"
TAIL_LINES="${TAIL_LINES:-80}"
SKIP_BUILD="${SKIP_BUILD:-0}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --compose-dir) COMPOSE_DIR="$2"; shift 2 ;;
    --compose-file) COMPOSE_FILE="$2"; shift 2 ;;
    --project) COMPOSE_PROJECT_NAME="$2"; shift 2 ;;
    --env-file) ENV_FILE="$2"; shift 2 ;;
    --branch) BRANCH="$2"; shift 2 ;;
    --tail) TAIL_LINES="$2"; shift 2 ;;
    --skip-build) SKIP_BUILD=1; shift ;;
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

if [[ -d .git ]]; then
  git fetch origin "$BRANCH"
  git checkout "$BRANCH"
  git pull --ff-only origin "$BRANCH"
fi

if [[ "$SKIP_BUILD" == "1" ]]; then
  "${compose_cmd[@]}" up -d --remove-orphans
else
  "${compose_cmd[@]}" up -d --build --remove-orphans
fi

"${compose_cmd[@]}" ps

"${compose_cmd[@]}" logs --tail "$TAIL_LINES" db prisma-migrate gateway-service admin-control-service notification-bot-service || true

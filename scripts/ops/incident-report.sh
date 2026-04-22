#!/usr/bin/env bash
set -euo pipefail

COMPOSE_DIR="${COMPOSE_DIR:-$(pwd)}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}"
COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-}"
ENV_FILE="${ENV_FILE:-}"
REPORT_DIR="${REPORT_DIR:-$COMPOSE_DIR/reports}"
API_HEALTH_URL="${API_HEALTH_URL:-https://api.swimvpn.pro/api/v1/health}"

mkdir -p "$REPORT_DIR"
timestamp="$(date +%Y%m%d-%H%M%S)"
report_file="$REPORT_DIR/incident-$timestamp.txt"

cd "$COMPOSE_DIR"

compose_cmd=(docker compose -f "$COMPOSE_FILE")
if [[ -n "$COMPOSE_PROJECT_NAME" ]]; then
  compose_cmd+=( -p "$COMPOSE_PROJECT_NAME" )
fi
if [[ -n "$ENV_FILE" ]]; then
  compose_cmd+=( --env-file "$ENV_FILE" )
fi

{
  echo "SWIMVPN+ Incident Report"
  echo "Generated: $(date -Is)"
  echo "Host: $(hostname)"
  echo
  echo "== Uptime =="
  uptime
  echo
  echo "== Memory =="
  free -m
  echo
  echo "== Disk =="
  df -h
  echo
  echo "== Docker Compose PS =="
  "${compose_cmd[@]}" ps
  echo
  echo "== API Health =="
  curl -sS -k "$API_HEALTH_URL" || true
  echo
  echo "== Logs: gateway-service (last 120) =="
  "${compose_cmd[@]}" logs --tail 120 gateway-service || true
  echo
  echo "== Logs: admin-control-service (last 120) =="
  "${compose_cmd[@]}" logs --tail 120 admin-control-service || true
  echo
  echo "== Logs: notification-bot-service (last 120) =="
  "${compose_cmd[@]}" logs --tail 120 notification-bot-service || true
  echo
  echo "== Logs: prisma-migrate (last 120) =="
  "${compose_cmd[@]}" logs --tail 120 prisma-migrate || true
} > "$report_file"

echo "Incident report saved: $report_file"

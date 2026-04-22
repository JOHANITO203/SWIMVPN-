#!/usr/bin/env bash
set -euo pipefail

COMPOSE_DIR="${COMPOSE_DIR:-$(pwd)}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}"
COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-}"
ENV_FILE="${ENV_FILE:-}"
API_HEALTH_URL="${API_HEALTH_URL:-https://api.swimvpn.pro/api/v1/health}"
TLS_HOST="${TLS_HOST:-api.swimvpn.pro}"
ALLOW_INSECURE_TLS="${ALLOW_INSECURE_TLS:-0}"

cd "$COMPOSE_DIR"

compose_cmd=(docker compose -f "$COMPOSE_FILE")
if [[ -n "$COMPOSE_PROJECT_NAME" ]]; then
  compose_cmd+=( -p "$COMPOSE_PROJECT_NAME" )
fi
if [[ -n "$ENV_FILE" ]]; then
  compose_cmd+=( --env-file "$ENV_FILE" )
fi

echo "[1/4] Containers running check"
required_services=(db gateway-service admin-control-service customer-order-service inventory-delivery-service vpn-config-engine-service store-engine-service notification-bot-service)
running_services="$("${compose_cmd[@]}" ps --status running --services || true)"
for svc in "${required_services[@]}"; do
  if echo "$running_services" | grep -qx "$svc"; then
    echo "  OK: $svc"
  else
    echo "  FAIL: $svc is not running"
    exit 1
  fi
done

echo "[2/4] prisma-migrate exit status"
prisma_id="$("${compose_cmd[@]}" ps -aq prisma-migrate || true)"
if [[ -z "$prisma_id" ]]; then
  echo "  FAIL: prisma-migrate container not found"
  exit 1
fi
prisma_exit="$(docker inspect -f '{{.State.ExitCode}}' "$prisma_id")"
if [[ "$prisma_exit" != "0" ]]; then
  echo "  FAIL: prisma-migrate exit code $prisma_exit"
  exit 1
fi
echo "  OK: prisma-migrate exit code 0"

echo "[3/4] API health endpoint"
if [[ "$ALLOW_INSECURE_TLS" == "1" ]]; then
  curl -fsS -k "$API_HEALTH_URL" > /dev/null
else
  curl -fsS "$API_HEALTH_URL" > /dev/null
fi
echo "  OK: $API_HEALTH_URL"

echo "[4/4] TLS certificate issuer"
issuer="$(echo | openssl s_client -servername "$TLS_HOST" -connect "$TLS_HOST:443" 2>/dev/null | openssl x509 -noout -issuer || true)"
if [[ -z "$issuer" ]]; then
  echo "  WARN: unable to read TLS issuer for $TLS_HOST"
else
  echo "  INFO: $issuer"
fi

echo "Health check finished successfully."

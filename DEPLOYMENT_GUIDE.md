# SWIMVPN+ Deployment Guide (Dockploy + Truth-Aligned)

## Scope
This guide reflects the current MVP backend reality:
- PostgreSQL + 6 backend services
- No embedded Traefik service in this compose (Dockploy global proxy is used)
- No WireGuard/OpenVPN infrastructure in compose
- PSP integration deferred from active public MVP APIs

## Services
- `gateway-service` (HTTP, port 3000)
- `customer-order-service` (TCP, 3001)
- `inventory-delivery-service` (TCP, 3002)
- `admin-control-service` (TCP, 3003)
- `vpn-config-engine-service` (TCP, 3004)
- `store-engine-service` (TCP, 3005)
- `db` (PostgreSQL 15)

## Environment
1. Copy `.env.example` to `.env`.
2. Set secure values for:
   - `POSTGRES_PASSWORD`
   - `JWT_SECRET`
   - `TELEGRAM_BOT_TOKEN`
   - `ADMIN_CHAT_ID`

## Database Readiness
The production rollout must now treat Prisma as an explicit deployment phase:
- backup DB
- optional one-time baseline for pre-migration databases
- `prisma migrate deploy`
- `prisma db seed`

Recommended production workflow:
1. Build images.
2. Start `db`.
3. Run `scripts/ops/prisma-rollout.sh`.
4. Start the full stack.
5. Run `scripts/ops/health-check.sh`.

## Commands
```bash
# Build
Docker compose build

# Prisma rollout for a fresh/new migration-managed database
scripts/ops/prisma-rollout.sh

# Prisma rollout for an already-existing production database that was created before versioned migrations
scripts/ops/prisma-rollout.sh --baseline

# Start full stack
Docker compose up -d

# Validate service state
Docker compose ps

# Validate compose config
Docker compose config
```

## Exact Dockploy Runbook
Use this when connected to the production host that serves `api.swimvpn.pro`.

### 1. Move into the deployed project directory
```bash
cd /etc/dokploy/compose/swimvpnapp-swimvpnbackend-d39yib/code
```

### 2. Pull the latest code
```bash
git fetch origin main
git checkout main
git pull --ff-only origin main
```

### 3. Validate compose rendering before touching the DB
```bash
docker compose -p swimvpnapp-swimvpnbackend-d39yib config > /dev/null
```

### 4. Run the Prisma rollout
For the first rollout on the old production database:
```bash
scripts/ops/prisma-rollout.sh \
  --compose-dir /etc/dokploy/compose/swimvpnapp-swimvpnbackend-d39yib/code \
  --project swimvpnapp-swimvpnbackend-d39yib \
  --baseline
```

For normal subsequent rollouts:
```bash
scripts/ops/prisma-rollout.sh \
  --compose-dir /etc/dokploy/compose/swimvpnapp-swimvpnbackend-d39yib/code \
  --project swimvpnapp-swimvpnbackend-d39yib
```

### 5. Start or refresh the full stack
```bash
docker compose -p swimvpnapp-swimvpnbackend-d39yib up -d --build --remove-orphans
```

### 6. Verify the stack locally on the server
```bash
scripts/ops/health-check.sh \
  --compose-dir /etc/dokploy/compose/swimvpnapp-swimvpnbackend-d39yib/code \
  --project swimvpnapp-swimvpnbackend-d39yib
```

### 7. Re-test the public endpoints
```bash
curl -i https://api.swimvpn.pro/api/v1/health
curl -i https://api.swimvpn.pro/api/v1/store/plans
curl -i -X POST https://api.swimvpn.pro/api/v1/access/bootstrap \
  -H 'Content-Type: application/json' \
  -d '{"deviceId":"prod-rollout-check","platform":"android","locale":"fr"}'
```

### 8. If anything fails
```bash
scripts/ops/incident-report.sh \
  --compose-dir /etc/dokploy/compose/swimvpnapp-swimvpnbackend-d39yib/code \
  --project swimvpnapp-swimvpnbackend-d39yib
```

## Health Checks
- Gateway health endpoint: `GET /api/v1/health`
- Internal TCP services are health-checked by TCP port probes in compose.

## Notes
- `Server` model is optional internal support data.
- Telegram is admin-control and notification only; PostgreSQL remains source of truth.
- Do not treat deferred PSP code as active MVP functionality.
- `prisma db push` must not be used as the normal production rollout path anymore.

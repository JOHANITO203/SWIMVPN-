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
The backend image runs Prisma deploy migrations at startup:
- `npx prisma migrate deploy`

Recommended production workflow:
1. Build images.
2. Start `db`.
3. Run migration deploy.
4. Start all services.

## Commands
```bash
# Build
Docker compose build

# Start
Docker compose up -d

# Validate service state
Docker compose ps

# Validate compose config
Docker compose config
```

## Health Checks
- Gateway health endpoint: `GET /api/v1/health`
- Internal TCP services are health-checked by TCP port probes in compose.

## Notes
- `Server` model is optional internal support data.
- Telegram is admin-control and notification only; PostgreSQL remains source of truth.
- Do not treat deferred PSP code as active MVP functionality.

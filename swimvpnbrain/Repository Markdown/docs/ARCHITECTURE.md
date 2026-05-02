# ARCHITECTURE - SWIMVPN+

## Architecture Direction
SWIMVPN+ backend is microservice-oriented. The architecture optimizes for clear boundaries, traceable order lifecycle, and strict data truth in PostgreSQL.

## Authoritative Components
- Database: PostgreSQL (single source of truth)
- ORM: Prisma (required data access layer)
- Admin side channel: Telegram (control + notification only)

## Target Services
### gateway-service
- Single external entry point.
- Routes requests to internal services.
- Enforces admin-authenticated access for admin endpoints.

### customer-order-service
- Owns customer order lifecycle.
- Tracks order creation, payment state, and fulfillment linkage.

### inventory-delivery-service
- Owns inventory status transitions and assignment flow.
- Owns delivery status tracking and admin delivery notifications.

### admin-control-service
- Owns admin authentication, sessions, and admin action events.
- Integrates Telegram as admin control interface, not as data authority.

### vpn-config-engine-service
- Owns config ingest/parse/validate/normalize/classify/preview/runtime-payload preparation.
- Preserves raw supplier config unchanged.

### store-engine-service
- Owns offers and store-facing pricing metadata for MVP plans (`week`, `month`, `quarter`) in `RUB` by default.

## Explicit MVP Non-Goals
- No VPN server provisioning.
- No WireGuard/OpenVPN backend server operations as core business logic.
- No PSP integration in current MVP stage.
- No additional services beyond target list without explicit justification.

## Compliance Notes
- PSP handlers may exist internally for deferred integration work but must not be exposed as active public MVP APIs.
- `Server` remains optional internal support data and is not a required business entity in MVP order fulfillment.

## Consistency Rules
- Every paid order must be traceable to customer, plan, assigned config, and delivery status.
- Inventory modeling remains simple for MVP.
- Delivery workflow remains intentionally lightweight in MVP.

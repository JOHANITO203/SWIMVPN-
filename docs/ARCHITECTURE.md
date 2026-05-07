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
- Owns backend config ingest/parse/validate/normalize/classify/preview/runtime-payload preparation for inventory/admin flows.
- Preserves raw supplier config unchanged.
- Does not replace the Android runtime parser, which currently supports additional provider/runtime formats.

### store-engine-service
- Owns offers, store-facing pricing metadata, and assigned premium server exposure.
- Verifies device, entitlement, assignment health, expiry, and quota before returning backend premium nodes.

## Explicit MVP Non-Goals
- No VPN server provisioning.
- No WireGuard/OpenVPN backend server operations as core business logic.
- No full generic PSP platform in current MVP stage.
- No additional services beyond target list without explicit justification.

## Compliance Notes
- Payment support is provider-specific: manual card and Crypto Pay flows exist when configured; Stripe/YooKassa remain disabled until signature verification is implemented.
- `Server` remains optional internal support data and is not a required business entity in MVP order fulfillment.

## Consistency Rules
- Every paid order must be traceable to customer, plan, assigned config, and delivery status.
- Inventory modeling remains simple for MVP.
- Delivery workflow remains intentionally lightweight in MVP.
- Freemium access is intentional: expired users keep the app shell and local/imported configs, while backend premium servers remain entitlement-gated.
- Current code and recent worklogs are the operational truth when older roadmap text disagrees.

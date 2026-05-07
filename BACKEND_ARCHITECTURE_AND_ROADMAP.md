# SWIMVPN+ Backend Architecture And Operational Roadmap

Date aligned: 2026-05-07

## 1. Current Reality

This document describes the installed backend as it works now. Older roadmap ideas are subordinate to the current code and worklogs.

SWIMVPN+ uses:

- a public NestJS `gateway-service`
- internal NestJS TCP microservices
- one PostgreSQL database managed through Prisma
- supplier-provided VPN configs stored as preserved raw inventory
- Android as the runtime authority for local import parsing and Xray/tun2socks execution
- Telegram as admin control/notification, not as data truth

There is no Redis transport in the current deployed architecture. Internal service communication uses NestJS TCP transport.

## 2. Service Boundaries

### gateway-service

Public HTTP entrypoint. It routes app/admin/payment requests to internal TCP services. It has no direct Prisma ownership.

Current public surface includes:

- `POST /api/v1/access/bootstrap`
- `POST /api/v1/access/trial`
- `POST /api/v1/access/trial/activate`
- `POST /api/v1/access/profile/complete`
- `GET /api/v1/access/:userNumber`
- `GET /api/v1/servers`
- `GET /api/v1/store/plans`
- `POST /api/v1/orders/checkout`
- `POST /api/v1/payments/crypto/webhook`
- `POST /api/v1/admin/login`
- protected `/api/v1/admin/*` routes

Current hardening in the working tree:

- targeted gateway rate limits
- production Swagger disabled unless explicitly enabled
- optional CORS allowlist through `GATEWAY_CORS_ORIGINS`

### customer-order-service

Owns customer/order/entitlement lifecycle:

- device-bound bootstrap
- trial eligibility and activation
- profile completion
- checkout preparation
- Crypto Pay webhook handling
- manual card approval/rejection
- profile state resolution
- premium usage reporting
- `swimvpn://crypt1/...` entitlement-bound resolution

`getProfile()` is an internal business function used by multiple flows. Do not globally lock it by device without adding an explicit mode or contract.

### inventory-delivery-service

Owns inventory and assignment:

- supplier config import
- assignment creation
- resale slot accounting
- supplier capacity checks
- source quota and sold-plan quota accounting
- assignment revocation/move
- scheduled health checks when enabled
- delivery notification triggers

Allocation uses transactional selection and `FOR UPDATE SKIP LOCKED`.

### store-engine-service

Owns plan catalog exposure and assigned premium server exposure.

It only returns backend premium servers when:

- customer exists
- `x-device-id` matches `Customer.device_id`
- active assignment exists
- inventory health allows use
- provider/source quota is not exhausted
- sold-plan quota is not exhausted
- access has not expired

### vpn-config-engine-service

Owns backend config processing for inventory/admin flows:

- ingest
- parse
- validate
- normalize
- classify
- preview
- prepare runtime payload
- crypt1 wrapping/resolution
- supplier health checks

The backend parser is intentionally narrower than the Android runtime parser today. Android handles more runtime import formats.

### admin-control-service

Owns:

- admin login
- admin JWT validation
- admin sessions
- admin events
- admin inventory actions
- Telegram admin/support bots
- accounting entries

Current hardening in the working tree stores a SHA-256 fingerprint of admin JWT sessions instead of storing reusable token plaintext.

### notification-bot-service

Owns post-purchase/manual-card notifications and Telegram/email delivery helpers.

## 3. Data Model Summary

PostgreSQL/Prisma is the source of truth.

Core models:

- `Customer`
- `Plan`
- `Order`
- `InventoryItem`
- `OrderAssignment`
- `Delivery`
- `Admin`
- `AdminSession`
- `AdminEvent`
- `AccountingEntry`
- optional `Server`

Important current fields include:

- `Customer.device_id`
- `InventoryItem.raw_config`
- `InventoryItem.health_status`
- `InventoryItem.source_quota_bytes`
- `InventoryItem.source_used_bytes`
- `InventoryItem.max_resale_slots`
- `InventoryItem.used_resale_slots`
- `InventoryItem.max_customer_allocations`
- `InventoryItem.supplier_expires_at`
- `OrderAssignment.access_status`
- `OrderAssignment.measured_used_bytes`

## 4. Access Model

The current app is freemium-capable:

- expired users are not locked out of the app shell
- imported configs remain usable
- backend premium servers/configs require active trial/subscription entitlement
- backend premium server exposure is enforced by backend device and assignment checks

States used by Android/backend include:

- `PROFILE_INCOMPLETE`
- `TRIAL_AVAILABLE`
- `ACTIVE_TRIAL`
- `ACTIVE_SUBSCRIPTION`
- `PENDING_FULFILLMENT`
- `EXPIRED_TRIAL`
- `EXPIRED_SUBSCRIPTION`
- `FREEMIUM`

## 5. Config Handling

Raw supplier configs are preserved as original input.

Current design reality:

- backend inventory stores raw supplier resources
- Android expands remote subscriptions and chooses runtime nodes when needed
- Android parser currently supports more provider/runtime formats than the backend parser
- backend must not mutate raw configs to "fix" runtime behavior

## 6. Payments

Payment logic is no longer purely future-state.

Current code includes:

- manual card checkout path through Telegram payment bot
- manual card proof/approval/rejection workflow
- Crypto Pay invoice and webhook handling when configured
- disabled Stripe/YooKassa handlers until signature verification is configured

Do not describe all PSP/payment integration as absent. The accurate statement is: payment support is partial and provider-specific, with Crypto/manual-card paths active when configured and other PSPs deferred.

## 7. Deployment

The production root `docker-compose.yml` is Dokploy/Traefik oriented:

- no host `ports:` mappings
- gateway and landing attach to `dokploy-network`
- internal services and DB attach to the private `swimvpn-private` network
- internal TCP ports are not published to the host

`backend/docker-compose.yml` is a local/dev compose file and must not be treated as the production topology without hardening.

## 8. Roadmap From Current Reality

### Release stabilization

- keep current freemium/entitlement behavior
- keep Android runtime reconnect improvements
- add low-risk compose/gateway hardening
- run long Android screen-off and network-handoff QA

### Device identity alignment

The current code intentionally uses the normalized Android device identifier for anti-abuse, customer continuity, entitlement checks, server exposure, crypt1 resolution, cancellation, and usage reporting.

This is the operational device identity model. Do not replace it with hashing unless the product decision changes and a separate migration is explicitly planned.

Required protections around this model:

- never expose `device_id` in public API responses
- never log raw device identifiers
- keep device matching on backend-sensitive actions
- protect database backups, secrets, and admin access
- keep privacy/legal copy aligned with this implemented behavior

### Parser alignment

Keep Android parser as runtime authority for now. Add backend parser support only where inventory/admin workflows require it.

### Local data protection

Config and auto-connect local storage should be encrypted in a future migration, but this is local data protection, not network obfuscation.

# DOMAIN MODEL - SWIMVPN+

## Core Entities
- `customers`
- `plans`
- `orders`
- `inventory_items`
- `order_assignments`
- `deliveries`
- `admins`
- `admin_sessions`
- `admin_events`
- `accounting_entries`
- optional `servers`

## Business Meanings
### customers
Represents app/customer identity, device-bound continuity, and contact surface used for order traceability and delivery correlation.

### plans
Commercial offer definitions. MVP plan set is fixed to `week`, `month`, `quarter` with default currency `RUB`.

### orders
Primary transactional record. Must remain traceable across payment state, assignment, and delivery.

### inventory_items
Represents supplier-provided VPN config assets managed by the platform. The raw config is preserved unchanged and operational metadata tracks health, quota, supplier expiry, resale slots, and provider labels.

### order_assignments
Join entity linking an order to an inventory item and assignment metadata. Access is controlled through `PENDING`, `ACTIVE`, `EXPIRED`, `REVOKED`, and `FAILED`.

### deliveries
Tracks delivery progression and status for fulfilled orders.

### admins
Admin identities authorized to operate backend/admin flows.

### admin_sessions
Authenticated admin session records.

### admin_events
Audit trail of admin actions (panel and Telegram control operations).

### accounting_entries
Accounting trail for order revenue, supplier costs, crypto/manual entries, refunds, and adjustments.

## Required Traceability Path
Every paid order must map to:
1. customer
2. plan
3. assigned config (`inventory_items` via `order_assignments`)
4. delivery status (`deliveries`)

## Inventory States (MVP)
Minimal status set:
- `available`
- `reserved`
- `assigned`
- `dead`

Health/status extensions used by the installed code:
- `HEALTHY`
- `DEGRADED`
- `FULL`
- `EXPIRED`
- `DISABLED`

## Current Access Model

- `PROFILE_INCOMPLETE`
- `TRIAL_AVAILABLE`
- `ACTIVE_TRIAL`
- `ACTIVE_SUBSCRIPTION`
- `PENDING_FULFILLMENT`
- `EXPIRED_TRIAL`
- `EXPIRED_SUBSCRIPTION`
- `FREEMIUM`

Freemium is a real operating mode. Expired users may keep the app shell and imported/local configs; backend premium servers and assigned configs remain protected by backend entitlement and device checks.

## Config Processing Model
Inventory config data lifecycle must be explicit:
- ingest
- parse
- validate
- normalize
- classify
- preview
- prepare runtime payload

Raw supplier config must remain preserved as original input.

## Documentation Rule

When older documentation conflicts with the installed code and recent worklogs, update the documentation. Do not rewrite working business logic just to match stale roadmap text.

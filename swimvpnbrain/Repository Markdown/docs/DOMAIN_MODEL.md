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

## Business Meanings
### customers
Represents buyer identity and contact surface used for order traceability and delivery correlation.

### plans
Commercial offer definitions. MVP plan set is fixed to `week`, `month`, `quarter` with default currency `RUB`.

### orders
Primary transactional record. Must remain traceable across payment state, assignment, and delivery.

### inventory_items
Represents supplier-provided VPN config assets managed by the platform.

### order_assignments
Join entity linking an order to an inventory item and assignment metadata.

### deliveries
Tracks delivery progression and status for fulfilled orders.

### admins
Admin identities authorized to operate backend/admin flows.

### admin_sessions
Authenticated admin session records.

### admin_events
Audit trail of admin actions (panel and Telegram control operations).

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
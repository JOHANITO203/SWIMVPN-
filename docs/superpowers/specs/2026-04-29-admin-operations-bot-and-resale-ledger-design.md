# Admin Operations Bot and Resale Ledger Design

**Date:** 2026-04-29

## Goal

Build a secure Admin Operations Bot that manages supplier VPN configs, fulfillment, quota/expiry operations, and simple accounting while keeping PostgreSQL and backend services as the only source of truth.

## Corrected Product Truth

A supplier config link can technically be used on up to 5 devices.

SWIMVPN intentionally resells that same supplier link to at most 2 customer orders, leaving supplier device capacity unused as a quality/speed buffer.

This applies equally to every commercial category:

- Basic
- Premium
- Platinum

Important correction:

- Inventory resale capacity is not the same as the public commercial plan name.
- One paid customer order consumes exactly 1 resale slot on one supplier config.
- `max_resale_slots` must default to `2` for every imported supplier config.
- `supplier_device_limit` may store `5` when detected or entered by admin.
- Fulfillment must reject allocation when `used_resale_slots + 1 > max_resale_slots`.

The previous ideas that Basic consumes 1 slot, Premium consumes 2 slots, Platinum consumes 4 slots, or that a link can be resold to 4 orders are no longer correct for supplier resale capacity.

## Bot Credentials And Responsibilities

### Admin Operations Bot

Recommended credential: `TELEGRAM_BOT_TOKEN`.

Runtime service: `admin-control-service`.

Responsibilities:

- Secure admin cockpit.
- Import supplier configs into Basic/Premium/Platinum inventory buckets.
- Inspect stock and resale slots.
- Retry pending fulfillment.
- Suspend or disable configs.
- Mark quota reached or supplier expired.
- View orders and accounting reports.

Security:

- Must require explicit admin allow-listing through `ADMIN_USER_IDS`.
- Group chat IDs are notification destinations, not sufficient by themselves for sensitive operations unless explicitly allowed by design.
- All sensitive actions must be written to `AdminEvent`.

### Notification / Payment Bot

Credential: `NOTIFICATION_BOT_TOKEN`.

Runtime service: `notification-bot-service`.

Responsibilities:

- Receive payment proof screenshots.
- Collect final customer email, customer phone, and sender payment phone.
- Send proof packets to admin review chat.
- Handle approve/reject callbacks.
- Send delivery/rejection emails through Resend.

### Support Bot

Credential: `ADMIN_SUPPORT_BOT_TOKEN`.

Runtime service: `admin-control-service` support bot module.

Responsibilities:

- Customer support intake.
- Escalation to admin/support group.
- No payment fulfillment authority.
- No inventory authority.

### Payment Bot Token

`PAYMENT_BOT_TOKEN` currently appears to be a fallback/resolution credential, not an active launched bot service.

Decision for this design:

- Do not make `PAYMENT_BOT_TOKEN` the admin bot.
- Keep `TELEGRAM_BOT_TOKEN` as Admin Operations Bot.
- Later either remove `PAYMENT_BOT_TOKEN` or reserve it for a dedicated payment-only bot if separation becomes necessary.

## Inventory Import Flow

Command shape:

```text
/import_config
```

Guided flow:

1. Bot asks admin to choose inventory bucket:
   - Basic
   - Premium
   - Platinum
2. Admin pastes raw config or subscription URL.
3. Backend parses supplier metadata through the existing config parser.
4. Bot shows preview:
   - category
   - protocol
   - profile/config name
   - traffic used
   - traffic total
   - expiry
   - provider/device limit if detected
   - supplier device limit
   - max resale slots = 2
   - used resale slots if detected or entered
   - available resale slots
5. Admin confirms save.
6. Backend creates `InventoryItem`.

Storage rules:

- Preserve raw config intact.
- Set `category` from selected Basic/Premium/Platinum bucket:
  - Basic -> `WEEK`
  - Premium -> `MONTH`
  - Platinum -> `QUARTER`
- Set `max_resale_slots = 2` by default.
- Set `supplier_device_limit = 5` by default unless parser/admin provides another supplier value.
- Set `used_resale_slots` from detected connected devices only if reliable; otherwise default to `0` and allow admin override.
- Set traffic/expiry metadata from parser when available.

## Fulfillment Flow

Payment approved or confirmed:

```text
Order PENDING
-> payment confirmed
-> Order PAID
-> inventory fulfillment
-> pick oldest healthy config in same category where used_resale_slots + 1 <= 2
-> create/activate assignment
-> increment used_resale_slots by 1
-> Order FULFILLED
-> notification-bot-service sends delivery email
```

No available capacity:

```text
Order PAID
-> PENDING_FULFILLMENT
-> AdminEvent FULFILLMENT_PENDING_NO_CAPACITY
-> Admin Operations Bot alert
-> no config exposed to app
-> retry after stock is added
```

## Quota And Expiry Truth

For supplier-managed configs, backend must not invent per-user isolated quota.

Backend/UI may show provider metadata:

- traffic used
- traffic total
- usage percentage
- supplier expiry

UI copy must make it clear this is provider-managed config metadata.

For store/boutique Premium configs:

- show category `Premium` or selected plan label Basic/Premium/Platinum
- show usage percentage only when source metadata exists
- show expiry when supplier expiry exists
- otherwise show `Unknown`, not `Unlimited`

For imported user configs:

- show category `Imported`
- show parser metadata when available
- imported configs remain usable in freemium/expired states

## Suspension And Enforcement

Do not delete raw config records.

When quota is reached or supplier expiry is reached:

- mark `InventoryItem.health_status` as `EXPIRED` or `DISABLED` depending on cause.
- mark linked active assignments `EXPIRED` or `REVOKED` with `status_reason`.
- stop exposing backend premium config as active in app.
- preserve raw config and audit events.

Bot commands:

```text
/mark_expired
/mark_quota_reached
/disable_config
/revoke_assignment
/retry_fulfillment
```

## Accounting Module

Build a minimal ledger after inventory and fulfillment are stable.

Entities to introduce later:

```text
AccountingEntry
- id
- type: REVENUE | EXPENSE | ADJUSTMENT
- amount
- currency
- cryptoAsset optional
- exchangeRateToRub optional
- source: ORDER | SUPPLIER_CONFIG | MANUAL | CRYPTO | REFUND
- orderRef optional
- inventoryItemId optional
- note optional
- createdAt
- createdByAdminId optional
```

Bot commands:

```text
/add_expense
/revenue_today
/profit_month
/orders_today
/crypto_report
/stock_value
```

Accounting principles:

- Store historical amount and currency at transaction time.
- Do not recompute old crypto values with future exchange rates.
- Link revenue entries to orders where possible.
- Link expense entries to supplier config batches where possible.

## Implementation Phases

### Phase 1: Align Resale Slot Semantics

- Change resale slot consumption so every paid order consumes 1 resale slot.
- Keep `max_resale_slots = 2`.
- Keep `supplier_device_limit = 5` metadata.
- Keep policy tests aligned with one resale slot per paid order and a max resale cap of two orders per supplier config.
- Keep public names Basic/Premium/Platinum unchanged.

### Phase 2: Secure Admin Operations Bot Foundation

- Harden admin auth with `ADMIN_USER_IDS`.
- Keep `TELEGRAM_BOT_TOKEN` for admin-control-service.
- Split bot modules inside admin-control-service:
  - inventory commands
  - order commands
  - accounting commands
- Write `AdminEvent` for all sensitive bot actions.

### Phase 3: Inventory Import Wizard

- Implement guided category selection.
- Implement config paste/subscription URL import.
- Show parser preview.
- Save to inventory only after confirmation.
- Alert if category stock is low or pending fulfillment exists.

### Phase 4: Fulfillment Retry Orchestration

- Add admin command to list pending fulfillment.
- Add admin command to retry fulfillment for one order or all eligible pending orders.
- Ensure delivery email is triggered only after active assignment exists.

### Phase 5: Quota/Expiry Operations

- Add manual health commands for quota reached and expired supplier config.
- Propagate status to linked assignments.
- Ensure app no longer treats those assignments as active.

### Phase 6: Accounting Ledger

- Add ledger schema.
- Record revenue on successful paid fulfillment.
- Add supplier expense entry command.
- Add reporting commands.

## Explicit Out Of Scope

- No deletion of raw VPN configs.
- No bot-side delivery without backend order/assignment.
- No fake unlimited quota.
- No bypass of payment or entitlement rules.
- No customer auth redesign.
- No new microservice unless the existing admin-control-service becomes too large after implementation.

## Open UI Wording Point

Since one supplier link is resold to at most two customer orders and the supplier total device limit may be five, the customer UI should not imply that each buyer owns five supplier devices.

Recommended MVP wording:

- `Access: Premium`
- `Plan: Basic/Premium/Platinum`
- `Provider quota: x / y`
- `Expires: date`
- `Device access: Up to 2 devices`

The subscription page may advertise up to 2 devices for every plan. Backend resale capacity still counts customer orders: one order consumes one resale slot, and a supplier link stops accepting new orders after two resale slots are used.

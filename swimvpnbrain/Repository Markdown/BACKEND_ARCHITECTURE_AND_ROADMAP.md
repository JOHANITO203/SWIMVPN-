# SWIMVPN+ Backend Architecture & Roadmap

## 1. High-Level Architecture
The system follows an API Gateway pattern over an internal Microservices mesh. 
* **External Layer:** Mobile App, Admin Web Dashboard, Telegram API, Future PSP Webhooks.
* **Entrypoint:** `gateway-service` (REST API). Handles authentication, rate-limiting, and request routing.
* **Internal Mesh:** NestJS microservices communicating via lightweight TCP or Redis transport.
* **Data Layer:** A single, authoritative PostgreSQL database. For MVP speed and referential integrity, services share a unified database schema managed by Prisma ORM.
* **Notification Layer:** Telegram bot integrated into `admin-control-service` for outbound alerts and basic triage commands.

## 2. Service Boundaries & Responsibilities
* **`gateway-service`**: HTTP REST API. Has no direct DB access. Validates JWTs, applies rate limits, and proxies requests to internal microservices via RPC.
* **`customer-order-service`**: Manages the core business lifecycle. Creates customers, opens orders, and tracks order status (Pending -> Paid -> Fulfilled).
* **`inventory-delivery-service`**: Manages the stock of pre-configured VPN links. Queries available stock by category, assigns it to paid orders, and tracks delivery state.
* **`store-engine-service`**: Manages the catalog (`plans`), pricing in RUB, and exposes the future PSP integration seam.
* **`vpn-config-engine-service`**: Pure computational service (stateless). Receives raw strings/files, identifies the protocol (VLESS, VMess, etc.), extracts metadata, and normalizes it into the canonical `SwimVpnProfile`.
* **`admin-control-service`**: Handles Admin RBAC, JWT issuance, audit logging (`admin_events`), and Telegram Webhook ingestion/dispatch.

## 3. Folder Structure (NestJS Monorepo)
Optimize for a single repository to share the Prisma client, DTOs, and TS interfaces natively.

```text
swimvpn-backend/
├── apps/
│   ├── gateway-service/       # REST API, Swagger, Auth Guards
│   ├── customer-order-service/
│   ├── inventory-delivery-service/
│   ├── store-engine-service/
│   ├── vpn-config-engine-service/
│   └── admin-control-service/
├── libs/
│   ├── database/              # Prisma schema, migrations, generated client
│   ├── contracts/             # Shared DTOs, canonical SwimVpnProfile interface
│   └── common/                # Shared utilities, Logger, TCP config
├── docker-compose.yml
├── Dockerfile.microservice
├── Dockerfile.gateway
├── package.json
└── tsconfig.json
```

## 4. Prisma Schema (`libs/database/schema.prisma`)
*Note: We use a shared schema for the MVP to enforce strict foreign keys and simplify transactions. Service boundaries are enforced at the application code level.*

```prisma
generator client {
  provider = "prisma-client-js"
}

datasource db {
  provider = "postgresql"
  url      = env("DATABASE_URL")
}

enum PlanCategory {
  WEEK
  MONTH
  QUARTER
}

enum OrderStatus {
  PENDING
  PAID
  FULFILLED
  FAILED
  CANCELLED
}

enum InventoryStatus {
  AVAILABLE
  RESERVED
  ASSIGNED
  DEAD
}

model Customer {
  id         String   @id @default(uuid())
  public_id  String   @unique @default(dbgenerated("nanoid(12)"))
  email      String?
  phone      String?
  created_at DateTime @default(now())
  updated_at DateTime @updatedAt

  orders           Order[]
  inventory_items  InventoryItem[]
}

model Plan {
  id             String       @id @default(uuid())
  code           PlanCategory @unique
  name           String
  duration_label String
  quota_label    String
  price_rub      Decimal      @db.Decimal(10, 2)
  active         Boolean      @default(true)
  display_order  Int          @default(0)

  orders Order[]
}

model Order {
  id           String      @id @default(uuid())
  order_ref    String      @unique
  customer_id  String
  plan_id      String
  status       OrderStatus @default(PENDING)
  amount_rub   Decimal     @db.Decimal(10, 2)
  payment_ref  String?
  created_at   DateTime    @default(now())
  paid_at      DateTime?
  fulfilled_at DateTime?

  customer    Customer          @relation(fields: [customer_id], references: [id])
  plan        Plan              @relation(fields: [plan_id], references: [id])
  assignments OrderAssignment[]
  deliveries  Delivery[]
}

model InventoryItem {
  id                   String          @id @default(uuid())
  category             PlanCategory
  raw_config           String          @db.Text
  config_type          String          // "VLESS", "VMESS", etc.
  display_protocol     String
  batch_name           String?
  status               InventoryStatus @default(AVAILABLE)
  assigned_order_id    String?         @unique
  assigned_customer_id String?
  imported_at          DateTime        @default(now())
  assigned_at          DateTime?

  customer    Customer?        @relation(fields: [assigned_customer_id], references: [id])
  assignments OrderAssignment?
}

model OrderAssignment {
  id                      String   @id @default(uuid())
  order_id                String
  inventory_item_id       String   @unique
  fallback_offer_title    String
  fallback_duration_label String
  fallback_quota_label    String
  assigned_at             DateTime @default(now())

  order          Order         @relation(fields: [order_id], references: [id])
  inventory_item InventoryItem @relation(fields: [inventory_item_id], references: [id])
}

model Delivery {
  id                String    @id @default(uuid())
  order_id          String
  customer_email    String?
  telegram_notified Boolean   @default(false)
  email_sent        Boolean   @default(false)
  delivery_mode     String    // "APP_ONLY", "EMAIL", "TELEGRAM"
  sent_at           DateTime?
  notes             String?   @db.Text

  order Order @relation(fields: [order_id], references: [id])
}

model Admin {
  id            String   @id @default(uuid())
  username      String   @unique
  password_hash String
  role          String   @default("SUPER_ADMIN")
  active        Boolean  @default(true)
  created_at    DateTime @default(now())

  sessions AdminSession[]
  events   AdminEvent[]
}

model AdminSession {
  id                 String    @id @default(uuid())
  admin_id           String
  refresh_token_hash String
  created_at         DateTime  @default(now())
  expires_at         DateTime
  revoked_at         DateTime?

  admin Admin @relation(fields: [admin_id], references: [id])
}

model AdminEvent {
  id           String   @id @default(uuid())
  admin_id     String?
  event_type   String   // "CONFIG_IMPORTED", "ORDER_MANUALLY_FULFILLED"
  entity_type  String   // "ORDER", "INVENTORY"
  entity_id    String
  payload_json Json
  created_at   DateTime @default(now())

  admin Admin? @relation(fields: [admin_id], references: [id])
}
```

## 5. API Contracts (Exposed by Gateway)

**Public / App Routes:**
* `GET /api/v1/store/plans` -> Returns active plans.
* `POST /api/v1/orders/checkout` -> Body: `{ email, phone, plan_code }`. Returns `OrderRef`.
* `GET /api/v1/orders/:ref/status` -> Long-polling or simple fetch for app to check if paid/fulfilled.
* `GET /api/v1/customers/:public_id/configs` -> Fetch canonical `SwimVpnProfile` for the app.

**Future Webhook:**
* `POST /api/v1/webhooks/psp` -> Receives PSP callback, triggers `markOrderPaid`.

**Admin Routes (Protected by Admin JWT):**
* `POST /admin/v1/auth/login`
* `GET /admin/v1/dashboard/stats` -> Unsold stock counts, daily revenue.
* `POST /admin/v1/inventory/import` -> Body: `{ category: "WEEK", rawConfigs: ["vless://..."] }`
* `GET /admin/v1/inventory` -> Search/Filter configs.
* `PUT /admin/v1/inventory/:id/status` -> Mark as DEAD.
* `GET /admin/v1/orders`
* `POST /admin/v1/orders/:id/reassign` -> Force assign new config to a failing order.

## 6. Docker Strategy & Target Hosting
Target hosting: **Hetzner (Finland/Germany)** or **DigitalOcean (Frankfurt)**. Extremely low cost, low latency to CIS/Russia, Docker-friendly. 

**`docker-compose.prod.yml` Overview:**
* **postgres**: `postgres:15-alpine` (Mapped volume, strict local-only exposure).
* **redis**: `redis:7-alpine` (Used as the message transport layer between NestJS microservices).
* **gateway-service**: Port `3000` mapped to host. Handled via NGINX/Traefik reverse proxy (handles SSL).
* **internal-services** (x5): Replicas configured, no ports exposed to the host. Communicate strictly via Redis transport.

## 7. Implementation Roadmap (MVP Timeline)
* **Day 1: Infrastructure & DB.** Initialize NestJS monorepo, generate Prisma schema, spin up Postgres/Redis locally.
* **Day 2: Core Domain.** Build `customer-order-service` and `store-engine-service`. Create basic CRUD for customers and catalog.
* **Day 3: The Engine.** Build `vpn-config-engine-service`. Implement Regex/parsers for VLESS/VMess/Reality. Define `SwimVpnProfile` canonical model.
* **Day 4: Inventory & Fulfillment.** Build `inventory-delivery-service`. Implement the import workflow and the transaction: Find available config -> Reserve -> Assign -> Mark Fulfilled.
* **Day 5: Gateway & Admin.** Build `gateway-service` routes and `admin-control-service`. Integrate Telegram alerts. End-to-end testing.

## 8. Security Notes
1. **Strict Boundary:** Only `gateway-service` accepts external HTTP.
2. **Config Sanitization:** The `vpn-config-engine-service` must strictly type-check and sanitize inputs to prevent NoSQL injection (even in JSON payloads) or log spoofing.
3. **Audit Logs:** Every admin action (e.g., marking an inventory item DEAD, manual assignment) writes an immutable record to `AdminEvent`.
4. **No PSP Keys locally yet:** The system expects PSP data only via webhook signature validation (to be implemented later).

## 9. Admin Module Scope
The Admin Panel is the control tower:
* **Stock Management:** View counts of `AVAILABLE` vs `ASSIGNED` vs `DEAD` configs per category.
* **Batch Import:** Paste 500 VLESS strings. The system pushes them to the config-engine, validates them, and writes to `InventoryItem`.
* **Order Triage:** Search orders by phone/email. If an order is stuck, the admin can click "Reassign Config" to pull a fresh config from stock and push it to the user.

## 10. Telegram Control Scope
Telegram is **read-heavy, action-light**:
* **Outbound Alerts:** Real-time push to a private admin group when:
  * An order is successfully fulfilled.
  * Stock for `WEEK` drops below 10 items.
  * A critical failure occurs (e.g., PSP paid but 0 inventory available).
* **Inbound Commands (Future MVP+):** `/stock` to get current counts. `/dead <config_id>` to kill a reported config. Telegram requests hit the gateway webhook, which validates the Chat ID against a whitelist.

## 11. Config Import Strategy
1. Admin uploads a `.txt` file or pastes a list of strings to the Gateway.
2. Gateway sends payload via RPC to `inventory-delivery-service`.
3. `inventory-delivery-service` streams each line to `vpn-config-engine-service`.
4. Engine returns `SwimVpnProfile` with `isValid`, `protocol`, `warnings`.
5. If valid, `inventory-delivery-service` inserts into Postgres with `category`, `raw_config`, and `display_protocol`.
6. *Crucial Rule:* Duplicate `raw_config` strings are rejected at the DB level (unique constraint or explicit pre-check).

## 12. Future PSP Integration Seam
The system is built to wait for the PSP. 
* **Current State:** Orders remain `PENDING`. Admins can manually trigger `markOrderPaid` via the dashboard for testing.
* **Future State:** You configure the PSP. The PSP sends an HTTP POST to `gateway-service` `/api/v1/webhooks/psp`.
* **The Seam:** The Gateway validates the webhook signature, extracts `payment_ref` and `order_ref`, and fires an RPC event: `order.paid`.
* **Fulfillment Reactor:** `customer-order-service` hears `order.paid`, updates the DB, and fires `order.ready_to_fulfill`. The `inventory-delivery-service` catches this, assigns the config, and finalizes the transaction.

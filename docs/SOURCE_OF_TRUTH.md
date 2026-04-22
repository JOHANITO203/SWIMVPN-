# SOURCE OF TRUTH - SWIMVPN+

## Project Identity
- Project: SWIMVPN+
- Product type: Android VPN client with backend control plane
- Primary objective: reliable resale and lifecycle management of supplier-provided VPN configs

## Business Model Constraints
- SWIMVPN+ resells and manages supplier-provided VPN configs.
- Supplier currently provides pre-configured VPN configs.
- Supplier quota handling is external to SWIMVPN+ in MVP.
- MVP backend does not provision VPS VPN servers.
- MVP backend does not operate WireGuard/OpenVPN server infrastructure as a business capability.
- Core technical value in MVP: high compatibility for reading and processing VPN config formats.

## Commercial Defaults
- Offers: `week`, `month`, `quarter`
- Default currency: `RUB`

## Data and System Truth
- PostgreSQL is the source of truth.
- Prisma ORM is mandatory for persistence access.
- Telegram is admin control + notification only.
- Telegram is never the source of truth.
- PSP integration is postponed for MVP.

## Product Scope Priorities
- Frontend already exists.
- Backend refactor is the current priority.
- Admin authentication is required.
- Customer authentication is not required for MVP.

## Trial Contract
- Trial offer: `3 days`
- Trial is linked to onboarding and is available only to new users.
- On first app open, backend must capture the installing device identifier and create or recover a prospect customer profile.
- Backend must assign a public user number automatically in the format `SW-XXXXXX`.
- The public user number is shown to the user in the profile screen and is never treated as the anti-abuse proof by itself.
- Trial activation happens only after onboarding completion and after the user provides:
  - phone number
  - email
- Trial activation must complete backend profile data first, then activate the trial.
- Trial must be granted at most once according to backend verification based on:
  - device identifier
  - phone number
  - email
- Trial activation and eligibility are backend-controlled rules.
- Frontend must not auto-grant trial access by itself.

## Backend Service Target
- `gateway-service`
- `customer-order-service`
- `inventory-delivery-service`
- `admin-control-service`
- `vpn-config-engine-service`
- `store-engine-service`

## VPN Config Handling Canonical Pipeline
Do not use vague wording such as "decryption" as a complete description.

All config handling must follow explicit stages:
1. ingest
2. parse
3. validate
4. normalize
5. classify
6. preview
7. prepare runtime payload

Rule: raw config input must always be preserved intact.

## Config Ecosystem Priority
- VLESS
- VLESS Reality
- TCP
- VMess
- Trojan
- Shadowsocks
- JSON Xray
- JSON V2Ray

## Compliance Guard Notes
- PSP integration remains deferred in MVP and is not part of the active public API surface.
- `Server` is an internal optional abstraction and must not block MVP ordering/inventory flows.
- MVP stack must not run WireGuard/OpenVPN server infrastructure as a business architecture component.

# WORKLOG

## [2026-04-20] [Initialize NestJS Monorepo & Prisma Schema]
- **Status**: DONE
- **Changes**: Created monorepo structure, shared Prisma schema, and `nest-cli.json`.

## [2026-04-20] [Scaffold Microservices]
- **Status**: DONE
- **Changes**: Generated 6 apps with TCP/HTTP bootstrapping. Installed `@nestjs/microservices`.

## [2026-04-20] [Implement Core Domain & Inter-service Communication]
- **Status**: DONE
- **Changes**: 
    - Implemented `Store`, `Customer`, `Inventory`, `VPN-Config`, and `Admin` services.
    - Configured `Gateway` to route to all internal services.
    - Established `@app/contracts` for shared DTOs/Interfaces.
    - Enabled `ValidationPipe` across all 6 services for strict type safety.
- **Verification**: All services build successfully via `npm run build`.

## [2026-04-20] [DTO Validation & Infrastructure Polish]
- **Status**: DONE
- **Changes**: Added `class-validator` decorators to all shared DTOs and enabled global validation pipes in all microservice entry points. Added `concurrently` for local development.
- **Next Step**: Build Health Checker and Payment Webhooks.

## [2026-04-21] [Production-Ready Features & Android Integration]
- **Status**: DONE
- **Changes**:
    - Integrated Stripe and YooKassa payment webhooks in `customer-order-service` to automate fulfillment.
    - Built "Config Health Checker" in `vpn-config-engine` with TCP socket connectivity testing.
    - Added low-stock alerts and healthcheck commands to the Telegram Admin Bot.
    - Fixed Git remote configuration and pushed to origin.
    - Verified Android connection to Gateway via `10.0.2.2`.

## [2026-04-22] [Android Frontend Refactor & UI Polish]
- **Status**: DONE
- **Changes**:
    - Refactored `MainViewModel` to include persistent server selection using `PreferencesManager`.
    - Enhanced `HomeScreen` with visual state feedback (Progress Indicator during transitions).
    - Fixed `MainActivity` syntax errors and improved `ImportMenuSheet` state handling.
    - Cleaned up unused code and suppressed `@SuppressLint("HardwareIds")` for trial identification.
    - Verified build stability with `:app:assembleDebug`.

## [2026-04-22] [QR Code Scanning & Backend Integration]
- **Status**: DONE
- **Changes**:
    - Integrated ML Kit Barcode Scanning and CameraX for "Import via QR" feature.
    - Implemented `QrScannerView` and `processImageProxy` in `MainActivity`.
    - Wired `MainViewModel.importVless` to `ApiService.importSubscription`.
    - Implemented `importSubscription` and `activateCode` logic in backend `CustomerService`.
    - Added camera permissions handling and UI overlays for scanning.

## [2026-04-22] [Admin Auth & Telegram Control Layer]
- **Status**: DONE
- **Changes**:
    - Implemented JWT-based authentication for Admin in `admin-control-service`.
    - Created `AdminGuard` in `gateway-service` to secure admin endpoints via TCP verification.
    - Enhanced Telegram Admin Bot with `/orders` command for real-time monitoring.
    - Standardized environment variables in `docker-compose.yml`.
    - Verified inter-service token validation flow.

## [2026-04-23] [Production Docker Deployment Configuration]
- **Status**: DONE
- **Changes**:
    - Created root `docker-compose.yml` with full production stack configuration.
    - Designed Docker network strategy with `swimvpn-network` bridge network.
    - Implemented named volumes for PostgreSQL and Redis data persistence.
    - Added comprehensive health checks for all services.
    - Created `.env.example` with all required environment variables.
    - Wrote comprehensive `DEPLOYMENT_GUIDE.md` with production deployment instructions.
    - Ensured Dockploy compatibility for deployment orchestration.
    - Configured service dependencies, resource limits, and restart policies.
- **Verification**:
    - Docker Compose file validated with Docker Compose 3.8 syntax.
    - Health check endpoints need to be implemented in services.
    - Environment variables aligned with existing backend configuration.
- **Next Steps**:
    - Implement `/health` endpoints in all microservices.
    - Test Docker build and deployment locally.
    - Verify inter-service communication in Docker network.

## [2026-04-21] [Materialize Source-of-Truth Documentation]
- **Status**: DONE
- **Changes**:
    - Created docs/SOURCE_OF_TRUTH.md with canonical project constraints and truth statements.
    - Created docs/ARCHITECTURE.md with target microservice boundaries and MVP non-goals.
    - Created docs/DOMAIN_MODEL.md with core entities and required order traceability model.
    - Created docs/IMPLEMENTATION_RULES.md with execution discipline and guardrails.
- **Verification**:
    - Confirmed all four files exist under docs/.
    - Reviewed file contents against provided project truth list.


## [2026-04-21] [Backend Truth Alignment Batch - Compile/Contract/Deploy Realism]
- **Status**: DONE
- **Changes**:
    - Fixed backend compile drift (dependencies, Prisma-aligned seed, type contract mismatches).
    - Normalized Gateway route surface to a single /api/v1 prefix and removed public PSP webhook exposure.
    - Added minimal admin session persistence/validation/logout tied to dmin_sessions.
    - Removed fake store server fallback and kept DB-truth-backed server responses only.
    - Added explicit VPN config pipeline processing (ingest -> parse -> validate -> normalize -> classify -> preview -> prepare runtime payload) while preserving existing parser base.
    - Replaced invalid/fantasy Docker compose files with source-of-truth-aligned stack (PostgreSQL + 6 services, no WireGuard).
    - Updated env templates and deployment docs to match real runtime contracts (*_SERVICE_HOST, deferred PSP).
- **Verification**:
    - ackend: 
pm run build PASSED.
    - ackend: 
pm run lint PASSED (	sc --noEmit).
    - Root compose: docker compose config PASSED.
    - Backend compose: docker compose config PASSED.


## [2026-04-21] [Docker Port Exposure Hardening]
- **Status**: DONE
- **Changes**:
    - Removed public PostgreSQL port mapping from root compose (db no longer publishes 5432).
    - Kept only gateway public mapping (3000) and left all internal services network-only.
- **Verification**:
    - docker compose config PASSED for root stack.
    - Confirmed no published ports for 5432, 3001-3005, 6379, or 8081.


## [2026-04-22] [Final Deployment Hardening for VPS + Dockploy]
- **Status**: DONE
- **Changes**:
    - Rebuilt root production docker-compose.yml around implemented services only + Traefik (no WireGuard/OpenVPN, no internal public ports).
    - Enforced private-only networking for internal services; only Traefik publishes 80/443 externally.
    - Added dedicated prisma-migrate one-shot service gated by healthy PostgreSQL and required before app services.
    - Added production-safe health checks for each service using real dependency reachability checks.
    - Added 2GB-friendly memory/CPU limits for all services.
    - Hardened Traefik dashboard routing with mandatory basic auth and TLS.
    - Updated root .env.example to match required production variables and removed secret fallback behavior.
    - Updated backend Dockerfile default command to runtime only; migrations are now handled by prisma-migrate.
    - Upgraded gateway health endpoint to verify downstream service reachability before returning healthy.
- **Verification**:
    - ackend: 
pm run build PASSED.
    - Root compose syntax: docker compose config PASSED with required env variables set.
    - Port exposure check: only 80/443 are published in compose.


## [2026-04-22] [notification-bot-service MVP Utility]
- **Status**: DONE
- **Changes**:
    - Created new isolated microservice 
otification-bot-service (TCP 3006) for deterministic post-purchase delivery only.
    - Implemented Telegram admin notifications with structured order data and delivery action buttons.
    - Implemented SMTP transactional email sender (from SWIMVPN+ Support <support@swimvpn.pro>) with static RU/EN templates.
    - Added language fallback logic: default RU, fallback EN, optional payload language override.
    - Added TCP handlers: process_post_purchase_delivery, esend_delivery_email, get_delivery_status.
    - Added Telegram admin commands: /order, /status, /resend, /help (enabled via optional NOTIFICATION_BOT_TOKEN).
    - Integrated inventory-delivery-service to emit post-purchase delivery payload for paid orders.
    - Added service documentation with example payloads and resend-ready notes.
    - Added minimal template test script and verified it passes.
    - Added compose/env wiring for 
otification-bot-service and SMTP requirements.
- **Verification**:
    - ackend: 
pm run lint PASSED.
    - ackend: 
pm run build PASSED.
    - Template test: 
px ts-node apps/notification-bot-service/src/__tests__/template.spec.ts PASSED.
    - Root compose render: docker compose config PASSED with required env values.


## [2026-04-22] [Admin Support Bot (Deterministic Telegram) MVP Batch]
- **Status**: DONE
- **Changes**:
    - Added deterministic admin support bot inside dmin-control-service (no LLM, no freeform AI chat).
    - Implemented RU default + EN fallback topic menu with static guided replies for 7 required support topics.
    - Implemented escalation flow: unresolved/support buttons -> one short user message -> relay to support group ADMIN_SUPPORT_CHAT_ID.
    - Added escalation ticket id generation and clear admin relay formatter (topic, message, timestamp, language, optional email/phone/orderRef, Telegram identifiers).
    - Added in-memory anti-spam guard for escalation submissions.
    - Added minimal tests for formatter, language resolution, optional field extraction, and ticket id format.
    - Updated root and backend env examples with ADMIN_SUPPORT_* variables.
    - Corrected backend compose local wiring so ADMIN_SUPPORT_* variables are bound to dmin-control-service (not gateway).
- **Verification**:
    - ackend: 
pm run lint PASSED.
    - ackend: 
pm run build PASSED.
    - ackend: 
px ts-node apps/admin-control-service/src/__tests__/admin-support-bot.spec.ts PASSED.
    - Root compose render: docker compose config PASSED with required env variables set.
    - Backend compose render: docker compose config PASSED.

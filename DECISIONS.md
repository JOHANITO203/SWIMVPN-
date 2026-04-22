# DECISIONS

## [2026-04-20] [Shared Prisma Schema for Microservices]
- **Decision**: Use a single unified `schema.prisma` inside `libs/database`.
- **Why**: Ensures rapid MVP development and strict referential integrity.
- **Impact**: Microservices share the data layer but are decoupled at the logic layer.

## [2026-04-20] [Internal Communication via TCP]
- **Decision**: Use NestJS TCP transport for inter-service communication.
- **Why**: Lower overhead than Redis/RabbitMQ for MVP; deterministic port mapping (3000-3005).
- **Impact**: Synchronous-style internal calls, easy to debug locally.

## [2026-04-20] [Centralized Validation in Contracts Library]
- **Decision**: Put `class-validator` decorators directly on the shared DTOs in `@app/contracts`.
- **Why**: Ensures "Write Once, Validate Everywhere". Both the Gateway and the internal microservices will reject malformed data automatically.
- **Impact**: Strict type safety across the entire monorepo.

## [2026-04-20] [Environment-Based Service Discovery]
- **Decision**: Use `process.env.*_SERVICE_HOST` for TCP client hostnames with a fallback to `127.0.0.1`.
- **Why**: Allows the same code to run natively (localhost) and inside Docker containers (service names).
- **Impact**: Flexible deployment and easy local debugging.

## [2026-04-22] [Persistent UI State & Visual Feedback]
- **Decision**: Persist `selectedServerId` in `DataStore` and show active progress in the "Big Power Button".
- **Why**: Enhances UX by remembering user preference across app restarts and providing clear feedback during slow VPN handshakes.
- **Impact**: Reliable state management and professional feel.

## [2026-04-21] [MVP Deployment Stack Truth Alignment]
- **Decision**: Align deployment stack to PostgreSQL + 6 backend services only; remove WireGuard/OpenVPN runtime infrastructure from compose.
- **Why**: Source-of-truth defines SWIMVPN+ MVP as supplier-config resale/management, not VPN server provisioning.
- **Impact**: Docker stack now represents implemented MVP reality and is compose-valid.

## [2026-04-21] [Admin Session Minimum Compliance]
- **Decision**: Implement minimal dmin_sessions lifecycle now (create on login, validate token against active session, revoke on logout).
- **Why**: Source-of-truth requires admin sessions, but full refresh-token architecture is intentionally deferred.
- **Impact**: Admin auth now uses persisted session checks without broad auth rewrite.


## [2026-04-22] [Traefik-Only Public Exposure in Production]
- **Decision**: Publish only 80/443 via Traefik; keep backend and database services private on Docker networks.
- **Why**: Matches MVP security posture and VPS deployment constraints while preserving domain-based TLS routing.
- **Impact**: Internal services are reachable only by Docker service name; no direct public exposure of service ports.

## [2026-04-22] [Dedicated Prisma Migration Job]
- **Decision**: Run prisma migrate deploy through a dedicated one-shot compose service (prisma-migrate) before app startup.
- **Why**: Prevents race conditions and avoids migration logic duplication across runtime services.
- **Impact**: App services are gated by migration completion using depends_on: service_completed_successfully.


## [2026-04-22] [Add notification-bot-service as Controlled Utility Service]
- **Decision**: Implement post-purchase delivery as a new isolated microservice (
otification-bot-service) instead of extending dmin-control-service.
- **Why**: Keeps responsibilities narrow (Telegram + email delivery only), avoids coupling admin auth/control concerns with delivery operations, and remains easy to plug into current flow.
- **Impact**: Backend keeps core 6 services plus one utility service justified by explicit MVP delivery requirements.

## [2026-04-22] [Dedicated Notification Bot Token Optionality]
- **Decision**: Use NOTIFICATION_BOT_TOKEN (optional) for command polling to avoid collision with existing admin bot polling token; fallback sender can still use TELEGRAM_BOT_TOKEN for outbound notifications.
- **Why**: Running two pollers on the same Telegram token is unstable.
- **Impact**: Reliable command mode when dedicated token is provided; deterministic notification sending remains available without it.


## [2026-04-22] [Admin Support Bot Embedded in admin-control-service]
- **Decision**: Implement the admin support bot as a focused deterministic module/service inside dmin-control-service for MVP.
- **Why**: Smallest viable change with least operational overhead; reuses existing admin Telegram context while keeping strict non-LLM menu-based behavior.
- **Impact**: No additional standalone support microservice needed now; escalation handling is operational with static RU/EN templates and support-group relay.

## [2026-04-22] [Use Resend as Notification Mail Provider]
- **Decision**: Replace SMTP transport in 
otification-bot-service with Resend API for transactional delivery emails.
- **Why**: Lower operational overhead, simpler deterministic integration, and cleaner production setup with one API key.
- **Impact**: Email sending now depends on RESEND_API_KEY; sender identity is controlled by MAILER_FROM_EMAIL and MAILER_FROM_NAME.

## [2026-04-22] [Explicit Admin JWT Secret]
- **Decision**: Introduce ADMIN_JWT_SECRET as a distinct required secret for dmin-control-service JWT signing.
- **Why**: Removes hardcoded fallback and separates admin token scope from generic service JWT usage.
- **Impact**: Deployment env must include ADMIN_JWT_SECRET (while JWT_SECRET remains required for other services).

## [2026-04-22] [Dockploy Owns Reverse Proxy Layer]
- **Decision**: In Dockploy deployment mode, the app compose must not include its own Traefik instance.
- **Why**: VPS already has Dockploy-managed proxy on 80/443; embedded Traefik caused port allocation failure.
- **Impact**: Domain/TLS routing is configured at Dockploy layer; app compose runs backend services only.

## [2026-04-22] [Prisma Runtime Dependency Baseline on Alpine]
- **Decision**: Install OpenSSL userland libs in backend images (`builder` + `runtime`) for Prisma stability.
- **Why**: Dockploy deployment showed `prisma-migrate` exit 1 with schema engine parse errors on Alpine.
- **Impact**: Backend image gains minimal system deps; migrations should run reliably in production containers.

## [2026-04-22] [Build All Nest Services for Monorepo Runtime Images]
- **Decision**: Build each service explicitly in Docker build pipeline rather than relying on default `nest build` target.
- **Why**: Default build produced only gateway output, causing runtime `MODULE_NOT_FOUND` for other service entrypoints.
- **Impact**: Slightly longer build time; deterministic multi-service runtime correctness.

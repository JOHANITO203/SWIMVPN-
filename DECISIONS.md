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

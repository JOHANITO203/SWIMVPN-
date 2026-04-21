# TODO

## Now
- Set up Docker Compose networking logic to replace `0.0.0.0` with proper service names.
- Implement Telegram Admin webhook integration for order notifications and fulfillment alerts.

## Next
- Implement PSP (Payment Service Provider) webhook seam (Stripe/ЮKassa).
- Add `Config Checker` logic to `vpn-config-engine` (background check of server availability).

## Done
- Scaffold and implement 6 backend microservices and shared libraries.
- Implement inter-service TCP communication and HTTP Gateway.
- Define Android Network Models and API Service.
- Connect Android `MainViewModel` and `SubscriptionScreen` to the real backend.
- Perform PostgreSQL migration to enable `nanoid` extension and seed initial plans.
- Implement robust `vpn-config-engine-service` logic for VLESS (Reality/GRPC) and Shadowsocks.
- Add DTO validation using `class-validator` to all TCP/HTTP endpoints.
- Replace mock logic in services with real Prisma DB operations.

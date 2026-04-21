# TODO

## Now
- Set up Docker Compose networking logic to replace `0.0.0.0` with proper service names.

## Next
- Implement Telegram Admin webhook integration for order notifications and fulfillment alerts.
- Build basic Admin Panel Frontend (optional, or focus on Bot).
- End-to-end testing of VpnState transitions from Android UI to Backend.

## Done
- Scaffold and implement 6 backend microservices and shared libraries.
- Implement inter-service TCP communication and HTTP Gateway.
- Define Android Network Models and API Service.
- Connect Android `MainViewModel` and `SubscriptionScreen` to the real backend.
- Perform PostgreSQL migration to enable `nanoid` extension and seed initial plans.
- Implement robust `vpn-config-engine-service` logic for VLESS (Reality/GRPC) and Shadowsocks.
- Add DTO validation using `class-validator` to all TCP/HTTP endpoints.
- Replace mock logic in services with real Prisma DB operations.
- Refactor Android backend integration and polish UI (VpnState handling, persistent selection).
- Implement OCR and QR code scanning for the "Import via QR" feature in Android.
- Implement Admin authentication and Telegram admin control layer.

## Alignment Follow-ups
- Expand vpn-config-engine protocol coverage beyond current parser support to match full priority ecosystem (VMess, Trojan, JSON Xray, JSON V2Ray).
- Add deeper admin session hardening (token rotation / refresh flow) when moving beyond minimal MVP compliance.
- Add integration tests for order paid->fulfilled path and session-bound admin auth flow.


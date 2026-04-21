# TODO - SWIMVPN+

## 🚀 Priority: MVP Completion (Phase 2 & 3)

### 1. Payment & Fulfillment (High Priority)
- [ ] Implement `POST /api/v1/payments/webhook` in `customer-order-service`.
- [ ] Add logic to mark orders as `PAID` upon webhook reception.
- [ ] Connect `inventory-delivery-service` to the payment success event to trigger automatic config assignment.

### 2. Monitoring & Reliability
- [ ] Implement "Config Health Checker" in `vpn-config-engine-service`.
- [ ] Add automated cleanup of `EXPIRED` inventory assignments.
- [ ] Add basic Sentry or Winston logging for production monitoring.

### 3. Telegram Admin Bot Enhancements
- [x] Add automated "Low Stock" alerts to the admin.
- [x] Add automated "New Sale" notifications to the admin.
- [x] Add `/users` command for quick statistics.
- [x] Add `/manual_fulfill` for emergency access granting.
- [ ] Add `/list_stock` command to see available configs per category.
- [ ] Add `/revoke [orderId]` command for manual intervention.

### 4. Android UX
- [ ] Implement "Subscription Expiry" countdown in `ProfileScreen`.
- [ ] Add "Support" button linking directly to the Telegram support bot.
- [ ] Improve error handling when no servers are available in the selected region.

---

## ✅ Completed Tasks
- [x] Refactor backend to NestJS Microservices.
- [x] Implement Telegram Admin Bot for config import.
- [x] Build robust VLESS/Shadowsocks parser.
- [x] Configure Docker Compose for 6 services + DB.
- [x] Sync Android app with device-id based trial logic.

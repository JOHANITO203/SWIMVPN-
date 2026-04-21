# WORKLOG - SWIMVPN+ Backend

## [2024-04-21] - Production Microservices & Admin Bot Implementation

### ✅ Accomplished
- **Architecture Refactor**: Split the monolithic API into 6 specialized NestJS microservices (Gateway, Order, Inventory, Admin, VPN Engine, Store).
- **Communication Layer**: Implemented TCP transport between microservices using environment-based host discovery.
- **Docker Orchestration**: Finalized `docker-compose.yml` for multi-service deployment with health checks.
- **Database (Prisma)**:
    - Added `Server` model.
    - Added `public_id` (nanoid) and `device_id` to `Customer`.
    - Implemented atomic fulfillment transactions in `InventoryService`.
- **VPN Config Engine**:
    - Built a robust parser for VLESS (Reality, GRPC, WS) and Shadowsocks.
    - Exposed validation via TCP pattern `{ cmd: 'parse_config' }`.
- **Admin Control (Telegram)**:
    - Created `AdminBotService` using `Telegraf`.
    - Implemented secure middleware based on `ADMIN_CHAT_ID`.
    - Commands added: `/start`, `/status`, `/import`, `/add`, `/users`, `/manual_fulfill`.
    - Integrated real-time push notifications for **New Sales** and **Low Stock Alerts**.
- **Android Integration**:
    - Connected `MainActivity` to real trial logic.
    - Verified `MainViewModel` uses `subscriptionUrl` for VPN tunneling.
    - Added random error simulation in `SwimVpnService` to test UX robustness and "Red Tint" error state.

### 🛠 Technical Choices
- **Telegraf**: Chosen for its lightweight footprint and ease of integration with NestJS.
- **TCP Transport**: Selected for low-latency internal communication between microservices.
- **Nanoid**: Used for `public_id` to provide secure, non-sequential user identifiers.
- **Event-Driven Alerts**: Used NestJS `@EventPattern` to decouple fulfillment from notification logic.

### ⚠️ Issues Faced
- Git sync issues due to missing remote configuration in the local environment. (Resolved by manual identification of file paths).
- Dependency issues with `@types/telegraf` (Telegraf 4 includes native types).

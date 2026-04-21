# SWIMVPN+ Backend Monorepo

Production-ready NestJS microservices for VLESS/Xray management and tiered subscriptions.

## Architecture

| Service | Port | Type | Responsibility |
|---------|------|------|----------------|
| **Gateway** | 3000 | HTTP | Entry point, Swagger, Routing |
| **Customer-Order** | 3001 | TCP | User management & Order lifecycle |
| **Inventory-Delivery** | 3002 | TCP | Bulk import & Atomic fulfillment |
| **Admin-Control** | 3003 | TCP | Administrative dashboard logic |
| **VPN-Config-Engine** | 3004 | TCP | VLESS Parsing & Normalization |
| **Store-Engine** | 3005 | TCP | Plan catalog & Active offers |

## Shared Libraries

- `@app/database`: Prisma Client wrapper and global database service.
- `@app/contracts`: Shared Interfaces, DTOs, and Enums.

## Getting Started

1. **Install Dependencies**
   ```bash
   npm install
   ```

2. **Database Setup**
   ```bash
   # Update .env with your DATABASE_URL
   npx prisma generate
   # Run migrations (ensure nanoid extension is enabled in PG)
   npx prisma db push
   ```

3. **Run All Services (Development)**
   ```bash
   npm run start:all
   ```

4. **API Documentation**
   Available at `http://localhost:3000/docs` when the Gateway is running.

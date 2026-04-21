# SWIMVPN+ Production Deployment Guide

## Overview
This document describes the Docker-based deployment architecture for the SWIMVPN+ backend microservices stack. The configuration is designed for production use on a VPS with Docker and is compatible with Dockploy orchestration.

## Architecture Components

### Core Services
1. **Database Layer**
   - PostgreSQL 15-alpine (source of truth)
   - Redis 7-alpine (cache & message transport)

2. **Application Services** (NestJS Microservices)
   - `gateway-service` (HTTP API Gateway, port 3000)
   - `customer-order-service` (Customer & Order lifecycle)
   - `inventory-delivery-service` (Config inventory & fulfillment)
   - `admin-control-service` (Admin dashboard & Telegram integration)
   - `vpn-config-engine-service` (VPN config parsing & validation)
   - `store-engine-service` (Plan catalog & pricing)

3. **Optional Admin Tools**
   - pgAdmin (Database administration UI)

## File Structure for Deployment
```
SWIMVPN-/
├── docker-compose.yml          # Root compose file (this file)
├── .env.example               # Environment template
├── .env                       # Production secrets (gitignored)
├── backend/                   # Source code
│   ├── Dockerfile            # Build all microservices
│   ├── apps/                 # Microservice source code
│   ├── prisma/              # Database schema & migrations
│   └── package.json         # Dependencies
├── DEPLOYMENT_GUIDE.md       # This document
└── README_DEV.md            # Development instructions
```

## Prerequisites

### 1. VPS Requirements
- Ubuntu 20.04+ or Debian 11+
- Docker Engine 20.10+
- Docker Compose 2.0+
- Minimum 2GB RAM (4GB recommended)
- Minimum 20GB storage

### 2. Domain & SSL (Optional but recommended)
- Domain name pointing to VPS IP
- SSL certificates (Let's Encrypt via Traefik or Nginx)

## Quick Start Deployment

### Step 1: Clone and Configure
```bash
# Clone repository
git clone <repository-url> swimvpn
cd swimvpn

# Copy environment template
cp .env.example .env

# Edit environment variables
nano .env
```

### Step 2: Configure Environment Variables
Edit `.env` file with secure values:
```bash
# Generate secure passwords
POSTGRES_PASSWORD=$(openssl rand -base64 32)
REDIS_PASSWORD=$(openssl rand -base64 32)
JWT_SECRET=$(openssl rand -base64 64)
ADMIN_JWT_SECRET=$(openssl rand -base64 64)

# Set Telegram bot credentials
TELEGRAM_BOT_TOKEN=your_bot_token_here
ADMIN_CHAT_ID=your_chat_id_here
```

### Step 3: Build and Deploy
```bash
# Build all services
docker-compose build

# Start the stack
docker-compose up -d

# Check status
docker-compose ps

# View logs
docker-compose logs -f gateway-service
```

### Step 4: Database Initialization
```bash
# Apply Prisma migrations (runs automatically via Dockerfile CMD)
# Or manually if needed:
docker-compose exec gateway-service npx prisma db push
```

### Step 5: Verify Deployment
```bash
# Check all services are healthy
curl http://localhost:3000/health

# Check API documentation
curl http://localhost:3000/docs

# Check service-specific health endpoints
curl http://localhost:3001/health  # customer-order-service
curl http://localhost:3002/health  # inventory-delivery-service
# etc.
```

## Port Mapping Strategy

| Service | Container Port | Host Port | External Access | Purpose |
|---------|---------------|-----------|----------------|---------|
| gateway-service | 3000 | 3000 | Yes (Reverse Proxy) | Main API Gateway |
| PostgreSQL | 5432 | 5432 | No (Internal only) | Database |
| Redis | 6379 | 6379 | No (Internal only) | Cache |
| pgAdmin | 80 | 8080 | Optional (Admin only) | DB Management UI |

**Security Note**: Only expose gateway-service to the internet. Database and Redis should remain internal to Docker network.

## Docker Network Strategy

### Network Configuration
- **Network Name**: `swimvpn-network`
- **Subnet**: `172.20.0.0/16`
- **Driver**: bridge
- **Purpose**: Isolated internal communication between services

### Service Discovery
Services communicate using Docker's internal DNS:
- `gateway-service:3000`
- `db:5432` (PostgreSQL)
- `redis:6379`
- `customer-order-service:3001`
- etc.

## Volume Strategy

### Persistent Data Volumes
1. **swimvpn-pgdata**: PostgreSQL database files
2. **swimvpn-redisdata**: Redis AOF/RDB persistence

### Volume Configuration
```yaml
volumes:
  swimvpn-pgdata:
    name: swimvpn-pgdata  # Named volume for easier management
  swimvpn-redisdata:
    name: swimvpn-redisdata
```

### Backup Recommendations
```bash
# Backup PostgreSQL
docker-compose exec db pg_dump -U swim_user swimvpn_db > backup_$(date +%Y%m%d).sql

# Backup Redis
docker-compose exec redis redis-cli --rdb /data/dump.rdb
```

## Health Check Strategy

### Health Check Configuration
Each service has a `/health` endpoint with the following checks:
1. **Database connectivity** (where applicable)
2. **Redis connectivity** (where applicable)
3. **Service-specific health indicators**

### Health Check Parameters
- **Interval**: 30 seconds
- **Timeout**: 10 seconds
- **Retries**: 3
- **Start Period**: 40 seconds (allows for service initialization)

### Health Check Commands
```bash
# Manual health checks
curl http://localhost:3000/health
docker-compose ps  # Shows health status
```

## Service Dependencies and Startup Order

### Dependency Chain
```
PostgreSQL (db) → Redis → Gateway → Other Services
      ↓              ↓
  Health OK    Health OK
      ↓              ↓
┌─────────────────────────────────┐
│      Application Services       │
│  (start in parallel after db)   │
└─────────────────────────────────┘
```

### Compose Depends-On Configuration
```yaml
depends_on:
  db:
    condition: service_healthy  # Wait for PostgreSQL health check
  redis:
    condition: service_healthy  # Wait for Redis health check
```

## Production Considerations

### 1. Resource Limits
```yaml
deploy:
  resources:
    limits:
      memory: 512M
      cpus: '0.5'
    reservations:
      memory: 256M
      cpus: '0.25'
```

### 2. Restart Policies
```yaml
restart: unless-stopped  # Auto-restart on failure, but not on manual stop
```

### 3. Logging Strategy
```bash
# Configure log drivers in production
docker-compose.yml:
  logging:
    driver: "json-file"
    options:
      max-size: "10m"
      max-file: "3"
```

### 4. Monitoring
```bash
# Basic monitoring commands
docker stats                     # Resource usage
docker-compose logs --tail=100  # Recent logs
docker-compose events           # Real-time events
```

## Dockploy Compatibility

### Configuration Requirements
The `docker-compose.yml` is designed to be compatible with Dockploy by:
1. Using standard Docker Compose 3.8 syntax
2. Having clear service definitions with health checks
3. Using environment variables for configuration
4. Following Docker best practices

### Dockploy Deployment Steps
```bash
# Assuming Dockploy is installed and configured
dockploy init swimvpn
dockploy deploy --env .env
```

### Environment Variable Support
All sensitive configuration is via environment variables, making it easy to integrate with Dockploy's secret management.

## Security Best Practices

### 1. Secrets Management
- Never commit `.env` to version control
- Use different secrets for each environment
- Rotate JWT secrets periodically

### 2. Network Security
- Keep database and Redis internal to Docker network
- Use firewall to restrict external access
- Consider VPN for admin access to internal services

### 3. Container Security
- Run as non-root user (already configured in Dockerfile)
- Keep base images updated
- Scan images for vulnerabilities

## Troubleshooting

### Common Issues

#### 1. Database Connection Issues
```bash
# Check PostgreSQL logs
docker-compose logs db

# Test database connectivity
docker-compose exec db pg_isready -U swim_user
```

#### 2. Service Health Check Failures
```bash
# Check service logs
docker-compose logs gateway-service

# Manually test health endpoint
docker-compose exec gateway-service curl -f http://localhost:3000/health
```

#### 3. Build Failures
```bash
# Clear Docker cache and rebuild
docker-compose build --no-cache

# Check Node.js dependencies
docker-compose run --rm gateway-service npm list
```

#### 4. Memory Issues
```bash
# Check memory usage
docker stats

# Increase memory limits in .env
DOCKER_MEMORY_LIMIT=1G
```

## Scaling Considerations

### Vertical Scaling
Increase resource limits in `.env`:
```bash
DOCKER_MEMORY_LIMIT=1G
DOCKER_CPU_LIMIT=1.0
```

### Horizontal Scaling
The architecture supports scaling gateway-service:
```bash
# Scale gateway (example)
docker-compose up -d --scale gateway-service=3
```

### Database Scaling
For high-load scenarios:
1. Add PostgreSQL read replicas
2. Implement connection pooling
3. Add Redis cluster for distributed caching

## Maintenance Operations

### Regular Maintenance Tasks
```bash
# Update containers
docker-compose pull
docker-compose up -d

# Clean up old images
docker image prune -a

# Backup database
./scripts/backup.sh
```

### Disaster Recovery
```bash
# Restore from backup
cat backup.sql | docker-compose exec -T db psql -U swim_user swimvpn_db

# Recreate volumes (last resort)
docker-compose down -v
docker-compose up -d
```

## Support and Monitoring

### Monitoring Setup (Optional)
```bash
# Add Prometheus and Grafana
# See docker-compose.monitoring.yml (optional extension)
```

### Alerting
- Telegram bot notifications for critical events
- Health check failures trigger restart
- Low inventory alerts via Telegram

## Appendix

### A. Complete .env Example
```bash
# See .env.example for complete list
```

### B. Docker Commands Cheat Sheet
```bash
# Start services
docker-compose up -d

# Stop services
docker-compose down

# View logs
docker-compose logs -f [service-name]

# Execute commands in container
docker-compose exec [service-name] [command]

# Rebuild specific service
docker-compose build [service-name]

# Check service status
docker-compose ps
```

### C. Service Port Reference
- Gateway: 3000
- PostgreSQL: 5432
- Redis: 6379
- pgAdmin: 8080

### D. Health Check URLs
- Gateway: `http://localhost:3000/health`
- Customer Service: `http://localhost:3001/health`
- Inventory Service: `http://localhost:3002/health`
- Admin Service: `http://localhost:3003/health`
- VPN Engine: `http://localhost:3004/health`
- Store Service: `http://localhost:3005/health`
```

## License
SWIMVPN+ Deployment Configuration - Proprietary
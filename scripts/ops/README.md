# SWIMVPN+ Ops Scripts

Minimal server automation scripts for deployment and operations.

## Files
- `deploy.sh`: pull latest branch and redeploy compose.
- `prisma-rollout.sh`: production-safe Prisma rollout helper (backup, optional baseline, migrate, seed).
- `health-check.sh`: validate containers, migration status, API health, TLS issuer.
- `backup-db.sh`: create PostgreSQL dump from running `db` container.
- `restore-db.sh`: restore PostgreSQL dump with explicit confirmation.
- `incident-report.sh`: collect runtime diagnostics and recent logs.

## Quick start
```bash
cd /path/to/repo
chmod +x scripts/ops/*.sh
```

## Common env overrides
- `COMPOSE_DIR` (default: current directory)
- `COMPOSE_FILE` (default: `docker-compose.yml`)
- `COMPOSE_PROJECT_NAME` (optional; Dockploy project name)
- `ENV_FILE` (optional compose env file)

## Examples
```bash
# Deploy from main branch
scripts/ops/deploy.sh --compose-dir /etc/dokploy/compose/swimvpnapp-swimvpnbackend-d39yib/code --project swimvpnapp-swimvpnbackend-d39yib --branch main

# Health check
scripts/ops/health-check.sh --compose-dir /etc/dokploy/compose/swimvpnapp-swimvpnbackend-d39yib/code --project swimvpnapp-swimvpnbackend-d39yib

# Prisma rollout for an existing production database that needs one-time baselining
scripts/ops/prisma-rollout.sh --compose-dir /etc/dokploy/compose/swimvpnapp-swimvpnbackend-d39yib/code --project swimvpnapp-swimvpnbackend-d39yib --baseline

# Backup DB
scripts/ops/backup-db.sh --compose-dir /etc/dokploy/compose/swimvpnapp-swimvpnbackend-d39yib/code --project swimvpnapp-swimvpnbackend-d39yib

# Restore DB
scripts/ops/restore-db.sh /path/to/backup.dump --compose-dir /etc/dokploy/compose/swimvpnapp-swimvpnbackend-d39yib/code --project swimvpnapp-swimvpnbackend-d39yib

# Incident report
scripts/ops/incident-report.sh --compose-dir /etc/dokploy/compose/swimvpnapp-swimvpnbackend-d39yib/code --project swimvpnapp-swimvpnbackend-d39yib
```

## Production order of execution
Use this order on the real Dockploy host:
1. `git pull --ff-only origin main`
2. `docker compose -p swimvpnapp-swimvpnbackend-d39yib config > /dev/null`
3. First rollout on the old DB only:
   - `scripts/ops/prisma-rollout.sh --compose-dir /etc/dokploy/compose/swimvpnapp-swimvpnbackend-d39yib/code --project swimvpnapp-swimvpnbackend-d39yib --baseline`
4. Later normal rollouts:
   - `scripts/ops/prisma-rollout.sh --compose-dir /etc/dokploy/compose/swimvpnapp-swimvpnbackend-d39yib/code --project swimvpnapp-swimvpnbackend-d39yib`
5. `docker compose -p swimvpnapp-swimvpnbackend-d39yib up -d --build --remove-orphans`
6. `scripts/ops/health-check.sh --compose-dir /etc/dokploy/compose/swimvpnapp-swimvpnbackend-d39yib/code --project swimvpnapp-swimvpnbackend-d39yib`

## Safety notes
- `restore-db.sh` requires typing `RESTORE` before execution.
- `prisma-rollout.sh` should be run with `--baseline` only once for databases that existed before versioned Prisma migrations were introduced.
- Use URL-safe values for `POSTGRES_PASSWORD` to avoid DSN parsing issues.
- Keep dumps outside git-tracked directories when possible.

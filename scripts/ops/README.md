# SWIMVPN+ Ops Scripts

Minimal server automation scripts for deployment and operations.

## Files
- `deploy.sh`: pull latest branch and redeploy compose.
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

# Backup DB
scripts/ops/backup-db.sh --compose-dir /etc/dokploy/compose/swimvpnapp-swimvpnbackend-d39yib/code --project swimvpnapp-swimvpnbackend-d39yib

# Restore DB
scripts/ops/restore-db.sh /path/to/backup.dump --compose-dir /etc/dokploy/compose/swimvpnapp-swimvpnbackend-d39yib/code --project swimvpnapp-swimvpnbackend-d39yib

# Incident report
scripts/ops/incident-report.sh --compose-dir /etc/dokploy/compose/swimvpnapp-swimvpnbackend-d39yib/code --project swimvpnapp-swimvpnbackend-d39yib
```

## Safety notes
- `restore-db.sh` requires typing `RESTORE` before execution.
- Use URL-safe values for `POSTGRES_PASSWORD` to avoid DSN parsing issues.
- Keep dumps outside git-tracked directories when possible.

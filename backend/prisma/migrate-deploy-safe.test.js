const assert = require('node:assert/strict');
const { containsRecoverableFailedMigration } = require('./migrate-deploy-safe');

const migration = '20260502093000_drop_stale_unique_assignment_indexes';

assert.equal(
  containsRecoverableFailedMigration(
    `Error: P3009\nThe \`${migration}\` migration started at 2026-05-02 failed`,
    migration,
  ),
  true,
  'detects the known failed migration from Prisma P3009 output',
);

assert.equal(
  containsRecoverableFailedMigration(
    'Database schema is up to date. No pending migrations to apply.',
    migration,
  ),
  false,
  'does not recover when no migration failed',
);

assert.equal(
  containsRecoverableFailedMigration(
    'The `202604230001_init_schema` migration failed',
    migration,
  ),
  false,
  'does not recover unrelated failed migrations',
);

console.log('migrate deploy safe parser tests passed');

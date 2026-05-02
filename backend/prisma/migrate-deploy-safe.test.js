const assert = require('node:assert/strict');
const path = require('node:path');
const {
  containsRecoverableFailedMigration,
  resolvePrismaExecutable,
} = require('./migrate-deploy-safe');

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

assert.equal(
  resolvePrismaExecutable('linux'),
  path.join(process.cwd(), 'node_modules', '.bin', 'prisma'),
  'uses the local Prisma binary on Linux containers instead of npx',
);

assert.equal(
  resolvePrismaExecutable('win32'),
  path.join(process.cwd(), 'node_modules', '.bin', 'prisma.cmd'),
  'uses the local Prisma command on Windows',
);

console.log('migrate deploy safe parser tests passed');

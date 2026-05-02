const { spawnSync } = require('node:child_process');

const RECOVERABLE_ROLLED_BACK_MIGRATIONS = [
  '20260502093000_drop_stale_unique_assignment_indexes',
];

function containsRecoverableFailedMigration(output, migrationName) {
  const text = String(output || '');
  return text.includes(migrationName) && text.toLowerCase().includes('failed');
}

function runPrisma(args, stdio = 'inherit') {
  return spawnSync('npx', ['prisma', ...args], {
    cwd: process.cwd(),
    encoding: 'utf8',
    shell: process.platform === 'win32',
    stdio,
  });
}

function main() {
  const status = runPrisma(['migrate', 'status'], ['ignore', 'pipe', 'pipe']);
  const statusOutput = `${status.stdout || ''}${status.stderr || ''}`;

  if (statusOutput.trim().length > 0) {
    process.stdout.write(statusOutput);
    if (!statusOutput.endsWith('\n')) {
      process.stdout.write('\n');
    }
  }

  for (const migrationName of RECOVERABLE_ROLLED_BACK_MIGRATIONS) {
    if (!containsRecoverableFailedMigration(statusOutput, migrationName)) {
      continue;
    }

    console.log(
      `Recovering known failed migration ${migrationName} before prisma migrate deploy...`,
    );
    const resolve = runPrisma(['migrate', 'resolve', '--rolled-back', migrationName]);
    if (resolve.status !== 0) {
      process.exit(resolve.status || 1);
    }
  }

  const deploy = runPrisma(['migrate', 'deploy']);
  process.exit(deploy.status || 0);
}

if (require.main === module) {
  main();
}

module.exports = {
  containsRecoverableFailedMigration,
};

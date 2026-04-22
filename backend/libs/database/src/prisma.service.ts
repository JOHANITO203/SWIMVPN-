import { Injectable, OnModuleInit, INestApplication } from '@nestjs/common';
import { PrismaClient } from '@prisma/client';

@Injectable()
export class PrismaService extends PrismaClient implements OnModuleInit {
  async onModuleInit() {
    await this.$connect();
    if (process.env.PRISMA_VALIDATE_SCHEMA !== 'false') {
      await this.assertProductionSchemaCompatibility();
    }
  }

  async enableShutdownHooks(app: INestApplication) {
    this.$on('beforeExit' as never, async () => {
      await app.close();
    });
  }

  private async assertProductionSchemaCompatibility() {
    const requiredColumns = [
      ['Customer', 'public_id'],
      ['Customer', 'device_id'],
      ['Plan', 'active'],
      ['Plan', 'display_order'],
      ['Plan', 'price_rub'],
      ['Server', 'is_active'],
      ['Order', 'amount_rub'],
      ['OrderAssignment', 'fallback_quota_label'],
    ] as const;

    const rows = await this.$queryRaw<Array<{ table_name: string; column_name: string }>>`
      SELECT table_name, column_name
      FROM information_schema.columns
      WHERE table_schema = 'public'
        AND table_name IN ('Customer', 'Plan', 'Server', 'Order', 'OrderAssignment')
    `;

    const available = new Set(rows.map((row) => `${row.table_name}.${row.column_name}`));
    const missing = requiredColumns.filter(([table, column]) => !available.has(`${table}.${column}`));

    if (missing.length > 0) {
      const missingList = missing.map(([table, column]) => `${table}.${column}`).join(', ');
      throw new Error(
        `Prisma schema is not aligned with the running database. Missing columns: ${missingList}. ` +
          `Run 'npm run prisma:migrate:deploy' and baseline existing production databases before starting services.`,
      );
    }
  }
}

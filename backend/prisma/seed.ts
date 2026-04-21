import { PrismaClient, PlanCategory } from '@prisma/client';

const prisma = new PrismaClient();

async function main() {
  // 1. Seed Plans
  const plans = [
    {
      code: PlanCategory.WEEK,
      name: '7 Days Trial',
      duration_label: '7 Days',
      quota_label: '5 GB',
      price_rub: 0,
      display_order: 1,
      active: true,
    },
    {
      code: PlanCategory.MONTH,
      name: 'Standard Monthly',
      duration_label: '30 Days',
      quota_label: '100 GB',
      price_rub: 490,
      display_order: 2,
      active: true,
    },
    {
      code: PlanCategory.YEAR,
      name: 'Annual Premium',
      duration_label: '365 Days',
      quota_label: 'Unlimited',
      price_rub: 2900,
      display_order: 3,
      active: true,
    },
  ];

  for (const plan of plans) {
    await prisma.plan.upsert({
      where: { code: plan.code },
      update: plan,
      create: plan,
    });
  }

  // 2. Seed some Servers
  const servers = [
    { country_code: 'DE', name: 'Frankfurt-1', host: 'de1.swimvpn.net' },
    { country_code: 'US', name: 'New York-1', host: 'us1.swimvpn.net' },
    { country_code: 'NL', name: 'Amsterdam-1', host: 'nl1.swimvpn.net' },
  ];

  for (const server of servers) {
    await prisma.server.upsert({
      where: { id: server.name }, // Hack for seeding: using name as ID check
      update: server,
      create: server,
    });
  }

  // 3. Seed some Inventory (Dummy VLESS for Trial)
  await prisma.inventoryItem.create({
    data: {
      category: PlanCategory.WEEK,
      raw_config: 'vless://8e966870-7389-4e58-958b-083656c07525@de1.swimvpn.net:443?encryption=none&security=tls&type=grpc&serviceName=swimvpn-grpc#SwimVPN-Trial',
      config_type: 'VLESS',
      display_protocol: 'VLESS',
      batch_name: 'SEED-BATCH',
      status: 'AVAILABLE',
    },
  });

  console.log('Seed completed: Plans, Servers, and 1 Trial Config created.');
}

main()
  .catch((e) => {
    console.error(e);
    process.exit(1);
  })
  .finally(async () => {
    await prisma.$disconnect();
  });

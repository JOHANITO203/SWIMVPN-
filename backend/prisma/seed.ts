import { PrismaClient, PlanCategory } from '@prisma/client';

const prisma = new PrismaClient();

async function main() {
  // 1. Seed Plans
  const plans = [
    {
      code: PlanCategory.WEEK,
      name: 'Basic',
      duration_label: '7 Days',
      quota_label: '50 GB',
      slot_count: 1,
      price_rub: 299,
      display_order: 1,
      active: true,
    },
    {
      code: PlanCategory.MONTH,
      name: 'Premium',
      duration_label: '30 Days',
      quota_label: '150 GB',
      slot_count: 2,
      price_rub: 699,
      display_order: 2,
      active: true,
    },
    {
      code: PlanCategory.QUARTER,
      name: 'Platinum',
      duration_label: '90 Days',
      quota_label: '500 GB',
      slot_count: 4,
      price_rub: 1899,
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

  const existingServers = await prisma.server.count();
  if (existingServers === 0) {
    await prisma.server.createMany({
      data: servers,
    });
  }

  // 3. Seed some Inventory (starter WEEK-category inventory used by assignments)
  const seededConfig = await prisma.inventoryItem.findFirst({
    where: { batch_name: 'SEED-BATCH' },
  });
  if (!seededConfig) {
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
  }

  console.log('Seed completed: paid plans, servers, and starter WEEK inventory created.');
}

main()
  .catch((e) => {
    console.error(e);
    process.exit(1);
  })
  .finally(async () => {
    await prisma.$disconnect();
  });

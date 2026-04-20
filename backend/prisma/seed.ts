import { PrismaClient } from '@prisma/client'
const prisma = new PrismaClient()

async function main() {
  const plans = [
    {
      name: 'WEEKLY',
      durationDays: 7,
      devicesAllowed: 3,
      dataLimitGB: 50,
      features: ['50 GB Traffic', 'Bronze Badge', 'Shark Protocol'],
      isTrial: false
    },
    {
      name: 'MONTHLY',
      durationDays: 30,
      devicesAllowed: 3,
      dataLimitGB: 150,
      features: ['150 GB Traffic', 'Silver Badge', 'Shark Protocol'],
      isTrial: false
    },
    {
      name: 'QUARTERLY',
      durationDays: 90,
      devicesAllowed: 5,
      dataLimitGB: 500,
      features: ['500 GB Traffic', 'Gold Badge', 'Shark Protocol'],
      isTrial: false
    }
  ]

  for (const plan of plans) {
    await prisma.plan.upsert({
      where: { id: plan.name }, // On utilise le nom comme ID pour le seed simple
      update: plan,
      create: {
        id: plan.name,
        ...plan
      }
    })
  }

  console.log('Pricing plans seeded successfully')
}

main()
  .catch((e) => {
    console.error(e)
    process.exit(1)
  })
  .finally(async () => {
    await prisma.$disconnect()
  })

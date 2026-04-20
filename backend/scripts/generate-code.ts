import { PrismaClient } from '@prisma/client';

const prisma = new PrismaClient();

async function generateCode() {
  // Par défaut, on lie le code au plan Premium 1 Month si non spécifié
  const planName = process.argv[2] || 'Premium 1 Month';

  // Trouver le plan dans la base de données
  const plan = await prisma.plan.findFirst({
    where: { name: planName }
  });

  if (!plan) {
    console.error(`❌ Plan '${planName}' introuvable. Exécutez d'abord npx prisma db seed`);
    process.exit(1);
  }

  // Générer un code lisible (Mode secondaire) : SWIM-ABCD-1234
  const randomAlpha = () => {
    const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';
    return Array.from({length: 4}, () => chars[Math.floor(Math.random() * chars.length)]).join('');
  };
  const randomNum = () => {
    const nums = '0123456789';
    return Array.from({length: 4}, () => nums[Math.floor(Math.random() * nums.length)]).join('');
  };

  const code = `SWIM-${randomAlpha()}-${randomNum()}`;

  // Insérer en base de données comme non utilisé
  await prisma.activationCode.create({
    data: {
      code,
      planId: plan.id,
      isUsed: false,
      expiresAt: new Date(new Date().getTime() + 365 * 24 * 60 * 60 * 1000) // Ce code peut être réclamé pendant 1 an
    }
  });

  console.log(`\n✅ Code d'activation généré avec succès !`);
  console.log(`🎟️  Code : ${code}`);
  console.log(`📦 Plan associé : ${plan.name} (${plan.durationDays} jours)`);
  console.log(`➡️  Donnez ce code à l'utilisateur, il l'entrera dans l'app.\n`);
}

generateCode()
  .catch((e) => console.error(e))
  .finally(async () => await prisma.$disconnect());

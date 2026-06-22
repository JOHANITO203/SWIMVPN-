import { NotificationService } from '../notification.service';

function assert(condition: boolean, message: string) {
  if (!condition) {
    throw new Error(message);
  }
}

// NotificationService deps order: (prisma, templateService, telegramSender, emailSender, inventoryClient)
function makeService(prisma: any, email: any) {
  return new NotificationService(prisma, {} as any, {} as any, email, {} as any);
}

async function run() {
  // 1. Crée un abonné PENDING + token, email normalisé, confirmation envoyée.
  {
    const calls: { upsert: any[]; send: any[] } = { upsert: [], send: [] };
    const prisma = {
      subscriber: {
        findUnique: async () => null,
        upsert: async (arg: any) => {
          calls.upsert.push(arg);
          return { id: 's1' };
        },
      },
    };
    const email = {
      sendOptInConfirmation: async (...a: any[]) => {
        calls.send.push(a);
      },
    };
    const res: any = await makeService(prisma, email).subscribeEmail({
      email: '  Jayden@Example.COM ',
      locale: 'fr',
      source: 'cine-hero',
    });
    assert(res.status === 'pending', 'should be pending');
    assert(res.emailSent === true, 'email should be sent');
    const arg = calls.upsert[0];
    assert(arg.where.email === 'jayden@example.com', 'email must be normalized');
    assert(!!arg.create.confirm_token, 'confirm token must be set');
    assert(arg.create.source === 'cine-hero', 'source must be stored');
    assert(calls.send[0][0] === 'jayden@example.com', 'confirmation sent to normalized email');
    assert(String(calls.send[0][1]).includes('token='), 'confirm url carries token');
    assert(calls.send[0][2] === 'fr', 'locale forwarded to mailer');
  }

  // 2. Idempotent : email déjà CONFIRMED → aucun envoi.
  {
    let sent = 0;
    const prisma = {
      subscriber: {
        findUnique: async () => ({ id: 's1', status: 'CONFIRMED' }),
        upsert: async () => {
          throw new Error('should not upsert a confirmed subscriber');
        },
      },
    };
    const email = {
      sendOptInConfirmation: async () => {
        sent++;
      },
    };
    const res: any = await makeService(prisma, email).subscribeEmail({ email: 'x@y.com' });
    assert(res.status === 'already_confirmed', 'already confirmed status');
    assert(sent === 0, 'no email for an already-confirmed subscriber');
  }

  // 3. Échec mailer → reste PENDING, emailSent=false.
  {
    const prisma = {
      subscriber: { findUnique: async () => null, upsert: async () => ({ id: 's2' }) },
    };
    const email = {
      sendOptInConfirmation: async () => {
        throw new Error('no transport');
      },
    };
    const res: any = await makeService(prisma, email).subscribeEmail({ email: 'z@z.com' });
    assert(res.status === 'pending', 'pending despite mailer failure');
    assert(res.emailSent === false, 'emailSent must be false on mailer failure');
  }

  // 4. Confirmation par token.
  {
    const calls: any[] = [];
    const prisma = {
      subscriber: {
        findUnique: async () => ({ id: 's1', status: 'PENDING' }),
        update: async (a: any) => {
          calls.push(a);
          return {};
        },
      },
    };
    const res: any = await makeService(prisma, {}).confirmSubscription('tok-123');
    assert(res.status === 'confirmed', 'confirmed status');
    assert(calls[0].data.status === 'CONFIRMED', 'status set to CONFIRMED');
    assert(calls[0].data.confirm_token === null, 'token cleared after confirmation');
  }

  // 5. Token inconnu → invalid.
  {
    const prisma = { subscriber: { findUnique: async () => null } };
    const res: any = await makeService(prisma, {}).confirmSubscription('nope');
    assert(res.status === 'invalid', 'invalid for unknown token');
  }

  console.log('opt-in subscription tests passed');
}

run().catch((e) => {
  console.error(e);
  process.exit(1);
});

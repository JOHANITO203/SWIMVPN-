import { AdminService } from '../admin.service';
import { JwtService } from '@nestjs/jwt';
import * as bcrypt from 'bcryptjs';

function assert(condition: boolean, message: string) {
  if (!condition) {
    throw new Error(message);
  }
}

const sessions: any[] = [];
const admin = {
  id: 'admin-1',
  username: 'root',
  role: 'OWNER',
  active: true,
  password_hash: bcrypt.hashSync('correct-password', 4),
};

const prisma: any = {
  admin: {
    findUnique: async ({ where }: any) => {
      if (where.username === admin.username || where.id === admin.id) return admin;
      return null;
    },
  },
  adminSession: {
    updateMany: async ({ where, data }: any) => {
      let count = 0;
      for (const session of sessions) {
        const adminMatches = !where.admin_id || where.admin_id === session.admin_id;
        const tokenMatches = !where.refresh_token_hash || where.refresh_token_hash === session.refresh_token_hash;
        const activeMatches = where.revoked_at === undefined || session.revoked_at === where.revoked_at;
        if (adminMatches && tokenMatches && activeMatches) {
          Object.assign(session, data);
          count += 1;
        }
      }
      return { count };
    },
    create: async ({ data }: any) => {
      sessions.push({ ...data, revoked_at: null });
      return sessions[sessions.length - 1];
    },
    findFirst: async ({ where }: any) => {
      return sessions.find((session) => {
        return session.admin_id === where.admin_id &&
          session.refresh_token_hash === where.refresh_token_hash &&
          session.revoked_at === where.revoked_at &&
          session.expires_at > where.expires_at.gt;
      }) || null;
    },
  },
};

const service = new AdminService(
  prisma,
  new JwtService({ secret: 'test-secret', signOptions: { expiresIn: '7d' } }),
  {} as any,
);

async function main() {
  const login = await service.login({
    username: admin.username,
    password_plain: 'correct-password',
  });

  assert(typeof login.token === 'string' && login.token.length > 20, 'login must return a usable JWT');
  assert(sessions.length === 1, 'login must create one admin session');
  assert(
    sessions[0].refresh_token_hash !== login.token,
    'admin session storage must not contain the reusable plaintext JWT',
  );

  const validated = await service.validateToken(login.token);
  assert(validated?.id === admin.id, 'hashed admin session must validate the returned token');

  const logout = await service.logout(login.token);
  assert(logout.success === true, 'logout must revoke a hashed admin session');
}

main()
  .then(() => console.log('admin session security tests passed'))
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });

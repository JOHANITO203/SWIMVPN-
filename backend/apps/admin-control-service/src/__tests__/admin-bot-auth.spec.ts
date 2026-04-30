import { isAdminBotAuthorized, parseAdminUserIds } from '../admin-bot-auth';

function assert(condition: boolean, message: string) {
  if (!condition) {
    throw new Error(message);
  }
}

assert(
  parseAdminUserIds('123, 456 ,,789').join('|') === '123|456|789',
  'admin user id parsing failed',
);

assert(
  isAdminBotAuthorized({
    fromId: '123',
    adminUserIds: ['123'],
    adminChatId: '-1003912107958',
  }),
  'explicit ADMIN_USER_IDS must authorize admin operations',
);

assert(
  isAdminBotAuthorized({
    fromId: '123',
    adminChatId: '123',
  }),
  'personal ADMIN_CHAT_ID must authorize matching user id',
);

assert(
  !isAdminBotAuthorized({
    fromId: '123',
    chatId: '-1003912107958',
    adminChatId: '-1003912107958',
  }),
  'group ADMIN_CHAT_ID alone must not authorize private admin operations',
);

console.log('admin bot auth tests passed');

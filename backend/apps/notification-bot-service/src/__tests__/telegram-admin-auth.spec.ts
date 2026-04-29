import { isTelegramAdminContext } from '../telegram-admin-auth';

function assert(condition: boolean, message: string) {
  if (!condition) {
    throw new Error(message);
  }
}

assert(
  isTelegramAdminContext({
    fromId: '7161959711',
    adminChatId: '7161959711',
  }),
  'personal ADMIN_CHAT_ID must authorize matching Telegram user id',
);

assert(
  isTelegramAdminContext({
    fromId: '123',
    chatId: '-1003580609681',
    reviewChatId: '-1003580609681',
  }),
  'callbacks/messages inside configured review group must be authorized',
);

assert(
  isTelegramAdminContext({
    fromId: '123',
    chatId: '-1003912107958',
    adminChatId: '-1003912107958',
  }),
  'callbacks/messages inside configured admin group must be authorized',
);

assert(
  !isTelegramAdminContext({
    fromId: '123',
    chatId: '123',
    adminChatId: '-1003912107958',
    reviewChatId: '-1003580609681',
  }),
  'ordinary private user chat must not be authorized by group chat ids',
);

assert(
  isTelegramAdminContext({
    fromId: '999',
    chatId: '123',
    adminChatId: '-1003912107958',
    adminUserIds: ['999'],
  }),
  'ADMIN_USER_IDS must authorize explicit Telegram user ids',
);

console.log('telegram admin auth tests passed');

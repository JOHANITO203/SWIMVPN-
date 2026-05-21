import {
  selectNotificationCommandBotToken,
  selectNotificationSenderBotToken,
} from '../telegram-token-routing';

function assert(condition: boolean, message: string) {
  if (!condition) {
    throw new Error(message);
  }
}

assert(
  selectNotificationCommandBotToken({
    notificationBotToken: 'notification-token',
    telegramBotToken: 'admin-token',
  }) === 'notification-token',
  'notification command bot must prefer NOTIFICATION_BOT_TOKEN',
);

assert(
  selectNotificationCommandBotToken({
    telegramBotToken: 'admin-token',
  }) === 'admin-token',
  'notification command bot may fall back to TELEGRAM_BOT_TOKEN',
);

assert(
  selectNotificationCommandBotToken({
    notificationBotToken: '   ',
    telegramBotToken: null,
  }) === null,
  'notification command bot must return null when no token is configured',
);

assert(
  selectNotificationSenderBotToken({
    notificationBotToken: 'notification-token',
    telegramBotToken: 'admin-token',
  }) === 'notification-token',
  'notification sender must prefer NOTIFICATION_BOT_TOKEN',
);

assert(
  selectNotificationSenderBotToken({
    telegramBotToken: 'admin-token',
  }) === 'admin-token',
  'notification sender must fall back to TELEGRAM_BOT_TOKEN',
);

assert(
  selectNotificationSenderBotToken({
    notificationBotToken: '   ',
    telegramBotToken: null,
  }) === null,
  'notification sender must return null when no token is configured',
);

console.log('telegram token routing tests passed');

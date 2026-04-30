import {
  selectNotificationSenderBotToken,
  selectPaymentCommandBotToken,
} from '../telegram-token-routing';

function assert(condition: boolean, message: string) {
  if (!condition) {
    throw new Error(message);
  }
}

assert(
  selectPaymentCommandBotToken({
    paymentBotToken: 'payment-token',
    notificationBotToken: 'notification-token',
    telegramBotToken: 'admin-token',
  }) === 'payment-token',
  'payment command bot must prefer PAYMENT_BOT_TOKEN',
);

assert(
  selectPaymentCommandBotToken({
    notificationBotToken: 'notification-token',
    telegramBotToken: 'admin-token',
  }) === 'notification-token',
  'payment command bot must fall back to NOTIFICATION_BOT_TOKEN',
);

assert(
  selectPaymentCommandBotToken({
    telegramBotToken: 'admin-token',
  }) === null,
  'payment command bot must not listen on TELEGRAM_BOT_TOKEN',
);

assert(
  selectNotificationSenderBotToken({
    paymentBotToken: 'payment-token',
    notificationBotToken: 'notification-token',
    telegramBotToken: 'admin-token',
  }) === 'payment-token',
  'notification sender must prefer the same payment bot for inline callback ownership',
);

assert(
  selectNotificationSenderBotToken({
    notificationBotToken: 'notification-token',
    telegramBotToken: 'admin-token',
  }) === 'notification-token',
  'notification sender must fall back to NOTIFICATION_BOT_TOKEN',
);

assert(
  selectNotificationSenderBotToken({
    telegramBotToken: 'admin-token',
  }) === 'admin-token',
  'notification sender may fall back to TELEGRAM_BOT_TOKEN for legacy one-way alerts',
);

console.log('telegram token routing tests passed');

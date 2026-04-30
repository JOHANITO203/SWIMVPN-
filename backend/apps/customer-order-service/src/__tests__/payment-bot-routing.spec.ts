import {
  normalizeConfiguredPaymentBotUsername,
  selectManualPaymentBotToken,
} from '../payment-bot-routing';

function assert(condition: boolean, message: string) {
  if (!condition) {
    throw new Error(message);
  }
}

assert(
  selectManualPaymentBotToken({
    paymentBotToken: 'payment-token',
    notificationBotToken: 'notification-token',
  }) === 'payment-token',
  'payment bot token must be preferred for manual payment routing',
);

assert(
  selectManualPaymentBotToken({
    paymentBotToken: '',
    notificationBotToken: 'notification-token',
  }) === 'notification-token',
  'notification bot token must remain fallback for manual payment routing',
);

assert(
  normalizeConfiguredPaymentBotUsername('@SWIMVPNPAYBOT') === 'SWIMVPNPAYBOT',
  'payment bot username must drop leading @',
);

assert(
  normalizeConfiguredPaymentBotUsername('123456789:abcdefghijklmnopqrstuvwxyz') === null,
  'payment bot username field must not be treated as username when it contains a token',
);

console.log('payment bot routing tests passed');

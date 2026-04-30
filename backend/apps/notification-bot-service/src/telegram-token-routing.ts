export type TelegramTokenRoutingEnv = {
  paymentBotToken?: string | null;
  notificationBotToken?: string | null;
  telegramBotToken?: string | null;
};

export function selectPaymentCommandBotToken(env: TelegramTokenRoutingEnv) {
  return env.paymentBotToken?.trim() || env.notificationBotToken?.trim() || null;
}

export function selectNotificationSenderBotToken(env: TelegramTokenRoutingEnv) {
  return (
    env.paymentBotToken?.trim() ||
    env.notificationBotToken?.trim() ||
    env.telegramBotToken?.trim() ||
    null
  );
}

export type TelegramTokenRoutingEnv = {
  notificationBotToken?: string | null;
  telegramBotToken?: string | null;
};

export function selectNotificationCommandBotToken(env: TelegramTokenRoutingEnv) {
  return env.notificationBotToken?.trim() || env.telegramBotToken?.trim() || null;
}

export function selectNotificationSenderBotToken(env: TelegramTokenRoutingEnv) {
  return (
    env.notificationBotToken?.trim() ||
    env.telegramBotToken?.trim() ||
    null
  );
}

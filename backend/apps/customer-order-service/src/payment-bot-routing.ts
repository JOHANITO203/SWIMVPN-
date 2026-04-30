export type PaymentBotRoutingEnv = {
  paymentBotUsername?: string | null;
  paymentBotToken?: string | null;
  notificationBotToken?: string | null;
};

export function looksLikeTelegramBotToken(value: string) {
  return /^\d+:[A-Za-z0-9_-]{20,}$/.test(value.trim());
}

export function normalizeConfiguredPaymentBotUsername(value?: string | null) {
  const normalized = value?.trim();
  if (!normalized || looksLikeTelegramBotToken(normalized)) {
    return null;
  }

  return normalized.replace(/^@/, '');
}

export function selectManualPaymentBotToken(env: PaymentBotRoutingEnv) {
  return env.paymentBotToken?.trim() || env.notificationBotToken?.trim() || null;
}

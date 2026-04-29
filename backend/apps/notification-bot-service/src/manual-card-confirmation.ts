export type ManualPaymentConfirmation = {
  email?: string;
  phone?: string;
  senderPhone?: string;
};

export type ManualPaymentReviewTextInput = {
  orderRef: string;
  proofEventId: string;
  telegramUsername?: string | null;
  telegramUserId: string;
  confirmationText: string;
  parsed: ManualPaymentConfirmation;
};

export function parseManualPaymentConfirmation(text: string): ManualPaymentConfirmation {
  const lines = text
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean);
  const normalizedText = lines.join('\n');
  const email = normalizedText.match(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i)?.[0]?.toLowerCase();
  const phones = lines
    .map((line) => line.replace(/[^\d+]/g, ''))
    .filter((value) => value.replace(/[^\d]/g, '').length >= 7);

  return {
    email,
    phone: phones[0],
    senderPhone: phones[1] || phones[0],
  };
}

export function buildManualPaymentContactReviewText(input: ManualPaymentReviewTextInput) {
  return [
    'SWIMVPN+ CARD PAYMENT FINAL REVIEW',
    `Order: ${input.orderRef}`,
    `Proof event: ${input.proofEventId}`,
    `Telegram: @${input.telegramUsername || '-'} (${input.telegramUserId})`,
    '',
    `Final email: ${input.parsed.email || '-'}`,
    `Final phone: ${input.parsed.phone || '-'}`,
    `Sender payment phone: ${input.parsed.senderPhone || '-'}`,
    '',
    'Raw confirmation:',
    input.confirmationText.slice(0, 2000),
  ].join('\n');
}

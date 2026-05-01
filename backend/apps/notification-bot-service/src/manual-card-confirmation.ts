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

const EMAIL_PATTERN = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i;

const LABEL_BOUNDARY_PATTERN =
  /\b(?:sender\s*(?:payment\s*)?(?:phone|tel|number)|payment\s*(?:phone|tel|number)|sender|from|email|e-mail|mail|courriel|phone|tel|telephone|number|numero|contact|whatsapp|client)\b\s*[:=\-]?|(?:expediteur|expéditeur|numero\s*expediteur|numéro\s*expéditeur|téléphone|telephone|numéro|numero|tél|tel)\s*[:=\-]?|(?:почта|мейл|телефон\s*оплаты|с\s*телефона\s*оплаты|номер|телефон|тел|отправитель|плательщик)\s*[:=\-]?/gi;

const SENDER_LABEL_PATTERN =
  /\b(?:sender|from|payment\s*(?:phone|tel|number)|sender\s*(?:payment\s*)?(?:phone|tel|number))\b|(?:expediteur|expéditeur|numero\s*expediteur|numéro\s*expéditeur)|(?:телефон\s*оплаты|с\s*телефона\s*оплаты|отправитель|плательщик)/i;

const PHONE_LABEL_PATTERN =
  /\b(?:phone|tel|telephone|number|numero|contact|whatsapp|client)\b|(?:téléphone|numéro|tél)|(?:номер|телефон|тел)/i;

export function parseManualPaymentConfirmation(text: string): ManualPaymentConfirmation {
  const normalizedText = normalizeConfirmationText(text);
  const email = normalizedText.match(EMAIL_PATTERN)?.[0]?.toLowerCase();
  const segments = splitConfirmationSegments(normalizedText);
  const allPhones = uniquePhones(extractPhones(normalizedText));

  let phone: string | undefined;
  let senderPhone: string | undefined;

  for (const segment of segments) {
    const segmentPhones = extractPhones(segment);
    if (segmentPhones.length === 0) continue;

    if (!senderPhone && SENDER_LABEL_PATTERN.test(segment)) {
      senderPhone = segmentPhones[0];
      continue;
    }

    if (!phone && PHONE_LABEL_PATTERN.test(segment)) {
      phone = segmentPhones[0];
    }
  }

  if (!phone) {
    phone = allPhones.find((value) => !senderPhone || !samePhone(value, senderPhone)) || allPhones[0];
  }

  if (!senderPhone) {
    senderPhone = allPhones.find((value) => phone && !samePhone(value, phone)) || phone;
  }

  return {
    email,
    phone,
    senderPhone,
  };
}

function normalizeConfirmationText(text: string) {
  return text
    .replace(/\u00a0/g, ' ')
    .replace(/[ \t]+/g, ' ')
    .trim();
}

function splitConfirmationSegments(text: string) {
  return text
    .replace(LABEL_BOUNDARY_PATTERN, '\n$&')
    .split(/[\r\n;|]+/)
    .map((segment) => segment.trim())
    .filter(Boolean);
}

function extractPhones(text: string) {
  const matches = text.match(/(?:\+?\d[\d \t().-]{5,}\d)/g) || [];
  return uniquePhones(matches.map(normalizePhone).filter((value): value is string => Boolean(value)));
}

function normalizePhone(value: string) {
  const trimmed = value.trim();
  const hasLeadingPlus = trimmed.startsWith('+');
  const digits = trimmed.replace(/\D/g, '');
  if (digits.length < 7) return undefined;
  return `${hasLeadingPlus ? '+' : ''}${digits}`;
}

function uniquePhones(values: string[]) {
  const seen = new Set<string>();
  const unique: string[] = [];
  for (const value of values) {
    const key = phoneKey(value);
    if (seen.has(key)) continue;
    seen.add(key);
    unique.push(value);
  }
  return unique;
}

function samePhone(left: string, right: string) {
  return phoneKey(left) === phoneKey(right);
}

function phoneKey(value: string) {
  return value.replace(/\D/g, '');
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

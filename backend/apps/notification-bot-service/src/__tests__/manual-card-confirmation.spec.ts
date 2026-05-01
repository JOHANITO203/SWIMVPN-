import {
  buildManualPaymentContactReviewText,
  parseManualPaymentConfirmation,
} from '../manual-card-confirmation';

function assert(condition: boolean, message: string) {
  if (!condition) {
    throw new Error(message);
  }
}

function assertParsed(
  label: string,
  text: string,
  expected: { email?: string; phone?: string; senderPhone?: string },
) {
  const parsed = parseManualPaymentConfirmation(text);
  assert(parsed.email === expected.email, `${label}: email mismatch (${parsed.email})`);
  assert(parsed.phone === expected.phone, `${label}: phone mismatch (${parsed.phone})`);
  assert(
    parsed.senderPhone === expected.senderPhone,
    `${label}: sender phone mismatch (${parsed.senderPhone})`,
  );
  return parsed;
}

const parsed = assertParsed(
  'plain three lines',
  [
    'Johaneoyaraht@gmail.com',
    '79507704623',
    '79507704623',
  ].join('\n'),
  {
    email: 'johaneoyaraht@gmail.com',
    phone: '79507704623',
    senderPhone: '79507704623',
  },
);

assertParsed(
  'english labels on one line',
  'Email: Customer@Example.COM Phone: +7 950 770-46-23 Sender phone: 7 (950) 770-46-24',
  {
    email: 'customer@example.com',
    phone: '+79507704623',
    senderPhone: '79507704624',
  },
);

assertParsed(
  'french labels',
  [
    'Courriel client: client@example.com',
    'Téléphone client : 07 50 77 04 623',
    'Numéro expéditeur: +7 950 770 46 24',
  ].join('\n'),
  {
    email: 'client@example.com',
    phone: '07507704623',
    senderPhone: '+79507704624',
  },
);

assertParsed(
  'russian labels',
  [
    'Почта: user@example.com',
    'Телефон: +7 950 770-46-23',
    'С телефона оплаты: 79507704624',
  ].join('\n'),
  {
    email: 'user@example.com',
    phone: '+79507704623',
    senderPhone: '79507704624',
  },
);

assertParsed(
  'unlabeled values with punctuation',
  'user@example.com / 79507704623 / sender 79507704624',
  {
    email: 'user@example.com',
    phone: '79507704623',
    senderPhone: '79507704624',
  },
);

assertParsed(
  'missing sender phone falls back to customer phone',
  'mail: user@example.com; phone: 79507704623',
  {
    email: 'user@example.com',
    phone: '79507704623',
    senderPhone: '79507704623',
  },
);

const review = buildManualPaymentContactReviewText({
  orderRef: 'ORD-123',
  proofEventId: 'proof-1',
  telegramUsername: 'customer',
  telegramUserId: '7161959711',
  confirmationText: 'Johaneoyaraht@gmail.com\n79507704623\n79507704623',
  parsed,
});

assert(review.includes('SWIMVPN+ CARD PAYMENT FINAL REVIEW'), 'review title missing');
assert(review.includes('Order: ORD-123'), 'order missing');
assert(review.includes('Proof event: proof-1'), 'proof event missing');
assert(review.includes('Final email: johaneoyaraht@gmail.com'), 'final email missing');
assert(review.includes('Final phone: 79507704623'), 'final phone missing');
assert(review.includes('Sender payment phone: 79507704623'), 'sender phone missing');

console.log('manual card confirmation tests passed');

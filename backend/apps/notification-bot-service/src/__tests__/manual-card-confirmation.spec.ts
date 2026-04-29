import {
  buildManualPaymentContactReviewText,
  parseManualPaymentConfirmation,
} from '../manual-card-confirmation';

function assert(condition: boolean, message: string) {
  if (!condition) {
    throw new Error(message);
  }
}

const parsed = parseManualPaymentConfirmation([
  'Johaneoyaraht@gmail.com',
  '79507704623',
  '79507704623',
].join('\n'));

assert(parsed.email === 'johaneoyaraht@gmail.com', 'email must be normalized from first line');
assert(parsed.phone === '79507704623', 'phone must be parsed from second line');
assert(parsed.senderPhone === '79507704623', 'sender phone must be parsed from third line');

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

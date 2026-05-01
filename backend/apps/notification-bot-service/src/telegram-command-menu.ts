export const NOTIFICATION_BOT_COMMANDS = [
  { command: 'help', description: 'Show payment/order bot help' },
  { command: 'whoami', description: 'Show your Telegram ids' },
  { command: 'order', description: 'Admin: show order delivery status' },
  { command: 'status', description: 'Admin: show delivery status' },
  { command: 'resend', description: 'Admin: resend delivery email' },
  { command: 'pending_cards', description: 'Admin: list pending manual card orders' },
  { command: 'trace_card', description: 'Admin: trace card proof output chain' },
  { command: 'review_card', description: 'Admin: resend stored card proof' },
  { command: 'approve_card', description: 'Admin: approve card proof by order' },
  { command: 'reject_card', description: 'Admin: reject card proof by order' },
];

export function formatTelegramCommandHelp() {
  return [
    '/order SW12345',
    '/status SW12345',
    '/resend SW12345',
    '/pending_cards',
    '/trace_card ORD-...',
    '/review_card ORD-...',
    '/approve_card ORD-...',
    '/reject_card ORD-...',
    '/whoami',
    '/help',
  ].join('\n');
}

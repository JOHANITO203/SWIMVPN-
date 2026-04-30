export const NOTIFICATION_BOT_COMMANDS = [
  { command: 'help', description: 'Show payment/order bot help' },
  { command: 'whoami', description: 'Show your Telegram ids' },
  { command: 'order', description: 'Admin: show order delivery status' },
  { command: 'status', description: 'Admin: show delivery status' },
  { command: 'resend', description: 'Admin: resend delivery email' },
];

export function formatTelegramCommandHelp() {
  return [
    '/order SW12345',
    '/status SW12345',
    '/resend SW12345',
    '/whoami',
    '/help',
  ].join('\n');
}

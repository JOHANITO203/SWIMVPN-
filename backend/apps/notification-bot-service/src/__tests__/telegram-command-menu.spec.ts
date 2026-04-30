import { NOTIFICATION_BOT_COMMANDS, formatTelegramCommandHelp } from '../telegram-command-menu';

function assert(condition: boolean, message: string) {
  if (!condition) {
    throw new Error(message);
  }
}

assert(
  NOTIFICATION_BOT_COMMANDS.some((command) => command.command === 'help'),
  'notification bot command menu must expose help',
);
assert(
  NOTIFICATION_BOT_COMMANDS.some((command) => command.command === 'status'),
  'notification bot command menu must expose status',
);
assert(
  NOTIFICATION_BOT_COMMANDS.some((command) => command.command === 'resend'),
  'notification bot command menu must expose resend',
);
assert(
  NOTIFICATION_BOT_COMMANDS.some((command) => command.command === 'whoami'),
  'notification bot command menu must expose whoami',
);
assert(
  NOTIFICATION_BOT_COMMANDS.every((command) => /^[a-z0-9_]{1,32}$/.test(command.command)),
  'notification bot commands must use valid Telegram command names',
);
assert(
  NOTIFICATION_BOT_COMMANDS.every((command) => command.description.length > 0 && command.description.length <= 256),
  'notification bot commands must have valid descriptions',
);
assert(formatTelegramCommandHelp().includes('/order SW12345'), 'notification help must include order usage');
assert(formatTelegramCommandHelp().includes('/whoami'), 'notification help must include whoami usage');

console.log('telegram command menu tests passed');

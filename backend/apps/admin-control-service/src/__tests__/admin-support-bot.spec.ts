import {
  ADMIN_SUPPORT_BOT_COMMANDS,
  buildTicketId,
  extractOptionalFields,
  formatAdminSupportReportMessage,
  formatEscalationRelayMessage,
} from '../admin-support-bot.formatter';
import { SUPPORT_TOPICS, resolveSupportLanguage } from '../admin-support-bot.templates';

function assert(condition: boolean, message: string) {
  if (!condition) {
    throw new Error(message);
  }
}

const topic = SUPPORT_TOPICS[0];
const message = formatEscalationRelayMessage({
  ticketId: 'SUP-20260422-AAAAAA',
  topic,
  userMessage: 'Cannot import link, order SW12345, email user@example.com',
  timestampIso: '2026-04-22T12:00:00.000Z',
  language: 'en',
  email: 'user@example.com',
  orderRef: 'SW12345',
  telegramUserId: '123456',
});
const report = formatAdminSupportReportMessage({
  ticketId: 'SUP-20260422-AAAAAA',
  topic,
  userMessage: 'Cannot import link, order SW12345, email user@example.com',
  timestampIso: '2026-04-22T12:00:00.000Z',
  language: 'en',
  email: 'user@example.com',
  orderRef: 'SW12345',
  telegramUserId: '123456',
});

assert(message.includes('SUP-20260422-AAAAAA'), 'ticket formatting failed');
assert(report.includes('SWIMVPN+ SUPPORT REPORT'), 'report formatting failed');
assert(resolveSupportLanguage(undefined) === 'ru', 'default language should be ru');
assert(resolveSupportLanguage('en') === 'en', 'en language resolution failed');

const fields = extractOptionalFields('email user@example.com phone +79990001122 order SW12345');
assert(fields.email === 'user@example.com', 'email extraction failed');
assert((fields.phone || '').includes('+79990001122'), 'phone extraction failed');
assert((fields.orderRef || '').toUpperCase().includes('SW12345'), 'orderRef extraction failed');

const ticket = buildTicketId(new Date('2026-04-22T00:00:00.000Z'));
assert(ticket.startsWith('SUP-20260422-'), 'ticket prefix failed');

assert(
  ADMIN_SUPPORT_BOT_COMMANDS.some((command) => command.command === 'start'),
  'support bot command menu must expose start',
);
assert(
  ADMIN_SUPPORT_BOT_COMMANDS.some((command) => command.command === 'help'),
  'support bot command menu must expose help',
);
assert(
  ADMIN_SUPPORT_BOT_COMMANDS.some((command) => command.command === 'whoami'),
  'support bot command menu must expose whoami',
);
assert(
  ADMIN_SUPPORT_BOT_COMMANDS.every((command) => /^[a-z0-9_]{1,32}$/.test(command.command)),
  'support bot commands must use valid Telegram command names',
);
assert(
  ADMIN_SUPPORT_BOT_COMMANDS.every((command) => command.description.length > 0 && command.description.length <= 256),
  'support bot commands must have valid descriptions',
);

console.log('admin support bot tests passed');

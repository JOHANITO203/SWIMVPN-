import { SupportLanguage, SupportTopic } from './admin-support-bot.templates';

export const ADMIN_SUPPORT_BOT_COMMANDS = [
  { command: 'start', description: 'Open the support menu' },
  { command: 'help', description: 'Show support help' },
  { command: 'language', description: 'Change support language' },
  { command: 'whoami', description: 'Show your Telegram ids' },
];

export interface EscalationRelayInput {
  ticketId: string;
  topic: SupportTopic;
  userMessage: string;
  timestampIso: string;
  language: SupportLanguage;
  email?: string;
  phone?: string;
  orderRef?: string;
  telegramUserId: string;
  telegramUsername?: string;
}

export function formatEscalationRelayMessage(input: EscalationRelayInput): string {
  return [
    'SWIMVPN+ SUPPORT ESCALATION',
    `Ticket: ${input.ticketId}`,
    `Time: ${input.timestampIso}`,
    `Language: ${input.language}`,
    `Topic: ${input.language === 'ru' ? input.topic.labelRu : input.topic.labelEn}`,
    `TelegramUserId: ${input.telegramUserId}`,
    `TelegramUsername: ${input.telegramUsername || '-'}`,
    `Email: ${input.email || '-'}`,
    `Phone: ${input.phone || '-'}`,
    `OrderRef: ${input.orderRef || '-'}`,
    'Message:',
    input.userMessage,
  ].join('\n');
}

export function formatAdminSupportReportMessage(input: EscalationRelayInput): string {
  return [
    'SWIMVPN+ SUPPORT REPORT',
    `Ticket: ${input.ticketId}`,
    `Topic: ${input.language === 'ru' ? input.topic.labelRu : input.topic.labelEn}`,
    `Email: ${input.email || '-'}`,
    `Language: ${input.language}`,
    `UserId: ${input.telegramUserId}`,
    `Username: ${input.telegramUsername || '-'}`,
    `Time: ${input.timestampIso}`,
    `Summary: ${input.userMessage.slice(0, 220)}`,
  ].join('\n');
}

export function buildTicketId(now: Date = new Date()): string {
  const datePart = now.toISOString().slice(0, 10).replace(/-/g, '');
  const randomPart = Math.random().toString(36).slice(2, 8).toUpperCase();
  return `SUP-${datePart}-${randomPart}`;
}

export function extractOptionalFields(text: string): { email?: string; phone?: string; orderRef?: string } {
  const emailMatch = text.match(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i);
  const phoneMatch = text.match(/\+?[0-9][0-9\-\s()]{7,}/);
  const explicitOrderRefMatch = text.match(/\border\s*[:#-]?\s*([A-Z0-9-]{4,})\b/i);
  const genericOrderRefMatch = text.match(/\b[A-Z]{2,}[A-Z0-9-]*\d+[A-Z0-9-]*\b/i);
  const orderRef = explicitOrderRefMatch?.[1] || genericOrderRefMatch?.[0];

  return {
    email: emailMatch?.[0],
    phone: phoneMatch?.[0],
    orderRef,
  };
}

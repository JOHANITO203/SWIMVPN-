export type TelegramAdminAuthInput = {
  fromId?: string | number | null;
  chatId?: string | number | null;
  messageChatId?: string | number | null;
  adminChatId?: string | null;
  reviewChatId?: string | null;
  adminUserIds?: Array<string | number>;
};

export function normalizeTelegramId(value?: string | number | null) {
  if (value === undefined || value === null) return undefined;
  const normalized = value.toString().trim().replace(/^['"]|['"]$/g, '');
  const match = normalized.match(/^-?\d+$/);
  return match ? match[0] : undefined;
}

function isGroupChatId(value?: string) {
  return !!value && value.startsWith('-100');
}

export function parseAdminUserIds(value?: string | null) {
  return (value || '')
    .split(/[,\s;]+/)
    .map((item) => normalizeTelegramId(item))
    .filter(Boolean);
}

export function isTelegramAdminContext(input: TelegramAdminAuthInput) {
  const fromId = normalizeTelegramId(input.fromId);
  const chatId = normalizeTelegramId(input.chatId);
  const messageChatId = normalizeTelegramId(input.messageChatId);
  const adminChatId = normalizeTelegramId(input.adminChatId);
  const reviewChatId = normalizeTelegramId(input.reviewChatId);
  const adminUserIds = (input.adminUserIds || []).map((item) => normalizeTelegramId(item)).filter(Boolean);

  if (fromId && adminUserIds.includes(fromId)) {
    return true;
  }

  if (fromId && adminChatId && !isGroupChatId(adminChatId) && fromId === adminChatId) {
    return true;
  }

  const allowedGroupChatIds = [reviewChatId, isGroupChatId(adminChatId) ? adminChatId : undefined]
    .filter(Boolean);
  const contextChatIds = [chatId, messageChatId].filter(Boolean);

  return contextChatIds.some((id) => allowedGroupChatIds.includes(id));
}

export type TelegramAdminAuthInput = {
  fromId?: string | number | null;
  chatId?: string | number | null;
  messageChatId?: string | number | null;
  adminChatId?: string | null;
  reviewChatId?: string | null;
  adminUserIds?: Array<string | number>;
};

function normalizeId(value?: string | number | null) {
  return value === undefined || value === null ? undefined : value.toString().trim();
}

function isGroupChatId(value?: string) {
  return !!value && value.startsWith('-100');
}

export function parseAdminUserIds(value?: string | null) {
  return (value || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);
}

export function isTelegramAdminContext(input: TelegramAdminAuthInput) {
  const fromId = normalizeId(input.fromId);
  const chatId = normalizeId(input.chatId);
  const messageChatId = normalizeId(input.messageChatId);
  const adminChatId = normalizeId(input.adminChatId);
  const reviewChatId = normalizeId(input.reviewChatId);
  const adminUserIds = (input.adminUserIds || []).map((item) => normalizeId(item)).filter(Boolean);

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

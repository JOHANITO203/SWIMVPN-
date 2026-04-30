export type AdminBotAuthInput = {
  fromId?: string | number | null;
  chatId?: string | number | null;
  adminChatId?: string | null;
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

export function isAdminBotAuthorized(input: AdminBotAuthInput) {
  const fromId = normalizeTelegramId(input.fromId);
  const adminChatId = normalizeTelegramId(input.adminChatId);
  const adminUserIds = (input.adminUserIds || [])
    .map((item) => normalizeTelegramId(item))
    .filter(Boolean);

  if (fromId && adminUserIds.includes(fromId)) {
    return true;
  }

  return !!fromId && !!adminChatId && !isGroupChatId(adminChatId) && fromId === adminChatId;
}

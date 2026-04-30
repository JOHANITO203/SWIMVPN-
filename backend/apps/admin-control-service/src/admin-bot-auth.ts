export type AdminBotAuthInput = {
  fromId?: string | number | null;
  chatId?: string | number | null;
  adminChatId?: string | null;
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

export function isAdminBotAuthorized(input: AdminBotAuthInput) {
  const fromId = normalizeId(input.fromId);
  const adminChatId = normalizeId(input.adminChatId);
  const adminUserIds = (input.adminUserIds || [])
    .map((item) => normalizeId(item))
    .filter(Boolean);

  if (fromId && adminUserIds.includes(fromId)) {
    return true;
  }

  return !!fromId && !!adminChatId && !isGroupChatId(adminChatId) && fromId === adminChatId;
}

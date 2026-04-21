export type SupportLanguage = 'ru' | 'en';

export type SupportTopicKey =
  | 'import_link'
  | 'vpn_not_connecting'
  | 'connected_no_internet'
  | 'slow_speed'
  | 'link_expired'
  | 'how_to_use_link'
  | 'need_support';

export interface SupportTopic {
  key: SupportTopicKey;
  labelRu: string;
  labelEn: string;
  answerRu: string;
  answerEn: string;
}

export const SUPPORT_TOPICS: SupportTopic[] = [
  {
    key: 'import_link',
    labelRu: 'Не удается импортировать VPN-ссылку',
    labelEn: 'Cannot import VPN link',
    answerRu:
      'Проверьте, что ссылка начинается с vless://, vmess://, trojan:// или ss://. Затем откройте SWIMVPN+, раздел Импорт, вставьте ссылку и сохраните.',
    answerEn:
      'Check that the link starts with vless://, vmess://, trojan:// or ss://. Then open SWIMVPN+, go to Import, paste the link and save.',
  },
  {
    key: 'vpn_not_connecting',
    labelRu: 'VPN не подключается',
    labelEn: 'VPN does not connect',
    answerRu:
      'Перезапустите приложение, переключите сеть (Wi-Fi/мобильная), затем попробуйте снова. Проверьте, что в профиле выбран активный сервер.',
    answerEn:
      'Restart the app, switch network (Wi-Fi/mobile), then try again. Ensure an active server is selected in your profile.',
  },
  {
    key: 'connected_no_internet',
    labelRu: 'VPN подключается, но интернет не работает',
    labelEn: 'VPN connects but internet does not work',
    answerRu:
      'Отключите и снова включите VPN, смените сервер и проверьте DNS/маршрутизацию в настройках приложения. Обычно помогает смена сети.',
    answerEn:
      'Disconnect and reconnect VPN, switch server, and check DNS/routing settings in the app. Network switch usually helps.',
  },
  {
    key: 'slow_speed',
    labelRu: 'Низкая скорость',
    labelEn: 'Slow speed',
    answerRu:
      'Попробуйте другой сервер и другую сеть. Закройте фоновые загрузки. На мобильной сети скорость может быть ниже в часы пик.',
    answerEn:
      'Try another server and another network. Stop background downloads. Mobile speed may be lower during peak hours.',
  },
  {
    key: 'link_expired',
    labelRu: 'Ссылка не работает / доступ истек',
    labelEn: 'Link not working / access expired',
    answerRu:
      'Проверьте срок действия тарифа и корректность ссылки. Если срок истек или ссылка повреждена, обратитесь в поддержку для проверки заказа.',
    answerEn:
      'Check plan expiry and link integrity. If access expired or link is broken, contact support for order verification.',
  },
  {
    key: 'how_to_use_link',
    labelRu: 'Как пользоваться VPN-ссылкой в приложении',
    labelEn: 'How to use the VPN link in the app',
    answerRu:
      'Откройте SWIMVPN+ -> Добавить конфигурацию/Импорт -> вставьте VPN-ссылку -> сохраните -> нажмите Подключиться.',
    answerEn:
      'Open SWIMVPN+ -> Add Configuration/Import -> paste VPN link -> save -> tap Connect.',
  },
  {
    key: 'need_support',
    labelRu: 'Нужна помощь поддержки',
    labelEn: 'I need support',
    answerRu:
      'Мы можем передать ваш запрос в поддержку. Нажмите Связаться с поддержкой и кратко опишите проблему.',
    answerEn:
      'We can escalate your request to support. Tap Contact support and briefly describe the issue.',
  },
];

export const SUPPORT_COPY = {
  ru: {
    welcome: 'Выберите язык и категорию проблемы. Я помогу короткими инструкциями и при необходимости передам запрос в поддержку.',
    chooseTopic: 'Выберите проблему:',
    unresolved: 'Проблема не решена',
    contact: 'Связаться с поддержкой',
    askMessage: 'Опишите проблему одним коротким сообщением (до 500 символов).',
    sent: (ticketId: string) => `Запрос передан в поддержку. Номер: ${ticketId}. При необходимости поддержка продолжит по email.`,
    rateLimited: 'Слишком много запросов за короткое время. Попробуйте позже.',
    invalidMessage: 'Нужно короткое текстовое сообщение (1-500 символов).',
  },
  en: {
    welcome: 'Select language and issue category. I provide short guidance and can escalate to support if needed.',
    chooseTopic: 'Choose your issue:',
    unresolved: 'Issue not resolved',
    contact: 'Contact support',
    askMessage: 'Describe your issue in one short message (up to 500 chars).',
    sent: (ticketId: string) => `Your request was sent to support. Ticket: ${ticketId}. Support can continue by email if needed.`,
    rateLimited: 'Too many requests in a short time. Please try later.',
    invalidMessage: 'Please send one short text message (1-500 chars).',
  },
} as const;

export function resolveSupportLanguage(
  preferred: string | undefined,
  def: SupportLanguage = 'ru',
  fallback: SupportLanguage = 'en',
): SupportLanguage {
  const normalized = (preferred || '').toLowerCase();
  if (normalized === 'ru' || normalized === 'en') return normalized;
  return def || fallback;
}

export function getTopicByKey(key: string): SupportTopic | undefined {
  return SUPPORT_TOPICS.find((t) => t.key === key);
}

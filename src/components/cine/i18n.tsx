import { createContext, useContext, useEffect, useState } from 'react';

export type CineLocale = 'fr' | 'en' | 'ru';
export const CINE_LOCALES: CineLocale[] = ['fr', 'en', 'ru'];
export const CINE_LOCALE_LABEL: Record<CineLocale, string> = { fr: 'FR', en: 'EN', ru: 'RU' };

export type CineStrings = {
  langName: string;
  nav: { apercu: string; technologie: string; tarifs: string; faq: string };
  hero: {
    tagline: string;
    lines: [string, string, string];
    sub: string;
    ctaPrimary: string;
    ctaSecondary: string;
    badge: [string, string];
    stats: { v: string; l: string }[];
  };
  tech: { eyebrow: string; title: string; lead: string; layers: { h: string; p: string }[]; cta: string };
  tarifs: {
    eyebrow: string;
    title: string;
    offres: string;
    periods: [string, string, string];
    features: [string[], string[], string[]];
    notes: [string, string, string];
    recommande: string;
    cta: string;
    inclusLabel: string;
    inclus: string[];
    paiementLabel: string;
    lead: string;
  };
  faq: { eyebrow: string; title: string; items: { q: string; a: string }[] };
  download: {
    telechargement: string;
    tarifs: string;
    version: string;
    versionSub: string;
    title: string;
    sub: string;
    winDetail: string;
    andDetail: string;
    accesLabel: string;
    emailPlaceholder: string;
    cta: string;
    sent: string;
    optinPending: string;
    plateformesLabel: string;
    paymentNote: string;
  };
  footer: { copyright: string; terms: string; privacy: string; refund: string; contact: string };
};

const FR: CineStrings = {
  langName: 'Français',
  nav: { apercu: 'Aperçu', technologie: 'Technologie', tarifs: 'Tarifs', faq: 'FAQ' },
  hero: {
    tagline: 'VPN furtif · REALITY',
    lines: ['Ils voient', 'tout.', 'Sauf vous.'],
    sub: "SWIMVPN fait transiter votre trafic par un nœud externe. REALITY l'habille en HTTPS ordinaire — invisible aux sondes, invisible à votre opérateur.",
    ctaPrimary: 'Télécharger',
    ctaSecondary: 'Voir les tarifs',
    badge: ['Windows · Android', 'Sans store'],
    stats: [
      { v: '0', l: 'log conservé' },
      { v: '<60 s', l: 'tunnel actif' },
      { v: '3', l: 'appareils par accès' },
    ],
  },
  tech: {
    eyebrow: 'Technologie',
    title: "Un tunnel qui n'en a pas l'air.",
    lead: "Vos données transitent via VLESS sur le moteur Xray (successeur de V2Ray). REALITY habille chaque paquet en HTTPS ordinaire — les systèmes d'inspection profonde voient une visite web banale, pas un tunnel VPN.",
    layers: [
      { h: 'VLESS · moteur Xray', p: "Successeur de V2Ray, Xray transporte votre trafic en VLESS — léger, chiffré, sans la surcharge qui trahit un VPN classique." },
      { h: 'REALITY · habillage HTTPS', p: "Chaque paquet emprunte l'empreinte TLS d'un vrai site. Pas de certificat suspect, pas de SNI qui dépasse : le handshake ressemble à n'importe quel HTTPS." },
      { h: 'Face au DPI', p: "L'inspection profonde lit les empreintes en ~30 ms. Ici elle lit « visite web » et passe son chemin. Il n'y a rien d'anormal à bloquer." },
    ],
    cta: 'Choisir une offre',
  },
  tarifs: {
    eyebrow: 'Tarifs',
    title: 'Ce que coûte votre liberté.',
    offres: 'Offres',
    periods: ['Accès 7 jours', 'Accès 30 jours', 'Accès 90 jours'],
    features: [
      ['100 GB inclus', '3 appareils', 'Agent IA'],
      ['300 GB inclus', '3 appareils', 'Agent IA temps réel'],
      ['1000 GB inclus', '3 appareils', 'Agent IA temps réel'],
    ],
    notes: [
      'Pour tester sous conditions réelles.',
      'Le choix de ceux qui restent connectés.',
      "La surveillance, elle, ne s'arrête pas.",
    ],
    recommande: 'Recommandé',
    cta: 'Commencer',
    inclusLabel: 'Inclus partout',
    inclus: ['Agent IA temps réel', 'Chiffrement REALITY', 'Bascule serveur auto', 'Zéro log', 'Sans store'],
    paiementLabel: 'Paiement',
    lead: "Sans store, sans intermédiaire. Vous choisissez la devise au paiement. Tunnel actif en moins d'une minute.",
  },
  faq: {
    eyebrow: 'FAQ',
    title: 'Questions franches, réponses franches.',
    items: [
      {
        q: 'Comment SWIMVPN passe-t-il là où les VPN classiques sont bloqués ?',
        a: "Le trafic voyage en VLESS sur le moteur Xray, et REALITY lui donne l'empreinte TLS d'un vrai site web. Pour les systèmes d'inspection profonde (DPI), votre connexion ressemble à une visite HTTPS ordinaire — il n'y a rien d'anormal à bloquer.",
      },
      {
        q: 'Gardez-vous des logs ?',
        a: "Non. Ni historique de navigation, ni journaux de connexion. Le paiement lui-même peut se faire en crypto, sans identité.",
      },
      {
        q: 'Sur quoi ça tourne ?',
        a: "Windows 10 / 11 (.exe) et Android 8+ (.apk), téléchargés directement ici — sans passer par un store, donc sans dépendre de sa disponibilité.",
      },
      {
        q: 'Comment je paie ?',
        a: "SwimPay (RUB, USD ou XOF — vous choisissez la devise au paiement) ou crypto (Bitcoin, USDT). L'accès est actif en moins d'une minute.",
      },
      {
        q: "Combien d'appareils ?",
        a: 'Chaque accès couvre 3 appareils, quel que soit le plan.',
      },
      {
        q: 'Et si ça ne marche pas chez moi ?',
        a: "L'agent IA intégré change de serveur automatiquement quand la connexion se dégrade. Si rien n'y fait, la politique de remboursement s'applique — voir la page Remboursement.",
      },
    ],
  },
  download: {
    telechargement: 'Téléchargement',
    tarifs: 'Tarifs',
    version: 'Version',
    versionSub: 'Windows · Android · sans store',
    title: 'Télécharger SWIMVPN',
    sub: "Une app, deux plateformes. Tunnel actif en moins d'une minute, sans store.",
    winDetail: 'Windows 10 / 11 · .exe',
    andDetail: 'Android 8+ · .apk',
    accesLabel: 'Accès anticipé',
    emailPlaceholder: 'votre@email.com',
    cta: "Obtenir l'accès",
    sent: 'Merci ✓',
    optinPending: 'Vérifie ta boîte mail ✓',
    plateformesLabel: 'Plateformes',
    paymentNote: 'Paiement via SwimPay (RUB · USD · XOF) ou crypto. Zéro log.',
  },
  footer: { copyright: '© 2026 · app.swimvpn.pro · Windows · Android', terms: 'Conditions', privacy: 'Confidentialité', refund: 'Remboursement', contact: 'Contact' },
};

const EN: CineStrings = {
  langName: 'English',
  nav: { apercu: 'Overview', technologie: 'Technology', tarifs: 'Pricing', faq: 'FAQ' },
  hero: {
    tagline: 'Stealth VPN · REALITY',
    lines: ['They see', 'it all.', 'Except you.'],
    sub: 'SWIMVPN routes your traffic through an external node. REALITY dresses it as ordinary HTTPS — invisible to probes, invisible to your carrier.',
    ctaPrimary: 'Download',
    ctaSecondary: 'See pricing',
    badge: ['Windows · Android', 'No store'],
    stats: [
      { v: '0', l: 'logs kept' },
      { v: '<60 s', l: 'to a live tunnel' },
      { v: '3', l: 'devices per access' },
    ],
  },
  tech: {
    eyebrow: 'Technology',
    title: "A tunnel that doesn't look like one.",
    lead: 'Your data travels over VLESS on the Xray engine (V2Ray successor). REALITY dresses every packet as ordinary HTTPS — deep packet inspection sees a plain web visit, not a VPN tunnel.',
    layers: [
      { h: 'VLESS · Xray engine', p: 'Successor to V2Ray, Xray carries your traffic over VLESS — light, encrypted, without the overhead that gives a classic VPN away.' },
      { h: 'REALITY · HTTPS disguise', p: "Each packet borrows the TLS fingerprint of a real site. No suspicious certificate, no SNI sticking out: the handshake looks like any HTTPS." },
      { h: 'Against DPI', p: 'Deep inspection reads fingerprints in ~30 ms. Here it reads "web visit" and moves on. There is nothing abnormal to block.' },
    ],
    cta: 'Choose a plan',
  },
  tarifs: {
    eyebrow: 'Pricing',
    title: 'What your freedom costs.',
    offres: 'Plans',
    periods: ['7-day access', '30-day access', '90-day access'],
    features: [
      ['100 GB included', '3 devices', 'AI agent'],
      ['300 GB included', '3 devices', 'Real-time AI agent'],
      ['1000 GB included', '3 devices', 'Real-time AI agent'],
    ],
    notes: [
      'To test in real conditions.',
      'The pick of those who stay connected.',
      "Surveillance doesn't take a break.",
    ],
    recommande: 'Recommended',
    cta: 'Start',
    inclusLabel: 'Included everywhere',
    inclus: ['Real-time AI agent', 'REALITY encryption', 'Auto server switch', 'Zero logs', 'No store'],
    paiementLabel: 'Payment',
    lead: 'No store, no middleman. You pick the currency at checkout. Tunnel up in under a minute.',
  },
  faq: {
    eyebrow: 'FAQ',
    title: 'Straight questions, straight answers.',
    items: [
      {
        q: 'How does SWIMVPN get through where classic VPNs are blocked?',
        a: 'Traffic travels over VLESS on the Xray engine, and REALITY gives it the TLS fingerprint of a real website. To deep packet inspection your connection looks like an ordinary HTTPS visit — there is nothing abnormal to block.',
      },
      {
        q: 'Do you keep logs?',
        a: 'No. No browsing history, no connection records. You can even pay in crypto, with no identity attached.',
      },
      {
        q: 'What does it run on?',
        a: 'Windows 10 / 11 (.exe) and Android 8+ (.apk), downloaded right here — no app store, no store dependency.',
      },
      {
        q: 'How do I pay?',
        a: 'SwimPay (RUB, USD or XOF — you pick the currency at checkout) or crypto (Bitcoin, USDT). Access is live in under a minute.',
      },
      {
        q: 'How many devices?',
        a: 'Every access covers 3 devices, on any plan.',
      },
      {
        q: "What if it doesn't work for me?",
        a: 'The built-in AI agent switches servers automatically when the link degrades. If nothing helps, the refund policy applies — see the Refund page.',
      },
    ],
  },
  download: {
    telechargement: 'Download',
    tarifs: 'Pricing',
    version: 'Version',
    versionSub: 'Windows · Android · no store',
    title: 'Download SWIMVPN',
    sub: 'One app, two platforms. Tunnel up in under a minute, no store.',
    winDetail: 'Windows 10 / 11 · .exe',
    andDetail: 'Android 8+ · .apk',
    accesLabel: 'Early access',
    emailPlaceholder: 'your@email.com',
    cta: 'Get access',
    sent: 'Thanks ✓',
    optinPending: 'Check your inbox ✓',
    plateformesLabel: 'Platforms',
    paymentNote: 'Payment via SwimPay (RUB · USD · XOF) or crypto. Zero logs.',
  },
  footer: { copyright: '© 2026 · app.swimvpn.pro · Windows · Android', terms: 'Terms', privacy: 'Privacy', refund: 'Refund', contact: 'Contact' },
};

const RU: CineStrings = {
  langName: 'Русский',
  nav: { apercu: 'Обзор', technologie: 'Технология', tarifs: 'Цены', faq: 'Вопросы' },
  hero: {
    tagline: 'Стелс-VPN · REALITY',
    lines: ['Они видят', 'всё.', 'Кроме вас.'],
    sub: 'SWIMVPN пропускает ваш трафик через внешний узел. REALITY маскирует его под обычный HTTPS — невидим для зондов, невидим для оператора.',
    ctaPrimary: 'Скачать',
    ctaSecondary: 'Смотреть цены',
    badge: ['Windows · Android', 'Без магазина'],
    stats: [
      { v: '0', l: 'логов' },
      { v: '<60 с', l: 'до туннеля' },
      { v: '3', l: 'устройства на доступ' },
    ],
  },
  tech: {
    eyebrow: 'Технология',
    title: 'Туннель, который на него не похож.',
    lead: 'Ваши данные идут через VLESS на движке Xray (преемник V2Ray). REALITY маскирует каждый пакет под обычный HTTPS — системы глубокой инспекции видят обычный визит на сайт, а не VPN-туннель.',
    layers: [
      { h: 'VLESS · движок Xray', p: 'Преемник V2Ray, Xray несёт ваш трафик по VLESS — легко, зашифровано, без нагрузки, выдающей обычный VPN.' },
      { h: 'REALITY · маскировка HTTPS', p: 'Каждый пакет берёт TLS-отпечаток реального сайта. Никакого подозрительного сертификата, никакого выделяющегося SNI: рукопожатие выглядит как любой HTTPS.' },
      { h: 'Против DPI', p: 'Глубокая инспекция читает отпечатки за ~30 мс. Здесь она читает «визит на сайт» и идёт дальше. Блокировать нечего.' },
    ],
    cta: 'Выбрать тариф',
  },
  tarifs: {
    eyebrow: 'Цены',
    title: 'Сколько стоит ваша свобода.',
    offres: 'Тарифы',
    periods: ['Доступ 7 дней', 'Доступ 30 дней', 'Доступ 90 дней'],
    features: [
      ['100 ГБ включено', '3 устройства', 'ИИ-агент'],
      ['300 ГБ включено', '3 устройства', 'ИИ-агент в реальном времени'],
      ['1000 ГБ включено', '3 устройства', 'ИИ-агент в реальном времени'],
    ],
    notes: [
      'Чтобы проверить в реальных условиях.',
      'Выбор тех, кто остаётся на связи.',
      'А слежка не прекращается.',
    ],
    recommande: 'Рекомендуем',
    cta: 'Начать',
    inclusLabel: 'Везде включено',
    inclus: ['ИИ-агент в реальном времени', 'Шифрование REALITY', 'Авто-переключение сервера', 'Ноль логов', 'Без магазина'],
    paiementLabel: 'Оплата',
    lead: 'Без магазина, без посредников. Валюту выбираете при оплате. Туннель активен меньше чем за минуту.',
  },
  faq: {
    eyebrow: 'Вопросы',
    title: 'Прямые вопросы — прямые ответы.',
    items: [
      {
        q: 'Как SWIMVPN проходит там, где обычные VPN заблокированы?',
        a: 'Трафик идёт по VLESS на движке Xray, а REALITY даёт ему TLS-отпечаток реального сайта. Для систем глубокой инспекции (DPI) ваше соединение выглядит как обычный HTTPS-визит — блокировать нечего.',
      },
      {
        q: 'Вы храните логи?',
        a: 'Нет. Ни истории посещений, ни журналов подключений. Оплатить можно криптовалютой — вообще без личных данных.',
      },
      {
        q: 'На чём работает?',
        a: 'Windows 10 / 11 (.exe) и Android 8+ (.apk), скачиваются прямо здесь — без магазина приложений и его ограничений.',
      },
      {
        q: 'Как оплатить?',
        a: 'SwimPay (RUB, USD или XOF — валюту выбираете при оплате) или криптовалюта (Bitcoin, USDT). Доступ активен меньше чем за минуту.',
      },
      {
        q: 'Сколько устройств?',
        a: 'Каждый доступ покрывает 3 устройства на любом тарифе.',
      },
      {
        q: 'А если у меня не заработает?',
        a: 'Встроенный ИИ-агент автоматически переключает сервер, когда связь ухудшается. Если ничего не помогает — действует политика возврата, см. страницу «Возврат».',
      },
    ],
  },
  download: {
    telechargement: 'Скачать',
    tarifs: 'Цены',
    version: 'Версия',
    versionSub: 'Windows · Android · без магазина',
    title: 'Скачать SWIMVPN',
    sub: 'Одно приложение, две платформы. Туннель активен меньше чем за минуту, без магазина.',
    winDetail: 'Windows 10 / 11 · .exe',
    andDetail: 'Android 8+ · .apk',
    accesLabel: 'Ранний доступ',
    emailPlaceholder: 'ваш@email.com',
    cta: 'Получить доступ',
    sent: 'Спасибо ✓',
    optinPending: 'Проверьте почту ✓',
    plateformesLabel: 'Платформы',
    paymentNote: 'Оплата через SwimPay (RUB · USD · XOF) или крипто. Ноль логов.',
  },
  footer: { copyright: '© 2026 · app.swimvpn.pro · Windows · Android', terms: 'Условия', privacy: 'Конфиденциальность', refund: 'Возврат', contact: 'Контакты' },
};

export const CINE_STRINGS: Record<CineLocale, CineStrings> = { fr: FR, en: EN, ru: RU };

type Ctx = { locale: CineLocale; setLocale: (l: CineLocale) => void; t: CineStrings };
const CineLocaleContext = createContext<Ctx>({ locale: 'fr', setLocale: () => {}, t: FR });

export function useCine() {
  return useContext(CineLocaleContext);
}

export function CineLocaleProvider({ children, initial }: { children: React.ReactNode; initial?: CineLocale }) {
  // SSR/prerender : `initial` (locale de l'URL : ru à /, fr à /fr) → corps prerendu cohérent
  // avec les meta. Runtime : la pref localStorage prime (switcher), sinon défaut FR.
  const [locale, setLocaleState] = useState<CineLocale>(initial && CINE_LOCALES.includes(initial) ? initial : 'fr');

  useEffect(() => {
    const saved = (typeof localStorage !== 'undefined' && localStorage.getItem('cine-locale')) as CineLocale | null;
    if (saved && CINE_LOCALES.includes(saved)) setLocaleState(saved);
  }, []);

  const setLocale = (l: CineLocale) => {
    setLocaleState(l);
    try {
      localStorage.setItem('cine-locale', l);
    } catch {
      /* noop */
    }
  };

  return (
    <CineLocaleContext.Provider value={{ locale, setLocale, t: CINE_STRINGS[locale] }}>
      {children}
    </CineLocaleContext.Provider>
  );
}

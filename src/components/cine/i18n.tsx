import { createContext, useContext, useEffect, useState } from 'react';

export type CineLocale = 'fr' | 'en' | 'ru';
export const CINE_LOCALES: CineLocale[] = ['fr', 'en', 'ru'];
export const CINE_LOCALE_LABEL: Record<CineLocale, string> = { fr: 'FR', en: 'EN', ru: 'RU' };

export type CineStrings = {
  langName: string;
  nav: { apercu: string; technologie: string; tarifs: string };
  hero: { l1: string; l2: string; sub: string; pills: [string, string, string] };
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
    plateformesLabel: string;
    paymentNote: string;
  };
  footer: { copyright: string };
};

const FR: CineStrings = {
  langName: 'Français',
  nav: { apercu: 'Aperçu', technologie: 'Technologie', tarifs: 'Tarifs' },
  hero: {
    l1: 'Ils voient tout.',
    l2: 'Sauf vous.',
    sub: "SWIMVPN fait transiter votre trafic par un nœud externe. REALITY l'habille en HTTPS ordinaire — invisible aux sondes, invisible à votre opérateur.",
    pills: ['Sans store', 'Sans intermédiaire', 'Zéro log'],
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
      ['50 GB inclus', '1 appareil', 'Agent IA'],
      ['150 GB inclus', '2 appareils', 'Agent IA temps réel'],
      ['500 GB inclus', '3 appareils', 'Agent IA temps réel'],
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
    plateformesLabel: 'Plateformes',
    paymentNote: 'Paiement via SwimPay (RUB · USD · XOF) ou crypto. Zéro log.',
  },
  footer: { copyright: '© 2026 · app.swimvpn.pro · Windows · Android' },
};

const EN: CineStrings = {
  langName: 'English',
  nav: { apercu: 'Overview', technologie: 'Technology', tarifs: 'Pricing' },
  hero: {
    l1: 'They see it all.',
    l2: 'Except you.',
    sub: 'SWIMVPN routes your traffic through an external node. REALITY dresses it as ordinary HTTPS — invisible to probes, invisible to your carrier.',
    pills: ['No store', 'No middleman', 'Zero logs'],
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
      ['50 GB included', '1 device', 'AI agent'],
      ['150 GB included', '2 devices', 'Real-time AI agent'],
      ['500 GB included', '3 devices', 'Real-time AI agent'],
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
    plateformesLabel: 'Platforms',
    paymentNote: 'Payment via SwimPay (RUB · USD · XOF) or crypto. Zero logs.',
  },
  footer: { copyright: '© 2026 · app.swimvpn.pro · Windows · Android' },
};

const RU: CineStrings = {
  langName: 'Русский',
  nav: { apercu: 'Обзор', technologie: 'Технология', tarifs: 'Цены' },
  hero: {
    l1: 'Они видят всё.',
    l2: 'Кроме вас.',
    sub: 'SWIMVPN пропускает ваш трафик через внешний узел. REALITY маскирует его под обычный HTTPS — невидим для зондов, невидим для оператора.',
    pills: ['Без магазина', 'Без посредников', 'Ноль логов'],
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
      ['50 ГБ включено', '1 устройство', 'ИИ-агент'],
      ['150 ГБ включено', '2 устройства', 'ИИ-агент в реальном времени'],
      ['500 ГБ включено', '3 устройства', 'ИИ-агент в реальном времени'],
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
    plateformesLabel: 'Платформы',
    paymentNote: 'Оплата через SwimPay (RUB · USD · XOF) или крипто. Ноль логов.',
  },
  footer: { copyright: '© 2026 · app.swimvpn.pro · Windows · Android' },
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

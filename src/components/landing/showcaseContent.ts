// Bilingual copy for the redesigned (showcase) landing. The visible sections follow
// docs/design/swimvpn-showcase-light.html; FR is the mock's text, RU is translated
// (review pass pending). SEO meta is reused verbatim from landingContent.ts so the
// redesign does not regress search/OG metadata.
import type { LandingLocale } from './landingContent';

export type ShowcaseCopy = {
  nav: { links: { href: string; label: string }[]; download: string };
  hero: {
    eyebrow: string;
    title: string;
    lead: string;
    ctaDownload: string;
    ctaHow: string;
    trust: string;
    scrollHint: string;
  };
  // 5 pinned captions of the scroll-scrub stage
  scrollCaps: { k: string; h3: string; p: string }[];
  palette: { phrases: [string, string]; sub: string };
  features: { eyebrow: string; titleLines: string[]; sub: string };
  pricing: {
    eyebrow: string;
    titleLines: string[];
    sub: string;
    plans: {
      name: string;
      price: string;
      period: string;
      features: string[];
      note: string;
      cta: string;
      best?: boolean;
      badge?: string;
    }[];
  };
  finalCta: {
    lead: string;
    emailPlaceholder: string;
    ctaAccess: string;
    apkLink: string;
  };
  footer: { support: string; apk: string; copyright: string };
};

export const SHOWCASE_COPY: Record<LandingLocale, ShowcaseCopy> = {
  fr: {
    nav: {
      links: [
        { href: '#showcase', label: 'Aperçu' },
        { href: '#features', label: 'Fonctions' },
        { href: '#pricing', label: 'Tarifs' },
      ],
      download: 'Télécharger',
    },
    hero: {
      eyebrow: 'Réseau surveillé · Données collectées · Android',
      title: "Vous n'êtes pas protégé.",
      lead:
        "À cet instant, votre VPN affiche « protégé ». Ce que vous ignorez : votre trafic quitte votre appareil, traverse le réseau de votre FAI, puis rejoint un serveur VPN. Votre opérateur voit l'adresse IP du serveur, la durée et le protocole. Les DPI lisent vos empreintes en 30 millisecondes. Vous n'êtes pas protégé.",
      ctaDownload: '↓ Télécharger',
      ctaHow: 'Comment ça marche',
      trust: 'Sans store · Sans intermédiaire · Zéro log',
      scrollHint: 'Faites défiler pour explorer ↓',
    },
    scrollCaps: [
      {
        k: 'Restrictions',
        h3: "Ce qui est bloqué chez vous ne l'est pas ici.",
        p: "Dans des dizaines de pays, l'État ou les opérateurs filtrent l'accès à des sites, apps et services entiers. SWIMVPN fait transiter votre connexion par un nœud externe — les restrictions ne s'appliquent plus.",
      },
      {
        k: 'Accès mondial',
        h3: 'Internet depuis Berlin, Rotterdam ou New York.',
        p: "Choisissez la porte de sortie — Allemagne, Pays-Bas, Lettonie, USA et plus. Votre trafic sort depuis ce nœud : vous accédez aux services locaux de ce pays, comme si vous y étiez physiquement.",
      },
      {
        k: 'Pilote automatique',
        h3: 'Le serveur change. Vous ne faites rien.',
        p: "L'agent surveille la latence et le débit sur votre opérateur en temps réel. Dès qu'un serveur ralentit ou se fait bloquer, il bascule sur le relais le plus rapide disponible — sans interruption, sans intervention de votre part.",
      },
      {
        k: 'Protocoles',
        h3: 'VLESS sur Xray : chiffré et invisible aux sondes.',
        p: "Vos données transitent via VLESS sur le moteur Xray (successeur de V2Ray). REALITY habille chaque paquet en HTTPS ordinaire — les systèmes d'inspection profonde voient une visite web banale, pas un tunnel VPN.",
      },
      {
        k: 'Paiement',
        h3: "Dans l'app, dans votre devise.",
        p: "Abonnez-vous sans passer par un store. SwimPay accepte RUB, USD et XOF directement dans l'application. Vous préférez les cryptomonnaies ? Bitcoin, USDT et autres sont disponibles — même confort, même discrétion.",
      },
    ],
    palette: {
      phrases: ['Ils voient tout.', 'Sauf vous.'],
      sub: "Switchez entre un accès VPN et un proxy résidentiel — un simple copier-coller dans SWIMVPN, et c'est actif sur votre Android. Vous êtes invisible. Profitez-en.",
    },
    features: {
      eyebrow: 'Accès anticipé',
      titleLines: ['Testez sur votre réseau.', 'Sans engagement.'],
      sub: "Profitez dès aujourd'hui d'un accès anticipé pour tester la puissance de notre infrastructure. Contournez les restrictions, pilotez votre connexion avec l'agent IA — tunnel actif en moins d'une minute.",
    },
    pricing: {
      eyebrow: 'Offres',
      titleLines: ['Ce que coûte', 'votre liberté.'],
      sub: "Sans store, sans intermédiaire. Payez depuis l'app avec SwimPay — RUB, USD, XOF — ou en crypto. Tunnel actif en moins d'une minute.",
      plans: [
        {
          name: 'Basic · 7 jours',
          price: '$3.49',
          period: 'Accès 7 jours',
          features: ['50 GB inclus', '1 appareil', 'Agent IA'],
          note: 'Pour tester sous conditions réelles.',
          cta: 'Commencer',
        },
        {
          name: 'Premium · 30 jours',
          price: '$7.99',
          period: 'Accès 30 jours',
          features: ['150 GB inclus', '2 appareils', 'Agent IA temps réel'],
          note: 'Le choix de ceux qui restent connectés.',
          cta: 'Commencer',
          best: true,
          badge: 'Recommandé',
        },
        {
          name: 'Platinum · 90 jours',
          price: '$21.99',
          period: 'Accès 90 jours',
          features: ['500 GB inclus', '3 appareils', 'Agent IA temps réel'],
          note: "La surveillance, elle, ne s'arrête pas.",
          cta: 'Commencer',
        },
      ],
    },
    finalCta: {
      lead: "Tunnel actif en moins d'une minute. Sans store, sans intermédiaire. Zéro log.",
      emailPlaceholder: 'votre@email.com',
      ctaAccess: "Obtenir l'accès",
      apkLink: "Télécharger l'APK gratuitement",
    },
    footer: { support: 'Support', apk: 'APK', copyright: '© 2026 · app.swimvpn.pro · Android' },
  },

  ru: {
    nav: {
      links: [
        { href: '#showcase', label: 'Обзор' },
        { href: '#features', label: 'Функции' },
        { href: '#pricing', label: 'Цены' },
      ],
      download: 'Скачать',
    },
    hero: {
      eyebrow: 'Сеть под наблюдением · Данные собираются · Android',
      title: 'Вы не защищены.',
      lead:
        'Прямо сейчас ваш VPN показывает «защищено». Но вот чего вы не знаете: ваш трафик покидает устройство, проходит через сеть провайдера и доходит до VPN-сервера. Оператор видит IP-адрес сервера, длительность и протокол. DPI читает ваши отпечатки за 30 миллисекунд. Вы не защищены.',
      ctaDownload: '↓ Скачать',
      ctaHow: 'Как это работает',
      trust: 'Без магазина · Без посредников · Ноль логов',
      scrollHint: 'Прокрутите, чтобы узнать больше ↓',
    },
    scrollCaps: [
      {
        k: 'Ограничения',
        h3: 'То, что заблокировано у вас, здесь доступно.',
        p: 'В десятках стран государство или операторы фильтруют доступ к сайтам, приложениям и целым сервисам. SWIMVPN пропускает ваше соединение через внешний узел — ограничения больше не действуют.',
      },
      {
        k: 'Глобальный доступ',
        h3: 'Интернет из Берлина, Роттердама или Нью-Йорка.',
        p: 'Выбирайте точку выхода — Германия, Нидерланды, Латвия, США и другие. Ваш трафик выходит через этот узел: вы получаете доступ к локальным сервисам этой страны, словно находитесь там физически.',
      },
      {
        k: 'Автопилот',
        h3: 'Сервер меняется. Вы не делаете ничего.',
        p: 'Агент в реальном времени следит за задержкой и скоростью у вашего оператора. Как только сервер замедляется или блокируется, он переключается на самый быстрый доступный релей — без обрывов и без вашего участия.',
      },
      {
        k: 'Протоколы',
        h3: 'VLESS на Xray: шифрование, невидимое для зондов.',
        p: 'Ваши данные идут через VLESS на движке Xray (преемник V2Ray). REALITY маскирует каждый пакет под обычный HTTPS — системы глубокой инспекции видят обычный визит на сайт, а не VPN-туннель.',
      },
      {
        k: 'Оплата',
        h3: 'В приложении, в вашей валюте.',
        p: 'Оформляйте подписку без магазина приложений. SwimPay принимает RUB, USD и XOF прямо в приложении. Предпочитаете криптовалюту? Bitcoin, USDT и другие — тот же комфорт, та же приватность.',
      },
    ],
    palette: {
      phrases: ['Они видят всё.', 'Кроме вас.'],
      sub: 'Переключайтесь между VPN-доступом и резидентным прокси — простая вставка в SWIMVPN, и всё работает на вашем Android. Вы невидимы. Пользуйтесь.',
    },
    features: {
      eyebrow: 'Ранний доступ',
      titleLines: ['Проверьте в своей сети.', 'Без обязательств.'],
      sub: 'Получите ранний доступ уже сегодня и оцените мощь нашей инфраструктуры. Обходите ограничения, управляйте соединением с ИИ-агентом — туннель активен меньше чем за минуту.',
    },
    pricing: {
      eyebrow: 'Тарифы',
      titleLines: ['Сколько стоит', 'ваша свобода.'],
      sub: 'Без магазина, без посредников. Платите из приложения через SwimPay — RUB, USD, XOF — или криптой. Туннель активен меньше чем за минуту.',
      plans: [
        {
          name: 'Basic · 7 дней',
          price: '$3.49',
          period: 'Доступ на 7 дней',
          features: ['50 ГБ включено', '1 устройство', 'ИИ-агент'],
          note: 'Чтобы протестировать в реальных условиях.',
          cta: 'Начать',
        },
        {
          name: 'Premium · 30 дней',
          price: '$7.99',
          period: 'Доступ на 30 дней',
          features: ['150 ГБ включено', '2 устройства', 'ИИ-агент в реальном времени'],
          note: 'Выбор тех, кто остаётся на связи.',
          cta: 'Начать',
          best: true,
          badge: 'Рекомендуем',
        },
        {
          name: 'Platinum · 90 дней',
          price: '$21.99',
          period: 'Доступ на 90 дней',
          features: ['500 ГБ включено', '3 устройства', 'ИИ-агент в реальном времени'],
          note: 'А слежка не прекращается.',
          cta: 'Начать',
        },
      ],
    },
    finalCta: {
      lead: 'Туннель активен меньше чем за минуту. Без магазина, без посредников. Ноль логов.',
      emailPlaceholder: 'ваш@email.com',
      ctaAccess: 'Получить доступ',
      apkLink: 'Скачать APK бесплатно',
    },
    footer: { support: 'Поддержка', apk: 'APK', copyright: '© 2026 · app.swimvpn.pro · Android' },
  },
};

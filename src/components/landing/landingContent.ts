export type LandingLocale = 'ru' | 'fr';

export const LANDING_DEFAULT_LOCALE: LandingLocale = 'ru';

export const LANDING_LOCALES: LandingLocale[] = ['ru', 'fr'];

export const LANDING_LOCALE_LABELS: Record<LandingLocale, string> = {
  ru: 'RU',
  fr: 'FR',
};

export const LANDING_BASE_URL = 'https://app.swimvpn.pro';

export const LANDING_OG_IMAGE_PATH = '/brand/swimvpn-og.png';

export const LANDING_OG_IMAGE_URL = `${LANDING_BASE_URL}${LANDING_OG_IMAGE_PATH}`;

export const LANDING_DOWNLOAD_URL = `${LANDING_BASE_URL}/downloads/swimvpn.apk`;

// Path-based locale routing (better for SEO than a ?lang query param): default locale at "/",
// others at "/<locale>" (e.g. "/fr"). The prerender writes one static HTML per path, and nginx's
// `try_files $uri $uri/ /index.html` serves "/fr/" from "dist/fr/index.html".
export function getLandingPath(locale: LandingLocale): string {
  return locale === LANDING_DEFAULT_LOCALE ? '/' : `/${locale}`;
}

export function getLandingUrl(locale: LandingLocale): string {
  return `${LANDING_BASE_URL}${getLandingPath(locale)}`;
}

export type LandingCopy = {
  seo: {
    htmlLang: string;
    title: string;
    description: string;
    ogLocale: string;
    ogTitle: string;
    ogDescription: string;
  };
  nav: {
    ariaHome: string;
    product: string;
    setup: string;
    payments: string;
    apk: string;
    install: string;
    language: string;
  };
  hero: {
    chip: string;
    announcement: string;
    title: string;
    body: string;
    primaryCta: string;
    secondaryCta: string;
    proofLabel: string;
    proofItems: string[];
  };
  product: {
    eyebrow: string;
    title: string;
    text: string;
    reasons: Array<{ title: string; text: string }>;
  };
  setup: {
    title: string;
    text: string;
    steps: string[];
  };
  business: {
    title: string;
    text: string;
  };
  compatibility: {
    title: string;
    text: string;
    chips: string[];
  };
  ai: {
    title: string;
    text: string;
    note: string;
  };
  trial: {
    title: string;
    text: string;
    label: string;
  };
  payments: {
    title: string;
    text: string;
    priceLabel: string;
    priceSuffix: string;
    swimpayVia: string;
    methods: Array<{ name: string; text: string; icon: 'swimpay' | 'crypto' }>;
  };
  finalCta: {
    title: string;
    stats: Array<[string, string]>;
  };
  footer: {
    privacy: string;
    terms: string;
    support: string;
  };
};

export const LANDING_COPY: Record<LandingLocale, LandingCopy> = {
  ru: {
    seo: {
      htmlLang: 'ru',
      title: 'SWIMVPN — Android VPN, покупка конфигов в приложении и импорт Xray/V2Ray',
      description:
        'SWIMVPN — Android VPN в предрелизе: покупайте VPN-доступ внутри приложения, используйте trial или импортируйте свои VLESS, VMess, Trojan, Shadowsocks, Xray и V2Ray конфиги.',
      ogLocale: 'ru_RU',
      ogTitle: 'SWIMVPN — Android VPN с покупкой конфигов и импортом Xray/V2Ray',
      ogDescription:
        'Скачайте APK предрелиза, активируйте trial, покупайте доступ в приложении или используйте собственные VPN-конфиги.',
    },
    nav: {
      ariaHome: 'SWIMVPN главная',
      product: 'Продукт',
      setup: 'Старт',
      payments: 'Оплата',
      apk: 'APK',
      install: 'Установить',
      language: 'Язык',
    },
    hero: {
      chip: 'Предрелиз',
      announcement: 'Trial доступен сейчас',
      title: 'VPN без лишней возни.',
      body:
        'SWIMVPN помогает купить VPN-доступ прямо в приложении или подключить свой конфиг как в привычном VPN-клиенте. Меньше технических экранов, больше понятных действий.',
      primaryCta: 'Скачать APK',
      secondaryCta: 'Что такое SWIMVPN',
      proofLabel: 'Поддерживает',
      proofItems: ['In-app покупка', 'Свои конфиги', 'Xray/V2Ray', 'SwimPay', 'Crypto'],
    },
    product: {
      eyebrow: 'SWIMVPN — это',
      title: 'Android VPN для покупки, импорта и удобного выбора подключения.',
      text:
        'Приложение объединяет два сценария: купить готовый доступ внутри SWIMVPN или бесплатно использовать свой VPN-конфиг. Пользователь не привязан к одному способу и не обязан разбираться в каждом техническом параметре.',
      reasons: [
        {
          title: 'Понятный старт',
          text: 'Скачали APK, открыли приложение, выбрали купить доступ или добавить свой конфиг.',
        },
        {
          title: 'Гибкость клиента',
          text: 'Можно использовать SWIMVPN как легкий клиент для собственных ссылок и QR-кодов.',
        },
        {
          title: 'Покупка внутри app',
          text: 'Не нужно искать продавца отдельно: доступ можно купить и получить прямо в приложении.',
        },
      ],
    },
    setup: {
      title: 'По ощущению — как простой клиент, но с магазином внутри.',
      text:
        'Если у вас уже есть ссылка или QR-код, добавьте его вручную. Если доступа нет, купите конфиг в приложении и подключайтесь из того же экрана.',
      steps: ['Скачать APK', 'Выбрать купить или импортировать', 'Получить подсказки по узлам', 'Подключиться'],
    },
    business: {
      title: 'Покупка VPN-доступа без лишнего обходного пути.',
      text:
        'Для пользователей, которым сложно удобно оплатить VPN у внешнего поставщика, SWIMVPN делает покупку ближе: выбор тарифа, оплата и получение доступа находятся в одном приложении.',
    },
    compatibility: {
      title: 'Не только VLESS и Trojan.',
      text:
        'SWIMVPN работает с современным VPN-экосистемой Xray/V2Ray. На лендинге мы говорим просто, но продукт не ограничивается двумя форматами.',
      chips: ['VLESS', 'VLESS Reality', 'VMess', 'Trojan', 'Shadowsocks', 'Xray JSON', 'V2Ray JSON'],
    },
    ai: {
      title: 'AI Agent подсказывает, но не решает за вас.',
      text:
        'Агент анализирует доступные признаки узлов и помогает увидеть лучшие варианты в реальном времени. Финальный выбор остается за пользователем.',
      note: 'Подсказки вместо автопилота',
    },
    trial: {
      title: 'Trial в предрелизе — чтобы попробовать до покупки.',
      text:
        'Сейчас можно протестировать приложение до Play Store релиза: оценить интерфейс, подключение, импорт и сценарий покупки внутри SWIMVPN.',
      label: 'Доступен сейчас',
    },
    payments: {
      title: 'SwimPay и Crypto — два способа оплаты внутри SWIMVPN.',
      text:
        'Мы показываем эти методы прямо в продукте, потому что это не обычные публичные модули. Пользователь знакомится с ними в контексте покупки VPN-доступа.',
      priceLabel: 'от',
      priceSuffix: '/ мес',
      swimpayVia: 'Оплата через SwimPay —',
      methods: [
        { name: 'SwimPay', text: 'Основной встроенный способ оплаты для доступа SWIMVPN.', icon: 'swimpay' },
        { name: 'Crypto', text: 'Альтернативный способ для пользователей, которым удобнее крипто-оплата.', icon: 'crypto' },
      ],
    },
    finalCta: {
      title: 'Скачайте APK и попробуйте SWIMVPN до релиза.',
      stats: [
        ['Trial', 'активен сейчас'],
        ['2 сценария', 'купить или импортировать'],
        ['Xray/V2Ray', 'совместимость'],
        ['Android', 'предрелиз APK'],
      ],
    },
    footer: {
      privacy: 'Конфиденциальность',
      terms: 'Условия',
      support: 'Поддержка',
    },
  },
  fr: {
    seo: {
      htmlLang: 'fr',
      title: 'SWIMVPN — VPN Android, achat in-app et import Xray/V2Ray',
      description:
        'SWIMVPN est un VPN Android en pré-release: achetez vos accès dans l’app, testez le trial ou importez vos configs VLESS, VMess, Trojan, Shadowsocks, Xray et V2Ray.',
      ogLocale: 'fr_FR',
      ogTitle: 'SWIMVPN — VPN Android avec achat in-app et import Xray/V2Ray',
      ogDescription:
        'Téléchargez l’APK pré-release, activez le trial, achetez vos accès dans l’app ou utilisez vos propres configs VPN.',
    },
    nav: {
      ariaHome: 'SWIMVPN accueil',
      product: 'Produit',
      setup: 'Prise en main',
      payments: 'Paiement',
      apk: 'APK',
      install: 'Installer',
      language: 'Langue',
    },
    hero: {
      chip: 'Pré-release',
      announcement: 'Trial disponible maintenant',
      title: 'Le VPN sans friction inutile.',
      body:
        'SWIMVPN permet d’acheter un accès VPN directement dans l’app ou d’utiliser vos propres configs comme dans un client VPN familier. Moins d’écrans techniques, plus d’actions compréhensibles.',
      primaryCta: 'Télécharger l’APK',
      secondaryCta: 'Comprendre SWIMVPN',
      proofLabel: 'Prend en charge',
      proofItems: ['Achat in-app', 'Configs personnelles', 'Xray/V2Ray', 'SwimPay', 'Crypto'],
    },
    product: {
      eyebrow: 'SWIMVPN, c’est quoi ?',
      title: 'Un VPN Android pour acheter, importer et choisir votre connexion simplement.',
      text:
        'L’app réunit deux usages: acheter un accès prêt à l’emploi dans SWIMVPN ou utiliser gratuitement votre propre config VPN. L’utilisateur garde le choix sans devoir comprendre chaque détail technique.',
      reasons: [
        {
          title: 'Prise en main claire',
          text: 'Téléchargez l’APK, ouvrez l’app, choisissez achat intégré ou import de config.',
        },
        {
          title: 'Usage type client VPN',
          text: 'Vous pouvez l’utiliser comme un client simple pour vos liens et QR codes personnels.',
        },
        {
          title: 'Achat dans l’app',
          text: 'Plus besoin de chercher un vendeur ailleurs: l’accès peut être acheté et reçu dans SWIMVPN.',
        },
      ],
    },
    setup: {
      title: 'La simplicité d’un client VPN, avec une boutique intégrée.',
      text:
        'Si vous avez déjà une config ou un QR code, ajoutez-le manuellement. Si vous n’avez pas d’accès, achetez une config dans l’app et connectez-vous depuis le même espace.',
      steps: ['Télécharger l’APK', 'Acheter ou importer', 'Lire les indices des noeuds', 'Se connecter'],
    },
    business: {
      title: 'Acheter un accès VPN sans détour compliqué.',
      text:
        'Pour les utilisateurs qui ont du mal à acheter facilement chez un fournisseur externe, SWIMVPN rapproche tout: choix de l’offre, paiement et réception de l’accès dans l’application.',
    },
    compatibility: {
      title: 'Pas seulement VLESS et Trojan.',
      text:
        'SWIMVPN s’inscrit dans l’écosystème moderne Xray/V2Ray. La landing reste simple, mais le produit couvre plus que deux formats.',
      chips: ['VLESS', 'VLESS Reality', 'VMess', 'Trojan', 'Shadowsocks', 'Xray JSON', 'V2Ray JSON'],
    },
    ai: {
      title: 'L’Agent IA suggère, il ne décide pas à votre place.',
      text:
        'Il observe les indices disponibles sur les noeuds et aide à repérer les meilleurs choix en temps réel. Le contrôle final reste entre les mains de l’utilisateur.',
      note: 'Des indices, pas un pilote automatique',
    },
    trial: {
      title: 'Trial actuel pour tester avant achat.',
      text:
        'Pendant la pré-release, vous pouvez découvrir l’expérience avant la publication Play Store: interface, connexion, import et achat intégré.',
      label: 'Disponible maintenant',
    },
    payments: {
      title: 'SwimPay et Crypto, deux moyens de paiement dans SWIMVPN.',
      text:
        'Ces modules ne sont pas des produits grand public séparés. On les présente dans le contexte SWIMVPN pour rendre le paiement plus concret et plus rassurant.',
      priceLabel: 'dès',
      priceSuffix: '/ mois',
      swimpayVia: 'Payable via SwimPay —',
      methods: [
        { name: 'SwimPay', text: 'Le moyen intégré principal pour acheter un accès SWIMVPN.', icon: 'swimpay' },
        { name: 'Crypto', text: 'Une alternative pour les utilisateurs qui préfèrent payer en crypto.', icon: 'crypto' },
      ],
    },
    finalCta: {
      title: 'Téléchargez l’APK et testez SWIMVPN avant la release.',
      stats: [
        ['Trial', 'actif maintenant'],
        ['2 usages', 'acheter ou importer'],
        ['Xray/V2Ray', 'compatibilité'],
        ['Android', 'pré-release APK'],
      ],
    },
    footer: {
      privacy: 'Confidentialité',
      terms: 'Conditions',
      support: 'Support',
    },
  },
};

export function isLandingLocale(value: string | null | undefined): value is LandingLocale {
  return LANDING_LOCALES.includes(value as LandingLocale);
}

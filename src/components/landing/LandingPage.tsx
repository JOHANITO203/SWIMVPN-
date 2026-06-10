import React, { useEffect, useState } from 'react';
import { motion, useReducedMotion } from 'motion/react';
import {
  ArrowUpRight,
  Bot,
  CheckCircle2,
  Download,
  QrCode,
  RadioTower,
  ShoppingBag,
  Smartphone,
  Sparkles,
} from 'lucide-react';
import {
  LANDING_COPY,
  LANDING_DEFAULT_LOCALE,
  LANDING_DOWNLOAD_URL,
  LANDING_LOCALES,
  LANDING_LOCALE_LABELS,
  LANDING_OG_IMAGE_URL,
  LandingLocale,
  getLandingPath,
  getLandingUrl,
  isLandingLocale,
} from './landingContent';

// Headline price shown on the landing, in USD — the flagship monthly plan (matches the "/month"
// suffix). Validated tiers: $3.49 week / $7.99 month / $21.99 quarter. Single source of truth here.
const USD_PRICE = '7.99';

// Currencies SwimPay actually settles in, each with its symbol. Truthful: SwimPay's live rails today
// are RUB, XOF, USD. Keep in sync with the app (swimPayCurrencyBadge) and the backend default.
const SWIMPAY_CURRENCIES = ['₽ RUB', 'CFA XOF', '$ USD'] as const;

const ui = {
  page:
    'min-h-screen overflow-hidden bg-[#050505] text-[#0A0A0D] selection:bg-[#8A6AF1]/30 selection:text-white',
  shell: 'relative z-10 mx-auto w-[calc(100vw-1.25rem)] max-w-[1600px] pb-3 pt-3 sm:w-auto sm:px-4 sm:pb-4 lg:px-6 lg:pb-6',
  lightCard:
    'relative overflow-hidden rounded-[1.75rem] border border-black/[0.08] bg-[linear-gradient(135deg,#FFFFFF_0%,#F4F2FA_48%,#ECE8F7_100%)] text-[#0A0A0D] shadow-[0_24px_90px_rgba(10,10,18,0.18)] sm:rounded-[2rem]',
  darkCard:
    'relative overflow-hidden rounded-[1.75rem] border border-white/[0.08] bg-[linear-gradient(145deg,#15151B_0%,#08080C_58%,#020204_100%)] text-white shadow-[0_24px_90px_rgba(0,0,0,0.52)] sm:rounded-[2rem]',
  purpleCard:
    'relative overflow-hidden rounded-[1.75rem] border border-white/[0.12] bg-[radial-gradient(circle_at_76%_18%,rgba(255,122,246,0.24),transparent_36%),linear-gradient(135deg,#7D57FF_0%,#5D3BD8_54%,#22105F_100%)] text-white shadow-[0_24px_90px_rgba(93,59,216,0.35)] sm:rounded-[2rem]',
  muted: 'text-[#5E5A68]',
  darkMuted: 'text-[#B9B4C7]',
  chipDark:
    'inline-flex items-center gap-2 rounded-full bg-black px-4 py-2 text-xs font-black text-white shadow-[0_12px_30px_rgba(0,0,0,0.18)]',
};

const paymentIconSrc = {
  swimpay: '/payments/swimpay.png',
  crypto: '/payments/crypto.svg',
} as const;

function getInitialLocale(initialLocale?: LandingLocale): LandingLocale {
  // SSR/prerender: no window — use the locale the build passed in.
  if (typeof window === 'undefined') return initialLocale ?? LANDING_DEFAULT_LOCALE;

  // Path-based locale first (e.g. "/fr"), so a visitor landing on the prerendered "/fr" page keeps
  // French after hydration; then the legacy ?lang query, then the stored preference.
  const pathLocale = window.location.pathname.split('/').filter(Boolean)[0];
  if (isLandingLocale(pathLocale)) return pathLocale;

  const queryLocale = new URLSearchParams(window.location.search).get('lang');
  if (isLandingLocale(queryLocale)) return queryLocale;

  const storedLocale = window.localStorage.getItem('swimvpn_landing_locale');
  if (isLandingLocale(storedLocale)) return storedLocale;

  return LANDING_DEFAULT_LOCALE;
}

const LandingPage = ({ initialLocale }: { initialLocale?: LandingLocale } = {}) => {
  const shouldReduceMotion = useReducedMotion();
  const [locale, setLocale] = useState<LandingLocale>(() => getInitialLocale(initialLocale));
  const copy = LANDING_COPY[locale];

  useEffect(() => {
    document.documentElement.lang = copy.seo.htmlLang;
    document.title = copy.seo.title;
    setMeta('description', copy.seo.description);
    setMeta('twitter:title', copy.seo.ogTitle);
    setMeta('twitter:description', copy.seo.ogDescription);
    setPropertyMeta('og:title', copy.seo.ogTitle);
    setPropertyMeta('og:description', copy.seo.ogDescription);
    setPropertyMeta('og:locale', copy.seo.ogLocale);
    setPropertyMeta('og:url', getLandingUrl(locale));
    setPropertyMeta('og:image', LANDING_OG_IMAGE_URL);
    setMeta('twitter:image', LANDING_OG_IMAGE_URL);
    setCanonical(getLandingUrl(locale));
    setAlternateLinks();
    setJsonLd(copy, locale);
    window.localStorage.setItem('swimvpn_landing_locale', locale);
  }, [copy, locale]);

  const setLanguage = (nextLocale: LandingLocale) => {
    setLocale(nextLocale);
    // Path-based switch ("/" <-> "/fr") to match the prerendered, indexable URLs. SPA replaceState
    // (no reload); the effect above re-syncs <head>. The legacy ?lang is dropped to avoid duplicates.
    const url = new URL(window.location.href);
    window.history.replaceState({}, '', `${getLandingPath(nextLocale)}${url.hash}`);
  };

  const motionProps = shouldReduceMotion
    ? {}
    : {
        initial: { opacity: 0, y: 18, scale: 0.985 },
        whileInView: { opacity: 1, y: 0, scale: 1 },
        viewport: { once: true, margin: '-80px' },
        transition: { duration: 0.62, ease: [0.22, 1, 0.36, 1] as [number, number, number, number] },
      };

  return (
    <div className={ui.page}>
      <div className="pointer-events-none fixed inset-0 z-0">
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_18%_10%,rgba(255,255,255,0.92),transparent_20%),radial-gradient(circle_at_68%_18%,rgba(184,154,255,0.22),transparent_34%),radial-gradient(circle_at_92%_64%,rgba(93,59,216,0.28),transparent_32%),linear-gradient(135deg,#F7F6FB_0%,#EEEAF6_38%,#07070B_39%,#010101_100%)]" />
        <div className="absolute inset-0 bg-[linear-gradient(rgba(16,16,22,0.045)_1px,transparent_1px),linear-gradient(90deg,rgba(16,16,22,0.035)_1px,transparent_1px)] bg-[size:86px_86px] opacity-55" />
        <div className="absolute inset-x-0 bottom-0 h-1/2 bg-gradient-to-t from-black via-black/60 to-transparent" />
      </div>

      <div className={ui.shell}>
        <nav className="relative z-30 mb-3 flex items-center justify-between gap-2 rounded-[1.5rem] border border-black/[0.08] bg-white/70 px-3 py-2.5 shadow-[0_18px_60px_rgba(24,20,38,0.16)] backdrop-blur-2xl sm:mb-4 sm:rounded-[2rem] sm:px-6 sm:py-3">
          <a href="#" className="flex min-w-0 items-center gap-2.5 sm:gap-3" aria-label={copy.nav.ariaHome}>
            <span className="relative flex h-9 w-9 shrink-0 items-center justify-center rounded-2xl bg-black shadow-[0_14px_34px_rgba(0,0,0,0.24)] sm:h-10 sm:w-10">
              <img src="/brand/swimvpn-shark-mark.svg" alt="" className="h-6 w-6 object-contain sm:h-7 sm:w-7" />
            </span>
            <span className="hidden truncate text-lg font-black tracking-[-0.04em] text-black min-[520px]:inline min-[520px]:text-xl lg:inline">SWIMVPN</span>
          </a>

          <div className="hidden items-center gap-8 text-sm font-black text-black/70 lg:flex">
            <a href="#product" className="transition hover:text-black">{copy.nav.product}</a>
            <a href="#setup" className="transition hover:text-black">{copy.nav.setup}</a>
            <a href="#payments" className="transition hover:text-black">{copy.nav.payments}</a>
            <a href="#download-apk" className="transition hover:text-black">{copy.nav.apk}</a>
          </div>

          <div className="flex shrink-0 items-center gap-2">
            <div className="flex rounded-2xl border border-black/[0.08] bg-white/54 p-1 shadow-[0_10px_28px_rgba(35,29,58,0.08)] backdrop-blur-xl" aria-label={copy.nav.language}>
              {LANDING_LOCALES.map((item) => (
                <button
                  key={item}
                  type="button"
                  aria-pressed={item === locale}
                  onClick={() => setLanguage(item)}
                  className={`min-h-8 rounded-xl px-2.5 text-[0.68rem] font-black transition sm:min-h-9 sm:px-3 sm:text-xs ${
                    item === locale ? 'bg-black text-white shadow-[0_10px_24px_rgba(0,0,0,0.18)]' : 'text-black/54 hover:text-black'
                  }`}
                >
                  {LANDING_LOCALE_LABELS[item]}
                </button>
              ))}
            </div>
            <a
              href="/downloads/swimvpn.apk"
              download="SwimVPN.apk"
              className="hidden min-h-11 items-center justify-center gap-2 rounded-2xl bg-black px-5 text-sm font-black text-white shadow-[0_16px_38px_rgba(0,0,0,0.28)] transition hover:-translate-y-0.5 hover:bg-[#17171C] sm:inline-flex"
            >
              {copy.nav.install}
              <ArrowUpRight size={16} strokeWidth={2.5} />
            </a>
          </div>
        </nav>

        <main className="grid grid-cols-1 gap-3 sm:gap-4 lg:grid-cols-12 lg:auto-rows-[minmax(150px,auto)]">
          <motion.section {...motionProps} className={`${ui.lightCard} min-h-[540px] p-6 sm:min-h-[620px] sm:p-10 max-lg:landscape:min-h-[360px] max-lg:landscape:p-8 lg:col-span-7 lg:row-span-3 lg:p-12`}>
            <div className="absolute right-[-12%] top-[9%] hidden h-[72%] w-[55%] rounded-[3rem] bg-[linear-gradient(145deg,rgba(255,255,255,0.58),rgba(17,17,24,0.22)_48%,rgba(138,106,241,0.28))] shadow-[inset_0_1px_0_rgba(255,255,255,0.75),0_28px_90px_rgba(74,62,113,0.28)] backdrop-blur-xl lg:block" />
            <GlassStackVisual />

            <div className="relative flex h-full flex-col justify-between gap-12 sm:gap-16">
              <div>
                <div className="mb-7 flex flex-wrap items-center gap-2.5 sm:mb-8 sm:gap-3">
                  <span className={ui.chipDark}>{copy.hero.chip}</span>
                  <span className="rounded-full border border-black/[0.08] bg-white/55 px-3.5 py-2 text-xs font-bold text-[#363240] backdrop-blur-xl sm:px-4 sm:text-sm">
                    {copy.hero.announcement}
                    <ArrowUpRight className="ml-2 inline" size={15} />
                  </span>
                </div>
                <h1 className="max-w-[760px] text-[clamp(3rem,14vw,9.2rem)] font-black leading-[0.86] tracking-[-0.08em] text-black max-lg:landscape:text-[clamp(2.7rem,10vw,5.4rem)] sm:leading-[0.84]">
                  {copy.hero.title}
                </h1>
                <p className="mt-6 max-w-xl text-base font-medium leading-relaxed text-[#5E5A68] sm:mt-7 sm:text-xl">{copy.hero.body}</p>
              </div>

              <div>
                <div className="mb-8 flex flex-col gap-3 sm:mb-9 sm:flex-row">
                  <motion.a
                    href="/downloads/swimvpn.apk"
                    download="SwimVPN.apk"
                    aria-label={copy.hero.primaryCta}
                    whileHover={shouldReduceMotion ? undefined : { scale: 1.025, y: -2 }}
                    whileTap={shouldReduceMotion ? undefined : { scale: 0.97 }}
                    className="inline-flex min-h-[3.25rem] items-center justify-center gap-3 rounded-2xl bg-black px-7 text-sm font-black text-white shadow-[0_22px_50px_rgba(0,0,0,0.24)] transition hover:bg-[#17171C] sm:min-h-14 sm:px-8 sm:text-base"
                  >
                    {copy.hero.primaryCta}
                    <Download size={21} strokeWidth={3} />
                  </motion.a>
                  <a
                    href="#product"
                    className="inline-flex min-h-[3.25rem] items-center justify-center gap-3 rounded-2xl border border-black/[0.08] bg-white/58 px-7 text-sm font-black text-black shadow-[0_16px_34px_rgba(35,29,58,0.08)] backdrop-blur-xl transition hover:bg-white sm:min-h-14 sm:px-8 sm:text-base"
                  >
                    {copy.hero.secondaryCta}
                    <CheckCircle2 size={19} />
                  </a>
                </div>
                <div className="text-sm font-bold text-[#625B70]">{copy.hero.proofLabel}</div>
                <div className="mt-4 flex flex-wrap gap-3">
                  {copy.hero.proofItems.map((item) => (
                    <span key={item} className="rounded-full border border-black/[0.08] bg-white/55 px-4 py-2 text-sm font-black text-black/62 shadow-[0_10px_28px_rgba(35,29,58,0.08)] backdrop-blur-xl">
                      {item}
                    </span>
                  ))}
                </div>
              </div>
            </div>
          </motion.section>

          <motion.article {...motionProps} id="product" className={`${ui.darkCard} min-h-[330px] p-7 lg:col-span-5 lg:row-span-2`}>
            <div className="absolute -right-12 -top-16 h-56 w-56 rounded-full bg-[#8A6AF1]/28 blur-[78px]" />
            <div className="relative mb-7 text-xs font-black uppercase tracking-[0.2em] text-[#B89AFF]">{copy.product.eyebrow}</div>
            <h2 className="relative max-w-xl text-4xl font-black leading-[0.95] tracking-[-0.055em] text-white sm:text-5xl">
              {copy.product.title}
            </h2>
            <p className={`relative mt-6 max-w-2xl text-base leading-relaxed ${ui.darkMuted}`}>{copy.product.text}</p>
          </motion.article>

          <motion.article {...motionProps} className={`${ui.lightCard} min-h-[230px] p-7 lg:col-span-2`}>
            <div className="text-6xl font-black tracking-[-0.08em] text-black">
              APK<span className="ml-2 align-top text-2xl text-[#18A86B]">↗</span>
            </div>
            <p className="mt-10 text-sm font-semibold leading-relaxed text-[#625B70]">{copy.trial.label}</p>
          </motion.article>

          {copy.product.reasons.map((reason, idx) => (
            <motion.article
              key={reason.title}
              {...motionProps}
              className={`${idx === 1 ? ui.darkCard : ui.lightCard} min-h-[220px] p-7 lg:col-span-4`}
            >
              <div className={idx === 1 ? 'mb-10 text-5xl font-black text-white/10' : 'mb-10 text-5xl font-black text-black/10'}>
                0{idx + 1}
              </div>
              <h3 className="text-2xl font-black tracking-[-0.04em]">{reason.title}</h3>
              <p className={`mt-4 text-sm font-medium leading-relaxed ${idx === 1 ? ui.darkMuted : ui.muted}`}>{reason.text}</p>
            </motion.article>
          ))}

          <motion.article {...motionProps} id="setup" className={`${ui.lightCard} min-h-[330px] p-7 lg:col-span-6 lg:row-span-2`}>
            <div className="grid h-full gap-8 lg:grid-cols-[0.9fr_1.1fr]">
              <div>
                <QrCode className="mb-8 text-[#7B57E8]" size={36} strokeWidth={1.8} />
                <h2 className="text-4xl font-black leading-[0.96] tracking-[-0.055em] text-black">{copy.setup.title}</h2>
                <p className="mt-5 text-sm font-medium leading-relaxed text-[#625B70]">{copy.setup.text}</p>
              </div>
              <div className="relative overflow-hidden rounded-[1.5rem] border border-black/[0.08] bg-white/62 p-4 shadow-[0_18px_46px_rgba(31,24,54,0.12)] backdrop-blur-xl">
                <div className="mb-4 flex items-center justify-between">
                  <div className="flex items-center gap-2 text-xs font-black text-black">
                    <img src="/brand/swimvpn-shark-mark.svg" alt="" className="h-5 w-5 object-contain" />
                    SWIMVPN
                  </div>
                  <div className="h-7 w-28 rounded-full bg-[#F0ECF7]" />
                </div>
                <div className="space-y-3">
                  {copy.setup.steps.map((step, idx) => (
                    <div key={step} className="flex items-center gap-3 rounded-2xl border border-black/[0.06] bg-white/72 p-4">
                      <span className={idx === 1 ? 'h-3 w-3 rounded-full bg-[#8A6AF1]' : 'h-3 w-3 rounded-full border border-[#8A6AF1]/50'} />
                      <span className="text-sm font-black text-[#363240]">{step}</span>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </motion.article>

          <motion.article {...motionProps} className={`${ui.darkCard} min-h-[330px] p-7 lg:col-span-3 lg:row-span-2`}>
            <ShoppingBag className="mb-10 text-[#B89AFF]" size={36} strokeWidth={1.8} />
            <h2 className="text-3xl font-black leading-tight tracking-[-0.04em]">{copy.business.title}</h2>
            <p className={`mt-5 text-sm leading-relaxed ${ui.darkMuted}`}>{copy.business.text}</p>
          </motion.article>

          <motion.article {...motionProps} className={`${ui.lightCard} min-h-[330px] p-7 lg:col-span-3 lg:row-span-2`}>
            <RadioTower className="mb-8 text-[#7B57E8]" size={36} strokeWidth={1.8} />
            <h2 className="text-3xl font-black leading-tight tracking-[-0.05em]">{copy.compatibility.title}</h2>
            <p className="mt-5 text-sm font-medium leading-relaxed text-[#625B70]">{copy.compatibility.text}</p>
            <div className="mt-8 flex flex-wrap gap-2">
              {copy.compatibility.chips.map((chip) => (
                <span key={chip} className="rounded-full border border-black/[0.08] bg-white/70 px-3 py-2 text-xs font-black text-black/64 shadow-[0_10px_22px_rgba(35,29,58,0.08)]">
                  {chip}
                </span>
              ))}
            </div>
          </motion.article>

          <motion.article {...motionProps} className={`${ui.purpleCard} min-h-[260px] p-7 lg:col-span-4`}>
            <Bot className="mb-8 text-white" size={34} />
            <h2 className="text-3xl font-black leading-tight tracking-[-0.04em]">{copy.ai.title}</h2>
            <p className="mt-5 text-sm font-semibold leading-relaxed text-white/78">{copy.ai.text}</p>
            <div className="mt-7 inline-flex rounded-full bg-white/14 px-4 py-2 text-xs font-black text-white backdrop-blur">
              {copy.ai.note}
            </div>
          </motion.article>

          <motion.article {...motionProps} className={`${ui.darkCard} min-h-[260px] p-7 lg:col-span-4`}>
            <Sparkles className="mb-8 text-[#B89AFF]" size={34} />
            <h2 className="text-3xl font-black leading-tight tracking-[-0.04em]">{copy.trial.title}</h2>
            <p className={`mt-5 text-sm leading-relaxed ${ui.darkMuted}`}>{copy.trial.text}</p>
          </motion.article>

          <motion.article {...motionProps} id="payments" className={`${ui.lightCard} min-h-[260px] p-7 lg:col-span-4`}>
            <div className="mb-6 text-sm font-black uppercase tracking-[0.18em] text-[#7B57E8]">{copy.payments.title}</div>
            <p className="mb-6 text-sm font-medium leading-relaxed text-[#625B70]">{copy.payments.text}</p>
            <div className="mb-6">
              <div className="flex items-baseline gap-2">
                <span className="text-sm font-bold text-[#625B70]">{copy.payments.priceLabel}</span>
                <span className="text-4xl font-black tracking-[-0.05em] text-black">${USD_PRICE}</span>
                <span className="text-sm font-bold text-[#625B70]">{copy.payments.priceSuffix}</span>
              </div>
              <div className="mt-2 text-xs font-bold text-[#7B57E8]">
                {copy.payments.swimpayVia} {SWIMPAY_CURRENCIES.join(' · ')}
              </div>
            </div>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              {copy.payments.methods.map((method) => (
                <div key={method.name} className="rounded-2xl bg-white p-5 shadow-[0_14px_34px_rgba(35,29,58,0.10)]">
                  <div className="mb-5 flex h-12 w-12 items-center justify-center rounded-2xl border border-black/[0.05] bg-[#F7F5FC] shadow-[inset_0_1px_0_rgba(255,255,255,0.9),0_10px_24px_rgba(35,29,58,0.08)]">
                    <img src={paymentIconSrc[method.icon]} alt="" className="h-10 w-10 object-contain" />
                  </div>
                  <div className="font-black">{method.name}</div>
                  <p className="mt-2 text-xs font-semibold leading-relaxed text-[#625B70]">{method.text}</p>
                </div>
              ))}
            </div>
          </motion.article>

          <motion.section {...motionProps} id="download-apk" className={`${ui.lightCard} p-6 sm:p-10 lg:col-span-12`}>
            <div className="grid gap-8 xl:grid-cols-[minmax(280px,0.95fr)_minmax(0,1.6fr)] xl:items-center">
              <div>
                <h2 className="max-w-md text-3xl font-black leading-tight tracking-[-0.05em] sm:text-4xl">{copy.finalCta.title}</h2>
                <a
                  href="/downloads/swimvpn.apk"
                  download="swimvpn.apk"
                  className="mt-7 inline-flex min-h-12 items-center justify-center gap-3 rounded-2xl bg-black px-6 text-sm font-black text-white shadow-[0_16px_40px_rgba(0,0,0,0.22)]"
                >
                  {copy.hero.primaryCta}
                  <Download size={18} />
                </a>
              </div>
              <div className="grid min-w-0 grid-cols-2 gap-3 sm:gap-4 xl:grid-cols-4">
                {copy.finalCta.stats.map(([value, label]) => (
                  <div key={value} className="min-w-0 rounded-3xl border border-black/[0.06] bg-white/62 p-4 shadow-[0_14px_34px_rgba(35,29,58,0.08)] backdrop-blur-xl sm:p-6">
                    <div className="break-words text-[clamp(1.15rem,5vw,1.5rem)] font-black tracking-[-0.05em]">{value}</div>
                    <div className="mt-2 text-xs font-semibold leading-snug text-[#625B70] sm:text-sm">{label}</div>
                  </div>
                ))}
              </div>
            </div>
          </motion.section>

          <motion.footer {...motionProps} className={`${ui.darkCard} flex min-h-[160px] flex-col justify-between gap-10 p-7 sm:flex-row sm:items-center lg:col-span-12`}>
            <div className="flex items-center gap-3">
              <img src="/brand/swimvpn-shark-mark.svg" alt="" className="h-9 w-9 object-contain" />
              <div className="text-xl font-black tracking-[-0.04em]">SWIMVPN</div>
            </div>
            <div className="flex flex-wrap gap-4 text-sm font-bold text-[#B9B4C7]">
              <a href="#privacy" className="hover:text-white">{copy.footer.privacy}</a>
              <a href="#terms" className="hover:text-white">{copy.footer.terms}</a>
              <a href="mailto:support@swimvpn.pro" className="hover:text-white">{copy.footer.support}</a>
            </div>
          </motion.footer>
        </main>
      </div>
    </div>
  );
};

function setMeta(name: string, content: string) {
  document.querySelector<HTMLMetaElement>(`meta[name="${name}"]`)?.setAttribute('content', content);
}

function setPropertyMeta(property: string, content: string) {
  document.querySelector<HTMLMetaElement>(`meta[property="${property}"]`)?.setAttribute('content', content);
}

function setCanonical(href: string) {
  document.querySelector<HTMLLinkElement>('link[rel="canonical"]')?.setAttribute('href', href);
}

function setAlternateLinks() {
  LANDING_LOCALES.forEach((item) => {
    document.querySelector<HTMLLinkElement>(`link[rel="alternate"][hreflang="${item}"]`)?.setAttribute('href', getLandingUrl(item));
  });
  document.querySelector<HTMLLinkElement>('link[rel="alternate"][hreflang="x-default"]')?.setAttribute('href', getLandingUrl(LANDING_DEFAULT_LOCALE));
}

function setJsonLd(copy: (typeof LANDING_COPY)[LandingLocale], locale: LandingLocale) {
  const element = document.querySelector<HTMLScriptElement>('script[type="application/ld+json"]');
  if (!element) return;

  element.textContent = JSON.stringify({
    '@context': 'https://schema.org',
    '@type': 'SoftwareApplication',
    name: 'SWIMVPN',
    alternateName: 'SWIMVPN Android VPN',
    inLanguage: [copy.seo.htmlLang],
    operatingSystem: 'Android',
    applicationCategory: 'SecurityApplication',
    description: copy.seo.description,
    downloadUrl: LANDING_DOWNLOAD_URL,
    url: getLandingUrl(locale),
    image: LANDING_OG_IMAGE_URL,
    offers: {
      '@type': 'Offer',
      price: USD_PRICE,
      priceCurrency: 'USD',
      availability: 'https://schema.org/InStock',
    },
    publisher: {
      '@type': 'Organization',
      name: 'SWIMVPN',
      url: getLandingUrl(LANDING_DEFAULT_LOCALE),
      logo: LANDING_OG_IMAGE_URL,
    },
  });
}

function GlassStackVisual() {
  return (
    <div aria-hidden="true" className="pointer-events-none absolute right-5 top-28 hidden h-[360px] w-[390px] lg:block">
      <div className="absolute left-20 top-0 h-64 w-48 rounded-[1.6rem] border border-white/45 bg-white/20 shadow-[inset_0_1px_0_rgba(255,255,255,0.9),0_28px_80px_rgba(26,23,41,0.25)] backdrop-blur-xl" />
      <div className="absolute left-16 top-40 h-20 w-64 -skew-y-6 rounded-[1.4rem] border border-black/15 bg-[linear-gradient(135deg,rgba(255,255,255,0.52),rgba(8,8,12,0.22)_48%,rgba(138,106,241,0.45))] shadow-[0_28px_70px_rgba(44,35,77,0.28)]" />
      <div className="absolute left-0 top-[13.75rem] h-24 w-80 -skew-y-6 rounded-[1.6rem] border border-black/18 bg-[linear-gradient(135deg,rgba(255,255,255,0.32),rgba(14,14,20,0.36)_52%,rgba(93,59,216,0.52))] shadow-[0_32px_86px_rgba(44,35,77,0.34)]" />
      <div className="absolute bottom-0 left-2 h-20 w-72 -skew-y-6 rounded-[1.5rem] border border-black/20 bg-[linear-gradient(135deg,rgba(255,255,255,0.18),rgba(7,7,11,0.48)_54%,rgba(93,59,216,0.38))] shadow-[0_34px_80px_rgba(0,0,0,0.28)]" />
      <div className="absolute left-56 top-52 h-16 w-16 rounded-full bg-[#8A6AF1]/55 blur-[34px]" />
    </div>
  );
}

export default LandingPage;

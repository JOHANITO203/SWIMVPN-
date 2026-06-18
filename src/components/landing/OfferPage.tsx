import React, { useEffect, useRef, useState } from 'react';
import {
  LANDING_BASE_URL,
  LANDING_DEFAULT_LOCALE,
  LandingLocale,
  isLandingLocale,
} from './landingContent';

// ── Config — wire when Tribute onboarding completes ──
const API_BASE = `${LANDING_BASE_URL.replace(/^https?:\/\/app\./, 'https://api.')}/api/v1`; // https://api.swimvpn.pro/api/v1
const BOT_USERNAME = 'SwimVpnLoginBot'; // @bot of the Login Widget (placeholder)
const TRIBUTE_LINKS: Record<string, string> = {
  WEEK: 'https://web.tribute.tg/p/WEEK_PLACEHOLDER',
  MONTH: 'https://web.tribute.tg/p/MONTH_PLACEHOLDER',
  QUARTER: 'https://web.tribute.tg/p/QUARTER_PLACEHOLDER',
};

type Plan = { id?: string; code: string; name: string; duration_label: string; price_usd: string; quota_label: string; badge?: string };
type TgUser = { id: number | string; first_name?: string; username?: string; auth_date: number | string; hash: string };

const FALLBACK_PLANS: Plan[] = [
  { code: 'WEEK', name: 'Semaine', duration_label: '7 jours', price_usd: '3.49', quota_label: 'Illimité' },
  { code: 'MONTH', name: 'Mois', duration_label: '30 jours', price_usd: '7.99', quota_label: 'Illimité', badge: 'Populaire' },
  { code: 'QUARTER', name: 'Trimestre', duration_label: '91 jours', price_usd: '21.99', quota_label: 'Illimité', badge: '-8%' },
];

const COPY: Record<LandingLocale, Record<string, string>> = {
  fr: {
    eyebrow: 'Accès SWIMVPN',
    title: "Obtenir l'accès",
    sub: "Choisis ta formule, lie ton compte Telegram, et paie en un geste via Tribute. Ton accès t'est livré par email dès la confirmation.",
    step1: '1 · Formule',
    step2: '2 · Email de livraison',
    step3: '3 · Lier Telegram',
    payer: 'Le payeur',
    emailErr: 'Entre une adresse email valide.',
    tgBtn: 'Continuer avec Telegram',
    tgLinked: 'Telegram lié',
    verified: 'Compte vérifié',
    change: 'changer',
    cta: 'Payer via Tribute →',
    preparing: 'Préparation…',
    note: "Le paiement s'effectue dans Telegram via Tribute. On relie ton compte Telegram à ton email pour t'activer automatiquement — aucune autre donnée n'est collectée.",
    back: '← Retour',
    okTitle: 'Commande enregistrée',
    okSub: 'Termine le paiement dans Telegram. Dès qu\'il est confirmé, ton accès part vers',
    okPending: 'Commande en attente de paiement',
    okNote: "L'onglet Tribute s'est ouvert. Reviens ici après le paiement.",
    unavailTitle: 'Paiement bientôt disponible',
    unavailSub: "Le paiement par Tribute n'est pas encore actif. En attendant, télécharge l'app gratuitement et utilise l'essai ou ta propre config.",
    unavailApk: "↓ Télécharger l'APK",
  },
  ru: {
    eyebrow: 'Доступ SWIMVPN',
    title: 'Получить доступ',
    sub: 'Выберите тариф, привяжите Telegram и оплатите в один клик через Tribute. Доступ придёт на email сразу после подтверждения.',
    step1: '1 · Тариф',
    step2: '2 · Email для доставки',
    step3: '3 · Привязать Telegram',
    payer: 'Плательщик',
    emailErr: 'Введите корректный email.',
    tgBtn: 'Продолжить через Telegram',
    tgLinked: 'Telegram привязан',
    verified: 'Аккаунт подтверждён',
    change: 'изменить',
    cta: 'Оплатить через Tribute →',
    preparing: 'Подготовка…',
    note: 'Оплата проходит в Telegram через Tribute. Мы связываем ваш Telegram с email для автоматической активации — больше никакие данные не собираются.',
    back: '← Назад',
    okTitle: 'Заказ создан',
    okSub: 'Завершите оплату в Telegram. Как только она подтвердится, доступ уйдёт на',
    okPending: 'Заказ ожидает оплаты',
    okNote: 'Вкладка Tribute открылась. Вернитесь сюда после оплаты.',
    unavailTitle: 'Оплата скоро будет доступна',
    unavailSub: 'Оплата через Tribute пока не активна. А пока скачайте приложение бесплатно и используйте пробный период или свою конфигурацию.',
    unavailApk: '↓ Скачать APK',
  },
};

const APK_URL = `${LANDING_BASE_URL}/downloads/swimvpn.apk`;

function detectLocale(): LandingLocale {
  if (typeof window === 'undefined') return LANDING_DEFAULT_LOCALE;
  const p = window.location.pathname.split('/').filter(Boolean)[0];
  if (isLandingLocale(p)) return p;
  const s = window.localStorage.getItem('swimvpn_landing_locale');
  if (isLandingLocale(s)) return s;
  return LANDING_DEFAULT_LOCALE;
}

const validEmail = (v: string) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v);

export default function OfferPage() {
  const t = COPY[detectLocale()];
  const [plans, setPlans] = useState<Plan[]>(FALLBACK_PLANS);
  const [planId, setPlanId] = useState<string | null>(null);
  const [planCode, setPlanCode] = useState<string | null>(null);
  const [email, setEmail] = useState('');
  const [emailErr, setEmailErr] = useState(false);
  const [tg, setTg] = useState<TgUser | null>(null);
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState<{ email: string; ref: string } | null>(null);
  const [unavailable, setUnavailable] = useState(false);
  const tgSlot = useRef<HTMLDivElement>(null);

  // prefill email from the landing's final CTA, fetch live plans, mount Telegram widget
  useEffect(() => {
    const stored = window.localStorage.getItem('swimvpn_offer_email');
    if (stored) setEmail(stored);

    fetch(`${API_BASE}/store/plans`, { headers: { Accept: 'application/json' } })
      .then((r) => (r.ok ? r.json() : Promise.reject()))
      .then((data) => { if (Array.isArray(data) && data.length) setPlans(data); })
      .catch(() => { /* offline / CORS — keep fallback */ });

    // Telegram Login Widget: global callback + script injection
    (window as any).onTelegramAuth = (user: TgUser) => setTg(user);
    const s = document.createElement('script');
    s.src = 'https://telegram.org/js/telegram-widget.js?22';
    s.async = true;
    s.setAttribute('data-telegram-login', BOT_USERNAME);
    s.setAttribute('data-size', 'large');
    s.setAttribute('data-onauth', 'onTelegramAuth(user)');
    s.setAttribute('data-request-access', 'write');
    tgSlot.current?.appendChild(s);
    return () => { delete (window as any).onTelegramAuth; };
  }, []);

  const selectPlan = (p: Plan) => { setPlanId(p.id || p.code); setPlanCode(p.code); };
  const mockTg = () => setTg({ id: '00000000', first_name: 'Demo', username: 'demo_user', auth_date: Math.floor(Date.now() / 1000), hash: 'MOCK_HASH' });
  const ready = !!planId && validEmail(email) && !!tg;

  const proceed = async () => {
    if (!validEmail(email)) { setEmailErr(true); return; }
    setBusy(true);
    let ref: string | null = null;
    let redirectUrl: string | null = null;
    try {
      const r = await fetch(`${API_BASE}/orders/checkout`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
        body: JSON.stringify({
          email,
          planId,
          paymentMethod: 'TRIBUTE',
          telegramUserId: String(tg!.id),
          telegramAuth: tg,
        }),
      });
      if (r.ok) { const d = await r.json(); ref = d.orderRef || null; redirectUrl = d.redirectUrl || null; }
    } catch { /* network / offline */ }

    // Honest gating: only show success when the backend actually created the order.
    // Before Tribute is wired (no API key / old backend image), fall back to "soon".
    if (!ref) { setUnavailable(true); setBusy(false); return; }

    const link = redirectUrl || (planCode ? TRIBUTE_LINKS[planCode] : null);
    if (link && !link.includes('PLACEHOLDER')) window.open(link, '_blank', 'noopener');
    setDone({ email, ref });
    setBusy(false);
  };

  return (
    <div className="relative min-h-screen overflow-x-hidden bg-[#07070C] text-white">
      {/* atmosphere */}
      <div
        className="pointer-events-none fixed inset-0 z-0"
        style={{
          background:
            'radial-gradient(ellipse 60% 50% at 20% 0%,rgba(139,92,246,.18),transparent 60%),radial-gradient(ellipse 50% 50% at 90% 30%,rgba(56,189,248,.10),transparent 60%),radial-gradient(ellipse 80% 60% at 50% 110%,rgba(139,92,246,.12),transparent 70%)',
        }}
      />
      <header className="relative z-10 flex items-center justify-between px-4 py-5 sm:px-12">
        <a href="#" className="flex items-center gap-2.5">
          <span className="h-[26px] w-[26px] rounded-[7px] bg-gradient-to-br from-[#B89AFF] via-[#8A6AF1] to-[#5D3BD8]" />
          <span className="text-[17px] font-semibold tracking-[-0.02em]">SWIMVPN</span>
        </a>
        <a href="#" className="rounded-full px-4 py-2 text-[13px] font-medium text-white/55 transition-colors hover:text-white">{t.back}</a>
      </header>

      <div className="relative z-10 mx-auto max-w-[560px] px-4 pb-20 pt-6 sm:px-6 sm:pt-10">
        {unavailable ? (
          <div className="pt-10 text-center">
            <h1 className="mb-3 text-[28px] font-bold">{t.unavailTitle}</h1>
            <p className="mx-auto mb-7 max-w-[400px] text-[15px] font-light text-white/55">{t.unavailSub}</p>
            <a href={APK_URL} download="SwimVPN.apk" className="inline-flex h-[54px] items-center justify-center rounded-[14px] bg-white px-7 text-[15px] font-semibold text-black transition-shadow hover:shadow-[0_0_32px_rgba(255,255,255,.22)]">
              {t.unavailApk}
            </a>
          </div>
        ) : !done ? (
          <>
            <div className="mb-3 text-[11px] font-semibold uppercase tracking-[0.18em] text-[#8B5CF6]/90">{t.eyebrow}</div>
            <h1 className="mb-3 text-[clamp(30px,7vw,42px)] font-bold leading-[1.05] tracking-[-0.04em]">{t.title}</h1>
            <p className="mb-9 max-w-[440px] text-[15px] font-light leading-[1.6] text-white/50">{t.sub}</p>

            {/* STEP 1 — plans */}
            <span className="mb-2 block text-[13px] font-medium text-white/70">{t.step1}</span>
            <div className="mb-8 flex flex-col gap-2.5">
              {plans.map((p) => {
                const sel = planId === (p.id || p.code);
                return (
                  <button
                    key={p.code}
                    type="button"
                    onClick={() => selectPlan(p)}
                    className={`relative flex w-full items-center gap-4 rounded-[18px] border p-[18px] text-left transition-colors ${sel ? 'border-[#8B5CF6]/50 bg-[#8B5CF6]/10' : 'border-white/[0.08] bg-white/[0.04] hover:border-white/[0.14] hover:bg-white/[0.07]'}`}
                  >
                    {p.badge && <span className="absolute -top-[9px] right-4 rounded-full bg-[#8B5CF6]/90 px-2.5 py-[3px] text-[10px] font-semibold uppercase tracking-[0.08em]">{p.badge}</span>}
                    <span className={`h-5 w-5 flex-shrink-0 rounded-full border-2 ${sel ? 'border-[#8B5CF6]' : 'border-white/25'} relative`}>
                      {sel && <span className="absolute inset-[3px] rounded-full bg-[#8B5CF6]" />}
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block text-[16px] font-semibold tracking-[-0.01em]">{p.name}</span>
                      <span className="mt-0.5 block text-[13px] font-light text-white/45">{p.duration_label} · {p.quota_label}</span>
                    </span>
                    <span className="flex-shrink-0 text-right">
                      <span className="block text-[19px] font-bold tracking-[-0.02em]">${p.price_usd}</span>
                      <span className="mt-px block text-[12px] text-white/40">USD</span>
                    </span>
                  </button>
                );
              })}
            </div>

            {/* STEP 2 — email */}
            <span className="mb-2 block text-[13px] font-medium text-white/70">{t.step2}</span>
            <input
              type="email"
              value={email}
              onChange={(e) => { setEmail(e.target.value.trim()); setEmailErr(false); }}
              placeholder="votre@email.com"
              autoComplete="email"
              inputMode="email"
              className={`h-[54px] w-full rounded-[14px] border bg-white/[0.08] px-[18px] text-[15px] text-white outline-none backdrop-blur transition-colors placeholder:text-white/30 focus:bg-white/[0.12] ${emailErr ? 'border-red-500/60' : 'border-white/[0.18] focus:border-white/[0.36]'}`}
            />
            {emailErr && <div className="mt-1.5 text-[12px] text-red-400/90">{t.emailErr}</div>}

            {/* STEP 3 — Telegram */}
            <span className="mb-2 mt-7 block text-[13px] font-medium text-white/70">{t.step3} <span className="font-light text-white/35">({t.payer})</span></span>
            {!tg ? (
              <div>
                <div ref={tgSlot} className="mb-2" />
                <button
                  type="button"
                  onClick={mockTg}
                  className="flex h-[56px] w-full items-center justify-center gap-3 rounded-[14px] bg-[#229ED9] text-[16px] font-semibold text-white transition-shadow hover:shadow-[0_0_28px_rgba(34,158,217,.4)]"
                >
                  <svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M11.94 2.5a9.5 9.5 0 100 19 9.5 9.5 0 000-19zm4.4 6.46l-1.47 6.95c-.11.49-.4.61-.81.38l-2.24-1.65-1.08 1.04c-.12.12-.22.22-.45.22l.16-2.28 4.15-3.75c.18-.16-.04-.25-.28-.09l-5.13 3.23-2.21-.69c-.48-.15-.49-.48.1-.71l8.63-3.33c.4-.15.75.09.62.71z" /></svg>
                  {t.tgBtn}
                </button>
              </div>
            ) : (
              <div className="flex w-full items-center gap-3 rounded-[14px] border border-green-500/40 bg-green-500/10 px-[18px] py-[15px]">
                <svg className="h-[22px] w-[22px] flex-shrink-0 text-green-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round"><path d="M20 6L9 17l-5-5" /></svg>
                <div className="min-w-0 flex-1">
                  <div className="text-[14px] font-semibold">{tg.username ? '@' + tg.username : tg.first_name || t.tgLinked}</div>
                  <div className="text-[12px] font-light text-white/50">{t.verified} · ID {tg.id}</div>
                </div>
                <button type="button" onClick={() => setTg(null)} className="text-[12px] text-white/50 underline">{t.change}</button>
              </div>
            )}

            <button
              type="button"
              disabled={!ready || busy}
              onClick={proceed}
              className="mt-6 flex h-[58px] w-full items-center justify-center rounded-[14px] bg-white text-[16px] font-semibold text-black transition-all enabled:hover:shadow-[0_0_32px_rgba(255,255,255,.22)] disabled:cursor-not-allowed disabled:opacity-[0.35]"
            >
              {busy ? t.preparing : t.cta}
            </button>

            <div className="mt-[22px] flex items-start gap-2.5 text-[12.5px] font-light leading-[1.55] text-white/40">
              <svg className="mt-0.5 h-3.5 w-3.5 flex-shrink-0 text-white/35" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="11" width="18" height="11" rx="2" /><path d="M7 11V7a5 5 0 0110 0v4" /></svg>
              <span>{t.note}</span>
            </div>
          </>
        ) : (
          <div className="pt-10 text-center">
            <div className="mx-auto mb-7 flex h-[84px] w-[84px] items-center justify-center rounded-full border-2 border-green-500/40 bg-green-500/[0.12]">
              <svg className="h-10 w-10 text-green-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M20 6L9 17l-5-5" /></svg>
            </div>
            <h1 className="mb-2.5 text-[28px] font-bold">{t.okTitle}</h1>
            <p className="mx-auto mb-2 max-w-[380px] text-[15px] font-light text-white/50">{t.okSub} <strong className="font-medium text-white">{done.email}</strong>.</p>
            <div className="mt-4 inline-block rounded-[10px] border border-[#8B5CF6]/25 bg-[#8B5CF6]/10 px-[18px] py-2.5 font-mono text-[15px] tracking-[0.05em] text-[#8B5CF6]">{done.ref}</div>
            <p className="mx-auto mt-6 max-w-[380px] text-[12.5px] font-light text-white/40">{t.okNote}</p>
          </div>
        )}
      </div>
    </div>
  );
}

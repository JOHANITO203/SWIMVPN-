import { useState, useEffect } from 'react';
import { ArrowUpRight } from 'lucide-react';
import { CINE_DOWNLOAD_URL, CINE_ANDROID_URL, CINE_APP_VERSION } from './tokens';
import { useCine } from './i18n';
import { subscribeEmail } from './api';

// Icône Windows (4 carreaux).
function WindowsIcon({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" className={className} aria-hidden>
      <rect x="2.4" y="2.4" width="8.6" height="8.6" rx="0.6" />
      <rect x="13" y="2.4" width="8.6" height="8.6" rx="0.6" />
      <rect x="2.4" y="13" width="8.6" height="8.6" rx="0.6" />
      <rect x="13" y="13" width="8.6" height="8.6" rx="0.6" />
    </svg>
  );
}

// Icône Android (tête robot) — yeux = noir des cartes pour faire des trous.
function AndroidIcon({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" className={className} aria-hidden>
      <path d="M6.8 3l1.9 2.8M17.2 3l-1.9 2.8" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" fill="none" />
      <path d="M4.8 11a7.2 7.2 0 0 1 14.4 0v6.6a1.1 1.1 0 0 1-1.1 1.1H5.9a1.1 1.1 0 0 1-1.1-1.1V11Z" fill="currentColor" />
      <circle cx="9.4" cy="10.4" r="1.05" fill="#000000" />
      <circle cx="14.6" cy="10.4" r="1.05" fill="#000000" />
    </svg>
  );
}

export default function CineSignup() {
  const { t, locale } = useCine();
  const [sent, setSent] = useState(false);
  const [email, setEmail] = useState('');
  // Le lien Android suit /version.json (source unique màj par la CI de release) : toujours la
  // dernière APK sans re-toucher la landing. Fallback = APK servi par la landing si le fetch échoue.
  const [androidUrl, setAndroidUrl] = useState(CINE_ANDROID_URL);
  useEffect(() => {
    let alive = true;
    fetch('/version.json', { cache: 'no-store' })
      .then((r) => (r.ok ? r.json() : null))
      .then((m) => {
        if (alive && m && typeof m.apkUrl === 'string' && m.apkUrl.startsWith('https://')) setAndroidUrl(m.apkUrl);
      })
      .catch(() => {/* offline / manifest absent → on garde le fallback */});
    return () => {
      alive = false;
    };
  }, []);
  const PLATFORMS = [
    { os: 'Windows', href: CINE_DOWNLOAD_URL, Icon: WindowsIcon },
    { os: 'Android', href: androidUrl, Icon: AndroidIcon },
  ];
  const details: Record<string, string> = { Windows: t.download.winDetail, Android: t.download.andDetail };

  return (
    <section id="cine-signup" className="relative z-50 min-h-dvh">
      <div className="mx-auto max-w-6xl px-6 pb-16 pt-32 sm:px-10 sm:pt-40 lg:px-12">
        <p className="cine-fade-up text-xs uppercase tracking-[0.3em] text-white/55" style={{ animationDelay: '0.1s' }}>
          {t.download.telechargement}
        </p>
        <h2
          className="cine-blur-up font-askan mt-4 text-4xl leading-[0.95] tracking-tight text-white sm:text-6xl md:text-7xl"
          style={{ animationDelay: '0.25s' }}
        >
          {t.download.title}
        </h2>
        <p className="cine-fade-up mt-5 max-w-md text-sm text-white/65 sm:text-base" style={{ animationDelay: '0.45s' }}>
          {t.download.sub}
        </p>

        <div className="mt-12 grid gap-6 lg:grid-cols-[1fr_300px]">
          {/* GAUCHE — cartes plateformes */}
          <div className="grid gap-6 sm:grid-cols-2">
            {PLATFORMS.map(({ os, href, Icon }) => (
              <a
                key={os}
                href={href}
                className="cine-press cine-reveal group flex flex-col items-start gap-6 border border-white/20 bg-black/70 p-8 hover:border-white/60 hover:bg-black/85 sm:p-10"
              >
                <Icon className="h-12 w-12 text-white transition-transform group-hover:scale-105" />
                <div className="w-full">
                  <span className="flex items-center justify-between font-askan text-3xl text-white">
                    {os}
                    <ArrowUpRight
                      size={22}
                      className="text-white/50 transition-transform group-hover:-translate-y-0.5 group-hover:translate-x-0.5 group-hover:text-white"
                    />
                  </span>
                  <p className="mt-2 text-xs uppercase tracking-wider text-white/55">{details[os]}</p>
                </div>
              </a>
            ))}
          </div>

          {/* DROITE — version, accès email, note paiement */}
          <aside className="cine-reveal flex flex-col gap-8 border border-white/15 bg-black/70 p-6">
            <div>
              <p className="text-[0.7rem] uppercase tracking-[0.2em] text-white/45">{t.download.version}</p>
              <p className="mt-2 text-sm font-medium text-white">{CINE_APP_VERSION}</p>
              <p className="text-[0.72rem] text-white/45">{t.download.versionSub}</p>
            </div>
            <div>
              <p className="text-[0.7rem] uppercase tracking-[0.2em] text-white/45">{t.download.accesLabel}</p>
              <form
                onSubmit={(e) => {
                  e.preventDefault();
                  if (!email.trim()) return;
                  setSent(true);
                  void subscribeEmail(email.trim(), locale, 'cine-signup');
                }}
                className="mt-3 flex flex-col gap-2"
              >
                <input
                  type="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  disabled={sent}
                  placeholder={t.download.emailPlaceholder}
                  aria-label="Email"
                  className="w-full border border-white/20 bg-black/40 px-4 py-2.5 text-sm text-white outline-none placeholder:text-white/40 focus:border-white/60"
                />
                <button
                  type="submit"
                  disabled={sent}
                  className="cine-press bg-white px-4 py-2.5 text-xs uppercase tracking-widest text-black"
                >
                  {sent ? t.download.optinPending : t.download.cta}
                </button>
              </form>
            </div>
            <div>
              <p className="text-[0.7rem] uppercase tracking-[0.2em] text-white/45">{t.download.plateformesLabel}</p>
              <ul className="mt-3 flex flex-col gap-2.5 text-sm text-white/70">
                <li>Windows 10 / 11</li>
                <li>Android 8+</li>
              </ul>
            </div>
            <p className="text-[0.72rem] leading-relaxed text-white/45">{t.download.paymentNote}</p>
          </aside>
        </div>
      </div>
    </section>
  );
}

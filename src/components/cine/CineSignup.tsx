import { useState } from 'react';
import { CINE_DOWNLOAD_URL } from './tokens';
import { useCine } from './i18n';
import { subscribeEmail } from './api';

// Icône Windows (4 carreaux) — sombre sur carte claire.
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

// Icône Android (tête robot) — yeux = couleur de la carte (#f1eee8) pour faire des trous.
function AndroidIcon({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" className={className} aria-hidden>
      <path d="M6.8 3l1.9 2.8M17.2 3l-1.9 2.8" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" fill="none" />
      <path d="M4.8 11a7.2 7.2 0 0 1 14.4 0v6.6a1.1 1.1 0 0 1-1.1 1.1H5.9a1.1 1.1 0 0 1-1.1-1.1V11Z" fill="currentColor" />
      <circle cx="9.4" cy="10.4" r="1.05" fill="#15110c" />
      <circle cx="14.6" cy="10.4" r="1.05" fill="#15110c" />
    </svg>
  );
}

const PLATFORMS = [
  { os: 'Windows', href: CINE_DOWNLOAD_URL, Icon: WindowsIcon },
  { os: 'Android', href: '/', Icon: AndroidIcon },
];

export default function CineSignup() {
  const { t, locale } = useCine();
  const [sent, setSent] = useState(false);
  const [email, setEmail] = useState('');
  const details: Record<string, string> = { Windows: t.download.winDetail, Android: t.download.andDetail };

  return (
    <section id="cine-signup" className="relative z-50 min-h-dvh">
      {/* marge → la vidéo transparaît AUTOUR du canva blanc */}
      <div className="mx-auto max-w-6xl px-4 pb-12 pt-28 sm:px-8 sm:pt-32 lg:px-12">
        {/* CANVA BLANC qui entoure tout */}
        <div className="cine-reveal rounded-3xl bg-white p-5 text-[#1a160f] shadow-[0_30px_80px_-30px_rgba(0,0,0,0.6)] sm:p-8 lg:p-10">
          <div className="grid gap-8 lg:grid-cols-[180px_1fr_220px]">
            {/* GAUCHE — nav + version */}
            <aside>
              <nav className="flex gap-1 overflow-x-auto lg:flex-col">
                <span className="shrink-0 rounded-full bg-[#15110c] px-4 py-2.5 text-sm font-medium text-white">
                  {t.download.telechargement}
                </span>
                <a
                  href="#cine-tarifs"
                  className="cine-press shrink-0 rounded-full px-4 py-2.5 text-sm font-medium text-[#1a160f]/70 hover:bg-black/5"
                >
                  {t.download.tarifs}
                </a>
              </nav>
              <div className="mt-6 border-t border-black/10 pt-6 lg:mt-8 lg:pt-8">
                <p className="px-1 text-[0.7rem] uppercase tracking-[0.2em] text-[#1a160f]/40">{t.download.version}</p>
                <p className="mt-2 px-1 text-sm font-medium">v1.0</p>
                <p className="px-1 text-[0.72rem] text-[#1a160f]/45">{t.download.versionSub}</p>
              </div>
            </aside>

            {/* CENTRE — grande carte titre + 2 cartes plateformes (icônes) */}
            <div>
              <div className="rounded-2xl bg-[#15110c] p-7 sm:p-9">
                <h2 className="font-askan text-4xl leading-[0.95] text-white sm:text-6xl">{t.download.title}</h2>
                <p className="mt-3 max-w-md text-sm text-white/65">{t.download.sub}</p>
              </div>

              <div className="mt-6 grid gap-6 sm:grid-cols-2">
                {PLATFORMS.map(({ os, href, Icon }) => (
                  <a
                    key={os}
                    href={href}
                    className="cine-press group flex flex-col items-center justify-center gap-4 rounded-2xl bg-[#15110c] px-6 py-10 text-center hover:bg-[#221d16]"
                  >
                    <Icon className="h-14 w-14 text-white transition-transform group-hover:scale-105" />
                    <div>
                      <span className="font-askan text-2xl text-white">↓ {os}</span>
                      <p className="mt-1 text-xs text-white/55">{details[os]}</p>
                    </div>
                  </a>
                ))}
              </div>
            </div>

            {/* DROITE — accès email + plateformes */}
            <aside className="flex flex-col gap-8">
              <div>
                <p className="text-[0.7rem] uppercase tracking-[0.2em] text-[#1a160f]/40">{t.download.accesLabel}</p>
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
                    className="w-full rounded-full border border-black/15 bg-[#f1eee8] px-4 py-2.5 text-sm text-[#1a160f] outline-none placeholder:text-[#1a160f]/40 focus:border-black/40"
                  />
                  <button
                    type="submit"
                    disabled={sent}
                    className="cine-press rounded-full bg-[#15110c] px-4 py-2.5 text-sm font-medium text-white"
                  >
                    {sent ? t.download.optinPending : t.download.cta}
                  </button>
                </form>
              </div>
              <div>
                <p className="text-[0.7rem] uppercase tracking-[0.2em] text-[#1a160f]/40">{t.download.plateformesLabel}</p>
                <ul className="mt-3 flex flex-col gap-2.5 text-sm text-[#1a160f]/80">
                  <li>Windows 10 / 11</li>
                  <li>Android 8+</li>
                </ul>
              </div>
              <p className="text-[0.72rem] leading-relaxed text-[#1a160f]/45">{t.download.paymentNote}</p>
            </aside>
          </div>
        </div>
      </div>
    </section>
  );
}

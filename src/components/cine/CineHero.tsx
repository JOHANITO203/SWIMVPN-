import { useState } from 'react';
import { useCine } from './i18n';
import { subscribeEmail } from './api';

// Profondeurs distinctes (parallaxe) : titre = plan proche, pills = plan encore plus proche.
const planeTitle: React.CSSProperties = {
  transform: 'translate3d(calc(var(--cine-px, 0) * -14px), calc(var(--cine-py, 0) * -10px), 0)',
};
const planePills: React.CSSProperties = {
  transform: 'translate3d(calc(var(--cine-px, 0) * -32px), calc(var(--cine-py, 0) * -22px), 0)',
};

export default function CineHero() {
  const { t, locale } = useCine();
  const [sent, setSent] = useState(false);
  const [email, setEmail] = useState('');
  const PILLS = t.hero.pills;

  return (
    <section
      id="cine"
      className="relative w-full overflow-hidden"
      style={{ height: '100dvh', minHeight: '100vh' }}
    >
      {/* pas d'overlay sombre (principe Aurai) — juste un soupçon en bas-gauche pour la typo */}
      <div
        className="pointer-events-none absolute inset-0 z-40"
        style={{ background: 'linear-gradient(to top, rgba(0,0,0,0.55) 0%, rgba(0,0,0,0) 42%)' }}
        aria-hidden
      />

      <div
        className="absolute inset-0 z-50 flex flex-col px-4 py-4 sm:px-10 sm:py-8 lg:px-12"
        style={{
          // bord-à-bord : contenu au-dessus du home indicator (barre gestuelle)
          paddingBottom: 'max(env(safe-area-inset-bottom), 1rem)',
        }}
      >
        {/* spacer → contenu en bas */}
        <div className="flex-1" />

        <div className="flex flex-col gap-8 pb-4 sm:flex-row sm:items-end sm:justify-between sm:pb-12 lg:pb-16">
          {/* colonne gauche — plan titre */}
          <div className="max-w-[700px]" style={planeTitle}>
            <h1
              className="cine-blur-up font-askan text-[2.4rem] leading-[0.92] text-white sm:text-[4rem] md:text-[5rem] lg:text-[6.5rem]"
              style={{ animationDelay: '0.25s' }}
            >
              {t.hero.l1}
              <br />
              {t.hero.l2}
            </h1>

            <p
              className="cine-fade-up mt-4 max-w-[520px] text-xs font-light leading-relaxed text-white/70 sm:text-base md:text-lg"
              style={{ animationDelay: '0.6s' }}
            >
              {t.hero.sub}
            </p>

            {/* form email — pill glass + bouton blanc inline */}
            <form
              onSubmit={(e) => {
                e.preventDefault();
                if (!email.trim()) return;
                setSent(true);
                void subscribeEmail(email.trim(), locale, 'cine-hero');
              }}
              className="cine-fade-up relative mt-6 flex max-w-[440px] items-center rounded-full border border-white/10 bg-black/30 backdrop-blur-md"
              style={{ animationDelay: '0.75s' }}
            >
              <input
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                disabled={sent}
                placeholder={t.download.emailPlaceholder}
                aria-label="Adresse e-mail"
                className="w-full bg-transparent px-4 py-3 text-sm text-white placeholder-white/50 outline-none sm:px-6 sm:py-4"
              />
              <button
                type="submit"
                disabled={sent}
                className="cine-press absolute right-1.5 rounded-full bg-white px-3 py-2 text-xs font-medium sm:px-6 sm:py-3 sm:text-sm"
                style={{ color: '#0b0b12' }}
              >
                {sent ? t.download.optinPending : t.download.cta}
              </button>
            </form>

            {/* feature pills — mobile */}
            <div className="mt-3 flex flex-wrap gap-2 sm:hidden">
              {PILLS.map((p) => (
                <span
                  key={p}
                  className="rounded-full border border-white/10 bg-black/30 px-3 py-1.5 text-xs text-white backdrop-blur-md"
                >
                  {p}
                </span>
              ))}
            </div>
          </div>

          {/* colonne droite — feature pills desktop (plan le plus proche) */}
          <div className="hidden flex-col items-end gap-2 self-end sm:flex" style={planePills}>
            {PILLS.map((p) => (
              <span
                key={p}
                className="cine-fade-up rounded-full border border-white/10 bg-black/30 px-4 py-2 text-sm text-white backdrop-blur-md"
                style={{ animationDelay: '0.9s' }}
              >
                {p}
              </span>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}

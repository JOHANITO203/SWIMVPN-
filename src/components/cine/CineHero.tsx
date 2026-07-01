import { EyeOff, ArrowUpRight } from 'lucide-react';
import CineLogo from './CineLogo';
import { useCine } from './i18n';

/**
 * Hero brutaliste — centré verticalement, aligné à gauche : tagline uppercase espacée,
 * titre display 3 lignes `clamp(2.8rem,8vw,7rem)` à entrée décalée, sous-texte court,
 * rangée CTA (plein blanc + bordé) + badge plateformes, rangée de stats honnêtes.
 */
export default function CineHero() {
  const { t } = useCine();

  return (
    <section id="cine" className="relative w-full overflow-hidden" style={{ height: '100dvh', minHeight: '100vh' }}>
      {/* soupçon de scrim bas pour la lisibilité de la typo sur la vidéo */}
      <div
        className="pointer-events-none absolute inset-0 z-40"
        style={{ background: 'linear-gradient(to top, rgba(0,0,0,0.55) 0%, rgba(0,0,0,0) 48%)' }}
        aria-hidden
      />

      <div
        className="absolute inset-0 z-50 flex flex-col justify-center px-6 pt-20 sm:px-10 lg:px-16"
        style={{
          paddingLeft: 'max(env(safe-area-inset-left), 1.5rem)',
          paddingRight: 'max(env(safe-area-inset-right), 1.5rem)',
          paddingBottom: 'max(env(safe-area-inset-bottom), 1rem)',
        }}
      >
        {/* tagline */}
        <p
          className="cine-fade-up flex items-center gap-2 text-xs uppercase tracking-[0.3em] text-white/70 sm:text-sm"
          style={{ animationDelay: '0.05s' }}
        >
          <EyeOff size={16} className="shrink-0 text-white/70" aria-hidden />
          {t.hero.tagline}
        </p>

        {/* titre display 3 lignes, entrée décalée ligne par ligne */}
        <h1 className="mt-5 lg:mt-6">
          {t.hero.lines.map((line, i) => (
            <span
              key={line}
              className="cine-fade-up block font-askan text-[clamp(2.5rem,7vw,6.2rem)] leading-[0.92] tracking-tight text-white"
              style={{ animationDelay: `${0.2 + i * 0.15}s` }}
            >
              {line}
            </span>
          ))}
        </h1>

        {/* sous-texte */}
        <p
          className="cine-fade-up mt-5 max-w-md text-sm leading-relaxed text-white/70 sm:text-base lg:mt-6"
          style={{ animationDelay: '0.75s' }}
        >
          {t.hero.sub}
        </p>

        {/* rangée CTA + badge */}
        <div
          className="cine-fade-up mt-7 flex flex-wrap items-center gap-4 sm:gap-6 lg:mt-8"
          style={{ animationDelay: '0.9s' }}
        >
          <a
            href="#cine-signup"
            className="cine-cta cine-press group flex items-center gap-2 px-5 py-3 text-[11px] uppercase tracking-widest sm:px-7 sm:py-4 sm:text-xs"
          >
            {t.hero.ctaPrimary}
            <ArrowUpRight size={14} className="transition-transform group-hover:-translate-y-0.5 group-hover:translate-x-0.5" />
          </a>
          <a
            href="#cine-tarifs"
            className="cine-press border border-white/30 px-5 py-3 text-[11px] uppercase tracking-widest text-white hover:border-white/60 hover:bg-white/10 sm:px-7 sm:py-4 sm:text-xs"
          >
            {t.hero.ctaSecondary}
          </a>
          <div className="hidden items-center gap-3 sm:flex">
            <CineLogo className="h-8 w-8 text-white/50" />
            <div className="text-xs uppercase tracking-wider text-white/60">
              <p>{t.hero.badge[0]}</p>
              <p>{t.hero.badge[1]}</p>
            </div>
          </div>
        </div>

        {/* stats honnêtes */}
        <div
          className="cine-fade-up mt-7 flex flex-wrap gap-6 sm:mt-9 sm:gap-12 lg:mt-10 lg:gap-16"
          style={{ animationDelay: '1.05s' }}
        >
          {t.hero.stats.map((s) => (
            <div key={s.l}>
              <p className="font-askan text-2xl tracking-tight text-white sm:text-4xl lg:text-5xl">{s.v}</p>
              <p className="mt-1 text-[9px] uppercase tracking-widest text-white/50 sm:text-xs">{s.l}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

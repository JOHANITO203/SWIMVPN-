import { cineAccentVars } from './tokens';
import { useCine } from './i18n';

export default function CineTech() {
  const { t } = useCine();
  const LAYERS = t.tech.layers.map((l, i) => ({ n: `0${i + 1}`, ...l }));
  return (
    <section id="cine-tech" className="relative min-h-dvh overflow-hidden">
      {/* atmosphère statique (pas d'image plein cadre ici → pas de Ken Burns) */}
      <div
        className="pointer-events-none absolute inset-0"
        style={{ background: 'radial-gradient(120% 80% at 50% -10%, rgba(255,255,255,0.06), transparent 60%)' }}
        aria-hidden
      />

      <div className="relative z-50 mx-auto max-w-5xl px-4 pb-24 pt-32 sm:px-10 sm:pt-40 lg:px-12">
        {/* cluster d'entrée */}
        <p className="cine-fade-up text-xs uppercase tracking-[0.22em] text-white/55" style={{ animationDelay: '0.1s' }}>
          {t.tech.eyebrow}
        </p>
        <h2
          className="cine-blur-up font-askan mt-4 max-w-[16ch] text-4xl leading-[1.02] text-white sm:text-6xl md:text-7xl"
          style={{ letterSpacing: '-0.02em', animationDelay: '0.25s' }}
        >
          {t.tech.title}
        </h2>
        <p className="cine-fade-up mt-6 max-w-xl text-base text-white/70" style={{ animationDelay: '0.5s' }}>
          {t.tech.lead}
        </p>

        {/* 3 couches, liste éditoriale à filets fins, entrée staggered */}
        <div className="mt-14 border-t border-white/10">
          {LAYERS.map((l, i) => (
            <div
              key={l.n}
              className="cine-reveal grid grid-cols-[auto_1fr] gap-5 border-b border-white/10 py-8 sm:gap-10"
              style={{ transitionDelay: `${i * 0.08}s` }}
            >
              <span className="font-askan text-3xl tabular-nums text-white/30 sm:text-5xl">{l.n}</span>
              <div>
                <h3 className="text-lg font-semibold text-white sm:text-xl">{l.h}</h3>
                <p className="mt-2 max-w-2xl text-sm text-white/60 sm:text-base">{l.p}</p>
              </div>
            </div>
          ))}
        </div>

        {/* CTA accent (rare) */}
        <div className="cine-fade-up mt-12" style={{ ...cineAccentVars, animationDelay: '1.05s' }}>
          <a href="#cine-tarifs" className="cine-cta inline-block rounded-full px-7 py-3.5 text-base font-semibold">
            {t.tech.cta}
          </a>
        </div>
      </div>
    </section>
  );
}

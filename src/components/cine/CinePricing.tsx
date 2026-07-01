import { useState } from 'react';
import { Check, ArrowUpRight } from 'lucide-react';
import { useCine } from './i18n';

const PLAN_META = [
  { tag: 'Basic', price: '$4.36' },
  { tag: 'Premium', price: '$9.99', best: true },
  { tag: 'Platinum', price: '$27.49' },
];
const PAIEMENT = ['SwimPay · RUB', 'SwimPay · USD', 'SwimPay · XOF', 'Bitcoin', 'USDT'];

export default function CinePricing() {
  const { t } = useCine();
  const [featured, setFeatured] = useState(1);

  const PLANS = PLAN_META.map((m, i) => ({
    ...m,
    period: t.tarifs.periods[i],
    features: t.tarifs.features[i],
    note: t.tarifs.notes[i],
    badge: m.best ? t.tarifs.recommande : undefined,
  }));
  const plan = PLANS[featured];

  return (
    <section id="cine-tarifs" className="relative min-h-dvh overflow-hidden">
      <div className="relative z-50 mx-auto max-w-6xl px-6 pb-24 pt-32 sm:px-10 sm:pt-40 lg:px-12">
        <p className="cine-fade-up text-xs uppercase tracking-[0.3em] text-white/55" style={{ animationDelay: '0.1s' }}>
          {t.tarifs.eyebrow}
        </p>
        <h2
          className="cine-blur-up font-askan mt-4 text-4xl leading-[0.95] tracking-tight text-white sm:text-6xl md:text-7xl"
          style={{ animationDelay: '0.25s' }}
        >
          {t.tarifs.title}
        </h2>

        <div className="mt-10 grid gap-6 lg:grid-cols-[190px_1fr_220px]">
          {/* GAUCHE — sélecteur d'offre */}
          <aside className="cine-reveal h-fit border border-white/15 bg-black/70 p-4">
            <p className="px-1 text-[0.7rem] uppercase tracking-[0.2em] text-white/45">{t.tarifs.offres}</p>
            <div className="mt-3 flex gap-1 overflow-x-auto lg:flex-col">
              {PLANS.map((pl, i) => (
                <button
                  key={pl.tag}
                  type="button"
                  onClick={() => setFeatured(i)}
                  aria-pressed={featured === i}
                  className={`cine-press shrink-0 px-4 py-2.5 text-left text-xs uppercase tracking-widest ${
                    featured === i ? 'bg-white text-black' : 'text-white/70 hover:bg-white/10 hover:text-white'
                  }`}
                >
                  {pl.tag}
                </button>
              ))}
            </div>
          </aside>

          {/* CENTRE — grande carte + 2 petites */}
          <div>
            <div className="cine-reveal relative flex flex-col border border-white/20 bg-black/75 p-7 sm:p-8">
              {plan.badge && (
                <span className="absolute right-6 top-6 border border-white/25 px-3 py-1 text-[0.62rem] uppercase tracking-widest text-white/70">
                  {plan.badge}
                </span>
              )}
              <p className="text-xs uppercase tracking-[0.2em] text-white/55">{plan.tag}</p>
              <div className="mt-3 flex items-end gap-3">
                <span className="font-askan text-6xl tabular-nums tracking-tight text-white sm:text-7xl">{plan.price}</span>
                <span className="mb-2 text-sm text-white/50">{plan.period}</span>
              </div>

              <ul className="mt-6 grid gap-3 border-t border-white/15 pt-6 sm:grid-cols-3">
                {plan.features.map((f) => (
                  <li key={f} className="flex items-center gap-2 text-sm text-white/75">
                    <Check size={15} className="shrink-0 text-white/40" />
                    {f}
                  </li>
                ))}
              </ul>

              <p className="mt-5 text-sm italic text-white/45">{plan.note}</p>

              <a
                href="#cine-signup"
                className="cine-cta cine-press group mt-7 inline-flex items-center gap-2 self-start px-8 py-4 text-xs uppercase tracking-widest"
              >
                {t.tarifs.cta} — {plan.price}
                <ArrowUpRight size={14} className="transition-transform group-hover:-translate-y-0.5 group-hover:translate-x-0.5" />
              </a>
            </div>

            <div className="mt-6 grid gap-6 sm:grid-cols-2">
              {PLANS.map((pl, i) =>
                i === featured ? null : (
                  <button
                    key={pl.tag}
                    type="button"
                    onClick={() => setFeatured(i)}
                    className="cine-press cine-reveal flex items-center justify-between border border-white/15 bg-black/60 p-5 text-left hover:border-white/40 hover:bg-black/75"
                  >
                    <span>
                      <span className="block text-xs uppercase tracking-widest text-white">{pl.tag}</span>
                      <span className="mt-1 block text-xs text-white/45">{pl.period}</span>
                    </span>
                    <span className="font-askan text-2xl tabular-nums text-white">{pl.price}</span>
                  </button>
                ),
              )}
            </div>
          </div>

          {/* DROITE — listes catégories */}
          <aside className="cine-reveal flex h-fit flex-col gap-8 border border-white/15 bg-black/70 p-6">
            <div>
              <p className="text-[0.7rem] uppercase tracking-[0.2em] text-white/45">{t.tarifs.inclusLabel}</p>
              <ul className="mt-3 flex flex-col gap-2.5">
                {t.tarifs.inclus.map((x) => (
                  <li key={x} className="text-sm text-white/70">{x}</li>
                ))}
              </ul>
            </div>
            <div>
              <p className="text-[0.7rem] uppercase tracking-[0.2em] text-white/45">{t.tarifs.paiementLabel}</p>
              <ul className="mt-3 flex flex-col gap-2.5">
                {PAIEMENT.map((x) => (
                  <li key={x} className="text-sm text-white/70">{x}</li>
                ))}
              </ul>
            </div>
          </aside>
        </div>

        <p className="cine-fade-up mt-10 max-w-2xl text-sm text-white/45" style={{ animationDelay: '0.6s' }}>
          {t.tarifs.lead}
        </p>
      </div>
    </section>
  );
}

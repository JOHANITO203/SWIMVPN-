import { useState } from 'react';
import { Plus } from 'lucide-react';
import { useCine } from './i18n';

/**
 * FAQ brutaliste — rangées à filets durs, numéros display, accordéon (une réponse ouverte
 * à la fois, la première par défaut). Contenu réel visible → crawlable tel quel.
 */
export default function CineFaq() {
  const { t } = useCine();
  const [openIdx, setOpenIdx] = useState(0);

  return (
    <section id="cine-faq" className="relative min-h-dvh overflow-hidden">
      <div className="relative z-50 mx-auto max-w-5xl px-6 pb-24 pt-32 sm:px-10 sm:pt-40 lg:px-12">
        <p className="cine-fade-up text-xs uppercase tracking-[0.3em] text-white/55" style={{ animationDelay: '0.1s' }}>
          {t.faq.eyebrow}
        </p>
        <h2
          className="cine-blur-up font-askan mt-4 max-w-[18ch] text-4xl leading-[0.95] tracking-tight text-white sm:text-6xl md:text-7xl"
          style={{ animationDelay: '0.25s' }}
        >
          {t.faq.title}
        </h2>

        <div className="mt-14 border-t border-white/15">
          {t.faq.items.map((item, i) => {
            const open = openIdx === i;
            return (
              <div key={item.q} className="cine-reveal border-b border-white/15" style={{ transitionDelay: `${i * 0.06}s` }}>
                <button
                  type="button"
                  onClick={() => setOpenIdx(open ? -1 : i)}
                  aria-expanded={open}
                  className="grid w-full grid-cols-[auto_1fr_auto] items-center gap-5 py-6 text-left sm:gap-10"
                >
                  <span className="font-askan text-2xl tabular-nums text-white/30 sm:text-4xl">0{i + 1}</span>
                  <span className="text-base font-semibold text-white sm:text-lg">{item.q}</span>
                  <Plus
                    size={20}
                    className={`shrink-0 text-white/50 transition-transform duration-300 ${open ? 'rotate-45' : ''}`}
                    aria-hidden
                  />
                </button>
                <div
                  className="grid transition-[grid-template-rows] duration-300 ease-out"
                  style={{ gridTemplateRows: open ? '1fr' : '0fr' }}
                >
                  <div className="overflow-hidden">
                    <p className="max-w-2xl pb-7 pl-14 text-sm leading-relaxed text-white/60 sm:pl-24 sm:text-base">
                      {item.a}
                    </p>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
}

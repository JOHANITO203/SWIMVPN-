import { useCine } from './i18n';
import { SHOWCASE_COPY } from '../landing/showcaseContent';

/**
 * Contenu SEO riche, rendu en SSR (prerender) dans la langue de l'URL, `sr-only`
 * (invisible mais crawlable + lu par les lecteurs d'écran). Réutilise le copy validé de
 * l'ancienne landing (`SHOWCASE_COPY`) → densité/mots-clés équivalents, sans cloaking
 * (contenu réel, pertinent, le même produit que l'expérience visuelle).
 */
export default function CineSeo() {
  const { locale } = useCine();
  const c = SHOWCASE_COPY[locale === 'ru' ? 'ru' : 'fr']; // en → fr (showcase = ru/fr)

  return (
    <div className="sr-only">
      <h1>SWIMVPN — {c.hero.title}</h1>
      <p>{c.hero.lead}</p>

      <h2>{c.palette.phrases.join(' ')}</h2>
      <p>{c.palette.sub}</p>

      {c.scrollCaps.map((s) => (
        <section key={s.k}>
          <h2>
            {s.k} — {s.h3}
          </h2>
          <p>{s.p}</p>
        </section>
      ))}

      <section>
        <h2>{c.features.titleLines.join(' ')}</h2>
        <p>{c.features.sub}</p>
      </section>

      <section>
        <h2>{c.pricing.titleLines.join(' ')}</h2>
        <p>{c.pricing.sub}</p>
        {c.pricing.plans.map((p) => (
          <article key={p.name}>
            <h3>
              {p.name} — {p.price} · {p.period}
            </h3>
            <p>{p.note}</p>
            <ul>
              {p.features.map((f) => (
                <li key={f}>{f}</li>
              ))}
            </ul>
          </article>
        ))}
      </section>

      <p>{c.finalCta.lead}</p>
      <p>{c.footer.copyright}</p>
    </div>
  );
}

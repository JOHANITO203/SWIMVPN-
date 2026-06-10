// Build-time prerender entry (Node, no browser). Vite builds this as an SSR bundle; scripts/prerender.mjs
// imports it and calls render() per locale to produce static HTML for crawlers (esp. Yandex, weak on JS).
// The client still does a normal createRoot render over this markup (no hydration), so no mismatch risk.
import { renderToStaticMarkup } from 'react-dom/server';
import App from './App';
import {
  LANDING_COPY,
  LANDING_LOCALES,
  LANDING_DEFAULT_LOCALE,
  LANDING_OG_IMAGE_URL,
  LandingLocale,
  getLandingUrl,
} from './components/landing/landingContent';

export function render(locale: LandingLocale): string {
  return renderToStaticMarkup(<App initialLocale={locale} />);
}

export const locales = LANDING_LOCALES;
export const defaultLocale = LANDING_DEFAULT_LOCALE;

// Per-locale head data the static-HTML rewriter needs — single source of truth (the React copy).
export const seo = Object.fromEntries(
  LANDING_LOCALES.map((l) => [
    l,
    {
      htmlLang: LANDING_COPY[l].seo.htmlLang,
      title: LANDING_COPY[l].seo.title,
      description: LANDING_COPY[l].seo.description,
      ogTitle: LANDING_COPY[l].seo.ogTitle,
      ogDescription: LANDING_COPY[l].seo.ogDescription,
      ogLocale: LANDING_COPY[l].seo.ogLocale,
      url: getLandingUrl(l),
      ogImage: LANDING_OG_IMAGE_URL,
    },
  ]),
) as Record<LandingLocale, {
  htmlLang: string; title: string; description: string;
  ogTitle: string; ogDescription: string; ogLocale: string; url: string; ogImage: string;
}>;

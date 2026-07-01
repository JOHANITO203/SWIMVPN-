import { useEffect } from 'react';
import { CineLocaleProvider, useCine } from './i18n';
import type { LandingLocale } from '../landing/landingContent';
import CineNav from './CineNav';
import CineHero from './CineHero';
import CineTech from './CineTech';
import CinePricing from './CinePricing';
import CineFaq from './CineFaq';
import CineSignup from './CineSignup';
import CineStage from './CineStage';
import CineSeo from './CineSeo';
import CineFooter from './CineFooter';

// Ordre des sections = ordre de navigation (hash → écran).
const ORDER = ['#cine', '#cine-tech', '#cine-tarifs', '#cine-faq', '#cine-signup'];

export default function CineApp({ hash, initialLocale }: { hash: string; initialLocale?: LandingLocale }) {
  return (
    <CineLocaleProvider initial={initialLocale}>
      <CineShell hash={hash} />
    </CineLocaleProvider>
  );
}

/**
 * Build « Brutaliste premium » — landing racine.
 * Nav + footer PERSISTANTS ; l'écran change selon le sous-hash et se remonte
 * → le cluster d'entrée rejoue. Décor `CineStage` (vidéo continue + accélérateur ×4
 * au changement de section) partagé par tous les écrans.
 * Livrés : Hero (1) · Technologie (2) · Tarifs (3) · FAQ (4) · Télécharger (5).
 */
function CineShell({ hash }: { hash: string }) {
  const { locale } = useCine();
  const idx = Math.max(0, ORDER.indexOf(hash)); // 0..4 (hash inconnu → 0 = Hero)

  // Bord-à-bord immersif : la barre du navigateur (theme-color) passe en sombre sur le cine,
  // restaurée à la sortie (les pages claires gardent leur teinte).
  useEffect(() => {
    const meta = document.querySelector('meta[name="theme-color"]');
    const prev = meta?.getAttribute('content') ?? null;
    meta?.setAttribute('content', '#0a0a0f');
    return () => {
      if (meta && prev !== null) meta.setAttribute('content', prev);
    };
  }, []);

  // Scroll-reveal : observe les .cine-reveal de l'écran courant, ajoute .in-view à l'entrée
  // dans le viewport. Re-scanné à chaque changement de section (nouveau contenu monté).
  useEffect(() => {
    const els = Array.from(document.querySelectorAll<HTMLElement>('.cine-reveal:not(.in-view)'));
    if (!els.length) return;
    const io = new IntersectionObserver(
      (entries) => {
        entries.forEach((e) => {
          if (e.isIntersecting) {
            e.target.classList.add('in-view');
            io.unobserve(e.target);
          }
        });
      },
      { threshold: 0.12, rootMargin: '0px 0px -8% 0px' },
    );
    els.forEach((el) => io.observe(el));
    return () => io.disconnect();
  }, [idx]);

  let screen;
  switch (idx) {
    case 1:
      screen = <CineTech key="tech" />;
      break;
    case 2:
      screen = <CinePricing key="tarifs" />;
      break;
    case 3:
      screen = <CineFaq key="faq" />;
      break;
    case 4:
      screen = <CineSignup key="signup" />;
      break;
    default:
      screen = <CineHero key="hero" />;
  }

  return (
    <div lang={locale} className="cine-root relative min-h-dvh bg-black text-white" style={{ letterSpacing: '-0.02em' }}>
      <CineNav active={ORDER[idx]} />
      {/* décor vidéo continu partagé, accéléré par la nav */}
      <CineStage activeIndex={idx} />
      {/* contenu SEO riche (sr-only, prerendu, dans la langue de l'URL) */}
      <CineSeo />
      {screen}
      <CineFooter />
    </div>
  );
}

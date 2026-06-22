import { useEffect, useRef } from 'react';
import { CineLocaleProvider, useCine } from './i18n';
import CineNav from './CineNav';
import CineHero from './CineHero';
import CineTech from './CineTech';
import CinePricing from './CinePricing';
import CineSignup from './CineSignup';
import CineStage from './CineStage';
import CineFooter from './CineFooter';

// Ordre des sections = ordre de l'orbite (frame i ↔ section i).
const ORDER = ['#cine', '#cine-tech', '#cine-tarifs', '#cine-signup'];

export default function CineApp({ hash }: { hash: string }) {
  return (
    <CineLocaleProvider>
      <CineShell hash={hash} />
    </CineLocaleProvider>
  );
}

/**
 * Build « Éditorial sombre cinématique » — route #cine.
 * Nav de verre + footer PERSISTANTS ; l'écran change selon le sous-hash et se remonte
 * → le cluster d'entrée rejoue. Décor `CineStage` partagé (nav-travel-parallax) pour
 * les sections 1-3 ; le Hero (section 0) garde sa signature spotlight-reveal.
 * Livrés : Hero (1) · Technologie (2) · Tarifs (3) · S'abonner (4).
 */
function CineShell({ hash }: { hash: string }) {
  const { locale } = useCine();
  const idx = Math.max(0, ORDER.indexOf(hash)); // 0..3 (hash inconnu → 0 = Hero)
  const rootRef = useRef<HTMLDivElement>(null);

  // Parallaxe souris → profondeur entre les plans : écrit --cine-px/--cine-py (-1..1, lissés)
  // sur la racine ; le fond (vidéo) et le contenu les consomment avec des facteurs opposés.
  useEffect(() => {
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    const el = rootRef.current;
    if (!el) return;
    const target = { x: 0, y: 0 };
    const cur = { x: 0, y: 0 };
    let raf = 0;
    const onMove = (e: MouseEvent) => {
      target.x = (e.clientX / window.innerWidth - 0.5) * 2;
      target.y = (e.clientY / window.innerHeight - 0.5) * 2;
    };
    const loop = () => {
      cur.x += (target.x - cur.x) * 0.06;
      cur.y += (target.y - cur.y) * 0.06;
      el.style.setProperty('--cine-px', cur.x.toFixed(4));
      el.style.setProperty('--cine-py', cur.y.toFixed(4));
      raf = requestAnimationFrame(loop);
    };
    window.addEventListener('mousemove', onMove);
    raf = requestAnimationFrame(loop);
    return () => {
      window.removeEventListener('mousemove', onMove);
      cancelAnimationFrame(raf);
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
      screen = <CineSignup key="signup" />;
      break;
    default:
      screen = <CineHero key="hero" />;
  }

  return (
    <div ref={rootRef} lang={locale} className="cine-root relative min-h-dvh bg-black text-white" style={{ letterSpacing: '-0.02em' }}>
      <CineNav active={ORDER[idx]} />
      {/* décor vidéo « journey » partagé sur TOUTES les sections, scrubé par la nav */}
      <CineStage activeIndex={idx} />
      {screen}
      <CineFooter />
    </div>
  );
}

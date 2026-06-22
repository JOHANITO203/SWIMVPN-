import { useEffect, useRef } from 'react';

const pad = (n: number) => String(n).padStart(2, '0');

// Grain film (bruit fractal SVG inline).
const GRAIN =
  "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='140' height='140'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='2' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)'/%3E%3C/svg%3E\")";

/**
 * Overlay « viewfinder » — donne l'impression de regarder DANS la caméra du cameraman
 * (pas un écran) : vignette + grain, coins de cadrage, grille tiers, réticule, REC + timecode.
 * Fixé au viewport, au-dessus de la vidéo (z-40), sous le contenu (z-50). pointer-events: none.
 */
export default function CineViewfinder() {
  const tcRef = useRef<HTMLSpanElement>(null);

  useEffect(() => {
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    let raf = 0;
    let start = 0;
    const loop = (t: number) => {
      if (!start) start = t;
      const e = (t - start) / 1000;
      const f = Math.floor((e * 24) % 24);
      const s = Math.floor(e) % 60;
      const m = Math.floor(e / 60) % 60;
      const h = Math.floor(e / 3600);
      if (tcRef.current) tcRef.current.textContent = `${pad(h)}:${pad(m)}:${pad(s)}:${pad(f)}`;
      raf = requestAnimationFrame(loop);
    };
    raf = requestAnimationFrame(loop);
    return () => cancelAnimationFrame(raf);
  }, []);

  return (
    <div className="pointer-events-none fixed inset-0 z-40 overflow-hidden" aria-hidden>
      {/* vignette objectif */}
      <div
        className="absolute inset-0"
        style={{ background: 'radial-gradient(115% 115% at 50% 50%, transparent 52%, rgba(0,0,0,0.6) 100%)' }}
      />
      {/* grain film */}
      <div className="absolute inset-0 opacity-[0.07] mix-blend-overlay" style={{ backgroundImage: GRAIN, backgroundSize: '140px 140px' }} />

      {/* grille règle des tiers */}
      <div className="absolute inset-0 opacity-[0.09]">
        <div className="absolute inset-y-0 left-1/3 w-px bg-white" />
        <div className="absolute inset-y-0 left-2/3 w-px bg-white" />
        <div className="absolute inset-x-0 top-1/3 h-px bg-white" />
        <div className="absolute inset-x-0 top-2/3 h-px bg-white" />
      </div>

      {/* coins de cadrage */}
      <div className="absolute left-4 top-4 h-7 w-7 border-l-2 border-t-2 border-white/30 sm:left-6 sm:top-6" />
      <div className="absolute right-4 top-4 h-7 w-7 border-r-2 border-t-2 border-white/30 sm:right-6 sm:top-6" />
      <div className="absolute bottom-4 left-4 h-7 w-7 border-b-2 border-l-2 border-white/30 sm:bottom-6 sm:left-6" />
      <div className="absolute bottom-4 right-4 h-7 w-7 border-b-2 border-r-2 border-white/30 sm:bottom-6 sm:right-6" />

      {/* réticule de mise au point (centre) */}
      <div className="absolute left-1/2 top-1/2 h-12 w-12 -translate-x-1/2 -translate-y-1/2 opacity-25">
        <div className="absolute left-1/2 top-0 h-3 w-px -translate-x-1/2 bg-white" />
        <div className="absolute bottom-0 left-1/2 h-3 w-px -translate-x-1/2 bg-white" />
        <div className="absolute left-0 top-1/2 h-px w-3 -translate-y-1/2 bg-white" />
        <div className="absolute right-0 top-1/2 h-px w-3 -translate-y-1/2 bg-white" />
      </div>

      {/* REC — haut gauche (sous la nav) */}
      <div className="absolute left-5 top-20 hidden items-center gap-2 sm:left-10 sm:top-28 sm:flex">
        <span className="h-2 w-2 animate-pulse rounded-full bg-red-500" />
        <span className="text-[0.7rem] font-medium uppercase tracking-[0.22em] text-white/80">REC</span>
      </div>

      {/* timecode + readout objectif — haut droite */}
      <div className="absolute right-5 top-20 hidden text-right sm:right-10 sm:top-28 sm:block">
        <span ref={tcRef} className="block font-mono text-[0.72rem] tracking-widest text-white/80">00:00:00:00</span>
        <span className="mt-1 block text-[0.6rem] uppercase tracking-[0.18em] text-white/40">f/2.8 · 1/50 · ISO 800 · 24mm</span>
      </div>
    </div>
  );
}

import { useEffect, useRef } from 'react';
import { CINE_JOURNEY } from './tokens';

// Décor « tiny-planet » = la VIDÉO COMPLÈTE (toutes les scènes), lue NATIVEMENT en boucle
// → playback fluide, zéro seek. Chaque bouton de nav agit comme un ACCÉLÉRATEUR : la lecture
// passe en ×2 pendant ~1,8 s depuis sa position courante, puis revient à la vitesse normale.
// Hébergée hors-repo (GitHub Releases, streaming + range). Fixée au viewport, sous le contenu.
const BOOST_RATE = 2;
const BOOST_MS = 1800;

export default function CineStage({ activeIndex }: { activeIndex: number }) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const timer = useRef<number>(0);

  // Bouton de nav (changement de section) → coup d'accélérateur ×2 pendant 1,8 s.
  useEffect(() => {
    const v = videoRef.current;
    if (!v) return;
    v.playbackRate = BOOST_RATE;
    window.clearTimeout(timer.current);
    timer.current = window.setTimeout(() => {
      const vv = videoRef.current;
      if (vv) vv.playbackRate = 1;
    }, BOOST_MS);
    return () => window.clearTimeout(timer.current);
  }, [activeIndex]);

  return (
    <div className="pointer-events-none fixed inset-0 z-0 overflow-hidden" aria-hidden>
      <video
        ref={videoRef}
        src={CINE_JOURNEY}
        autoPlay
        loop
        muted
        playsInline
        preload="auto"
        className="absolute inset-0 h-full w-full object-cover"
        style={{
          transform: 'translate3d(calc(var(--cine-px, 0) * 10px), calc(var(--cine-py, 0) * 8px), 0) scale(1.08)',
          willChange: 'transform',
        }}
      />
      <div
        className="absolute inset-0"
        style={{
          background:
            'linear-gradient(90deg, rgba(0,0,0,0.5) 0%, rgba(0,0,0,0.06) 42%, rgba(0,0,0,0.06) 70%, rgba(0,0,0,0.28) 100%)',
        }}
      />
    </div>
  );
}

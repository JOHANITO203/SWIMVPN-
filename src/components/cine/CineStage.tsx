import { useEffect, useRef } from 'react';
import { CINE_JOURNEY } from './tokens';

// Décor « tiny-planet » = la VIDÉO COMPLÈTE, lue NATIVEMENT en boucle (autoplay muet + loop).
// TOUJOURS VISIBLE — pas de gating d'opacité (c'est ce gating, ajouté au "polish", qui cachait
// la vidéo sur mobile : elle jouait mais restait invisible → on ne voyait que le poster figé).
// Chaque bouton de nav = ACCÉLÉRATEUR : la lecture rampe jusqu'à ×4 pendant ~1,8 s (montée/
// plateau/descente douces) puis revient à la vitesse normale. Servie same-origine (video/mp4).
const BOOST_RATE = 4; // accélérateur ×4
const BOOST_MS = 1800;
const POSTER = '/assets/cine-poster.webp';
const PARALLAX =
  'translate3d(calc(var(--cine-px, 0) * 10px), calc(var(--cine-py, 0) * 8px), 0) scale(1.08)';
const SCRIM =
  'linear-gradient(90deg, rgba(0,0,0,0.5) 0%, rgba(0,0,0,0.06) 42%, rgba(0,0,0,0.06) 70%, rgba(0,0,0,0.28) 100%)';

export default function CineStage({ activeIndex }: { activeIndex: number }) {
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const rampRaf = useRef(0);

  // Bouton de nav → coup d'accélérateur ×4 avec easing (montée rapide, plateau, descente douce).
  useEffect(() => {
    const v = videoRef.current;
    if (!v) return;
    let start = 0;
    cancelAnimationFrame(rampRaf.current);
    const ramp = (t: number) => {
      if (!start) start = t;
      const e = (t - start) / BOOST_MS;
      if (e >= 1) {
        v.playbackRate = 1;
        return;
      }
      const up = Math.min(e / 0.12, 1); // montée 0→1 sur les 12 % premiers
      const down = e > 0.55 ? (e - 0.55) / 0.45 : 0; // descente sur les 45 % finaux
      v.playbackRate = 1 + (BOOST_RATE - 1) * up * (1 - down);
      rampRaf.current = requestAnimationFrame(ramp);
    };
    rampRaf.current = requestAnimationFrame(ramp);
    return () => cancelAnimationFrame(rampRaf.current);
  }, [activeIndex]);

  return (
    <div className="pointer-events-none fixed inset-0 z-0 overflow-hidden" aria-hidden>
      <video
        ref={(el) => {
          videoRef.current = el;
          // muted posé sur la propriété DOM (React ne le reflète pas toujours) → autoplay muet OK
          if (el) {
            el.muted = true;
            el.defaultMuted = true;
          }
        }}
        src={CINE_JOURNEY}
        poster={POSTER}
        autoPlay
        loop
        muted
        playsInline
        preload="auto"
        className="absolute inset-0 h-full w-full object-cover"
        style={{ transform: PARALLAX, willChange: 'transform' }}
      />
      <div className="absolute inset-0" style={{ background: SCRIM }} />
    </div>
  );
}

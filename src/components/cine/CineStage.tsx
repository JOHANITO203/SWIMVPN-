import { useEffect, useRef, useState } from 'react';
import { CINE_JOURNEY } from './tokens';

// Décor « tiny-planet » = la VIDÉO COMPLÈTE (toutes les scènes), lue NATIVEMENT en boucle.
// Poster affiché instantanément (pas de noir au chargement), la vidéo apparaît en fondu quand
// prête. Chaque bouton de nav = ACCÉLÉRATEUR : la lecture rampe jusqu'à ×4 pendant ~1,8 s
// (montée/descente douces) puis revient à la vitesse normale. En reduced-motion : poster fixe,
// aucune lecture. Vidéo hébergée hors-repo (GitHub Releases, streaming + range).
const POSTER = '/assets/cine-poster.webp';
const BOOST_RATE = 4; // accélérateur ×4
const BOOST_MS = 1800; // durée du coup d'accélérateur

const PARALLAX =
  'translate3d(calc(var(--cine-px, 0) * 10px), calc(var(--cine-py, 0) * 8px), 0) scale(1.08)';
const SCRIM =
  'linear-gradient(90deg, rgba(0,0,0,0.5) 0%, rgba(0,0,0,0.06) 42%, rgba(0,0,0,0.06) 70%, rgba(0,0,0,0.28) 100%)';

export default function CineStage({ activeIndex }: { activeIndex: number }) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const rampRaf = useRef(0);
  const [ready, setReady] = useState(false);
  const [reduced, setReduced] = useState(false);

  useEffect(() => {
    const m = window.matchMedia('(prefers-reduced-motion: reduce)');
    setReduced(m.matches);
    const onChange = () => setReduced(m.matches);
    m.addEventListener?.('change', onChange);
    return () => m.removeEventListener?.('change', onChange);
  }, []);

  // Lecture fiable sur MOBILE : l'autoplay muet est souvent insuffisant (Low Power Mode iOS,
  // data-saver Android, preload bridé) → on appelle play() explicitement (sur mount + quand la
  // vidéo est prête), et on relance au 1er geste utilisateur si l'autoplay a été bloqué.
  useEffect(() => {
    if (reduced) return;
    const v = videoRef.current;
    if (!v) return;
    const tryPlay = () => {
      const p = v.play();
      if (p && typeof p.then === 'function') p.then(() => setReady(true)).catch(() => {});
    };
    tryPlay();
    v.addEventListener('loadeddata', tryPlay);
    v.addEventListener('canplay', tryPlay);
    const onGesture = () => tryPlay();
    const opts: AddEventListenerOptions = { passive: true, once: true };
    window.addEventListener('touchstart', onGesture, opts);
    window.addEventListener('pointerdown', onGesture, opts);
    window.addEventListener('scroll', onGesture, opts);
    return () => {
      v.removeEventListener('loadeddata', tryPlay);
      v.removeEventListener('canplay', tryPlay);
      window.removeEventListener('touchstart', onGesture);
      window.removeEventListener('pointerdown', onGesture);
      window.removeEventListener('scroll', onGesture);
    };
  }, [reduced]);

  // Bouton de nav → coup d'accélérateur ×4 avec easing (montée rapide, plateau, descente douce).
  useEffect(() => {
    if (reduced) return;
    const v = videoRef.current;
    if (!v) return;
    let start = 0;
    cancelAnimationFrame(rampRaf.current);
    const ramp = (t: number) => {
      if (!start) start = t;
      const e = (t - start) / BOOST_MS; // 0..1 sur la fenêtre
      if (e >= 1) {
        v.playbackRate = 1;
        return;
      }
      const up = Math.min(e / 0.12, 1); // montée 0→1 sur les 12 % premiers
      const down = e > 0.55 ? (e - 0.55) / 0.45 : 0; // descente sur les 45 % finaux
      const eased = up * (1 - down); // 0..1
      v.playbackRate = 1 + (BOOST_RATE - 1) * eased;
      rampRaf.current = requestAnimationFrame(ramp);
    };
    rampRaf.current = requestAnimationFrame(ramp);
    return () => cancelAnimationFrame(rampRaf.current);
  }, [activeIndex, reduced]);

  return (
    <div className="pointer-events-none fixed inset-0 z-0 overflow-hidden" aria-hidden>
      {/* poster : visible instantanément, sous la vidéo (et seul rendu en reduced-motion) */}
      <div
        className="absolute inset-0 bg-cover bg-center"
        style={{ backgroundImage: `url(${POSTER})`, transform: PARALLAX, willChange: 'transform' }}
      />
      {!reduced && (
        <video
          ref={videoRef}
          src={CINE_JOURNEY}
          poster={POSTER}
          autoPlay
          loop
          muted
          playsInline
          preload="auto"
          onCanPlay={() => setReady(true)}
          className="absolute inset-0 h-full w-full object-cover transition-opacity duration-700"
          style={{ opacity: ready ? 1 : 0, transform: PARALLAX, willChange: 'transform, opacity' }}
        />
      )}
      <div className="absolute inset-0" style={{ background: SCRIM }} />
    </div>
  );
}

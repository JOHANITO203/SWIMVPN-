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
  const videoRef = useRef<HTMLVideoElement | null>(null);
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

  // Démarrage increvable sur MOBILE : on retente play() sur mount + dès que la vidéo a des données
  // (loadeddata/canplay) + à CHAQUE geste (pas `once`), jusqu'à ce que `playing` se déclenche. Gère
  // le Low Power Mode iOS / data-saver qui bloque l'autoplay par politique (→ démarre au 1er contact).
  // On ne révèle la vidéo (setReady) que quand elle JOUE vraiment → le poster couvre l'attente.
  useEffect(() => {
    if (reduced) return;
    const v = videoRef.current;
    if (!v) return;
    v.muted = true;
    v.defaultMuted = true;
    let started = false;
    const tryPlay = () => {
      if (started) return;
      const p = v.play();
      if (p && typeof p.then === 'function') p.catch(() => {});
    };
    const onPlaying = () => {
      started = true;
      setReady(true);
      detach();
    };
    const gestures = ['touchstart', 'pointerdown', 'click', 'scroll'];
    const detach = () => {
      v.removeEventListener('playing', onPlaying);
      v.removeEventListener('loadeddata', tryPlay);
      v.removeEventListener('canplay', tryPlay);
      gestures.forEach((g) => window.removeEventListener(g, tryPlay));
    };
    v.addEventListener('playing', onPlaying);
    v.addEventListener('loadeddata', tryPlay);
    v.addEventListener('canplay', tryPlay);
    gestures.forEach((g) => window.addEventListener(g, tryPlay, { passive: true }));
    tryPlay();
    return detach;
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
          ref={(el) => {
            videoRef.current = el;
            // muted posé DÈS le montage (avant la décision d'autoplay du navigateur) — React ne
            // reflète pas toujours l'attribut `muted` sur la propriété DOM, d'où l'autoplay refusé.
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
          className="absolute inset-0 h-full w-full object-cover transition-opacity duration-700"
          style={{ opacity: ready ? 1 : 0, transform: PARALLAX, willChange: 'transform, opacity' }}
        />
      )}
      <div className="absolute inset-0" style={{ background: SCRIM }} />
    </div>
  );
}

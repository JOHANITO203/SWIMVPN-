import { useEffect, useRef } from 'react';
import { CINE_JOURNEY, CINE_ANCHORS } from './tokens';

/**
 * nav-travel — la vidéo « journey » du drone (HOLD → déplacement+angle → HOLD) FIXÉE au
 * viewport, scrubbée par la nav : section i → ancre i (timestamp dans un HOLD). On anime
 * `currentTime` en lerp vers la cible → le segment hold→move→hold se joue dans les 2 sens.
 * Vidéo all-intra (seek image par image fluide). Le contenu défile par-dessus (scrim).
 */
export default function CineStage({ activeIndex }: { activeIndex: number }) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const displayed = useRef(CINE_ANCHORS[0]);
  const rafRef = useRef(0);

  useEffect(() => {
    const v = videoRef.current;
    if (!v) return;
    const i = Math.max(0, Math.min(activeIndex, CINE_ANCHORS.length - 1));
    const target = CINE_ANCHORS[i];

    const tick = () => {
      const diff = target - displayed.current;
      if (Math.abs(diff) < 0.012) {
        displayed.current = target;
        try { v.currentTime = target; } catch { /* seek pas prêt */ }
        return;
      }
      displayed.current += diff * 0.1; // lerp → scrub doux
      try { v.currentTime = displayed.current; } catch { /* idem */ }
      rafRef.current = requestAnimationFrame(tick);
    };

    cancelAnimationFrame(rafRef.current);
    rafRef.current = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(rafRef.current);
  }, [activeIndex]);

  return (
    <div className="pointer-events-none fixed inset-0 z-0 overflow-hidden" aria-hidden>
      <video
        ref={videoRef}
        src={CINE_JOURNEY}
        muted
        playsInline
        preload="auto"
        onLoadedData={(e) => {
          // pose la 1ʳᵉ ancre dès que la vidéo est seekable
          try { (e.currentTarget as HTMLVideoElement).currentTime = displayed.current; } catch { /* noop */ }
        }}
        className="absolute inset-0 h-full w-full object-cover"
        style={{
          transform:
            'translate3d(calc(var(--cine-px, 0) * 10px), calc(var(--cine-py, 0) * 8px), 0) scale(1.08)',
          willChange: 'transform',
        }}
      />
      {/* scrim léger — assombrit la gauche (zone texte), laisse le drone visible */}
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

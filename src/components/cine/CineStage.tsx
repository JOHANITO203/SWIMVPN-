import { useEffect, useRef } from 'react';

// Décor « tiny-planet » en SÉQUENCE D'IMAGES 1080p (technique Apple), dessinée au <canvas>.
// Lecture CONTINUE en vitesse NORMALE (boucle vers l'avant) ; chaque bouton de nav agit
// comme un ACCÉLÉRATEUR (×2 pendant ~1,8 s depuis la position courante) → on ne sait jamais
// sur quelle scène on tombe. Servie même-origine depuis /assets/seq.
const FRAME_COUNT = 60;
const frameUrl = (i: number) => `/assets/seq/f${String(i).padStart(3, '0')}.webp`;

const BASE_FPS = 10; // playback normal (idle)
const BOOST_MULT = 2; // accélérateur ×2
const BOOST_MS = 1800; // durée du boost après un bouton

export default function CineStage({ activeIndex }: { activeIndex: number }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const imgs = useRef<HTMLImageElement[]>([]);
  const frame = useRef(1);
  const boostUntil = useRef(0);
  const raf = useRef(0);
  const lastT = useRef(0);

  // Préchargement des 60 frames.
  useEffect(() => {
    const arr: HTMLImageElement[] = new Array(FRAME_COUNT + 1);
    for (let n = 1; n <= FRAME_COUNT; n++) {
      const im = new Image();
      im.decoding = 'async';
      im.src = frameUrl(n);
      arr[n] = im;
    }
    imgs.current = arr;
  }, []);

  const draw = (f: number) => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    const idx = Math.max(1, Math.min(FRAME_COUNT, Math.round(f)));
    const img = imgs.current[idx];
    if (!img || !img.complete || !img.naturalWidth) return;
    const cw = canvas.width;
    const ch = canvas.height;
    const ir = img.naturalWidth / img.naturalHeight;
    const cr = cw / ch;
    let dw: number, dh: number, dx: number, dy: number;
    if (ir > cr) {
      dh = ch;
      dw = ch * ir;
      dx = (cw - dw) / 2;
      dy = 0;
    } else {
      dw = cw;
      dh = cw / ir;
      dx = 0;
      dy = (ch - dh) / 2;
    }
    ctx.drawImage(img, dx, dy, dw, dh);
  };

  // Canvas plein viewport (retina) + redraw au resize.
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    const size = () => {
      canvas.width = Math.round(window.innerWidth * dpr);
      canvas.height = Math.round(window.innerHeight * dpr);
      draw(frame.current);
    };
    size();
    window.addEventListener('resize', size);
    return () => window.removeEventListener('resize', size);
  }, []);

  // Chaque bouton de nav (changement de section) = coup d'accélérateur.
  useEffect(() => {
    boostUntil.current = (typeof performance !== 'undefined' ? performance.now() : 0) + BOOST_MS;
  }, [activeIndex]);

  // Boucle de lecture continue (slow-mo + boost), ping-pong.
  useEffect(() => {
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      // pas d'animation : on dessine une image fixe dès qu'elle est prête
      let n = 0;
      let r = 0;
      const once = () => {
        draw(frame.current);
        if (++n < 120) r = requestAnimationFrame(once);
      };
      r = requestAnimationFrame(once);
      return () => cancelAnimationFrame(r);
    }
    const loop = (t: number) => {
      const dt = lastT.current ? Math.min(t - lastT.current, 50) : 16;
      lastT.current = t;
      const fps = t < boostUntil.current ? BASE_FPS * BOOST_MULT : BASE_FPS;
      frame.current += fps * (dt / 1000);
      // boucle vers l'avant (playback normal)
      while (frame.current > FRAME_COUNT) frame.current -= FRAME_COUNT - 1;
      draw(frame.current);
      raf.current = requestAnimationFrame(loop);
    };
    raf.current = requestAnimationFrame(loop);
    return () => cancelAnimationFrame(raf.current);
  }, []);

  return (
    <div className="pointer-events-none fixed inset-0 z-0 overflow-hidden" aria-hidden>
      <canvas
        ref={canvasRef}
        className="absolute inset-0 h-full w-full"
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

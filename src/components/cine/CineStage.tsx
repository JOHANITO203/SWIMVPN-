import { useEffect, useRef } from 'react';

// Décor « tiny-planet » en SÉQUENCE D'IMAGES 1080p (technique Apple) : on dessine l'image
// au <canvas> par index → scrub fluide image par image, zéro seek vidéo, zéro aller-retour
// réseau. Servies même-origine depuis /assets/seq.
const FRAME_COUNT = 60;
const ANCHOR_FRAMES = [5, 22, 39, 56]; // Aperçu / Technologie / Tarifs / S'abonner
const frameUrl = (i: number) => `/assets/seq/f${String(i).padStart(3, '0')}.webp`;

export default function CineStage({ activeIndex }: { activeIndex: number }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const imgs = useRef<HTMLImageElement[]>([]);
  const displayed = useRef<number>(ANCHOR_FRAMES[0]);
  const raf = useRef(0);

  // Préchargement (ancres d'abord → nav instantanée, puis les intermédiaires).
  useEffect(() => {
    const arr: HTMLImageElement[] = new Array(FRAME_COUNT + 1);
    const order = Array.from(new Set([...ANCHOR_FRAMES, ...Array.from({ length: FRAME_COUNT }, (_, i) => i + 1)]));
    order.forEach((n) => {
      const im = new Image();
      im.decoding = 'async';
      im.src = frameUrl(n);
      arr[n] = im;
    });
    imgs.current = arr;
  }, []);

  const draw = (frame: number) => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    const idx = Math.max(1, Math.min(FRAME_COUNT, Math.round(frame)));
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
      draw(displayed.current);
    };
    size();
    window.addEventListener('resize', size);
    return () => window.removeEventListener('resize', size);
  }, []);

  // Redraw tant que les frames chargent (la 1ʳᵉ ancre peut arriver après le montage).
  useEffect(() => {
    let r = 0;
    let n = 0;
    const poll = () => {
      draw(displayed.current);
      if (++n < 180) r = requestAnimationFrame(poll); // ~3 s
    };
    r = requestAnimationFrame(poll);
    return () => cancelAnimationFrame(r);
  }, []);

  // Scrub lissé vers l'ancre de la section active.
  useEffect(() => {
    const target = ANCHOR_FRAMES[Math.max(0, Math.min(activeIndex, ANCHOR_FRAMES.length - 1))];
    cancelAnimationFrame(raf.current);
    const tick = () => {
      const diff = target - displayed.current;
      if (Math.abs(diff) < 0.2) {
        displayed.current = target;
        draw(target);
        return;
      }
      displayed.current += diff * 0.1;
      draw(displayed.current);
      raf.current = requestAnimationFrame(tick);
    };
    raf.current = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf.current);
  }, [activeIndex]);

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

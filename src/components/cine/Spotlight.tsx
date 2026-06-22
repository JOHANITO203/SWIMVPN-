import { useEffect, useRef } from 'react';

const SPOTLIGHT_R = 260;

type Props = { base: string; reveal: string };

/**
 * spotlight-reveal — deux images plein cadre, MÊME cadrage. `base` (z-10) dessous,
 * `reveal` (z-30) dessus mais masquée : un cercle doux suit le curseur avec inertie
 * et ne rend `reveal` visible que dans ce cercle ; ailleurs, `base` transparaît.
 *
 * Limite connue : suit `mousemove` → pas de spotlight sur tactile. Signalée, pas masquée
 * (sur mobile on ne voit que `base`, ce qui reste un visuel plein cadre cohérent).
 */
export default function Spotlight({ base, reveal }: Props) {
  const revealRef = useRef<HTMLDivElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const mouse = useRef({ x: -9999, y: -9999 });
  const smooth = useRef({ x: -9999, y: -9999 });

  useEffect(() => {
    const canvas = canvasRef.current;
    const revealEl = revealRef.current;
    if (!canvas || !revealEl) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let raf = 0;
    const maskStyle = revealEl.style as CSSStyleDeclaration & {
      webkitMaskImage?: string;
      webkitMaskSize?: string;
    };

    const size = () => {
      canvas.width = window.innerWidth;
      canvas.height = window.innerHeight;
    };
    size();

    const onMove = (e: MouseEvent) => {
      mouse.current.x = e.clientX;
      mouse.current.y = e.clientY;
    };

    const loop = () => {
      // suivi lissé (inertie)
      smooth.current.x += (mouse.current.x - smooth.current.x) * 0.1;
      smooth.current.y += (mouse.current.y - smooth.current.y) * 0.1;
      const cx = smooth.current.x;
      const cy = smooth.current.y;

      ctx.clearRect(0, 0, canvas.width, canvas.height);
      const g = ctx.createRadialGradient(cx, cy, 0, cx, cy, SPOTLIGHT_R);
      g.addColorStop(0, 'rgba(255,255,255,1)');
      g.addColorStop(0.4, 'rgba(255,255,255,1)');
      g.addColorStop(0.6, 'rgba(255,255,255,0.75)');
      g.addColorStop(0.75, 'rgba(255,255,255,0.4)');
      g.addColorStop(0.88, 'rgba(255,255,255,0.12)');
      g.addColorStop(1, 'rgba(255,255,255,0)');
      ctx.fillStyle = g;
      ctx.beginPath();
      ctx.arc(cx, cy, SPOTLIGHT_R, 0, Math.PI * 2);
      ctx.fill();

      const url = canvas.toDataURL();
      maskStyle.maskImage = `url(${url})`;
      maskStyle.webkitMaskImage = `url(${url})`;
      maskStyle.maskSize = '100% 100%';
      maskStyle.webkitMaskSize = '100% 100%';

      raf = requestAnimationFrame(loop);
    };

    window.addEventListener('mousemove', onMove);
    window.addEventListener('resize', size);
    raf = requestAnimationFrame(loop);

    // Cleanup obligatoire au démontage.
    return () => {
      window.removeEventListener('mousemove', onMove);
      window.removeEventListener('resize', size);
      cancelAnimationFrame(raf);
    };
  }, []);

  return (
    <>
      {/* base — z-10, Ken Burns au premier paint */}
      <div
        className="cine-kenburns absolute inset-0 z-10 bg-cover bg-center"
        style={{ backgroundImage: `url(${base})`, backgroundColor: '#0a0a0a' }}
        aria-hidden
      />
      {/* reveal — z-30, masqué par le spotlight */}
      <div
        ref={revealRef}
        className="absolute inset-0 z-30 bg-cover bg-center"
        style={{
          backgroundImage: `url(${reveal})`,
          maskRepeat: 'no-repeat',
          WebkitMaskRepeat: 'no-repeat',
        }}
        aria-hidden
      />
      {/* canvas de masque, hors écran */}
      <canvas ref={canvasRef} className="hidden" aria-hidden />
    </>
  );
}

import { useState, useEffect } from 'react';
import { Menu, X, Download, Globe } from 'lucide-react';
import { useCine, CINE_LOCALES, CINE_LOCALE_LABEL } from './i18n';

// Vraie marque SWIMVPN (shark) — inline en currentColor → monochrome blanc sur la pill sombre.
function Logo({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 1024 1024" fill="currentColor" fillRule="evenodd" className={className} aria-hidden>
      <path d="M 132.7 603.5 L 182.4 537.9 L 252.2 470.2 L 339.0 411.0 L 402.5 382.4 L 395.1 313.6 L 373.9 261.8 L 352.8 230.0 L 355.9 225.8 L 440.6 263.9 L 509.4 324.2 L 594.0 328.4 L 690.3 344.3 L 796.1 375.0 L 877.6 411.0 L 833.1 454.3 L 770.7 491.4 L 704.0 512.5 L 541.1 542.2 L 474.4 573.9 L 424.7 614.1 L 497.7 585.5 L 565.4 569.7 L 557.0 578.1 L 592.9 598.2 L 621.5 630.0 L 642.7 687.1 L 650.1 621.5 L 627.9 560.1 L 724.1 540.0 L 809.9 507.2 L 868.0 473.4 L 940.0 405.7 L 844.8 358.0 L 743.2 323.1 L 622.6 298.8 L 521.0 293.5 L 466.0 243.8 L 401.4 205.7 L 330.5 181.3 L 253.3 172.9 L 299.9 216.3 L 327.4 260.7 L 342.2 307.3 L 344.3 362.3 L 268.1 409.9 L 207.8 467.0 L 154.9 544.3 Z" />
      <path d="M 597.2 851.1 L 521.0 833.1 L 478.7 817.3 L 432.1 792.9 L 388.7 761.2 L 352.8 723.1 L 329.5 682.9 L 316.8 637.4 L 318.9 590.8 L 296.7 589.8 L 324.2 566.5 L 382.4 533.7 L 481.8 500.9 L 422.6 499.8 L 358.0 512.5 L 299.9 534.7 L 235.3 572.8 L 194.0 605.6 L 142.2 662.8 L 103.0 729.4 L 84.0 784.5 L 86.1 786.6 L 123.1 731.6 L 166.5 688.2 L 225.8 649.0 L 274.5 628.9 L 286.1 674.4 L 303.0 708.3 L 337.9 752.7 L 371.8 781.3 L 427.9 814.1 L 475.5 832.1 L 543.2 846.9 Z" />
      <path d="M 641.6 393.0 L 641.6 395.1 L 677.6 428.9 L 693.5 436.3 L 709.3 437.4 L 762.2 428.9 L 762.2 426.8 L 699.8 405.7 Z" />
    </svg>
  );
}

export default function CineNav({ active }: { active: string }) {
  const { t, locale, setLocale } = useCine();
  const [open, setOpen] = useState(false);
  const [hidden, setHidden] = useState(false);

  const NAV = [
    { href: '#cine', label: t.nav.apercu },
    { href: '#cine-tech', label: t.nav.technologie },
    { href: '#cine-tarifs', label: t.nav.tarifs },
  ];
  const cycleLocale = () => setLocale(CINE_LOCALES[(CINE_LOCALES.indexOf(locale) + 1) % CINE_LOCALES.length]);

  // Masque la navbar au scroll vers le bas, la révèle au scroll vers le haut.
  useEffect(() => {
    let last = window.scrollY;
    let raf = 0;
    const onScroll = () => {
      if (raf) return;
      raf = requestAnimationFrame(() => {
        const y = window.scrollY;
        if (Math.abs(y - last) > 6) {
          setHidden(y > last && y > 80); // descend (au-delà de 80px) → masque ; remonte → révèle
          last = y;
        }
        raf = 0;
      });
    };
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => {
      window.removeEventListener('scroll', onScroll);
      cancelAnimationFrame(raf);
    };
  }, []);

  return (
    <nav
      className="fixed inset-x-0 top-0 z-[100] flex justify-center px-4 py-4 sm:py-6"
      style={{
        transform: hidden && !open ? 'translateY(-140%)' : 'translateY(0)',
        transition: 'transform .4s cubic-bezier(.16,1,.3,1)',
        // bord-à-bord : la pill reste sous l'encoche / barre de statut (et hors notch en paysage)
        paddingTop: 'max(env(safe-area-inset-top), 1rem)',
        paddingLeft: 'max(env(safe-area-inset-left), 1rem)',
        paddingRight: 'max(env(safe-area-inset-right), 1rem)',
      }}
    >
      {/* pill centrale : liens gauche · brand centre · bouton droite */}
      <div className="relative flex w-full max-w-5xl items-center justify-between rounded-full border border-white/10 bg-black/30 px-3 py-2 backdrop-blur-md">
        {/* gauche : liens (desktop) / hamburger (mobile) */}
        <div className="flex items-center gap-1">
          <div className="hidden items-center gap-1 md:flex">
            {NAV.map((n) => (
              <a
                key={n.href}
                href={n.href}
                aria-current={active === n.href ? 'page' : undefined}
                className={`cine-press rounded-full px-4 py-2 text-sm ${
                  active === n.href ? 'bg-white/15 text-white' : 'text-white/70 hover:bg-white/10 hover:text-white'
                }`}
              >
                {n.label}
              </a>
            ))}
          </div>
          <button
            type="button"
            onClick={() => setOpen((v) => !v)}
            aria-label="Menu"
            aria-expanded={open}
            className="cine-press p-2 text-white md:hidden"
          >
            {open ? <X size={20} /> : <Menu size={20} />}
          </button>
        </div>

        {/* centre : icône seule dans une pastille circulaire noire */}
        <a
          href="#cine"
          aria-label="SWIMVPN — accueil"
          className="cine-press absolute left-1/2 -translate-x-1/2"
        >
          <span className="flex h-10 w-10 items-center justify-center rounded-full border border-white/10 bg-black text-white">
            <Logo className="h-6 w-6" />
          </span>
        </a>

        {/* droite : langue + télécharger */}
        <div className="flex items-center gap-1.5 sm:gap-2">
          <button
            type="button"
            onClick={cycleLocale}
            aria-label="Changer de langue"
            className="cine-press flex items-center gap-1.5 rounded-full px-2.5 py-2 text-xs font-medium text-white/80 hover:bg-white/10 hover:text-white"
          >
            <Globe size={14} />
            {CINE_LOCALE_LABEL[locale]}
          </button>
          <a
            href="#cine-signup"
            className="cine-press flex items-center gap-2 rounded-full bg-white px-4 py-2.5 text-sm font-medium"
            style={{ color: '#0b0b12' }}
          >
            <Download size={15} />
            <span className="hidden sm:inline">{t.download.telechargement}</span>
          </a>
        </div>
      </div>

      {/* menu mobile */}
      {open && (
        <div className="absolute left-4 right-4 top-[4.75rem] rounded-2xl border border-white/10 bg-black/40 p-4 backdrop-blur-xl md:hidden">
          <div className="flex flex-col gap-1">
            {NAV.map((n) => (
              <a
                key={n.href}
                href={n.href}
                onClick={() => setOpen(false)}
                aria-current={active === n.href ? 'page' : undefined}
                className={`cine-press rounded-full px-4 py-2.5 ${
                  active === n.href ? 'bg-white/15 text-white' : 'text-white/80'
                }`}
              >
                {n.label}
              </a>
            ))}
          </div>
        </div>
      )}
    </nav>
  );
}

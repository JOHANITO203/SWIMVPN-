import { useState, useEffect } from 'react';
import { X, Globe, ArrowUpRight } from 'lucide-react';
import CineLogo from './CineLogo';
import { useCine, CINE_LOCALES, CINE_LOCALE_LABEL } from './i18n';

/**
 * Nav brutaliste — barre pleine largeur : marque à gauche, liens uppercase au centre,
 * langue + CTA bordé à droite. Sous `md` : hamburger → menu PLEIN ÉCRAN (bg-black/95,
 * liens display géants à entrée décalée). Se masque au scroll descendant, se révèle en
 * remontant ; fond solide dès qu'on quitte le hero.
 */
export default function CineNav({ active }: { active: string }) {
  const { t, locale, setLocale } = useCine();
  const [open, setOpen] = useState(false);
  const [hidden, setHidden] = useState(false);
  const [scrolled, setScrolled] = useState(false);

  const NAV = [
    { href: '#cine', label: t.nav.apercu },
    { href: '#cine-tech', label: t.nav.technologie },
    { href: '#cine-tarifs', label: t.nav.tarifs },
    { href: '#cine-faq', label: t.nav.faq },
  ];
  const cycleLocale = () => setLocale(CINE_LOCALES[(CINE_LOCALES.indexOf(locale) + 1) % CINE_LOCALES.length]);

  useEffect(() => {
    let last = window.scrollY;
    let raf = 0;
    const onScroll = () => {
      if (raf) return;
      raf = requestAnimationFrame(() => {
        const y = window.scrollY;
        setScrolled(y > 24);
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
    <>
      <nav
        className={`fixed inset-x-0 top-0 z-[100] flex items-center justify-between px-6 py-5 transition-colors duration-300 sm:px-10 lg:px-16 lg:py-7 ${
          scrolled ? 'border-b border-white/10 bg-black/85' : 'bg-transparent'
        }`}
        style={{
          transform: hidden && !open ? 'translateY(-110%)' : 'translateY(0)',
          transition: 'transform .4s cubic-bezier(.16,1,.3,1), background-color .3s ease',
          paddingTop: 'max(env(safe-area-inset-top), 1.25rem)',
          paddingLeft: 'max(env(safe-area-inset-left), 1.5rem)',
          paddingRight: 'max(env(safe-area-inset-right), 1.5rem)',
        }}
      >
        {/* gauche : marque */}
        <a href="#cine" aria-label="SWIMVPN — accueil" className="flex items-center gap-3 text-white">
          <CineLogo className="h-8 w-8" />
          <span className="font-askan text-xl tracking-wider sm:text-2xl">SWIMVPN</span>
        </a>

        {/* centre : liens (desktop) */}
        <div className="hidden items-center gap-8 md:flex lg:gap-10">
          {NAV.map((n) => (
            <a
              key={n.href}
              href={n.href}
              aria-current={active === n.href ? 'page' : undefined}
              className={`text-xs uppercase tracking-widest transition-colors ${
                active === n.href ? 'text-white underline decoration-1 underline-offset-8' : 'text-white/80 hover:text-white'
              }`}
            >
              {n.label}
            </a>
          ))}
        </div>

        {/* droite : langue + CTA bordé (desktop) / langue + hamburger (mobile) */}
        <div className="flex items-center gap-3 sm:gap-4">
          <button
            type="button"
            onClick={cycleLocale}
            aria-label="Changer de langue"
            className="flex items-center gap-1.5 px-2 py-2 text-xs uppercase tracking-widest text-white/80 transition-colors hover:text-white"
          >
            <Globe size={14} />
            {CINE_LOCALE_LABEL[locale]}
          </button>
          <a
            href="#cine-signup"
            className="group hidden items-center gap-2 border border-white/30 px-6 py-3 text-xs uppercase tracking-widest text-white transition-colors hover:border-white/60 hover:bg-white/10 md:flex"
          >
            {t.download.telechargement}
            <ArrowUpRight size={14} className="transition-transform group-hover:-translate-y-0.5 group-hover:translate-x-0.5" />
          </a>
          <button
            type="button"
            onClick={() => setOpen(true)}
            aria-label="Menu"
            aria-expanded={open}
            className="space-y-1.5 p-2 md:hidden"
          >
            <div className="h-0.5 w-6 bg-white" />
            <div className="h-0.5 w-6 bg-white" />
            <div className="h-0.5 w-4 bg-white" />
          </button>
        </div>
      </nav>

      {/* menu plein écran (mobile) — liens display géants, entrée décalée */}
      <div
        className={`fixed inset-0 z-[110] bg-black/95 backdrop-blur-sm transition-all duration-500 md:hidden ${
          open ? 'visible opacity-100' : 'invisible opacity-0'
        }`}
        aria-hidden={!open}
      >
        <div
          className="flex items-center justify-between px-6 py-5"
          style={{ paddingTop: 'max(env(safe-area-inset-top), 1.25rem)' }}
        >
          <a href="#cine" onClick={() => setOpen(false)} aria-label="SWIMVPN — accueil" className="flex items-center gap-3 text-white">
            <CineLogo className="h-8 w-8" />
            <span className="font-askan text-xl tracking-wider">SWIMVPN</span>
          </a>
          <button type="button" onClick={() => setOpen(false)} aria-label="Fermer le menu" className="p-2 text-white">
            <X size={24} />
          </button>
        </div>
        <div className="flex h-[calc(100%-6rem)] flex-col items-start justify-center gap-6 px-8">
          {NAV.map((n, i) => (
            <a
              key={n.href}
              href={n.href}
              onClick={() => setOpen(false)}
              aria-current={active === n.href ? 'page' : undefined}
              className="font-askan text-4xl uppercase text-white sm:text-5xl"
              style={{
                transitionProperty: 'opacity, transform',
                transitionDuration: '500ms',
                transitionDelay: `${i * 80 + 100}ms`,
                opacity: open ? 1 : 0,
                transform: open ? 'translateY(0)' : 'translateY(20px)',
              }}
            >
              {n.label}
            </a>
          ))}
          <a
            href="#cine-signup"
            onClick={() => setOpen(false)}
            className="mt-4 flex items-center gap-2 border border-white/30 px-6 py-3 text-xs uppercase tracking-widest text-white"
            style={{
              transitionProperty: 'opacity, transform',
              transitionDuration: '500ms',
              transitionDelay: `${NAV.length * 80 + 100}ms`,
              opacity: open ? 1 : 0,
              transform: open ? 'translateY(0)' : 'translateY(20px)',
            }}
          >
            {t.download.telechargement}
            <ArrowUpRight size={14} />
          </a>
        </div>
      </div>
    </>
  );
}

import React, { useEffect, useState } from 'react';
import {
  LANDING_COPY,
  LANDING_DEFAULT_LOCALE,
  LANDING_DOWNLOAD_URL,
  LANDING_WINDOWS_URL,
  LANDING_LOCALES,
  LANDING_LOCALE_LABELS,
  LANDING_OG_IMAGE_URL,
  LandingLocale,
  getLandingPath,
  getLandingUrl,
  isLandingLocale,
} from './landingContent';
import { SHOWCASE_COPY } from './showcaseContent';

// Headline price for the JSON-LD offer (flagship monthly plan, USD). Validated tiers:
// $3.49 week / $7.99 month / $21.99 quarter.
const USD_PRICE = '7.99';

// Palette stage auto-cycle themes (bg / ink / accent) — ported from the mock.
const PAL_THEMES = [
  { bg: '#000000', ink: '#ffffff', accent: '#8B5CF6' },
  { bg: '#F4EDE8', ink: '#14130F', accent: '#7B57E8' },
  { bg: '#0C1210', ink: '#ffffff', accent: '#34D399' },
  { bg: '#F5F4F0', ink: '#1A1A2E', accent: '#3B5BDB' },
  { bg: '#080F1A', ink: '#ffffff', accent: '#38BDF8' },
];

// Bento layout for the 5 capability tiles: a wide feature tile + a dark accent
// tile (the AI pilot) among the rest. Indexes match SHOWCASE_COPY.scrollCaps.
const POINT_LAYOUT = ['span4 feature', 'span2', 'span2 accent', 'span2', 'span2'];

const ico = (children: React.ReactNode) => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" strokeLinejoin="round">
    {children}
  </svg>
);
const POINT_ICONS = [
  ico(<><path d="M7 11V8a5 5 0 0 1 9.9-1" /><rect x="3" y="11" width="18" height="10" rx="2" /></>), // unlock — bypass blocks
  ico(<><circle cx="12" cy="12" r="9" /><path d="M3 12h18" /><path d="M12 3c2.6 2.7 2.6 15.3 0 18M12 3c-2.6 2.7-2.6 15.3 0 18" /></>), // globe — worldwide
  ico(<><rect x="4" y="4" width="16" height="16" rx="2" /><rect x="9" y="9" width="6" height="6" rx="1" /><path d="M9 1v3M15 1v3M9 20v3M15 20v3M1 9h3M1 15h3M20 9h3M20 15h3" /></>), // cpu — AI pilot
  ico(<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />), // shield — protocols
  ico(<><rect x="3" y="6" width="18" height="13" rx="2" /><path d="M3 10h18" /><path d="M15 15h2" /></>), // card — payment
];

function getInitialLocale(initialLocale?: LandingLocale): LandingLocale {
  if (typeof window === 'undefined') return initialLocale ?? LANDING_DEFAULT_LOCALE;
  const pathLocale = window.location.pathname.split('/').filter(Boolean)[0];
  if (isLandingLocale(pathLocale)) return pathLocale;
  const queryLocale = new URLSearchParams(window.location.search).get('lang');
  if (isLandingLocale(queryLocale)) return queryLocale;
  const storedLocale = window.localStorage.getItem('swimvpn_landing_locale');
  if (isLandingLocale(storedLocale)) return storedLocale;
  return LANDING_DEFAULT_LOCALE;
}

const SharkLogo = ({ className, gradId }: { className?: string; gradId: string }) => (
  <svg className={className} viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
    <defs>
      <linearGradient id={gradId} x1="136" y1="132" x2="880" y2="898" gradientUnits="userSpaceOnUse">
        <stop offset="0" stopColor="#B89AFF" />
        <stop offset=".5" stopColor="#8A6AF1" />
        <stop offset="1" stopColor="#5D3BD8" />
      </linearGradient>
    </defs>
    <g transform="translate(512,512) scale(0.84) translate(-512,-512)" fill={`url(#${gradId})`} fillRule="evenodd">
      <path d="M132.7,603.5L182.4,537.9L252.2,470.2L339,411L402.5,382.4L395.1,313.6L373.9,261.8L352.8,230L355.9,225.8L440.6,263.9L509.4,324.2L594,328.4L690.3,344.3L796.1,375L877.6,411L833.1,454.3L770.7,491.4L704,512.5L541.1,542.2L474.4,573.9L424.7,614.1L497.7,585.5L565.4,569.7L557,578.1L592.9,598.2L621.5,630L642.7,687.1L650.1,621.5L627.9,560.1L724.1,540L809.9,507.2L868,473.4L940,405.7L844.8,358L743.2,323.1L622.6,298.8L521,293.5L466,243.8L401.4,205.7L330.5,181.3L253.3,172.9L299.9,216.3L327.4,260.7L342.2,307.3L344.3,362.3L268.1,409.9L207.8,467L154.9,544.3Z" />
      <path d="M597.2,851.1L521,833.1L478.7,817.3L432.1,792.9L388.7,761.2L352.8,723.1L329.5,682.9L316.8,637.4L318.9,590.8L296.7,589.8L324.2,566.5L382.4,533.7L481.8,500.9L422.6,499.8L358,512.5L299.9,534.7L235.3,572.8L194,605.6L142.2,662.8L103,729.4L84,784.5L86.1,786.6L123.1,731.6L166.5,688.2L225.8,649L274.5,628.9L286.1,674.4L303,708.3L337.9,752.7L371.8,781.3L427.9,814.1L475.5,832.1L543.2,846.9Z" />
      <path d="M641.6,393L641.6,395.1L677.6,428.9L693.5,436.3L709.3,437.4L762.2,428.9L762.2,426.8L699.8,405.7Z" />
    </g>
  </svg>
);

// Liquid-glass button (mock's gbtn markup). `variant` may include "ghost" and/or "sm".
const GlassBtn = ({
  href,
  label,
  variant = '',
  onClick,
}: {
  href: string;
  label: string;
  variant?: string;
  onClick?: (e: React.MouseEvent) => void;
}) => (
  <a className={`gbtn-wrap${variant ? ' ' + variant : ''}`} href={href} onClick={onClick}>
    <div className="gbtn-blur" />
    <div className="gbtn-tint" />
    <div className="gbtn-edge" />
    <span className="gbtn-text">{label}</span>
  </a>
);

// Platform glyphs (inline SVG, no extra deps) for the Windows + Android download elements.
const WindowsGlyph = ({ size = 15 }: { size?: number }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="currentColor" aria-hidden="true" style={{ flexShrink: 0 }}>
    <path d="M3 5.62 10.5 4.6v6.65H3V5.62ZM11.5 4.46 21 3.18v8.07h-9.5V4.46ZM3 12.75h7.5v6.63L3 18.36v-5.61ZM11.5 12.75H21v8.07l-9.5-1.28v-6.79Z" />
  </svg>
);
const AndroidGlyph = ({ size = 15 }: { size?: number }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="currentColor" aria-hidden="true" style={{ flexShrink: 0 }}>
    <path d="M17.6 9.48l1.84-3.18a.4.4 0 1 0-.69-.4l-1.86 3.23a11.5 11.5 0 0 0-9.78 0L5.25 5.9a.4.4 0 1 0-.69.4L6.4 9.48A10.8 10.8 0 0 0 1 18.4h22a10.8 10.8 0 0 0-5.4-8.92ZM7 15.2a1.1 1.1 0 1 1 0-2.2 1.1 1.1 0 0 1 0 2.2Zm10 0a1.1 1.1 0 1 1 0-2.2 1.1 1.1 0 0 1 0 2.2Z" />
  </svg>
);

// Cascade text — words/chars with per-char staggered lift (CSS :hover handles the motion).
const Cascade = ({ text }: { text: string }) => (
  <>
    {text.split(' ').map((word, wi) => (
      <span className="cword" key={wi}>
        {[...word].map((ch, ci) => (
          <span
            className="cchar"
            key={ci}
            style={{
              textShadow: '0 1.1em currentColor',
              transition: `transform 265ms cubic-bezier(.16,1,.3,1) ${ci * 24}ms`,
            }}
          >
            {ch}
          </span>
        ))}
      </span>
    ))}
  </>
);

const LandingPage = ({ initialLocale }: { initialLocale?: LandingLocale } = {}) => {
  const [locale, setLocale] = useState<LandingLocale>(() => getInitialLocale(initialLocale));
  const copy = LANDING_COPY[locale]; // SEO meta (unchanged)
  const c = SHOWCASE_COPY[locale]; // visible copy (redesign)

  // ── SEO / locale head sync (preserved verbatim from the previous landing) ──
  useEffect(() => {
    document.documentElement.lang = copy.seo.htmlLang;
    document.title = copy.seo.title;
    setMeta('description', copy.seo.description);
    setMeta('twitter:title', copy.seo.ogTitle);
    setMeta('twitter:description', copy.seo.ogDescription);
    setPropertyMeta('og:title', copy.seo.ogTitle);
    setPropertyMeta('og:description', copy.seo.ogDescription);
    setPropertyMeta('og:locale', copy.seo.ogLocale);
    setPropertyMeta('og:url', getLandingUrl(locale));
    setPropertyMeta('og:image', LANDING_OG_IMAGE_URL);
    setMeta('twitter:image', LANDING_OG_IMAGE_URL);
    setCanonical(getLandingUrl(locale));
    setAlternateLinks();
    setJsonLd(copy, locale);
    window.localStorage.setItem('swimvpn_landing_locale', locale);
  }, [copy, locale]);

  const setLanguage = (nextLocale: LandingLocale) => {
    setLocale(nextLocale);
    const url = new URL(window.location.href);
    window.history.replaceState({}, '', `${getLandingPath(nextLocale)}${url.hash}`);
  };

  // ── Behaviours ported from the mock's <script>: reveal, header hide/rewind,
  //    scroll-scrub video, palette auto-cycle. Client-only; cleaned up on unmount. ──
  useEffect(() => {
    if (typeof window === 'undefined') return;

    // reveal-on-scroll
    const io = new IntersectionObserver(
      (es) =>
        es.forEach((e) => {
          if (e.isIntersecting) {
            e.target.classList.add('in');
            io.unobserve(e.target);
          }
        }),
      { threshold: 0.16 },
    );
    document.querySelectorAll('.rv').forEach((el) => io.observe(el));

    // header hide on scroll-down, rewind on scroll-up
    const hdr = document.getElementById('hdr');
    let hdrHidden = false;
    let lastY = 0;
    const DZONE = 5;
    let ticking = false;
    const onScroll = () => {
      if (ticking) return;
      ticking = true;
      requestAnimationFrame(() => {
        ticking = false;
        const y = window.scrollY;
        if (!hdr) return;
        const goingDown = y > lastY + DZONE;
        const goingUp = y < lastY - DZONE;
        if (goingDown && y > window.innerHeight * 0.6 && !hdrHidden) {
          hdr.classList.remove('hdr-rewind');
          void hdr.offsetWidth;
          hdr.classList.add('hdr-hidden');
          hdrHidden = true;
        } else if (goingUp && hdrHidden) {
          hdr.classList.remove('hdr-hidden');
          void hdr.offsetWidth;
          hdr.classList.add('hdr-rewind');
          hdrHidden = false;
        }
        if (Math.abs(y - lastY) > DZONE) lastY = y;
        hdr.classList.toggle('scrolled', y > 8 && !hdrHidden);
      });
    };
    window.addEventListener('scroll', onScroll, { passive: true });
    onScroll();

    // palette stage auto-cycle
    const palStage = document.getElementById('themes');
    const palDots = [...document.querySelectorAll<HTMLElement>('#palDots .pal-dot')];
    let palIdx = 0;
    const applyPal = (i: number) => {
      palIdx = i;
      const t = PAL_THEMES[i];
      if (palStage) {
        palStage.style.background = t.bg;
        palStage.style.color = t.ink;
        palStage.style.setProperty('--pal-accent', t.accent);
      }
      palDots.forEach((d, k) => {
        d.classList.toggle('active', k === i);
        d.style.background = k === i ? t.accent : 'rgba(128,128,128,.28)';
        d.style.width = k === i ? '38px' : '20px';
      });
    };
    palDots.forEach((d, i) => d.addEventListener('click', () => applyPal(i)));
    applyPal(0);
    const palTimer = setInterval(() => applyPal((palIdx + 1) % PAL_THEMES.length), 4200);

    return () => {
      io.disconnect();
      window.removeEventListener('scroll', onScroll);
      clearInterval(palTimer);
    };
  }, [locale]);

  // Hero robot: desktop → eyes track the mouse (scrub the clip by horizontal
  // movement); touch/no-mouse → just loop-play (no cursor to follow).
  useEffect(() => {
    if (typeof window === 'undefined') return;
    const v = document.getElementById('heroVid') as HTMLVideoElement | null;
    if (!v) return;

    const fine = window.matchMedia('(pointer: fine)').matches;
    if (!fine) {
      // Touch device → the gaze follows the phone's tilt (gyroscope), the mobile
      // equivalent of "eyes follow the cursor". Loop-play until the sensor kicks in;
      // if unsupported / permission denied, it just keeps looping.
      v.loop = true;
      const play = () => v.play().catch(() => {});
      if (v.readyState >= 2) play();
      else v.addEventListener('loadeddata', play, { once: true });

      let target = 0;
      let seeking = false;
      let gyroOn = false;
      const seekNext = () => {
        if (!gyroOn || !v.duration) { seeking = false; return; }
        if (Math.abs(v.currentTime - target) < 0.02) { seeking = false; return; }
        seeking = true;
        v.currentTime = target;
      };
      const onSeeked = () => seekNext();
      const onOrient = (e: DeviceOrientationEvent) => {
        if (e.gamma == null || !v.duration) return;
        if (!gyroOn) {
          gyroOn = true;
          v.loop = false;
          v.pause();
          v.addEventListener('seeked', onSeeked);
        }
        const g = Math.max(-38, Math.min(38, e.gamma)); // left-right tilt
        target = ((g + 38) / 76) * (v.duration - 0.05);
        if (!seeking) seekNext();
      };
      const DOE = window.DeviceOrientationEvent as typeof DeviceOrientationEvent & {
        requestPermission?: () => Promise<'granted' | 'denied'>;
      };
      const enableGyro = () => {
        if (!DOE) return;
        if (typeof DOE.requestPermission === 'function') {
          DOE.requestPermission().then((s) => {
            if (s === 'granted') window.addEventListener('deviceorientation', onOrient);
          }).catch(() => {});
        } else {
          window.addEventListener('deviceorientation', onOrient);
        }
      };
      // iOS needs a user gesture to grant the sensor → arm it on first touch.
      let armer: (() => void) | null = null;
      if (DOE && typeof DOE.requestPermission === 'function') {
        armer = () => { enableGyro(); };
        window.addEventListener('touchend', armer, { once: true });
      } else {
        enableGyro();
      }
      return () => {
        window.removeEventListener('deviceorientation', onOrient);
        v.removeEventListener('seeked', onSeeked);
        if (armer) window.removeEventListener('touchend', armer);
      };
    }

    let prevX: number | null = null;
    let targetTime = 0;
    let seeking = false;
    const SENS = 0.8;
    const seekNext = () => {
      if (!v.duration) { seeking = false; return; }
      if (Math.abs(v.currentTime - targetTime) < 0.012) { seeking = false; return; }
      seeking = true;
      v.currentTime = targetTime;
    };
    const onMove = (e: MouseEvent) => {
      if (!v.duration) return;
      if (prevX === null) { prevX = e.clientX; return; }
      const delta = e.clientX - prevX;
      prevX = e.clientX;
      targetTime = Math.min(v.duration - 0.05, Math.max(0, targetTime + (delta / window.innerWidth) * SENS * v.duration));
      if (!seeking) seekNext();
    };
    const onSeeked = () => seekNext();
    v.addEventListener('seeked', onSeeked);
    window.addEventListener('mousemove', onMove, { passive: true });
    v.load();
    return () => {
      window.removeEventListener('mousemove', onMove);
      v.removeEventListener('seeked', onSeeked);
    };
  }, []);

  const goToOffers = (e: React.FormEvent) => {
    e.preventDefault();
    const email = (document.getElementById('finalEmail') as HTMLInputElement | null)?.value.trim();
    if (email) window.localStorage.setItem('swimvpn_offer_email', email);
    window.location.hash = '#offres';
  };

  return (
    <>
      {/* SVG filters for the liquid-glass buttons/tiles */}
      <svg style={{ position: 'absolute', width: 0, height: 0, overflow: 'hidden' }} aria-hidden="true">
        <defs>
          <filter id="glass-blur" x="-20%" y="-20%" width="140%" height="140%">
            <feTurbulence type="fractalNoise" baseFrequency="0.003 0.007" numOctaves={1} result="noise" />
            <feDisplacementMap in="SourceGraphic" in2="noise" scale={160} xChannelSelector="R" yChannelSelector="G" />
          </filter>
          <filter id="glass-distortion" x="0%" y="0%" width="100%" height="100%" filterUnits="objectBoundingBox" colorInterpolationFilters="sRGB">
            <feTurbulence type="fractalNoise" baseFrequency="0.001 0.005" numOctaves={1} seed={17} result="turbulence" />
            <feComponentTransfer in="turbulence" result="mapped">
              <feFuncR type="gamma" amplitude={1} exponent={10} offset={0.5} />
              <feFuncG type="gamma" amplitude={0} exponent={1} offset={0} />
              <feFuncB type="gamma" amplitude={0} exponent={1} offset={0.5} />
            </feComponentTransfer>
            <feGaussianBlur in="turbulence" stdDeviation={3} result="softMap" />
            <feSpecularLighting in="softMap" surfaceScale={5} specularConstant={1} specularExponent={100} lightingColor="white" result="specLight">
              <fePointLight x={-200} y={-200} z={300} />
            </feSpecularLighting>
            <feComposite in="specLight" operator="arithmetic" k1={0} k2={1} k3={1} k4={0} result="litImage" />
            <feDisplacementMap in="SourceGraphic" in2="softMap" scale={200} xChannelSelector="R" yChannelSelector="G" />
          </filter>
        </defs>
      </svg>

      {/* HEADER */}
      <header id="hdr">
        <div className="wrap nav">
          <div className="brand">
            <SharkLogo className="brand-logo" gradId="swLg" />
            SWIMVPN
          </div>
          <nav className="nav-links">
            {c.nav.links.map((l) => (
              <a href={l.href} key={l.href}>{l.label}</a>
            ))}
            <button
              type="button"
              onClick={() => setLanguage(locale === 'fr' ? 'ru' : 'fr')}
              style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'inherit', font: 'inherit', padding: 0 }}
              aria-label="language"
            >
              {LANDING_LOCALE_LABELS[locale === 'fr' ? 'ru' : 'fr']}
            </button>
          </nav>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <a href={LANDING_WINDOWS_URL} aria-label="Windows" title="Télécharger pour Windows (.exe)" style={{ display: 'inline-flex', color: 'rgba(255,255,255,.7)' }}>
              <WindowsGlyph size={16} />
            </a>
            <a href={LANDING_DOWNLOAD_URL} download="SwimVPN.apk" aria-label="Android" title="Télécharger l’APK Android" style={{ display: 'inline-flex', color: 'rgba(255,255,255,.7)' }}>
              <AndroidGlyph size={16} />
            </a>
            <GlassBtn href="#download" label={c.nav.download} variant="sm" />
          </div>
        </div>
      </header>

      {/* HERO — surveillance robot video (eyes track the mouse), SWIMVPN copy */}
      <section className="hero-v">
        <video
          id="heroVid"
          className="hero-video"
          src="/assets/hero-robot.mp4"
          poster="/assets/hero-robot-poster.jpg"
          muted
          playsInline
          preload="auto"
          disablePictureInPicture
        />
        <div className="hero-scrim" aria-hidden="true" />
        <div className="hero-v-inner">
          <span className="eyebrow">{c.hero.eyebrow}</span>
          <h1>{c.hero.title}</h1>
          <p className="lead">{c.hero.lead}</p>
          <div className="hero-cta">
            <GlassBtn href="#download" label={c.hero.ctaDownload} />
            <GlassBtn href="#showcase" label={c.hero.ctaHow} variant="ghost" />
          </div>
          <div className="hero-trust"><b>{c.hero.trust}</b> · app.swimvpn.pro</div>
        </div>
        <div className="scrollhint">{c.hero.scrollHint}</div>
      </section>

      {/* CAPABILITIES — bento grid (varied tiles, icons, one accent tile) */}
      <section id="showcase" className="points-sec">
        <div className="wrap points-grid">
          {c.scrollCaps.map((cap, i) => (
            <div className={`point rv ${POINT_LAYOUT[i] || 'span2'}`} data-d={String((i % 3) + 1)} key={i}>
              <span className="point-ico" aria-hidden="true">{POINT_ICONS[i]}</span>
              <span className="k">{cap.k}</span>
              <h3>{cap.h3}</h3>
              <p>{cap.p}</p>
            </div>
          ))}
        </div>
      </section>

      {/* PALETTE STAGE */}
      <section id="themes" className="pal-stage" style={{ background: '#000', color: '#fff' }}>
        <div className="wrap pal-inner">
          <div className="pal-copy">
            <div className="pal-phrase"><Cascade text={c.palette.phrases[0]} /></div>
            <div className="pal-phrase"><Cascade text={c.palette.phrases[1]} /></div>
          </div>
          <div className="pal-foot">
            <p className="pal-sub">{c.palette.sub}</p>
            <div className="pal-dots" id="palDots">
              {PAL_THEMES.map((_, i) => (
                <div className={`pal-dot${i === 0 ? ' active' : ''}`} key={i} />
              ))}
            </div>
          </div>
        </div>
      </section>

      {/* FEATURES — horizon hero */}
      <section id="features" className="sec" style={{ paddingTop: 0, paddingBottom: 0 }}>
        <div className="feat-hero">
          <div className="feat-stars" aria-hidden="true" />
          <div className="feat-horizon" aria-hidden="true" />
          <div className="wrap feat-hero-inner">
            <span className="eyebrow rv">{c.features.eyebrow}</span>
            <h2 className="rv feat-h2" data-d="1">
              {c.features.titleLines.map((line, i) => (
                <React.Fragment key={i}>
                  {line}
                  {i < c.features.titleLines.length - 1 && <br />}
                </React.Fragment>
              ))}
            </h2>
            <p className="rv feat-hero-sub" data-d="2">{c.features.sub}</p>
          </div>
        </div>
      </section>

      {/* PRICING */}
      <section id="pricing">
        <div className="price-header">
          <span className="eyebrow rv">{c.pricing.eyebrow}</span>
          <h2 className="rv">
            {c.pricing.titleLines.map((line, i) => (
              <React.Fragment key={i}>
                {line}
                {i < c.pricing.titleLines.length - 1 && <br />}
              </React.Fragment>
            ))}
          </h2>
          <p className="rv" data-d="1">{c.pricing.sub}</p>
        </div>
        <div className="price-grid">
          {c.pricing.plans.map((plan, i) => (
            <div className={`pc rv${plan.best ? ' best' : ''}`} key={i}>
              {plan.badge && <div className="pc-badge">{plan.badge}</div>}
              <div className="pc-name">{plan.name}</div>
              <div className="pc-price">{plan.price}</div>
              <div className="pc-period">{plan.period}</div>
              <ul>
                {plan.features.map((f, k) => (
                  <li key={k}>{f}</li>
                ))}
              </ul>
              <p className="pc-note">{plan.note}</p>
              <GlassBtn href="#download" label={plan.cta} variant={plan.best ? 'sm' : 'ghost sm'} />
            </div>
          ))}
        </div>
      </section>

      {/* FINAL HERO */}
      <section id="download" className="final">
        <div className="final-overlay" />
        <div className="final-content">
          <h2 className="final-word fw1">protect</h2>
          <h2 className="final-word fw2">your</h2>
          <h2 className="final-word fw3">data</h2>
          <div className="final-cta">
            <p style={{ maxWidth: 260, fontSize: 15, lineHeight: 1.35, color: 'rgba(255,255,255,.75)', marginBottom: 20 }}>
              {c.finalCta.lead}
            </p>
            <form className="bq-hero-form" onSubmit={goToOffers}>
              <input className="bq-glass-input" type="email" placeholder={c.finalCta.emailPlaceholder} id="finalEmail" autoComplete="email" inputMode="email" />
              <button className="bq-hero-btn" type="submit">{c.finalCta.ctaAccess}</button>
            </form>
            <div style={{ display: 'flex', gap: 14, flexWrap: 'wrap' }}>
              <a className="bq-apk-link" href={LANDING_WINDOWS_URL} style={{ display: 'inline-flex' }}>
                <WindowsGlyph size={13} />
                {c.nav.download} · Windows
              </a>
              <a className="bq-apk-link" href={LANDING_DOWNLOAD_URL} download="SwimVPN.apk" style={{ display: 'inline-flex' }}>
                <AndroidGlyph size={13} />
                {c.nav.download} · Android
              </a>
            </div>
          </div>
        </div>
      </section>

      {/* FOOTER */}
      <footer className="wrap">
        <div className="brand" style={{ fontSize: 15 }}>
          <SharkLogo className="brand-logo" gradId="ftLg" />
          SWIMVPN
        </div>
        <nav style={{ display: 'flex', alignItems: 'center', gap: 22, fontSize: 13.5 }}>
          <a href="mailto:support@swimvpn.pro" style={{ color: 'rgba(255,255,255,.5)' }}>{c.footer.support}</a>
          <a href={LANDING_WINDOWS_URL} style={{ color: 'rgba(255,255,255,.5)' }}>Windows</a>
          <a href={LANDING_DOWNLOAD_URL} download="SwimVPN.apk" style={{ color: 'rgba(255,255,255,.5)' }}>{c.footer.apk}</a>
          <a href="#privacy" style={{ color: 'rgba(255,255,255,.5)' }}>{copy.footer.privacy}</a>
          <a href="#terms" style={{ color: 'rgba(255,255,255,.5)' }}>{copy.footer.terms}</a>
        </nav>
        <div>{c.footer.copyright}</div>
      </footer>
    </>
  );
};

function setMeta(name: string, content: string) {
  document.querySelector<HTMLMetaElement>(`meta[name="${name}"]`)?.setAttribute('content', content);
}
function setPropertyMeta(property: string, content: string) {
  document.querySelector<HTMLMetaElement>(`meta[property="${property}"]`)?.setAttribute('content', content);
}
function setCanonical(href: string) {
  document.querySelector<HTMLLinkElement>('link[rel="canonical"]')?.setAttribute('href', href);
}
function setAlternateLinks() {
  LANDING_LOCALES.forEach((item) => {
    document.querySelector<HTMLLinkElement>(`link[rel="alternate"][hreflang="${item}"]`)?.setAttribute('href', getLandingUrl(item));
  });
  document.querySelector<HTMLLinkElement>('link[rel="alternate"][hreflang="x-default"]')?.setAttribute('href', getLandingUrl(LANDING_DEFAULT_LOCALE));
}
function setJsonLd(copy: (typeof LANDING_COPY)[LandingLocale], locale: LandingLocale) {
  const element = document.querySelector<HTMLScriptElement>('script[type="application/ld+json"]');
  if (!element) return;
  element.textContent = JSON.stringify({
    '@context': 'https://schema.org',
    '@type': 'SoftwareApplication',
    name: 'SWIMVPN',
    alternateName: 'SWIMVPN Android VPN',
    inLanguage: [copy.seo.htmlLang],
    operatingSystem: 'Android',
    applicationCategory: 'SecurityApplication',
    description: copy.seo.description,
    downloadUrl: LANDING_DOWNLOAD_URL,
    url: getLandingUrl(locale),
    image: LANDING_OG_IMAGE_URL,
    offers: {
      '@type': 'Offer',
      price: USD_PRICE,
      priceCurrency: 'USD',
      availability: 'https://schema.org/InStock',
    },
    publisher: {
      '@type': 'Organization',
      name: 'SWIMVPN',
      url: getLandingUrl(LANDING_DEFAULT_LOCALE),
      logo: LANDING_OG_IMAGE_URL,
    },
  });
}

export default LandingPage;

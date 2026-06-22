import React, { useState, useEffect, lazy, Suspense } from 'react';
import CineApp from './components/cine/CineApp';
import { LandingLocale } from './components/landing/landingContent';

// CineApp (design « Reveal ») est désormais LA landing par défaut → importé statiquement
// pour être prerendu (SSR). Les autres routes (hash) sont code-splittées, dont l'ancienne
// landing showcase déplacée à #showcase.
const LandingPage = lazy(() => import('./components/landing/LandingPage'));
const OfferPage = lazy(() => import('./components/landing/OfferPage'));
const PrivacyPolicy = lazy(() => import('./components/legal/PrivacyPolicy'));
const TermsOfService = lazy(() => import('./components/legal/TermsOfService'));

export default function App({ initialLocale }: { initialLocale?: LandingLocale } = {}) {
  const [hash, setHash] = useState(() => (typeof window !== 'undefined' ? window.location.hash : ''));

  useEffect(() => {
    const handleHashChange = () => setHash(window.location.hash);
    window.addEventListener('hashchange', handleHashChange);
    return () => window.removeEventListener('hashchange', handleHashChange);
  }, []);

  let content;
  switch (hash) {
    case '#privacy':
      content = <PrivacyPolicy />;
      break;
    case '#terms':
      content = <TermsOfService />;
      break;
    case '#offres':
      content = <OfferPage />;
      break;
    case '#showcase':
      // Ancienne landing showcase, préservée ici.
      content = <LandingPage initialLocale={initialLocale} />;
      break;
    default:
      // Racine + #cine* → le design « Reveal » est la landing.
      content = <CineApp hash={hash} />;
  }

  // Design system (showcase.css) owns the page background. Suspense covers the
  // lazy routes; the default landing renders synchronously (no fallback flash).
  return (
    <div className="font-sans">
      <Suspense fallback={null}>{content}</Suspense>
    </div>
  );
}

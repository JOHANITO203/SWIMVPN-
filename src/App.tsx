import React, { useState, useEffect, lazy, Suspense } from 'react';
import LandingPage from './components/landing/LandingPage';
import { LandingLocale } from './components/landing/landingContent';

// Landing is the default + prerendered route → stays statically imported.
// The other routes are client-only (hash-triggered) → code-split so their JS
// is not shipped in the initial landing bundle.
const OfferPage = lazy(() => import('./components/landing/OfferPage'));
const PrivacyPolicy = lazy(() => import('./components/legal/PrivacyPolicy'));
const TermsOfService = lazy(() => import('./components/legal/TermsOfService'));
const CineApp = lazy(() => import('./components/cine/CineApp'));

export default function App({ initialLocale }: { initialLocale?: LandingLocale } = {}) {
  const [hash, setHash] = useState(() => (typeof window !== 'undefined' ? window.location.hash : ''));

  useEffect(() => {
    const handleHashChange = () => setHash(window.location.hash);
    window.addEventListener('hashchange', handleHashChange);
    return () => window.removeEventListener('hashchange', handleHashChange);
  }, []);

  let content;
  if (hash.startsWith('#cine')) {
    // Build « Éditorial sombre cinématique » (preview, non lié à la landing prod).
    content = <CineApp hash={hash} />;
  } else {
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
      default:
        content = <LandingPage initialLocale={initialLocale} />;
    }
  }

  // Design system (showcase.css) owns the page background. Suspense covers the
  // lazy routes; the default landing renders synchronously (no fallback flash).
  return (
    <div className="font-sans">
      <Suspense fallback={null}>{content}</Suspense>
    </div>
  );
}

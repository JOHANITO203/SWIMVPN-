import React, { useState, useEffect } from 'react';
import LandingPage from './components/landing/LandingPage';
import OfferPage from './components/landing/OfferPage';
import PrivacyPolicy from './components/legal/PrivacyPolicy';
import TermsOfService from './components/legal/TermsOfService';
import { LandingLocale } from './components/landing/landingContent';

// `initialLocale` is only supplied by the build-time prerender (Node, no `window`); on the client it
// is undefined and the locale is detected from the URL/storage. The `window` guard lets the tree
// render to static markup in Node without a browser.
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
    default:
      content = <LandingPage initialLocale={initialLocale} />;
  }

  // The landing's design system (showcase.css) owns the page background (light grammar);
  // legal pages set their own surface via LegalLayout. No forced dark wrapper here.
  return <div className="font-sans">{content}</div>;
}

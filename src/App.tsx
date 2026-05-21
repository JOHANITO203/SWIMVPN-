import React, { useState, useEffect } from 'react';
import LandingPage from './components/landing/LandingPage';
import PrivacyPolicy from './components/legal/PrivacyPolicy';
import TermsOfService from './components/legal/TermsOfService';

export default function App() {
  const [hash, setHash] = useState(window.location.hash);

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
    default:
      content = <LandingPage />;
  }

  return (
    <div className="min-h-screen bg-[#050505] font-sans">
      {content}
    </div>
  );
}

import React from 'react';

export const Footer = () => {
  return (
    <footer className="relative z-10 border-t border-white/5 bg-[#010101] px-6 py-12">
      <div className="container mx-auto flex flex-col items-center justify-between gap-8 md:flex-row">
        <div className="flex items-center gap-3">
          <div className="h-8 w-8 rounded-full bg-gradient-to-br from-[#B89AFF] via-[#8A6AF1] to-[#5D3BD8]" />
          <span className="text-xl font-black tracking-tighter text-white">SWIMVPN+</span>
        </div>

        <div className="flex flex-wrap justify-center gap-8 text-sm font-semibold text-[#A6A1B3]">
          <a href="#privacy" className="transition-colors hover:text-[#B89AFF]">Confidentialité</a>
          <a href="#terms" className="transition-colors hover:text-[#B89AFF]">Conditions</a>
          <a href="mailto:support@swimvpn.pro" className="transition-colors hover:text-[#B89AFF]">Support</a>
        </div>

        <div className="text-sm text-[#6E6978]">
          © {new Date().getFullYear()} SWIMVPN+. Tous droits réservés.
        </div>
      </div>
    </footer>
  );
};

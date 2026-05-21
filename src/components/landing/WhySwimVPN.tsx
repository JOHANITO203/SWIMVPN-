import React from 'react';
import { PhoneMockup } from './PhoneMockup';

export const WhySwimVPN = () => {
  return (
    <section className="relative z-10 overflow-hidden border-y border-white/5 px-4 py-24 md:px-6 md:py-32">
      <div className="absolute right-0 top-1/4 h-96 w-96 rounded-full bg-[#8A6AF1]/10 blur-[120px]" />

      <div className="container mx-auto grid items-center gap-8 lg:grid-cols-12">
        <div className="lg:col-span-7">
          <div className="mb-6 inline-flex rounded-full border border-[#8A6AF1]/30 bg-[#8A6AF1]/10 px-4 py-2 text-[11px] font-bold uppercase tracking-[0.18em] text-[#C8B6FF]">
            Deux façons d’utiliser SWIMVPN+
          </div>
          <h2 className="mb-8 max-w-3xl text-4xl font-black leading-[0.95] tracking-tighter text-white md:text-7xl">
            Payant quand vous voulez. Gratuit quand vous avez déjà vos configs.
          </h2>
          <p className="mb-10 max-w-2xl text-lg leading-relaxed text-[#A6A1B3]">
            L’app ne bloque pas l’utilisateur dans un seul modèle. Vous pouvez acheter des configs directement
            dans SWIMVPN+, ou utiliser l’app comme client VPN pour vos accès personnels.
          </p>

          <div className="grid gap-4 md:grid-cols-2">
            <div className="rounded-[2rem] border border-white/10 bg-[#111118]/75 p-7 shadow-[0_18px_42px_rgba(0,0,0,0.35)]">
              <div className="mb-5 text-sm font-bold uppercase tracking-[0.16em] text-[#B89AFF]">Accès SWIMVPN+</div>
              <h3 className="mb-3 text-2xl font-black text-white">Acheter dans l’app</h3>
              <p className="text-sm leading-relaxed text-[#A6A1B3]">
                Idéal si vous voulez une config prête à utiliser, reliée à votre offre et disponible depuis l’application.
              </p>
            </div>
            <div className="rounded-[2rem] border border-white/10 bg-[#111118]/75 p-7 shadow-[0_18px_42px_rgba(0,0,0,0.35)]">
              <div className="mb-5 text-sm font-bold uppercase tracking-[0.16em] text-[#B89AFF]">Client libre</div>
              <h3 className="mb-3 text-2xl font-black text-white">Importer sa config</h3>
              <p className="text-sm leading-relaxed text-[#A6A1B3]">
                Collez une config VLESS/Trojan ou scannez un QR code et utilisez SWIMVPN+ gratuitement avec vos accès existants.
              </p>
            </div>
          </div>
        </div>

        <div className="relative lg:col-span-5">
          <div className="absolute left-1/2 top-1/2 h-[70%] w-[70%] -translate-x-1/2 -translate-y-1/2 rounded-full bg-[#8A6AF1]/20 blur-[100px]" />
          <PhoneMockup />
        </div>
      </div>
    </section>
  );
};

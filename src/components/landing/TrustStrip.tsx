import React from 'react';

export const TrustStrip = () => {
  const BADGES = [
    'APK pré-release',
    'Trial offert',
    'Achat in-app',
    'Import VLESS/Trojan gratuit'
  ];

  return (
    <div className="relative z-20 border-y border-white/5 bg-[#07070B]/70 px-6 py-8 backdrop-blur-xl">
      <div className="container mx-auto flex flex-wrap justify-between items-center gap-6">
        <div className="hidden text-[10px] font-bold uppercase tracking-[0.2em] text-white/25 lg:block">
          Accès Android
        </div>
        <div className="flex flex-wrap justify-center items-center gap-6 md:gap-12 lg:gap-16">
          {BADGES.map((badge, idx) => (
            <div key={idx} className="flex items-center gap-2 md:gap-3">
              <div className="h-1.5 w-1.5 rounded-full bg-[#8A6AF1] shadow-[0_0_14px_rgba(138,106,241,0.7)]" />
              <span className="text-center text-[10px] font-bold uppercase tracking-[0.14em] text-white">{badge}</span>
            </div>
          ))}
        </div>
        <div className="hidden text-[10px] font-bold uppercase tracking-[0.2em] text-white/25 lg:block">
          Avant Play Store
        </div>
      </div>
    </div>
  );
};

import React from 'react';

export const TrustStrip = () => {
  const BADGES = [
    "One-tap connection",
    "Instant activation",
    "Smart resilience",
    "Military encryption"
  ];

  return (
    <div className="py-10 border-y border-white/5 bg-black/40 backdrop-blur-xl px-6 relative z-20">
      <div className="container mx-auto flex flex-wrap justify-between items-center gap-6">
        <div className="text-[10px] font-mono font-bold text-white/20 uppercase tracking-[0.2em] hidden lg:block">
          // NETWORK_VERIFIED
        </div>
        <div className="flex flex-wrap justify-center items-center gap-8 md:gap-16">
          {BADGES.map((badge, idx) => (
            <div key={idx} className="flex items-center gap-3">
              <div className="w-1.5 h-1.5 bg-cyan-500 rounded-full" />
              <span className="text-white font-mono font-bold tracking-widest uppercase text-[10px]">{badge}</span>
            </div>
          ))}
        </div>
        <div className="text-[10px] font-mono font-bold text-white/20 uppercase tracking-[0.2em] hidden lg:block">
          STATUS: OPTIMAL
        </div>
      </div>
    </div>
  );
};

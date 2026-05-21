import React from 'react';
import { motion } from 'motion/react';
import { Download, Gift, ShieldCheck, Smartphone } from 'lucide-react';

export const DownloadSection = () => {
  return (
    <section id="download-apk" className="relative z-10 overflow-hidden px-4 py-24 md:px-6 md:py-32">
      <div className="absolute left-1/2 top-1/2 h-[560px] w-full max-w-5xl -translate-x-1/2 -translate-y-1/2 rounded-full bg-[#8A6AF1]/10 blur-[160px]" />
      
      <div className="container relative z-10 mx-auto max-w-5xl">
        <div className="overflow-hidden rounded-[2.5rem] border border-white/10 bg-[#111118]/80 p-6 shadow-[0_24px_80px_rgba(0,0,0,0.58)] backdrop-blur-3xl md:p-10">
          <div className="grid gap-4 lg:grid-cols-5">
            <div className="rounded-[2rem] bg-[#07070B]/70 p-8 lg:col-span-3 md:p-12">
              <div className="mb-8 inline-flex items-center gap-2 rounded-full border border-[#35D978]/25 bg-[#35D978]/10 px-4 py-2 text-[11px] font-bold uppercase tracking-[0.16em] text-[#70F0A3]">
                <span className="h-2 w-2 rounded-full bg-[#35D978]" />
                Trial offert actuellement
              </div>
              <h2 className="mb-6 text-4xl font-black leading-[0.95] tracking-tighter text-white md:text-7xl">
                Téléchargez la pré-release APK.
              </h2>
              <p className="mb-10 max-w-2xl text-lg leading-relaxed text-[#A6A1B3]">
                La version Play Store arrive plus tard. Aujourd’hui, vous pouvez déjà installer l’APK officiel,
                tester l’application et choisir entre achat in-app ou import gratuit de configs personnelles.
              </p>
              <motion.a
                href="/downloads/swimvpn.apk"
                download="swimvpn.apk"
                aria-label="Télécharger l’APK SwimVPN"
                whileHover={{ scale: 1.02, y: -2 }}
                whileTap={{ scale: 0.98 }}
                className="inline-flex min-h-14 items-center justify-center gap-3 rounded-full bg-gradient-to-br from-[#B89AFF] via-[#8A6AF1] to-[#5D3BD8] px-9 py-4 text-lg font-black text-white shadow-[0_0_28px_rgba(138,106,241,0.42)] transition hover:brightness-110"
              >
                <Download size={24} strokeWidth={3} />
                Télécharger l’APK
              </motion.a>
            </div>

            <div className="grid gap-4 lg:col-span-2">
              <div className="rounded-[2rem] border border-white/10 bg-white/[0.04] p-7">
                <Smartphone className="mb-6 text-[#B89AFF]" />
                <h3 className="mb-2 text-xl font-black text-white">Android uniquement</h3>
                <p className="text-sm leading-relaxed text-[#A6A1B3]">APK officiel pour tester SWIMVPN+ avant la release Play Store.</p>
              </div>
              <div className="rounded-[2rem] border border-white/10 bg-white/[0.04] p-7">
                <Gift className="mb-6 text-[#B89AFF]" />
                <h3 className="mb-2 text-xl font-black text-white">Trial disponible</h3>
                <p className="text-sm leading-relaxed text-[#A6A1B3]">Essayez l’app maintenant et validez le workflow avant d’acheter.</p>
              </div>
              <div className="rounded-[2rem] border border-white/10 bg-white/[0.04] p-7">
                <ShieldCheck className="mb-6 text-[#35D978]" />
                <h3 className="mb-2 text-xl font-black text-white">Libre avec vos configs</h3>
                <p className="text-sm leading-relaxed text-[#A6A1B3]">VLESS et Trojan peuvent être collés ou importés pour un usage gratuit.</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
};

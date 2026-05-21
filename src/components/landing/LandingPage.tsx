import React from 'react';
import { motion, useReducedMotion } from 'motion/react';
import { Download } from 'lucide-react';
import { HeroSection } from './HeroSection';
import { TrustStrip } from './TrustStrip';
import { FeatureGrid } from './FeatureGrid';
import { HowItWorks } from './HowItWorks';
import { WhySwimVPN } from './WhySwimVPN';
import { DownloadSection } from './DownloadSection';
import { Footer } from './Footer';

const LandingPage = () => {
  const shouldReduceMotion = useReducedMotion();

  return (
    <div className="min-h-screen overflow-hidden bg-[#050505] text-[#F3F1F6] selection:bg-[#8A6AF1]/30 selection:text-white">
      <div className="pointer-events-none fixed inset-0 z-0">
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_76%_18%,rgba(138,106,241,0.18),transparent_34%),radial-gradient(circle_at_18%_52%,rgba(93,59,216,0.12),transparent_30%),linear-gradient(180deg,#07070B_0%,#050505_52%,#010101_100%)]" />
        <div className="absolute inset-0 bg-[linear-gradient(rgba(255,255,255,0.025)_1px,transparent_1px),linear-gradient(90deg,rgba(255,255,255,0.018)_1px,transparent_1px)] bg-[size:64px_64px] opacity-30" />
      </div>

      <nav className="fixed top-6 left-1/2 -translate-x-1/2 w-[calc(100%-2rem)] max-w-5xl z-50 pointer-events-none">
        <div className="px-4 py-2 md:px-6 md:py-2.5 bg-[#111118]/75 border border-white/10 backdrop-blur-2xl rounded-full flex justify-between items-center pointer-events-auto shadow-[0_18px_42px_rgba(0,0,0,0.55)]">
          <div className="flex items-center gap-3">
             <div className="relative group">
               <div className="absolute inset-0 bg-[#8A6AF1] blur-lg opacity-25 group-hover:opacity-45 transition-opacity" />
               <div className="relative w-8 h-8 md:w-9 md:h-9 bg-gradient-to-br from-[#B89AFF] via-[#8A6AF1] to-[#5D3BD8] rounded-full flex items-center justify-center shadow-lg">
                  <span className="text-white font-black italic text-base">S</span>
               </div>
             </div>
             <span className="text-white font-black tracking-tighter text-lg md:text-xl hidden xs:block">SWIMVPN+</span>
          </div>
          
          <div className="flex items-center gap-4 md:gap-8">
            <div className="hidden lg:flex gap-8 text-[10px] font-bold text-[#A6A1B3] uppercase tracking-[0.18em]">
              <a href="#features" className="hover:text-[#B89AFF] transition-colors">Fonctions</a>
              <a href="#usage" className="hover:text-[#B89AFF] transition-colors">Utilisation</a>
              <a href="#download-apk" className="hover:text-[#B89AFF] transition-colors">APK</a>
            </div>
            <a 
              href="#download-apk" 
              className="inline-flex items-center gap-2 px-4 py-2 md:px-5 md:py-2.5 bg-gradient-to-br from-[#B89AFF] via-[#8A6AF1] to-[#5D3BD8] text-white text-[10px] md:text-[11px] font-black rounded-full hover:brightness-110 transition-all uppercase tracking-tight shadow-[0_0_28px_rgba(138,106,241,0.38)]"
            >
              <Download size={14} />
              APK
            </a>
          </div>
        </div>
      </nav>

      <main>
        <HeroSection />
        <TrustStrip />
        <FeatureGrid />
        <HowItWorks />
        <WhySwimVPN />
        <DownloadSection />
        
        <section className="relative z-10 px-6 py-24 text-center">
          <h2 className="mx-auto mb-6 max-w-3xl text-4xl font-black tracking-tighter text-white md:text-6xl">
            La pré-release est disponible maintenant.
          </h2>
          <p className="mx-auto mb-10 max-w-xl text-lg leading-relaxed text-[#A6A1B3]">
            Téléchargez l’APK, activez le trial depuis l’app et utilisez SWIMVPN+ avec nos configs in-app ou vos propres configs VLESS/Trojan.
          </p>
          <motion.a
            href="#download-apk"
            whileHover={shouldReduceMotion ? undefined : { scale: 1.05 }}
            whileTap={shouldReduceMotion ? undefined : { scale: 0.95 }}
            className="inline-flex items-center gap-3 rounded-full bg-white px-10 py-5 text-lg font-black text-black shadow-[0_24px_70px_rgba(255,255,255,0.16)] transition-all hover:bg-[#F3F1F6]"
          >
            <Download size={22} />
            Télécharger l’APK
          </motion.a>
        </section>
      </main>

      <Footer />
    </div>
  );
};

export default LandingPage;

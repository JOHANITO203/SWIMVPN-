import React from 'react';
import { motion } from 'motion/react';
import { HeroSection } from './HeroSection';
import { TrustStrip } from './TrustStrip';
import { FeatureGrid } from './FeatureGrid';
import { HowItWorks } from './HowItWorks';
import { WhySwimVPN } from './WhySwimVPN';
import { DownloadSection } from './DownloadSection';
import { Footer } from './Footer';

const LandingPage = () => {
  return (
    <div className="bg-[#05070A] min-h-screen text-slate-200 selection:bg-cyan-500/30 selection:text-cyan-200">
      <nav className="fixed top-6 left-1/2 -translate-x-1/2 w-[calc(100%-2rem)] max-w-5xl z-50 pointer-events-none">
        <div className="px-4 py-2 md:px-6 md:py-2.5 bg-black/40 border border-white/10 backdrop-blur-2xl rounded-2xl flex justify-between items-center pointer-events-auto shadow-[0_0_50px_rgba(0,0,0,0.5)]">
          <div className="flex items-center gap-3">
             <div className="relative group">
               <div className="absolute inset-0 bg-cyan-500 blur-lg opacity-20 group-hover:opacity-40 transition-opacity" />
               <div className="relative w-8 h-8 md:w-9 md:h-9 bg-gradient-to-br from-cyan-400 to-blue-600 rounded-lg flex items-center justify-center shadow-lg">
                  <span className="text-white font-black italic text-base">S</span>
               </div>
             </div>
             <span className="text-white font-black tracking-tighter text-lg md:text-xl italic hidden xs:block">SWIMVPN+</span>
          </div>
          
          <div className="flex items-center gap-4 md:gap-8">
            <div className="hidden md:flex gap-8 text-[10px] font-bold text-slate-400 uppercase tracking-[0.2em] font-mono">
              <a href="#features" className="hover:text-cyan-400 transition-colors">/FEATURES</a>
              <a href="#protocol" className="hover:text-cyan-400 transition-colors">/PROTOCOL</a>
              <a href="#download" className="hover:text-cyan-400 transition-colors">/DOWNLOAD</a>
            </div>
            <a 
              href="#download-apk" 
              className="px-4 py-2 md:px-5 md:py-2 bg-cyan-500 text-black text-[10px] font-black rounded-lg hover:bg-cyan-400 transition-all uppercase tracking-tight shadow-[0_0_20px_rgba(6,182,212,0.3)]"
            >
              FETCH APK
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
        
        {/* Final CTA */}
        <section className="py-24 px-6 text-center">
          <h2 className="text-4xl md:text-5xl font-black text-white mb-6 uppercase tracking-tighter italic">READY TO CONNECT?</h2>
          <p className="text-slate-400 mb-10 text-lg">Start with one tap. No setup required.</p>
          <motion.a
            href="#download-apk"
            whileHover={{ scale: 1.05 }}
            whileTap={{ scale: 0.95 }}
            className="px-12 py-5 bg-white text-black font-black text-xl rounded-2xl inline-block hover:bg-cyan-50 transition-all uppercase tracking-tight shadow-[0_0_40px_rgba(255,255,255,0.1)]"
          >
            DOWNLOAD APK
          </motion.a>
        </section>
      </main>

      <Footer />
    </div>
  );
};

export default LandingPage;

import React from 'react';
import { motion } from 'motion/react';
import { Download, ChevronRight } from 'lucide-react';
import InteractivePixelGlobe from './InteractivePixelGlobe';

export const HeroSection = () => {
  return (
    <section className="relative min-h-[110vh] flex flex-col items-center justify-center pt-32 md:pt-40 pb-20 px-6 overflow-hidden">
      {/* Background ambient light */}
      <div className="absolute top-0 left-1/4 w-full max-w-4xl h-96 bg-cyan-500/10 blur-[120px] rounded-full pointer-events-none" />
      <div className="absolute bottom-0 right-1/4 w-full max-w-4xl h-96 bg-blue-500/5 blur-[120px] rounded-full pointer-events-none" />
      
      <div className="container mx-auto grid lg:grid-cols-2 gap-12 items-center z-10">
        <motion.div
          initial={{ opacity: 0, x: -30 }}
          animate={{ opacity: 1, x: 0 }}
          transition={{ duration: 1, ease: [0.16, 1, 0.3, 1] }}
          className="text-center lg:text-left"
        >
          <motion.div
            initial={{ opacity: 0, scale: 0.9 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ delay: 0.2 }}
            className="flex items-center justify-center lg:justify-start gap-2 mb-8"
          >
            <div className="px-3 py-1 border border-cyan-500/30 bg-cyan-500/5 rounded text-[10px] font-bold text-cyan-400 uppercase tracking-[0.3em] font-mono">
              V.4.2.0-STABLE
            </div>
            <div className="w-1 h-1 bg-white/20 rounded-full" />
            <div className="text-[10px] font-bold text-white/40 uppercase tracking-[0.2em] font-mono">
              ENCRYPTED NODES: 4,092
            </div>
          </motion.div>
          
          <h1 className="text-6xl md:text-8xl lg:text-[9rem] font-black text-white leading-[0.85] tracking-[-0.05em] mb-8 uppercase italic shrink-0">
            SECURE <br />
            <span className="text-transparent bg-clip-text bg-gradient-to-r from-white via-cyan-400 to-blue-600">ANYTHING.</span>
          </h1>
          
          <p className="text-slate-400 text-lg md:text-xl max-w-xl mx-auto lg:mx-0 mb-12 leading-relaxed font-medium">
            SwimVPN+ is the ultimate Android protocol for seamless browsing. Persistent, stealthy, and built for speed on restricted networks.
          </p>
          
          <div className="flex flex-col sm:flex-row items-center justify-center lg:justify-start gap-6">
            <motion.a
              href="#download-apk"
              whileHover={{ scale: 1.02, y: -2 }}
              whileTap={{ scale: 0.98 }}
              className="group relative px-10 py-5 bg-white text-black font-black rounded-2xl flex items-center gap-3 overflow-hidden transition-all shadow-[0_20px_40px_-15px_rgba(255,255,255,0.3)] hover:shadow-[0_30px_60px_-15px_rgba(255,255,255,0.4)]"
            >
              <Download size={22} strokeWidth={3} />
              DOWNLOAD APK
            </motion.a>
            
            <a
              href="#features"
              className="group px-10 py-5 bg-white/5 border border-white/10 text-white font-bold rounded-2xl flex items-center gap-3 hover:bg-white/10 transition-all hover:border-white/20"
            >
              /DOCUMENTATION
              <ChevronRight size={20} className="group-hover:translate-x-1 transition-transform" />
            </a>
          </div>
        </motion.div>
        
        <motion.div
          initial={{ opacity: 0, scale: 0.9 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ duration: 1.2, ease: "easeOut" }}
          className="relative order-first lg:order-last h-[400px] md:h-[600px] lg:h-[700px] flex items-center justify-center lg:pl-12"
        >
          <div className="w-full h-full lg:translate-x-12 lg:translate-y-40">
            <InteractivePixelGlobe />
          </div>
        </motion.div>
      </div>
      
      {/* Scroll indicator */}
      <motion.div 
        animate={{ y: [0, 10, 0] }}
        transition={{ duration: 2, repeat: Infinity }}
        className="absolute bottom-10 left-1/2 -translate-x-1/2 w-6 h-10 border-2 border-white/10 rounded-full flex justify-center p-1"
      >
        <div className="w-1.5 h-3 bg-cyan-400 rounded-full" />
      </motion.div>
    </section>
  );
};

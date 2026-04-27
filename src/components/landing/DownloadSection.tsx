import React from 'react';
import { motion } from 'motion/react';
import { Download, Smartphone } from 'lucide-react';

export const DownloadSection = () => {
  return (
    <section id="download-apk" className="py-32 px-6 relative overflow-hidden">
      {/* Background effects */}
      <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-full max-w-5xl h-[500px] bg-cyan-600/5 blur-[160px] rounded-full" />
      
      <div className="container mx-auto max-w-4xl relative z-10">
        <div className="border border-white/5 bg-black/40 rounded-[2rem] p-12 md:p-24 text-center backdrop-blur-3xl overflow-hidden relative">
          <div className="absolute top-0 left-0 w-full h-px bg-gradient-to-r from-transparent via-cyan-500/20 to-transparent" />
          
          <motion.div
            initial={{ opacity: 0, scale: 0.9 }}
            whileInView={{ opacity: 1, scale: 1 }}
            viewport={{ once: true }}
            className="mb-12 flex flex-col items-center gap-4"
          >
            <div className="px-4 py-1 border border-cyan-500/30 bg-cyan-500/5 rounded text-[10px] font-bold text-cyan-400 uppercase tracking-[0.4em] font-mono mb-4">
              // DOWNLOAD_V_4_2_0
            </div>
            <div className="w-20 h-20 border border-white/10 rounded-3xl flex items-center justify-center relative group">
              <div className="absolute inset-0 bg-cyan-500/10 blur-xl opacity-0 group-hover:opacity-100 transition-opacity" />
              <Download className="text-white group-hover:text-cyan-400 transition-colors" size={32} />
            </div>
          </motion.div>
          
          <h2 className="text-5xl md:text-8xl font-black text-white mb-8 tracking-tighter uppercase italic leading-[0.85]">
            GET THE <br /> <span className="text-glow-cyan">OFFICIAL APK.</span>
          </h2>
          
          <p className="text-slate-500 text-lg md:text-xl max-w-2xl mx-auto mb-16 font-medium leading-relaxed">
            Unrestricted access starts here. Optimized for stability, 256-bit encryption, and zero-latency performance.
          </p>
          
          <div className="flex flex-col items-center gap-8">
            <motion.a
              href="#download-apk"
              whileHover={{ scale: 1.02, y: -2 }}
              whileTap={{ scale: 0.98 }}
              className="px-16 py-6 bg-white text-black font-black text-xl rounded-2xl flex items-center gap-4 shadow-[0_30px_60px_-15px_rgba(255,255,255,0.2)] hover:shadow-[0_40px_80px_-15px_rgba(255,255,255,0.3)] transition-all"
            >
              <Download size={28} strokeWidth={3} />
              DOWNLOAD APK
            </motion.a>
            
            <div className="flex flex-wrap justify-center items-center gap-6 text-white/30 text-[10px] font-bold tracking-[0.2em] uppercase font-mono">
              <span className="flex items-center gap-2"><Smartphone size={14} /> ANDROID_X64</span>
              <div className="w-1 h-1 bg-white/10 rounded-full" />
              <span>SHA-256 VERIFIED</span>
              <div className="w-1 h-1 bg-white/10 rounded-full" />
              <span>BUILD: 2026.04.27</span>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
};

import React from 'react';
import { motion } from 'motion/react';
import { Shield, Zap, Lock } from 'lucide-react';

export const PhoneMockup = () => {
  return (
    <div className="relative w-72 h-[580px] mx-auto bg-black border-[8px] border-white/5 rounded-[3.5rem] shadow-2xl overflow-hidden shadow-cyan-500/10 transition-transform duration-500 hover:scale-[1.02]">
      {/* Notch */}
      <div className="absolute top-0 left-1/2 -translate-x-1/2 w-32 h-6 bg-white/5 rounded-b-2xl z-20 border-x border-b border-white/5" />
      
      {/* Inner Screen */}
      <div className="relative h-full w-full bg-[#05070A] p-8 flex flex-col items-center justify-between pt-16">
        {/* Technical Grid inside phone */}
        <div className="absolute inset-0 opacity-[0.03] pointer-events-none" 
             style={{ backgroundImage: 'linear-gradient(#fff 1px, transparent 1px), linear-gradient(90deg, #fff 1px, transparent 1px)', backgroundSize: '20px 20px' }} />

        <div className="w-full flex justify-between items-center bg-white/5 p-4 rounded-xl border border-white/5 relative z-10">
           <div className="flex items-center gap-3">
             <div className="w-7 h-7 bg-gradient-to-br from-cyan-400 to-blue-600 rounded-lg shadow-lg flex items-center justify-center">
                <Shield size={14} className="text-white" />
             </div>
             <span className="text-[10px] font-black italic text-white tracking-widest leading-none">SWIMVPN+</span>
           </div>
           <div className="text-[8px] text-white/30 font-bold uppercase tracking-[0.2em] font-mono">NODE:STABLE</div>
        </div>

        <div className="flex-1 flex flex-col items-center justify-center gap-12 relative z-10 w-full">
           <motion.div 
             animate={{ scale: [1, 1.02, 1], rotate: [0, 1, 0] }}
             transition={{ duration: 6, repeat: Infinity, ease: "easeInOut" }}
             className="w-40 h-40 rounded-full border border-cyan-500/20 flex items-center justify-center relative"
           >
             <div className="absolute inset-0 bg-cyan-400/5 rounded-full" />
             <div className="absolute inset-2 border border-blue-500/10 rounded-full animate-spin-slow" />
             <div className="w-28 h-28 bg-black border border-white/10 rounded-full flex items-center justify-center shadow-[0_0_50px_rgba(6,182,212,0.2)]">
                <motion.div
                  animate={{ opacity: [0.5, 1, 0.5] }}
                  transition={{ duration: 2, repeat: Infinity }}
                  className="w-24 h-24 bg-cyan-500/5 rounded-full flex items-center justify-center"
                >
                  <Shield className="text-cyan-400" size={40} strokeWidth={1.5} />
                </motion.div>
             </div>
           </motion.div>
           
           <div className="text-center w-full space-y-2">
             <h4 className="text-white font-black text-2xl tracking-tighter uppercase italic leading-none">PROTECTED</h4>
             <div className="flex items-center justify-center gap-2">
               <div className="w-1.5 h-1.5 bg-cyan-500 rounded-full animate-pulse" />
               <p className="text-cyan-400 text-[9px] font-mono font-bold tracking-[0.4em] uppercase">LINK_ESTABLISHED</p>
             </div>
           </div>
        </div>

        <div className="w-full space-y-6 relative z-10">
          <div className="grid grid-cols-2 gap-4">
            <div className="bg-white/5 border border-white/5 p-3 rounded-xl">
              <div className="flex items-center gap-2 mb-1">
                <Zap size={12} className="text-yellow-400 opacity-60" />
                <span className="text-[8px] text-white/30 font-mono font-bold">LATENCY</span>
              </div>
              <div className="text-[12px] text-white font-mono font-bold">14.02ms</div>
            </div>
            <div className="bg-white/5 border border-white/5 p-3 rounded-xl">
              <div className="flex items-center gap-2 mb-1">
                <Lock size={12} className="text-emerald-400 opacity-60" />
                <span className="text-[8px] text-white/30 font-mono font-bold">SECURITY</span>
              </div>
              <div className="text-[12px] text-white font-mono font-bold italic">AES-256</div>
            </div>
          </div>
          <button className="w-full py-5 bg-white text-black font-black text-[11px] rounded-2xl uppercase tracking-[0.2em] shadow-xl hover:scale-[1.02] active:scale-[0.98] transition-all">
            TERMINATE LINK
          </button>
        </div>
      </div>
    </div>
  );
};

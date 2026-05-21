import React from 'react';
import { motion, useReducedMotion } from 'motion/react';
import { Copy, Shield, Zap } from 'lucide-react';

export const PhoneMockup = () => {
  const shouldReduceMotion = useReducedMotion();

  return (
    <div className="relative w-72 h-[580px] mx-auto overflow-hidden rounded-[3.5rem] border-[8px] border-white/5 bg-black shadow-2xl shadow-[#8A6AF1]/10 transition-transform duration-500 hover:scale-[1.02]">
      {/* Notch */}
      <div className="absolute top-0 left-1/2 -translate-x-1/2 w-32 h-6 bg-white/5 rounded-b-2xl z-20 border-x border-b border-white/5" />
      
      {/* Inner Screen */}
      <div className="relative flex h-full w-full flex-col items-center justify-between bg-[#050505] p-8 pt-16">
        {/* Technical Grid inside phone */}
        <div className="absolute inset-0 opacity-[0.03] pointer-events-none" 
             style={{ backgroundImage: 'linear-gradient(#fff 1px, transparent 1px), linear-gradient(90deg, #fff 1px, transparent 1px)', backgroundSize: '20px 20px' }} />

        <div className="relative z-10 flex w-full items-center justify-between rounded-3xl border border-white/10 bg-white/[0.04] p-4">
           <div className="flex items-center gap-3">
             <div className="flex h-7 w-7 items-center justify-center rounded-full bg-gradient-to-br from-[#B89AFF] via-[#8A6AF1] to-[#5D3BD8] shadow-lg">
                <Shield size={14} className="text-white" />
             </div>
             <span className="text-[10px] font-black text-white tracking-widest leading-none">SWIMVPN+</span>
           </div>
           <div className="text-[8px] font-bold uppercase tracking-[0.2em] text-white/30">TRIAL</div>
        </div>

        <div className="flex-1 flex flex-col items-center justify-center gap-12 relative z-10 w-full">
           <motion.div 
             animate={shouldReduceMotion ? undefined : { scale: [1, 1.02, 1], rotate: [0, 1, 0] }}
             transition={shouldReduceMotion ? undefined : { duration: 6, repeat: Infinity, ease: "easeInOut" }}
             className="relative flex h-40 w-40 items-center justify-center rounded-full border border-[#8A6AF1]/25"
           >
             <div className="absolute inset-0 rounded-full bg-[#8A6AF1]/10" />
             <div className="absolute inset-2 rounded-full border border-[#B89AFF]/10" />
             <div className="flex h-28 w-28 items-center justify-center rounded-full border border-white/10 bg-black shadow-[0_0_50px_rgba(138,106,241,0.24)]">
                <motion.div
                  animate={shouldReduceMotion ? undefined : { opacity: [0.5, 1, 0.5] }}
                  transition={shouldReduceMotion ? undefined : { duration: 2, repeat: Infinity }}
                  className="flex h-24 w-24 items-center justify-center rounded-full bg-[#8A6AF1]/10"
                >
                  <Shield className="text-[#B89AFF]" size={40} strokeWidth={1.5} />
                </motion.div>
             </div>
           </motion.div>
           
           <div className="text-center w-full space-y-2">
             <h4 className="text-2xl font-black leading-none tracking-tighter text-white">Connecté</h4>
             <div className="flex items-center justify-center gap-2">
               <div className="h-1.5 w-1.5 animate-pulse rounded-full bg-[#35D978]" />
               <p className="text-[9px] font-bold uppercase tracking-[0.3em] text-[#70F0A3]">Protection active</p>
             </div>
           </div>
        </div>

        <div className="w-full space-y-6 relative z-10">
          <div className="grid grid-cols-2 gap-4">
            <div className="bg-white/5 border border-white/5 p-3 rounded-xl">
              <div className="mb-1 flex items-center gap-2">
                <Zap size={12} className="text-[#B89AFF] opacity-70" />
                <span className="text-[8px] font-bold text-white/30">Agent IA</span>
              </div>
              <div className="text-[12px] font-bold text-white">Meilleur nœud</div>
            </div>
            <div className="bg-white/5 border border-white/5 p-3 rounded-xl">
              <div className="mb-1 flex items-center gap-2">
                <Copy size={12} className="text-[#B89AFF] opacity-70" />
                <span className="text-[8px] font-bold text-white/30">Config</span>
              </div>
              <div className="text-[12px] font-bold text-white">VLESS/Trojan</div>
            </div>
          </div>
          <button className="w-full rounded-full bg-gradient-to-br from-[#B89AFF] via-[#8A6AF1] to-[#5D3BD8] py-5 text-[11px] font-black uppercase tracking-[0.18em] text-white shadow-xl transition-all hover:scale-[1.02] active:scale-[0.98]">
            Connecter
          </button>
        </div>
      </div>
    </div>
  );
};

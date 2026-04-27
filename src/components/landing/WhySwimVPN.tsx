import React from 'react';
import { PhoneMockup } from './PhoneMockup';

export const WhySwimVPN = () => {
  return (
    <section className="py-32 px-6 border-y border-white/5 relative overflow-hidden">
      <div className="absolute top-0 left-0 w-full h-px bg-gradient-to-r from-transparent via-cyan-500/20 to-transparent" />
      
      <div className="container mx-auto grid lg:grid-cols-2 gap-24 items-center">
        <div className="text-left">
          <div className="text-cyan-400 font-mono text-xs font-bold tracking-[0.4em] mb-6 uppercase">
            // NETWORK_ADAPTIVITY
          </div>
          <h2 className="text-5xl md:text-7xl font-black text-white mb-10 tracking-tighter uppercase italic leading-[0.9]">
            BUILT FOR <br /> 
            <span className="text-glow-cyan">ADVERSE CONDITIONS.</span>
          </h2>
          <p className="text-slate-400 text-lg md:text-xl max-w-xl mb-12 leading-relaxed">
            Standard protocols fail under pressure. SwimVPN+ is a custom-engineered tunnel designed to maintain persistence where others drop.
          </p>
          
          <div className="space-y-10">
            <div className="flex flex-col sm:flex-row items-center gap-6 font-bold tracking-[0.2em] text-[9px] uppercase font-mono">
               <div className="flex flex-col items-center lg:items-start gap-4 flex-1 w-full">
                 <span className="text-white/20">// LOCAL_NODE</span>
                 <div className="w-full px-6 py-4 bg-white/5 rounded-xl border border-white/10 text-white italic">
                   CLIENTDEVICE.v4
                 </div>
               </div>
               
               <div className="text-cyan-500/40 hidden sm:block mt-8">
                 <div className="flex gap-1 animate-pulse">
                   {[...Array(3)].map((_, i) => <div key={i} className="w-1.5 h-1.5 bg-cyan-500 rounded-full" />)}
                 </div>
               </div>
               
               <div className="flex flex-col items-center lg:items-start gap-4 flex-1 w-full">
                 <span className="text-cyan-400">// PROTOCOL_SMART_TUNNEL</span>
                 <div className="w-full px-6 py-4 border border-cyan-400/30 bg-cyan-400/5 rounded-xl text-cyan-400 italic shadow-[0_0_30px_rgba(6,182,212,0.1)]">
                   AETHER_BYPASS_ENG
                 </div>
               </div>
            </div>
            
            <div className="p-6 border-l-2 border-cyan-500 bg-cyan-500/5 text-[11px] text-slate-500 font-medium leading-relaxed italic">
              "SwimVPN+ employs dynamic obfuscation heuristics to mask traffic as standard TLS-encrypted telemetry, ensuring maximum penetration across restricted local gateways."
            </div>
          </div>
        </div>
        
        <div className="relative order-first lg:order-last">
          {/* Decorative glows */}
          <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[120%] h-[120%] bg-cyan-500/10 blur-[120px] rounded-full pointer-events-none" />
          <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-48 h-48 bg-blue-500/20 shadow-[0_0_100px_rgba(6,182,212,0.4)] rounded-full pointer-events-none" />
          <PhoneMockup />
        </div>
      </div>
    </section>
  );
};

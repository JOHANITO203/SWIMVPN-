import React from 'react';
import { motion } from 'motion/react';
import { Shield, Zap, Globe, Lock, Signal, Gauge } from 'lucide-react';

const FEATURES = [
  {
    title: "Always Connected",
    text: "Automatically keeps your connection stable when networks become unreliable.",
    icon: Signal,
    color: "text-cyan-400",
    bg: "bg-cyan-400/10"
  },
  {
    title: "Access Anywhere",
    text: "Reach the content you need, even when access is restricted.",
    icon: Globe,
    color: "text-blue-400",
    bg: "bg-blue-400/10"
  },
  {
    title: "Fast Performance",
    text: "Optimized routes help keep browsing and streaming smooth.",
    icon: Zap,
    color: "text-yellow-400",
    bg: "bg-yellow-400/10"
  },
  {
    title: "Secure Traffic",
    text: "Your traffic is encrypted and protected while you stay connected.",
    icon: Lock,
    color: "text-emerald-400",
    bg: "bg-emerald-400/10"
  },
  {
    title: "Advanced Obfuscation",
    text: "Your connection blends into normal traffic to reduce blocking.",
    icon: Shield,
    color: "text-indigo-400",
    bg: "bg-indigo-400/10"
  },
  {
    title: "Smart Connection",
    text: "SwimVPN+ adapts automatically without manual configuration.",
    icon: Gauge,
    color: "text-rose-400",
    bg: "bg-rose-400/10"
  }
];

export const FeatureGrid = () => {
  return (
    <section id="features" className="py-32 px-6 relative">
      <div className="absolute top-0 left-0 w-full h-px bg-gradient-to-r from-transparent via-white/10 to-transparent" />
      
      <div className="container mx-auto">
        <div className="flex flex-col lg:flex-row items-end justify-between mb-20 gap-8">
          <div className="max-w-2xl text-left">
            <div className="text-cyan-400 font-mono text-xs font-bold tracking-[0.4em] mb-4 uppercase">
              // CORE_CAPABILITIES
            </div>
            <h2 className="text-5xl md:text-7xl font-black text-white tracking-tighter uppercase italic leading-none">
              ENGINEERED FOR <br /> HIGH-STAKES ADAPTIVITY.
            </h2>
          </div>
          <p className="text-slate-400 text-lg max-w-md text-left lg:text-right border-l lg:border-l-0 lg:border-r border-white/10 pl-6 lg:pl-0 lg:pr-6 py-2">
            SwimVPN+ isn't just a client. It's a persistent networking layer that protects your digital footprint across aggressive environments.
          </p>
        </div>
        
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-1">
          {FEATURES.map((feature, idx) => (
            <motion.div
              key={idx}
              whileHover={{ backgroundColor: 'rgba(255, 255, 255, 0.03)' }}
              className="p-10 border border-white/5 transition-all duration-500 relative group overflow-hidden"
            >
              <div className="absolute top-0 right-0 w-8 h-8 border-t border-r border-cyan-500/0 group-hover:border-cyan-500/40 transition-all duration-500" />
              
              <div className="text-[10px] font-mono text-white/20 mb-12 flex justify-between items-center tracking-widest uppercase">
                <span>00{idx + 1} // SYS.MOD</span>
                <feature.icon className="opacity-40 group-hover:opacity-100 transition-opacity text-cyan-400" size={16} />
              </div>
              
              <h3 className="text-2xl font-black text-white mb-4 tracking-tighter uppercase italic">
                {feature.title}
              </h3>
              <p className="text-slate-500 leading-relaxed font-medium group-hover:text-slate-400 transition-colors">
                {feature.text}
              </p>
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  );
};

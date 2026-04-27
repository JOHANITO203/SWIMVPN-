import React from 'react';
import { motion } from 'motion/react';

const STEPS = [
  { id: "01", title: "Download the APK", text: "Secure the latest version directly." },
  { id: "02", title: "Open SwimVPN+", text: "Instant installation on Android." },
  { id: "03", title: "Tap Connect", text: "One click to bypass restrictions." },
  { id: "04", title: "Stay Connected", text: "Automatic smart reconnection." }
];

export const HowItWorks = () => {
  return (
    <section className="py-32 px-6 bg-black/20 border-y border-white/5">
      <div className="container mx-auto">
        <div className="flex flex-col md:flex-row md:items-center justify-between mb-20 gap-4">
          <h2 className="text-4xl md:text-6xl font-black text-white tracking-tighter uppercase italic">
            DEPLOYMENT <br /> PROTOCOL
          </h2>
          <div className="h-px flex-grow bg-white/10 mx-8 hidden md:block" />
          <div className="text-[10px] font-mono text-cyan-400 font-bold tracking-[0.3em] uppercase">
            // 04_SIMPLE_STEPS
          </div>
        </div>
        
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
          {STEPS.map((step, idx) => (
            <motion.div
              key={idx}
              initial={{ opacity: 0, y: 10 }}
              whileInView={{ opacity: 1, y: 0 }}
              transition={{ delay: idx * 0.1 }}
              viewport={{ once: true }}
              className="p-8 border border-white/5 bg-white/[0.02] relative group"
            >
              <div className="text-4xl font-black text-white/5 mb-8 font-mono group-hover:text-cyan-500/10 transition-colors">
                {step.id}
              </div>
              <h4 className="text-lg font-black text-white mb-3 uppercase tracking-tight italic group-hover:text-cyan-400 transition-colors">
                {step.title}
              </h4>
              <p className="text-slate-500 text-sm font-medium leading-relaxed">
                {step.text}
              </p>
              
              <div className="absolute bottom-0 left-0 w-0 h-[2px] bg-cyan-500 group-hover:w-full transition-all duration-700" />
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  );
};

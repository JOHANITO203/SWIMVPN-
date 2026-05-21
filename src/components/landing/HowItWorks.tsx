import React from 'react';
import { motion } from 'motion/react';

const STEPS = [
  { id: '01', title: 'Téléchargez l’APK', text: 'Installez la pré-release officielle avant l’arrivée Play Store.' },
  { id: '02', title: 'Activez le trial', text: 'Testez l’expérience SWIMVPN+ sans attendre.' },
  { id: '03', title: 'Choisissez votre source', text: 'Achetez une config in-app ou importez une config VLESS/Trojan personnelle.' },
  { id: '04', title: 'Connectez-vous', text: 'Lancez le VPN et laissez l’app gérer l’état de connexion.' }
];

export const HowItWorks = () => {
  return (
    <section id="usage" className="relative z-10 border-y border-white/5 bg-[#07070B]/50 px-4 py-24 md:px-6 md:py-32">
      <div className="container mx-auto">
        <div className="flex flex-col md:flex-row md:items-end justify-between mb-16 md:mb-20 gap-6">
          <h2 className="text-4xl font-black leading-[0.95] tracking-tighter text-white sm:text-5xl md:text-7xl">
            Simple à installer. <br /> Flexible à utiliser.
          </h2>
          <div className="mx-8 mb-4 hidden h-px flex-grow bg-gradient-to-r from-transparent via-white/10 to-transparent md:block" />
          <div className="text-[11px] font-bold uppercase tracking-[0.18em] text-[#C8B6FF] md:mb-4">
            4 étapes
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
              className="group relative min-h-[230px] overflow-hidden rounded-[2rem] border border-white/10 bg-[#111118]/75 p-8 shadow-[0_18px_42px_rgba(0,0,0,0.35)]"
            >
              <div className="mb-8 text-5xl font-black text-white/5 transition-colors group-hover:text-[#8A6AF1]/20">
                {step.id}
              </div>
              <h4 className="mb-3 text-xl font-black tracking-tight text-white transition-colors group-hover:text-[#C8B6FF]">
                {step.title}
              </h4>
              <p className="text-sm font-medium leading-relaxed text-[#A6A1B3]">
                {step.text}
              </p>
              
              <div className="absolute bottom-0 left-0 h-px w-0 bg-[#8A6AF1] transition-all duration-700 group-hover:w-full" />
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  );
};

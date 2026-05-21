import React from 'react';
import { motion, useReducedMotion } from 'motion/react';
import { Bot, Copy, Lock, QrCode, RadioTower, ShoppingBag } from 'lucide-react';

const FEATURES = [
  {
    title: 'Acheter dans l’app',
    text: 'Choisissez une offre, payez depuis SWIMVPN+, puis utilisez les configs livrées dans votre espace.',
    icon: ShoppingBag,
    span: 'md:col-span-2 lg:col-span-2',
  },
  {
    title: 'Importer gratuitement',
    text: 'Vous avez déjà une config VLESS ou Trojan ? Copiez-collez-la et connectez-vous sans abonnement.',
    icon: Copy,
    span: 'md:col-span-2 lg:col-span-2',
  },
  {
    title: 'QR code',
    text: 'Ajoutez une config plus vite avec le scan QR quand votre fournisseur vous en donne un.',
    icon: QrCode,
    span: '',
  },
  {
    title: 'Agent IA',
    text: 'L’app aide à choisir les meilleurs nœuds disponibles en temps réel pour une connexion plus simple.',
    icon: Bot,
    span: '',
  },
  {
    title: 'Protocoles utiles',
    text: 'Pensé pour les configs modernes comme VLESS, Trojan et les formats compatibles Xray/V2Ray.',
    icon: RadioTower,
    span: '',
  },
  {
    title: 'Tunnel sécurisé',
    text: 'Une interface Android sobre pour lancer, surveiller et couper votre connexion VPN simplement.',
    icon: Lock,
    span: '',
  },
];

export const FeatureGrid = () => {
  const shouldReduceMotion = useReducedMotion();

  return (
    <section id="features" className="relative z-10 px-4 py-24 md:px-6 md:py-32">
      <div className="container mx-auto">
        <div className="mb-12 max-w-3xl md:mb-16">
          <div className="mb-4 inline-flex rounded-full border border-[#8A6AF1]/30 bg-[#8A6AF1]/10 px-4 py-2 text-[11px] font-bold uppercase tracking-[0.18em] text-[#C8B6FF]">
            Ce que l’app permet
          </div>
          <h2 className="text-4xl font-black leading-[0.95] tracking-tighter text-white md:text-7xl">
            Acheter, importer, connecter.
          </h2>
          <p className="mt-6 max-w-2xl text-lg leading-relaxed text-[#A6A1B3]">
            SWIMVPN+ n’impose pas un seul modèle. Vous pouvez acheter des accès dans l’app ou utiliser vos propres configurations gratuitement.
          </p>
        </div>

        <div className="grid grid-cols-1 gap-4 md:grid-cols-4">
          {FEATURES.map((feature, idx) => (
            <motion.article
              key={feature.title}
              initial={shouldReduceMotion ? { opacity: 1, y: 0 } : { opacity: 0, y: 18 }}
              whileInView={{ opacity: 1, y: 0 }}
              transition={shouldReduceMotion ? { duration: 0 } : { delay: idx * 0.04, duration: 0.55, ease: [0.22, 1, 0.36, 1] }}
              viewport={{ once: true, margin: '-80px' }}
              className={`group relative min-h-[220px] overflow-hidden rounded-[2rem] border border-white/10 bg-[#111118]/75 p-7 shadow-[0_18px_42px_rgba(0,0,0,0.42)] backdrop-blur-2xl ${feature.span}`}
            >
              <div className="absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-white/18 to-transparent" />
              <div className="absolute -right-10 -top-10 h-32 w-32 rounded-full bg-[#8A6AF1]/0 blur-3xl transition group-hover:bg-[#8A6AF1]/20" />
              <feature.icon className="mb-10 text-[#B89AFF]" size={30} strokeWidth={1.8} />
              <h3 className="mb-3 text-2xl font-black tracking-tight text-white">{feature.title}</h3>
              <p className="max-w-md text-sm leading-relaxed text-[#A6A1B3]">{feature.text}</p>
            </motion.article>
          ))}
        </div>
      </div>
    </section>
  );
};

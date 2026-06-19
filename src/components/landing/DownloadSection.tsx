import React from 'react';
import { motion, useReducedMotion } from 'motion/react';
import { Download, Gift, Globe, ShieldCheck, type LucideIcon } from 'lucide-react';
import { LANDING_WINDOWS_URL } from './landingContent';

type IconProps = { className?: string };

const AndroidIcon = ({ className }: IconProps) => (
  <svg className={className} viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
    <path d="M17.6 9.48l1.84-3.18a.4.4 0 1 0-.69-.4l-1.86 3.23a11.5 11.5 0 0 0-9.78 0L5.25 5.9a.4.4 0 1 0-.69.4L6.4 9.48A10.8 10.8 0 0 0 1 18.4h22a10.8 10.8 0 0 0-5.4-8.92ZM7 15.2a1.1 1.1 0 1 1 0-2.2 1.1 1.1 0 0 1 0 2.2Zm10 0a1.1 1.1 0 1 1 0-2.2 1.1 1.1 0 0 1 0 2.2Z" />
  </svg>
);

const WindowsIcon = ({ className }: IconProps) => (
  <svg className={className} viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
    <path d="M3 5.62 10.5 4.6v6.65H3V5.62ZM11.5 4.46 21 3.18v8.07h-9.5V4.46ZM3 12.75h7.5v6.63L3 18.36v-5.61ZM11.5 12.75H21v8.07l-9.5-1.28v-6.79Z" />
  </svg>
);

type Platform = {
  name: string;
  format: string;
  Icon: (props: IconProps) => React.JSX.Element;
  href: string;
  download?: string;
  tint: string;
};

const platforms: Platform[] = [
  { name: 'Windows', format: 'Installateur .exe', Icon: WindowsIcon, href: LANDING_WINDOWS_URL, tint: '#5FB0FF' },
  { name: 'Android', format: 'APK officiel', Icon: AndroidIcon, href: '/downloads/swimvpn.apk', download: 'swimvpn.apk', tint: '#3DDC84' },
];

export const DownloadSection = () => {
  const shouldReduceMotion = useReducedMotion();

  return (
    <section id="download" className="relative z-10 overflow-hidden px-4 py-24 md:px-6 md:py-32">
      <div className="absolute left-1/2 top-1/2 h-[560px] w-full max-w-5xl -translate-x-1/2 -translate-y-1/2 rounded-full bg-[#8A6AF1]/10 blur-[160px]" />

      <div className="container relative z-10 mx-auto max-w-5xl">
        <div className="overflow-hidden rounded-[2.5rem] border border-white/10 bg-[#111118]/80 p-6 shadow-[0_24px_80px_rgba(0,0,0,0.58)] backdrop-blur-3xl md:p-10">
          <div className="mb-8 inline-flex items-center gap-2 rounded-full border border-[#35D978]/25 bg-[#35D978]/10 px-4 py-2 text-[11px] font-bold uppercase tracking-[0.16em] text-[#70F0A3]">
            <span className="h-2 w-2 rounded-full bg-[#35D978]" />
            Pré-release · Trial offert
          </div>
          <h2 className="mb-4 text-4xl font-black leading-[0.95] tracking-tighter text-white md:text-6xl">
            Téléchargez SWIMVPN.
          </h2>
          <p className="mb-10 max-w-2xl text-lg leading-relaxed text-[#A6A1B3]">
            Disponible sur <span className="text-white">Windows</span> et <span className="text-white">Android</span> —
            même moteur, mêmes liens et configs. Activez le trial directement depuis l’app.
          </p>

          <div className="grid gap-4 md:grid-cols-2">
            {platforms.map(({ name, format, Icon, href, download, tint }) => (
              <motion.a
                key={name}
                href={href}
                download={download}
                aria-label={`Télécharger SWIMVPN pour ${name}`}
                whileHover={shouldReduceMotion ? undefined : { scale: 1.02, y: -3 }}
                whileTap={shouldReduceMotion ? undefined : { scale: 0.98 }}
                className="group flex items-center gap-5 rounded-[2rem] border border-white/10 bg-[#07070B]/70 p-7 transition-colors hover:border-[#8A6AF1]/45"
              >
                <span
                  className="flex h-16 w-16 shrink-0 items-center justify-center rounded-2xl bg-white/[0.05]"
                  style={{ color: tint }}
                >
                  <Icon className="h-9 w-9" />
                </span>
                <span className="flex-1">
                  <span className="block text-xl font-black text-white">{name}</span>
                  <span className="block text-sm text-[#A6A1B3]">{format}</span>
                </span>
                <span className="inline-flex h-12 w-12 items-center justify-center rounded-full bg-gradient-to-br from-[#B89AFF] via-[#8A6AF1] to-[#5D3BD8] text-white shadow-[0_0_24px_rgba(138,106,241,0.42)] transition group-hover:brightness-110">
                  <Download size={20} strokeWidth={3} />
                </span>
              </motion.a>
            ))}
          </div>

          <div className="mt-4 grid gap-4 sm:grid-cols-3">
            <Feature icon={Gift} color="#B89AFF" title="Trial offert" desc="Activez le trial dans l’app, sa durée exacte est affichée avant l’achat." />
            <Feature icon={ShieldCheck} color="#35D978" title="Libre avec vos configs" desc="Collez ou importez vos liens VLESS / Trojan pour un usage gratuit." />
            <Feature icon={Globe} color="#B89AFF" title="Même moteur" desc="Windows et Android partagent le parseur — mêmes liens, mêmes serveurs." />
          </div>
        </div>
      </div>
    </section>
  );
};

const Feature = ({ icon: Icon, color, title, desc }: { icon: LucideIcon; color: string; title: string; desc: string }) => (
  <div className="rounded-[2rem] border border-white/10 bg-white/[0.04] p-6">
    <Icon className="mb-4" style={{ color }} />
    <h3 className="mb-1 text-base font-black text-white">{title}</h3>
    <p className="text-sm leading-relaxed text-[#A6A1B3]">{desc}</p>
  </div>
);

import React, { Suspense, lazy } from 'react';
import { motion, useReducedMotion } from 'motion/react';
import { Download, QrCode, ShieldCheck, Sparkles, WalletCards } from 'lucide-react';

const InteractivePixelGlobe = lazy(() => import('./InteractivePixelGlobe'));

const cardBase =
  'relative overflow-hidden rounded-[2rem] border border-white/10 bg-[#111118]/80 shadow-[0_18px_42px_rgba(0,0,0,0.55)] backdrop-blur-2xl';

export const HeroSection = () => {
  const shouldReduceMotion = useReducedMotion();
  const heroInitial = shouldReduceMotion ? { opacity: 1, y: 0 } : { opacity: 0, y: 22 };
  const tileInitial = shouldReduceMotion ? { opacity: 1, y: 0 } : { opacity: 0, y: 18 };
  const heroTransition = shouldReduceMotion
    ? { duration: 0 }
    : { duration: 0.7, ease: [0.22, 1, 0.36, 1] as [number, number, number, number] };
  const tileTransition = (delay: number) =>
    shouldReduceMotion
      ? { duration: 0 }
      : { delay, duration: 0.65, ease: [0.22, 1, 0.36, 1] as [number, number, number, number] };

  return (
    <section className="relative z-10 px-4 pb-12 pt-32 md:px-6 md:pb-20 md:pt-40">
      <div className="container mx-auto">
        <div className="grid min-h-[calc(100vh-8rem)] grid-cols-1 gap-4 lg:grid-cols-12 lg:grid-rows-[minmax(220px,0.9fr)_minmax(170px,0.7fr)_minmax(150px,0.55fr)]">
          <motion.div
            initial={heroInitial}
            animate={{ opacity: 1, y: 0 }}
            transition={heroTransition}
            className={`${cardBase} min-h-[420px] p-7 sm:p-10 lg:col-span-7 lg:row-span-2 lg:p-12`}
          >
            <div className="absolute -right-24 -top-28 h-72 w-72 rounded-full bg-[#8A6AF1]/30 blur-[90px]" />
            <div className="absolute bottom-0 left-0 h-px w-full bg-gradient-to-r from-transparent via-white/20 to-transparent" />

            <div className="relative flex h-full flex-col justify-between gap-12">
              <div>
                <div className="mb-8 inline-flex items-center gap-2 rounded-full border border-[#8A6AF1]/30 bg-[#8A6AF1]/10 px-4 py-2 text-[11px] font-bold uppercase tracking-[0.18em] text-[#C8B6FF]">
                  <img src="/brand/swimvpn-shark-mark.svg" alt="" className="h-4 w-4 object-contain" />
                  Pré-release APK disponible
                </div>
                <h1 className="max-w-4xl text-5xl font-black leading-[0.88] tracking-tighter text-white sm:text-7xl md:text-8xl lg:text-9xl">
                  SWIMVPN+
                </h1>
                <p className="mt-8 max-w-2xl text-lg leading-relaxed text-[#A6A1B3] md:text-xl">
                  Un VPN Android qui vous laisse choisir : acheter des configurations directement dans l’app,
                  ou coller/importer gratuitement vos propres configs VLESS et Trojan.
                </p>
              </div>

              <div className="flex flex-col gap-3 sm:flex-row">
                <motion.a
                  href="/downloads/swimvpn.apk"
                  download="SwimVPN.apk"
                  aria-label="Télécharger l’APK SwimVPN"
                  whileHover={shouldReduceMotion ? undefined : { scale: 1.02, y: -2 }}
                  whileTap={shouldReduceMotion ? undefined : { scale: 0.98 }}
                  className="inline-flex min-h-14 items-center justify-center gap-3 rounded-full bg-white px-8 py-4 font-black text-black shadow-[0_24px_70px_rgba(255,255,255,0.16)] transition hover:bg-[#F3F1F6]"
                >
                  <Download size={22} strokeWidth={3} />
                  Télécharger l’APK
                </motion.a>
                <a
                  href="#usage"
                  className="inline-flex min-h-14 items-center justify-center rounded-full border border-white/10 bg-white/[0.04] px-8 py-4 font-bold text-white transition hover:border-[#8A6AF1]/50 hover:bg-[#8A6AF1]/10"
                >
                  Voir comment ça marche
                </a>
              </div>
            </div>
          </motion.div>

          <motion.div
            initial={heroInitial}
            animate={{ opacity: 1, y: 0 }}
            transition={shouldReduceMotion ? { duration: 0 } : { delay: 0.08, duration: 0.7, ease: [0.22, 1, 0.36, 1] }}
            className={`${cardBase} min-h-[260px] p-6 lg:col-span-5 lg:row-span-2`}
          >
            <div className="absolute inset-0 bg-[radial-gradient(circle_at_60%_35%,rgba(138,106,241,0.22),transparent_42%)]" />
            <div className="relative h-full min-h-[300px] md:min-h-[360px]">
              <Suspense fallback={<GlobeFallback />}>
                <InteractivePixelGlobe interactive={false} performanceMode={shouldReduceMotion ? 'static' : 'live'} />
              </Suspense>
            </div>
          </motion.div>

          <motion.div
            initial={tileInitial}
            animate={{ opacity: 1, y: 0 }}
            transition={tileTransition(0.14)}
            className={`${cardBase} p-6 lg:col-span-3`}
          >
            <WalletCards className="mb-8 text-[#B89AFF]" size={30} />
            <h2 className="mb-3 text-2xl font-black text-white">Configs in-app</h2>
            <p className="text-sm leading-relaxed text-[#A6A1B3]">
              Achetez vos accès VPN depuis l’application et recevez des configs prêtes à utiliser.
            </p>
          </motion.div>

          <motion.div
            initial={tileInitial}
            animate={{ opacity: 1, y: 0 }}
            transition={tileTransition(0.2)}
            className={`${cardBase} p-6 lg:col-span-3`}
          >
            <QrCode className="mb-8 text-[#B89AFF]" size={30} />
            <h2 className="mb-3 text-2xl font-black text-white">Mode gratuit</h2>
            <p className="text-sm leading-relaxed text-[#A6A1B3]">
              Collez ou scannez vos configs VLESS/Trojan personnelles et utilisez SWIMVPN+ sans achat.
            </p>
          </motion.div>

          <motion.div
            initial={tileInitial}
            animate={{ opacity: 1, y: 0 }}
            transition={tileTransition(0.26)}
            className={`${cardBase} p-6 lg:col-span-3`}
          >
            <Sparkles className="mb-8 text-[#B89AFF]" size={30} />
            <h2 className="mb-3 text-2xl font-black text-white">Trial offert</h2>
            <p className="text-sm leading-relaxed text-[#A6A1B3]">
              Activez le trial depuis l’app et consultez sa durée exacte avant la release Play Store.
            </p>
          </motion.div>

          <motion.div
            initial={tileInitial}
            animate={{ opacity: 1, y: 0 }}
            transition={tileTransition(0.32)}
            className={`${cardBase} p-6 lg:col-span-3`}
          >
            <ShieldCheck className="mb-8 text-[#35D978]" size={30} />
            <h2 className="mb-3 text-2xl font-black text-white">Pré-release</h2>
            <p className="text-sm leading-relaxed text-[#A6A1B3]">
              Disponible en APK officiel pendant que la publication Play Store se prépare.
            </p>
          </motion.div>
        </div>
      </div>
    </section>
  );
};

function GlobeFallback() {
  return (
    <div className="relative flex h-full min-h-[260px] w-full items-center justify-center overflow-hidden rounded-[1.5rem]">
      <div className="absolute h-64 w-64 rounded-full bg-[#8A6AF1]/20 blur-[70px]" />
      <div className="relative h-48 w-48 rounded-full border border-[#B89AFF]/25 bg-[radial-gradient(circle_at_50%_45%,rgba(184,154,255,0.22),rgba(7,7,11,0.2)_44%,transparent_70%)] shadow-[0_0_80px_rgba(138,106,241,0.22)]" />
      <div className="absolute h-56 w-56 rounded-full border border-white/10" />
    </div>
  );
}

import React from 'react';
import { motion, useReducedMotion } from 'motion/react';
import {
  ArrowUpRight,
  Bot,
  CheckCircle2,
  Copy,
  CreditCard,
  Download,
  QrCode,
  RadioTower,
  ShieldCheck,
  Smartphone,
  Sparkles,
  WalletCards,
} from 'lucide-react';

const ui = {
  page:
    'min-h-screen overflow-hidden bg-[#050505] text-[#0A0A0D] selection:bg-[#8A6AF1]/30 selection:text-white',
  shell: 'relative z-10 mx-auto w-full max-w-[1600px] px-3 pb-3 pt-3 sm:px-4 sm:pb-4 lg:px-6 lg:pb-6',
  lightCard:
    'relative overflow-hidden rounded-[1.75rem] border border-black/[0.08] bg-[linear-gradient(135deg,#FFFFFF_0%,#F4F2FA_48%,#ECE8F7_100%)] text-[#0A0A0D] shadow-[0_24px_90px_rgba(10,10,18,0.18)] sm:rounded-[2rem]',
  darkCard:
    'relative overflow-hidden rounded-[1.75rem] border border-white/[0.08] bg-[linear-gradient(145deg,#15151B_0%,#08080C_58%,#020204_100%)] text-white shadow-[0_24px_90px_rgba(0,0,0,0.52)] sm:rounded-[2rem]',
  purpleCard:
    'relative overflow-hidden rounded-[1.75rem] border border-white/[0.12] bg-[radial-gradient(circle_at_76%_18%,rgba(255,122,246,0.24),transparent_36%),linear-gradient(135deg,#7D57FF_0%,#5D3BD8_54%,#22105F_100%)] text-white shadow-[0_24px_90px_rgba(93,59,216,0.35)] sm:rounded-[2rem]',
  muted: 'text-[#5E5A68]',
  darkMuted: 'text-[#B9B4C7]',
  chipDark:
    'inline-flex items-center gap-2 rounded-full bg-black px-4 py-2 text-xs font-black text-white shadow-[0_12px_30px_rgba(0,0,0,0.18)]',
  chipLight:
    'inline-flex items-center gap-2 rounded-full border border-black/[0.08] bg-white/70 px-4 py-2 text-xs font-black text-[#111118] shadow-[0_12px_30px_rgba(33,25,54,0.08)] backdrop-blur-xl',
};

const partners = ['APK', 'VLESS', 'Trojan', 'SwimPay', 'Crypto'];

const bentoFeatures = [
  {
    title: 'Configs in-app',
    text: 'Achetez vos accès VPN directement depuis SWIMVPN et recevez une configuration prête à utiliser.',
    icon: WalletCards,
    tone: 'dark',
  },
  {
    title: 'Mode gratuit',
    text: 'Collez une config VLESS ou Trojan personnelle et utilisez l’app comme client VPN sans achat.',
    icon: Copy,
    tone: 'light',
  },
  {
    title: 'QR code',
    text: 'Ajoutez une configuration en scannant le QR fourni par votre source.',
    icon: QrCode,
    tone: 'light',
  },
  {
    title: 'Agent IA',
    text: 'L’app peut aider à choisir les meilleurs noeuds disponibles en temps réel.',
    icon: Bot,
    tone: 'dark',
  },
];

const steps = [
  'Téléchargez l’APK pré-release',
  'Activez le trial dans l’app',
  'Achetez ou importez une config',
  'Connectez le VPN',
];

const stats = [
  ['Pré-release', 'APK disponible'],
  ['Trial', 'Offert actuellement'],
  ['2 modes', 'Achat ou import'],
  ['VLESS/Trojan', 'Compatibles'],
];

const LandingPage = () => {
  const shouldReduceMotion = useReducedMotion();
  const motionProps = shouldReduceMotion
    ? {}
    : {
        initial: { opacity: 0, y: 18, scale: 0.985 },
        whileInView: { opacity: 1, y: 0, scale: 1 },
        viewport: { once: true, margin: '-80px' },
        transition: { duration: 0.62, ease: [0.22, 1, 0.36, 1] as [number, number, number, number] },
      };

  return (
    <div className={ui.page}>
      <div className="pointer-events-none fixed inset-0 z-0">
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_18%_10%,rgba(255,255,255,0.92),transparent_20%),radial-gradient(circle_at_68%_18%,rgba(184,154,255,0.22),transparent_34%),radial-gradient(circle_at_92%_64%,rgba(93,59,216,0.28),transparent_32%),linear-gradient(135deg,#F7F6FB_0%,#EEEAF6_38%,#07070B_39%,#010101_100%)]" />
        <div className="absolute inset-0 bg-[linear-gradient(rgba(16,16,22,0.045)_1px,transparent_1px),linear-gradient(90deg,rgba(16,16,22,0.035)_1px,transparent_1px)] bg-[size:86px_86px] opacity-55" />
        <div className="absolute inset-x-0 bottom-0 h-1/2 bg-gradient-to-t from-black via-black/60 to-transparent" />
      </div>

      <div className={ui.shell}>
        <nav className="relative z-30 mb-3 flex items-center justify-between rounded-[1.5rem] border border-black/[0.08] bg-white/70 px-4 py-3 shadow-[0_18px_60px_rgba(24,20,38,0.16)] backdrop-blur-2xl sm:mb-4 sm:rounded-[2rem] sm:px-6">
          <a href="#" className="flex items-center gap-3" aria-label="SWIMVPN accueil">
            <span className="relative flex h-10 w-10 items-center justify-center rounded-2xl bg-black shadow-[0_14px_34px_rgba(0,0,0,0.24)]">
              <img src="/brand/swimvpn-shark-mark.svg" alt="" className="h-7 w-7 object-contain" />
            </span>
            <span className="text-xl font-black tracking-[-0.04em] text-black">SWIMVPN</span>
          </a>

          <div className="hidden items-center gap-8 text-sm font-black text-black/70 lg:flex">
            <a href="#features" className="transition hover:text-black">Fonctions</a>
            <a href="#usage" className="transition hover:text-black">Utilisation</a>
            <a href="#download-apk" className="transition hover:text-black">APK</a>
          </div>

          <a
            href="/downloads/swimvpn.apk"
            download="SwimVPN.apk"
            className="inline-flex min-h-11 items-center justify-center gap-2 rounded-2xl bg-black px-5 text-sm font-black text-white shadow-[0_16px_38px_rgba(0,0,0,0.28)] transition hover:-translate-y-0.5 hover:bg-[#17171C]"
          >
            Installer
            <ArrowUpRight size={16} strokeWidth={2.5} />
          </a>
        </nav>

        <main className="grid grid-cols-1 gap-3 sm:gap-4 lg:grid-cols-12 lg:auto-rows-[minmax(150px,auto)]">
          <motion.section {...motionProps} className={`${ui.lightCard} min-h-[620px] p-7 sm:p-10 lg:col-span-7 lg:row-span-3 lg:p-12`}>
            <div className="absolute right-[-12%] top-[9%] hidden h-[72%] w-[55%] rounded-[3rem] bg-[linear-gradient(145deg,rgba(255,255,255,0.58),rgba(17,17,24,0.22)_48%,rgba(138,106,241,0.28))] shadow-[inset_0_1px_0_rgba(255,255,255,0.75),0_28px_90px_rgba(74,62,113,0.28)] backdrop-blur-xl lg:block" />
            <GlassStackVisual />

            <div className="relative flex h-full flex-col justify-between gap-16">
              <div>
                <div className="mb-8 flex flex-wrap items-center gap-3">
                  <span className={ui.chipDark}>Nouveau</span>
                  <span className="rounded-full border border-black/[0.08] bg-white/55 px-4 py-2 text-sm font-bold text-[#363240] backdrop-blur-xl">
                    Pré-release APK disponible
                    <ArrowUpRight className="ml-2 inline" size={15} />
                  </span>
                </div>

                <h1 className="max-w-[780px] text-[clamp(3.4rem,9vw,8.8rem)] font-black leading-[0.86] tracking-[-0.075em] text-black">
                  Votre VPN. Vos configs. Un seul app.
                </h1>
                <p className="mt-7 max-w-xl text-lg font-medium leading-relaxed text-[#5E5A68] sm:text-xl">
                  Achetez des configurations VPN dans SWIMVPN, ou utilisez gratuitement l’app avec vos propres
                  liens VLESS et Trojan. La pré-release est disponible avant la release Play Store.
                </p>
              </div>

              <div>
                <div className="mb-10 flex flex-col gap-3 sm:flex-row">
                  <motion.a
                    href="/downloads/swimvpn.apk"
                    download="SwimVPN.apk"
                    aria-label="Télécharger l’APK SwimVPN"
                    whileHover={shouldReduceMotion ? undefined : { scale: 1.025, y: -2 }}
                    whileTap={shouldReduceMotion ? undefined : { scale: 0.97 }}
                    className="inline-flex min-h-14 items-center justify-center gap-3 rounded-2xl bg-black px-8 text-base font-black text-white shadow-[0_22px_50px_rgba(0,0,0,0.24)] transition hover:bg-[#17171C]"
                  >
                    Télécharger l’APK
                    <Download size={21} strokeWidth={3} />
                  </motion.a>
                  <a
                    href="#usage"
                    className="inline-flex min-h-14 items-center justify-center gap-3 rounded-2xl border border-black/[0.08] bg-white/58 px-8 text-base font-black text-black shadow-[0_16px_34px_rgba(35,29,58,0.08)] backdrop-blur-xl transition hover:bg-white"
                  >
                    Voir comment ça marche
                    <CheckCircle2 size={19} />
                  </a>
                </div>

                <div className="text-sm font-bold text-[#625B70]">Compatible avec</div>
                <div className="mt-4 flex flex-wrap gap-3">
                  {partners.map((partner) => (
                    <span key={partner} className="rounded-full border border-black/[0.08] bg-white/55 px-4 py-2 text-sm font-black text-black/62 shadow-[0_10px_28px_rgba(35,29,58,0.08)] backdrop-blur-xl">
                      {partner}
                    </span>
                  ))}
                </div>
              </div>
            </div>
          </motion.section>

          <motion.article {...motionProps} className={`${ui.darkCard} min-h-[230px] p-7 lg:col-span-3`}>
            <div className="absolute -right-10 -top-14 h-44 w-44 rounded-full bg-[#8A6AF1]/24 blur-[70px]" />
            <h2 className="relative text-3xl font-black leading-tight tracking-[-0.04em]">Configs VPN in-app</h2>
            <p className={`relative mt-5 max-w-sm text-sm leading-relaxed ${ui.darkMuted}`}>
              Achetez une offre, recevez vos accès dans l’app, connectez sans manipuler de fichiers techniques.
            </p>
            <div className="relative mt-8 flex -space-x-2">
              {['B', 'P', 'Q'].map((label) => (
                <span key={label} className="flex h-10 w-10 items-center justify-center rounded-full border border-white/10 bg-white/10 text-xs font-black backdrop-blur">
                  {label}
                </span>
              ))}
              <span className="flex h-10 items-center justify-center rounded-full border border-white/10 bg-white/10 px-3 text-xs font-black backdrop-blur">+trial</span>
            </div>
          </motion.article>

          <motion.article {...motionProps} className={`${ui.lightCard} min-h-[230px] p-7 lg:col-span-2`}>
            <div className="text-6xl font-black tracking-[-0.08em] text-black">
              APK
              <span className="ml-2 align-top text-2xl text-[#18A86B]">↗</span>
            </div>
            <p className="mt-10 text-sm font-semibold leading-relaxed text-[#625B70]">
              Pré-release officielle disponible avant publication Play Store.
            </p>
          </motion.article>

          <motion.article {...motionProps} className={`${ui.lightCard} min-h-[330px] p-7 lg:col-span-5 lg:row-span-2`}>
            <div className="grid h-full gap-8 lg:grid-cols-[0.8fr_1.2fr]">
              <div>
                <h2 className="text-4xl font-black leading-[0.96] tracking-[-0.055em] text-black">
                  Tout votre accès VPN, organisé.
                </h2>
                <p className="mt-5 text-sm font-medium leading-relaxed text-[#625B70]">
                  Une même app pour acheter des configs, importer vos liens personnels, tester le trial et lancer la connexion.
                </p>
                <a href="#features" className="mt-14 inline-flex rounded-2xl bg-white px-5 py-4 text-sm font-black text-black shadow-[0_18px_42px_rgba(31,24,54,0.12)]">
                  Explorer
                </a>
              </div>

              <div className="relative min-h-[260px] overflow-hidden rounded-[1.5rem] border border-black/[0.08] bg-white/62 p-4 shadow-[0_18px_46px_rgba(31,24,54,0.12)] backdrop-blur-xl">
                <div className="mb-4 flex items-center justify-between">
                  <div className="flex items-center gap-2 text-xs font-black text-black">
                    <img src="/brand/swimvpn-shark-mark.svg" alt="" className="h-5 w-5 object-contain" />
                    SWIMVPN
                  </div>
                  <div className="h-7 w-28 rounded-full bg-[#F0ECF7]" />
                </div>
                <div className="grid grid-cols-[86px_1fr] gap-3">
                  <div className="space-y-2">
                    {['Home', 'Servers', 'Import', 'Plans'].map((item, idx) => (
                      <div key={item} className={`rounded-xl px-3 py-2 text-[10px] font-black ${idx === 1 ? 'bg-black text-white' : 'bg-[#F5F2FB] text-[#625B70]'}`}>
                        {item}
                      </div>
                    ))}
                  </div>
                  <div className="space-y-3">
                    <div className="rounded-2xl bg-[#F7F4FC] p-4">
                      <div className="text-sm font-black text-black">Bonjour</div>
                      <div className="mt-1 text-xs font-semibold text-[#8D849B]">Choisissez votre source VPN</div>
                    </div>
                    {steps.slice(1).map((step) => (
                      <div key={step} className="flex items-center gap-3 rounded-2xl border border-black/[0.06] bg-white/72 p-3">
                        <span className="h-3 w-3 rounded-full border border-[#8A6AF1]/50" />
                        <span className="text-xs font-bold text-[#363240]">{step}</span>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            </div>
          </motion.article>

          <motion.article {...motionProps} className={`${ui.darkCard} min-h-[270px] p-7 lg:col-span-3`}>
            <div className="absolute bottom-0 right-0 h-52 w-52 rounded-full bg-[radial-gradient(circle,rgba(184,154,255,0.24),transparent_66%)]" />
            <ShieldCheck className="relative mb-10 text-[#B89AFF]" size={34} strokeWidth={1.8} />
            <h2 className="relative text-3xl font-black leading-tight tracking-[-0.04em]">Client libre</h2>
            <p className={`relative mt-5 text-sm leading-relaxed ${ui.darkMuted}`}>
              VLESS et Trojan restent utilisables gratuitement si vous possédez déjà une configuration.
            </p>
          </motion.article>

          <motion.article {...motionProps} className={`${ui.lightCard} min-h-[270px] p-7 lg:col-span-2`}>
            <RadioTower className="mb-10 text-[#7B57E8]" size={36} strokeWidth={1.8} />
            <h2 className="text-3xl font-black leading-tight tracking-[-0.05em]">VLESS / Trojan</h2>
            <p className="mt-5 text-sm font-medium leading-relaxed text-[#625B70]">Copier-coller manuel ou QR code selon votre fournisseur.</p>
            <ArrowUpRight className="absolute bottom-7 right-7 rounded-2xl bg-white p-3 text-black shadow-[0_16px_36px_rgba(35,29,58,0.12)]" size={50} />
          </motion.article>

          <motion.article {...motionProps} className={`${ui.darkCard} min-h-[270px] p-7 lg:col-span-3`}>
            <div className="absolute bottom-0 right-2 h-52 w-44 rounded-t-full border border-white/10 bg-[linear-gradient(145deg,#24242C,#060608)] shadow-[inset_0_1px_0_rgba(255,255,255,0.12)] rotate-12" />
            <Smartphone className="relative mb-10 text-[#B89AFF]" size={34} strokeWidth={1.8} />
            <h2 className="relative text-3xl font-black leading-tight tracking-[-0.04em]">Mobile ready</h2>
            <p className={`relative mt-5 max-w-[13rem] text-sm leading-relaxed ${ui.darkMuted}`}>
              Pensé pour Android, dense, lisible, et prêt pour votre QA pré-release.
            </p>
          </motion.article>

          {bentoFeatures.map((feature, idx) => {
            const Icon = feature.icon;
            const isDark = feature.tone === 'dark';
            return (
              <motion.article
                key={feature.title}
                {...motionProps}
                id={idx === 0 ? 'features' : undefined}
                className={`${isDark ? ui.darkCard : ui.lightCard} min-h-[220px] p-7 lg:col-span-3`}
              >
                <Icon className={isDark ? 'mb-10 text-[#B89AFF]' : 'mb-10 text-[#7B57E8]'} size={33} strokeWidth={1.8} />
                <h3 className="text-2xl font-black tracking-[-0.04em]">{feature.title}</h3>
                <p className={`mt-4 text-sm font-medium leading-relaxed ${isDark ? ui.darkMuted : ui.muted}`}>{feature.text}</p>
              </motion.article>
            );
          })}

          <motion.article {...motionProps} id="usage" className={`${ui.purpleCard} min-h-[220px] p-7 lg:col-span-4`}>
            <Sparkles className="mb-8 text-white" size={32} />
            <blockquote className="text-2xl font-black leading-tight tracking-[-0.04em]">
              Trial disponible maintenant dans la pré-release.
            </blockquote>
            <p className="mt-5 text-sm font-semibold leading-relaxed text-white/76">
              Testez l’expérience avant la release Play Store, puis gardez le choix entre achat in-app et import personnel.
            </p>
          </motion.article>

          <motion.article {...motionProps} className={`${ui.lightCard} min-h-[220px] p-7 lg:col-span-4`}>
            <div className="mb-8 text-sm font-black uppercase tracking-[0.18em] text-[#7B57E8]">Paiement</div>
            <div className="grid grid-cols-2 gap-3">
              <div className="rounded-2xl bg-white p-5 shadow-[0_14px_34px_rgba(35,29,58,0.10)]">
                <CreditCard className="mb-5 text-[#7B57E8]" />
                <div className="font-black">SwimPay</div>
              </div>
              <div className="rounded-2xl bg-white p-5 shadow-[0_14px_34px_rgba(35,29,58,0.10)]">
                <WalletCards className="mb-5 text-[#7B57E8]" />
                <div className="font-black">Crypto</div>
              </div>
            </div>
          </motion.article>

          <motion.article {...motionProps} className={`${ui.darkCard} min-h-[220px] p-7 lg:col-span-4`}>
            <Bot className="mb-8 text-[#B89AFF]" size={32} />
            <h3 className="text-3xl font-black tracking-[-0.04em]">Agent IA</h3>
            <p className={`mt-4 text-sm leading-relaxed ${ui.darkMuted}`}>
              Une aide discrète pour sélectionner les meilleurs noeuds du réseau disponible, sans surcharger l’interface.
            </p>
          </motion.article>

          <motion.section {...motionProps} id="download-apk" className={`${ui.lightCard} p-7 sm:p-10 lg:col-span-10`}>
            <div className="grid gap-8 lg:grid-cols-[1.2fr_2fr] lg:items-center">
              <div>
                <h2 className="max-w-md text-3xl font-black leading-tight tracking-[-0.05em] sm:text-4xl">
                  Tout ce qu’il faut pour tester SWIMVPN avant la release.
                </h2>
                <a
                  href="/downloads/swimvpn.apk"
                  download="swimvpn.apk"
                  className="mt-7 inline-flex min-h-12 items-center justify-center gap-3 rounded-2xl bg-black px-6 text-sm font-black text-white shadow-[0_16px_40px_rgba(0,0,0,0.22)]"
                >
                  Télécharger l’APK
                  <Download size={18} />
                </a>
              </div>
              <div className="grid gap-4 sm:grid-cols-4">
                {stats.map(([value, label]) => (
                  <div key={value} className="rounded-3xl border border-black/[0.06] bg-white/62 p-6 shadow-[0_14px_34px_rgba(35,29,58,0.08)] backdrop-blur-xl">
                    <div className="text-2xl font-black tracking-[-0.05em]">{value}</div>
                    <div className="mt-2 text-sm font-semibold text-[#625B70]">{label}</div>
                  </div>
                ))}
              </div>
            </div>
          </motion.section>

          <motion.footer {...motionProps} className={`${ui.darkCard} flex min-h-[160px] flex-col justify-between p-7 lg:col-span-2`}>
            <div className="flex items-center gap-3">
              <img src="/brand/swimvpn-shark-mark.svg" alt="" className="h-9 w-9 object-contain" />
              <div className="text-xl font-black tracking-[-0.04em]">SWIMVPN</div>
            </div>
            <div className="flex flex-wrap gap-4 text-sm font-bold text-[#B9B4C7]">
              <a href="#privacy" className="hover:text-white">Confidentialité</a>
              <a href="#terms" className="hover:text-white">Conditions</a>
              <a href="mailto:support@swimvpn.pro" className="hover:text-white">Support</a>
            </div>
          </motion.footer>
        </main>
      </div>
    </div>
  );
};

function GlassStackVisual() {
  return (
    <div aria-hidden="true" className="pointer-events-none absolute right-5 top-28 hidden h-[360px] w-[390px] lg:block">
      <div className="absolute left-20 top-0 h-64 w-48 rounded-[1.6rem] border border-white/45 bg-white/20 shadow-[inset_0_1px_0_rgba(255,255,255,0.9),0_28px_80px_rgba(26,23,41,0.25)] backdrop-blur-xl" />
      <div className="absolute left-16 top-40 h-20 w-64 -skew-y-6 rounded-[1.4rem] border border-black/15 bg-[linear-gradient(135deg,rgba(255,255,255,0.52),rgba(8,8,12,0.22)_48%,rgba(138,106,241,0.45))] shadow-[0_28px_70px_rgba(44,35,77,0.28)]" />
      <div className="absolute left-0 top-[13.75rem] h-24 w-80 -skew-y-6 rounded-[1.6rem] border border-black/18 bg-[linear-gradient(135deg,rgba(255,255,255,0.32),rgba(14,14,20,0.36)_52%,rgba(93,59,216,0.52))] shadow-[0_32px_86px_rgba(44,35,77,0.34)]" />
      <div className="absolute bottom-0 left-2 h-20 w-72 -skew-y-6 rounded-[1.5rem] border border-black/20 bg-[linear-gradient(135deg,rgba(255,255,255,0.18),rgba(7,7,11,0.48)_54%,rgba(93,59,216,0.38))] shadow-[0_34px_80px_rgba(0,0,0,0.28)]" />
      <div className="absolute left-56 top-52 h-16 w-16 rounded-full bg-[#8A6AF1]/55 blur-[34px]" />
    </div>
  );
}

export default LandingPage;

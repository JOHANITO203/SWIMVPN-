'use client';

import { useRef, useState, useEffect } from 'react';
import { motion, useScroll, useTransform, useSpring } from 'motion/react';
import {
  Shield,
  Power,
  Globe,
  Lock,
  Zap,
  Cpu,
  Activity,
  Download,
  Smartphone,
  QrCode,
  CheckCircle,
  X,
  ChevronRight,
  HelpCircle
} from 'lucide-react';
import { SharkLogo } from './SharkLogo';
import { PixelGlobe } from './PixelGlobe';

const useWindowWidth = () => {
  const [width, setWidth] = useState(1200);

  useEffect(() => {
    const handleResize = () => {
      setWidth(window.innerWidth);
    };

    handleResize();

    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, []);

  return width;
};

export const LandingPage = () => {
  const containerRef = useRef<HTMLDivElement>(null);
  const width = useWindowWidth();
  const isMobile = width < 768;

  const { scrollYProgress } = useScroll({
    target: containerRef,
    offset: ['start start', 'end end']
  });

  const smoothProgress = useSpring(scrollYProgress, {
    stiffness: 100,
    damping: 30,
    restDelta: 0.001
  });

  const bgY1 = useTransform(
    smoothProgress,
    [0, 1],
    [0, isMobile ? -100 : -300]
  );

  const bgY2 = useTransform(
    smoothProgress,
    [0, 1],
    [0, isMobile ? 100 : 300]
  );

  const heroY = useTransform(
    smoothProgress,
    [0, 0.3],
    [0, isMobile ? -30 : -100]
  );

  const globeScale = useTransform(
    smoothProgress,
    [0, 0.3],
    [1, isMobile ? 0.95 : 0.85]
  );

  const heroRotate = useTransform(
    smoothProgress,
    [0, 0.3],
    [0, isMobile ? -2 : -5]
  );

  const statsY = useTransform(
    smoothProgress,
    [0.3, 0.6],
    [50, -50]
  );

  const phonePreviewY = useTransform(
    smoothProgress,
    [0, 0.3],
    [0, isMobile ? 0 : 100]
  );

  const features = [
    {
      icon: <Shield className="w-6 h-6" />,
      title: 'Protection Layer',
      description: 'Encrypted routing layer designed for private network access.'
    },
    {
      icon: <Lock className="w-6 h-6" />,
      title: 'Private Access',
      description: 'Secure access model with minimal setup for end users.'
    },
    {
      icon: <Cpu className="w-6 h-6" />,
      title: 'Adaptive Engine',
      description: 'Smart routing logic prepared for advanced VPN infrastructure.'
    },
    {
      icon: <Zap className="w-6 h-6" />,
      title: 'Fast Activation',
      description: 'Designed for quick access activation and simple onboarding.'
    }
  ];

  const steps = [
    {
      icon: <Download className="w-6 h-6" />,
      title: 'Download APK',
      description: 'Install the Android client directly on your device.'
    },
    {
      icon: <QrCode className="w-6 h-6" />,
      title: 'Scan QR',
      description: 'Import your access configuration from a QR code.'
    },
    {
      icon: <Smartphone className="w-6 h-6" />,
      title: 'Open App',
      description: 'Launch the app and select your access profile.'
    },
    {
      icon: <Power className="w-6 h-6" />,
      title: 'Connect',
      description: 'Tap once to start the VPN connection flow.'
    }
  ];

  const checks = [
    'One-tap connection',
    'Private access profile',
    'QR import support',
    'Mobile-first experience'
  ];

  const faqs = [
    {
      question: 'Do I need technical setup?',
      answer: 'No. The interface is designed for simple import and one-tap connection.'
    },
    {
      question: 'Can I use a QR code?',
      answer: 'Yes. The landing and app flow already include QR-based access import.'
    },
    {
      question: 'Is this Android-focused?',
      answer: 'Yes. The current product direction is focused on Android APK distribution.'
    }
  ];

  return (
    <div
      ref={containerRef}
      className="relative min-h-screen w-full app-bg overflow-x-hidden transition-colors duration-500 selection:bg-minimal-accent/30"
    >
      {/* Dynamic Background Elements */}
      <div className="absolute top-0 left-0 w-full h-full overflow-hidden pointer-events-none">
        <motion.div
          style={{ y: bgY1 }}
          className="absolute top-[-5%] right-[-5%] md:top-[-10%] md:right-[-10%] w-[300px] md:w-[600px] h-[300px] md:h-[600px] bg-minimal-accent/10 dark:bg-dark-accent/5 rounded-full blur-[80px] md:blur-[120px]"
        />

        <motion.div
          style={{ y: bgY2 }}
          className="absolute bottom-[20%] left-[-5%] md:bottom-[-10%] md:left-[-10%] w-[250px] md:w-[500px] h-[250px] md:h-[500px] bg-minimal-accent/5 dark:bg-dark-accent/10 rounded-full blur-[60px] md:blur-[100px]"
        />
      </div>

      {/* Navigation */}
      <nav className="fixed top-0 left-0 w-full z-50 backdrop-blur-md border-b app-border transition-all duration-300">
        <div className="max-w-7xl mx-auto px-6 h-20 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 app-btn-primary rounded-xl flex items-center justify-center shadow-lg">
              <SharkLogo className="w-6 h-6 text-white dark:text-dark-bg" />
            </div>

            <span className="font-black text-xl tracking-tighter app-text-bold uppercase">
              Swim VPN
            </span>
          </div>

          <div className="hidden md:flex items-center gap-8 text-[10px] font-black uppercase tracking-widest app-text-secondary">
            <a
              href="#features"
              className="transition-colors hover:text-minimal-accent dark:hover:text-dark-accent"
            >
              Features
            </a>

            <a
              href="#how-it-works"
              className="transition-colors hover:text-minimal-accent dark:hover:text-dark-accent"
            >
              How it works
            </a>

            <a
              href="#download"
              className="transition-colors hover:text-minimal-accent dark:hover:text-dark-accent"
            >
              Download
            </a>
          </div>
        </div>
      </nav>

      {/* Hero Section */}
      <header className="relative pt-40 pb-20 px-6 max-w-7xl mx-auto text-center md:text-left flex flex-col md:flex-row items-center gap-16">
        <motion.div
          style={{ y: heroY, rotateZ: heroRotate }}
          className="flex-1 space-y-8 z-10"
        >
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            className="inline-flex items-center gap-2 px-4 py-1.5 bg-minimal-accent/10 dark:bg-dark-accent/10 text-minimal-accent dark:text-dark-accent rounded-full text-[10px] font-black uppercase tracking-[0.2em] border border-minimal-accent/20"
          >
            <Activity className="w-3.5 h-3.5" />
            Reliability First
          </motion.div>

          <motion.h1
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.1 }}
            className="text-5xl md:text-8xl font-black leading-[0.9] tracking-tighter app-text-bold uppercase"
          >
            ACCESS BLOCKED CONTENT.
            <br />
            NO DROPS.{' '}
            <span className="text-minimal-accent dark:text-dark-accent">
              NO SETUP.
            </span>
          </motion.h1>

          <motion.p
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.2 }}
            className="text-lg app-text-secondary max-w-xl mx-auto md:mx-0 leading-relaxed font-medium relative z-20"
          >
            Swim VPN automatically adapts to your network to keep you connected — even in restricted environments.
          </motion.p>

          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.3 }}
            className="flex flex-col sm:flex-row items-center gap-4 pt-4 relative z-20"
          >
            <a
              href="#pricing"
              className="w-full sm:w-auto px-10 py-6 app-btn-primary rounded-[2rem] shadow-2xl flex items-center justify-center gap-3 font-black uppercase tracking-widest text-xs hover:scale-105 transition-all"
            >
              <Zap className="w-5 h-5" />
              GET INSTANT ACCESS
              <ChevronRight className="w-4 h-4" />
            </a>

            <a
              href="#download"
              className="w-full sm:w-auto px-10 py-6 app-surface border app-border rounded-[2rem] flex items-center justify-center gap-3 font-black uppercase tracking-widest text-xs hover:bg-minimal-accent/5 transition-all"
            >
              <Download className="w-5 h-5" />
              Download APK
            </a>
          </motion.div>
        </motion.div>

        <motion.div
          style={{ scale: globeScale, y: isMobile ? 0 : heroY }}
          className="flex-1 relative w-full h-[500px] md:h-[600px] flex items-center justify-center"
        >
          <div className="w-full h-full relative z-10 pointer-events-auto">
            <PixelGlobe />
          </div>

          <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[300px] h-[300px] bg-minimal-accent/5 blur-[80px] rounded-full -z-10" />

          <div className="absolute top-10 right-10 z-20 hidden md:block animate-float">
            <div className="app-card p-6 border app-border rounded-2xl shadow-xl backdrop-blur-md">
              <p className="text-[10px] font-black uppercase tracking-widest text-minimal-accent mb-2">
                Network Status
              </p>

              <div className="flex items-center gap-2 mb-1">
                <div className="w-2 h-2 rounded-full bg-green-500 animate-pulse" />
                <span className="text-[10px] font-bold app-text">
                  Nodes Active
                </span>
              </div>

              <p className="text-[9px] app-text-secondary leading-tight mt-2 italic">
                Tap any node to visualize the automatic routing paths.
              </p>
            </div>
          </div>

          <motion.div
            initial={{ opacity: 0, x: 100, scale: 0.6 }}
            animate={{ opacity: 1, x: 0, scale: 0.8 }}
            transition={{ delay: 0.5, duration: 1 }}
            style={{ y: phonePreviewY }}
            className="absolute -right-20 -bottom-10 z-20 hidden md:block w-[200px] aspect-[9/19] app-surface rounded-[2.5rem] border-[6px] app-border shadow-2xl overflow-hidden scale-75 rotate-12 opacity-80"
          >
            <div className="h-full flex flex-col p-4 items-center justify-center space-y-4">
              <div className="w-12 h-12 rounded-full border-2 border-minimal-accent flex items-center justify-center">
                <Power className="w-6 h-6 text-minimal-accent" />
              </div>

              <div className="w-16 h-2 bg-gray-200 dark:bg-gray-800 rounded-full" />
              <div className="w-10 h-1 bg-gray-100 dark:bg-gray-900 rounded-full" />
            </div>
          </motion.div>
        </motion.div>
      </header>

      {/* Trust Section */}
      <section className="py-12 border-t border-b app-border bg-minimal-bg/30">
        <div className="max-w-7xl mx-auto px-6">
          <div className="flex flex-col md:flex-row items-center justify-between gap-8">
            <p className="text-sm font-black uppercase tracking-widest app-text-bold">
              Works worldwide. No configuration required.
            </p>

            <div className="flex flex-wrap justify-center gap-6">
              {[
                {
                  icon: <Zap className="w-4 h-4" />,
                  label: 'One-tap connection'
                },
                {
                  icon: <Activity className="w-4 h-4" />,
                  label: 'Instant activation'
                },
                {
                  icon: <Globe className="w-4 h-4" />,
                  label: 'Works on restricted networks'
                }
              ].map((badge, i) => (
                <div
                  key={i}
                  className="flex items-center gap-2 px-4 py-2 app-card border app-border rounded-full"
                >
                  <div className="text-minimal-accent">
                    {badge.icon}
                  </div>

                  <span className="text-[9px] font-black uppercase tracking-wider app-text-secondary">
                    {badge.label}
                  </span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </section>

      {/* Features Section */}
      <section id="features" className="relative py-28 px-6">
        <div className="max-w-7xl mx-auto">
          <div className="max-w-2xl mb-14">
            <p className="text-[10px] font-black uppercase tracking-[0.3em] text-minimal-accent dark:text-dark-accent mb-4">
              Core Features
            </p>

            <h2 className="text-4xl md:text-6xl font-black tracking-tighter app-text-bold uppercase leading-none">
              Built for secure mobile access.
            </h2>
          </div>

          <motion.div
            style={{ y: statsY }}
            className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6"
          >
            {features.map((feature, index) => (
              <div
                key={index}
                className="app-card border app-border rounded-[2rem] p-8 shadow-xl"
              >
                <div className="w-14 h-14 app-btn-primary rounded-2xl flex items-center justify-center mb-8">
                  {feature.icon}
                </div>

                <h3 className="text-lg font-black app-text-bold uppercase tracking-tight mb-3">
                  {feature.title}
                </h3>

                <p className="text-sm app-text-secondary leading-relaxed font-medium">
                  {feature.description}
                </p>
              </div>
            ))}
          </motion.div>
        </div>
      </section>

      {/* How It Works Section */}
      <section id="how-it-works" className="relative py-28 px-6 app-surface border-y app-border">
        <div className="max-w-7xl mx-auto">
          <div className="max-w-2xl mb-14">
            <p className="text-[10px] font-black uppercase tracking-[0.3em] text-minimal-accent dark:text-dark-accent mb-4">
              How It Works
            </p>

            <h2 className="text-4xl md:text-6xl font-black tracking-tighter app-text-bold uppercase leading-none">
              Install. Import. Connect.
            </h2>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
            {steps.map((step, index) => (
              <div
                key={index}
                className="relative app-card border app-border rounded-[2rem] p-8 shadow-xl"
              >
                <div className="flex items-center justify-between mb-8">
                  <div className="w-14 h-14 app-bg border app-border rounded-2xl flex items-center justify-center text-minimal-accent dark:text-dark-accent">
                    {step.icon}
                  </div>

                  <span className="text-[10px] font-black app-text-muted uppercase tracking-widest">
                    0{index + 1}
                  </span>
                </div>

                <h3 className="text-lg font-black app-text-bold uppercase tracking-tight mb-3">
                  {step.title}
                </h3>

                <p className="text-sm app-text-secondary leading-relaxed font-medium">
                  {step.description}
                </p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Download Section */}
      <section id="download" className="relative py-28 px-6">
        <div className="max-w-7xl mx-auto grid grid-cols-1 lg:grid-cols-2 gap-10 items-center">
          <div>
            <p className="text-[10px] font-black uppercase tracking-[0.3em] text-minimal-accent dark:text-dark-accent mb-4">
              Android Client
            </p>

            <h2 className="text-4xl md:text-6xl font-black tracking-tighter app-text-bold uppercase leading-none mb-8">
              Download the Swim VPN APK.
            </h2>

            <div className="space-y-4 mb-10">
              {checks.map((item, index) => (
                <div key={index} className="flex items-center gap-3">
                  <CheckCircle className="w-5 h-5 text-minimal-accent dark:text-dark-accent" />
                  <span className="text-sm font-black uppercase tracking-widest app-text-secondary">
                    {item}
                  </span>
                </div>
              ))}
            </div>

            <a
              href="#download-apk"
              className="inline-flex items-center gap-3 px-10 py-6 app-btn-primary rounded-[2rem] shadow-2xl font-black uppercase tracking-widest text-xs hover:scale-105 transition-all"
            >
              <Smartphone className="w-5 h-5" />
              Download for Android
              <Download className="w-5 h-5" />
            </a>
          </div>

          <div className="app-card border app-border rounded-[3rem] p-8 shadow-2xl">
            <div className="flex items-center justify-between mb-8">
              <div>
                <p className="text-[10px] font-black uppercase tracking-[0.3em] text-minimal-accent dark:text-dark-accent mb-2">
                  QR Import
                </p>

                <h3 className="text-3xl font-black app-text-bold tracking-tighter uppercase">
                  Scan your access key.
                </h3>
              </div>

              <QrCode className="w-12 h-12 text-minimal-accent dark:text-dark-accent" />
            </div>

            <div className="aspect-square app-bg border app-border rounded-[2rem] flex items-center justify-center">
              <QrCode className="w-32 h-32 app-text-muted" />
            </div>
          </div>
        </div>
      </section>

      {/* FAQ Section */}
      <section className="relative py-28 px-6 app-surface border-t app-border">
        <div className="max-w-5xl mx-auto">
          <div className="flex items-center gap-3 mb-12">
            <HelpCircle className="w-7 h-7 text-minimal-accent dark:text-dark-accent" />

            <h2 className="text-4xl md:text-5xl font-black tracking-tighter app-text-bold uppercase">
              Questions
            </h2>
          </div>

          <div className="space-y-4">
            {faqs.map((faq, index) => (
              <div
                key={index}
                className="app-card border app-border rounded-[2rem] p-6 shadow-lg"
              >
                <div className="flex items-start gap-4">
                  <div className="mt-1">
                    {index === 0 ? (
                      <X className="w-5 h-5 text-red-500" />
                    ) : (
                      <CheckCircle className="w-5 h-5 text-minimal-accent dark:text-dark-accent" />
                    )}
                  </div>

                  <div className="flex-1">
                    <h3 className="font-black app-text-bold uppercase tracking-tight mb-2">
                      {faq.question}
                    </h3>

                    <p className="text-sm app-text-secondary leading-relaxed">
                      {faq.answer}
                    </p>
                  </div>

                  <ChevronRight className="w-5 h-5 app-text-muted" />
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>
    </div>
  );
};
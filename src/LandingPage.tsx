import React, { useRef, useEffect, useState } from 'react';
import { motion, useScroll, useSpring, useTransform } from 'motion/react';
import { Canvas, useFrame } from '@react-three/fiber';
import { Points, PointMaterial } from '@react-three/drei';
import * as THREE from 'three';
import { Zap, Shield, Globe } from 'lucide-react';
import AppMockup from './App';

// --- Three.js Globe Component ---
const InteractiveGlobe = () => {
  const pointsRef = useRef<THREE.Points>(null);

  // Generate sphere points
  const [sphere] = useState(() => {
    const points = [];
    const radius = 1.5;
    for (let i = 0; i < 5000; i++) {
      const phi = Math.acos(-1 + (2 * i) / 5000);
      const theta = Math.sqrt(5000 * Math.PI) * phi;
      const x = radius * Math.cos(theta) * Math.sin(phi);
      const y = radius * Math.sin(theta) * Math.sin(phi);
      const z = radius * Math.cos(phi);
      // add some noise to make it organic
      points.push(x, y, z);
    }
    return new Float32Array(points);
  });

  useFrame((state) => {
    if (pointsRef.current) {
      pointsRef.current.rotation.y = state.clock.elapsedTime * 0.05;
      pointsRef.current.rotation.x = state.clock.elapsedTime * 0.02;
    }
  });

  return (
    <group rotation={[0, 0, Math.PI / 4]}>
      <Points ref={pointsRef} positions={sphere} stride={3} frustumCulled={false}>
        <PointMaterial
          transparent
          color="#2E90FA"
          size={0.015}
          sizeAttenuation={true}
          depthWrite={false}
          opacity={0.6}
        />
      </Points>
    </group>
  );
};

// --- Landing Page ---
export default function LandingPage() {
  const containerRef = useRef<HTMLDivElement>(null);

  // Responsive parallax scaling
  const [isMobile, setIsMobile] = useState(false);
  useEffect(() => {
    const handleResize = () => setIsMobile(window.innerWidth < 768);
    handleResize();
    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, []);

  const { scrollYProgress } = useScroll({
    target: containerRef,
    offset: ["start start", "end start"]
  });

  // Parallax physical inertia mapped to 0-1 range
  const smoothProgress = useSpring(scrollYProgress, { stiffness: 100, damping: 30 });

  // Layer 0: Background
  const bgY = useTransform(smoothProgress, [0, 1], [0, isMobile ? -120 : -300]);

  // Layer 1: Content
  const heroY = useTransform(smoothProgress, [0, 0.5], [0, isMobile ? -60 : -100]);
  const heroRotateZ = useTransform(smoothProgress, [0, 0.5], [0, 2]);

  // Layer 2: Globe Focus
  const globeScale = useTransform(smoothProgress, [0, 0.3], [1, 0.85]);
  const globeY = useTransform(smoothProgress, [0, 1], [0, isMobile ? 120 : 300]);

  const features = [
    { icon: <Zap size={24} />, title: "Ultra-Fast Engine", desc: "Military-grade VLESS+Reality encryption that leaves zero digital footprint." },
    { icon: <Shield size={24} />, title: "Stealth Mode", desc: "Traffic obfuscation v2 designed for high-performance VPN clients." },
    { icon: <Globe size={24} />, title: "Global Network", desc: "Real-time adaptive logic to bypass any network restrictions globally." }
  ];

  return (
    <div ref={containerRef} className="relative min-h-[200vh] bg-dark-bg text-white overflow-hidden font-sans">

      {/* --- LAYER 0: Background Effects --- */}
      <motion.div
        style={{ y: bgY }}
        className="fixed inset-0 pointer-events-none z-0"
      >
        <div className="absolute top-[-10%] left-[-10%] w-[500px] h-[500px] bg-minimal-accent/20 rounded-full blur-[120px]" />
        <div className="absolute bottom-[-10%] right-[-10%] w-[600px] h-[600px] bg-[#1e1b4b]/50 rounded-full blur-[150px]" />
      </motion.div>

      {/* --- NAVBAR --- */}
      <nav className="fixed top-0 left-0 right-0 z-50 flex items-center justify-between px-8 py-6 backdrop-blur-xl bg-dark-bg/50 border-b border-dark-border">
        <div className="font-black text-2xl tracking-tighter uppercase text-white">SwimVPN+</div>
        <button className="px-6 py-2.5 rounded-full bg-minimal-accent text-white font-black uppercase tracking-widest text-[10px] hover:scale-105 transition-transform shadow-[0_0_20px_rgba(46,144,250,0.3)]">
          Get Access
        </button>
      </nav>

      {/* --- HERO SECTION --- */}
      <div className="relative z-10 flex flex-col lg:flex-row items-center min-h-screen pt-24 px-8 max-w-7xl mx-auto">

        {/* Left: Typography Layer */}
        <motion.div
          style={{ y: heroY, rotateZ: heroRotateZ }}
          className="flex-1 w-full lg:w-1/2 flex flex-col justify-center z-20"
        >
          <motion.h1
            initial={{ opacity: 0, y: 30 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8, delay: 0.1 }}
            className="text-6xl lg:text-[7rem] font-black leading-none tracking-tighter uppercase text-white drop-shadow-2xl"
          >
            Beyond
            <br />
            <span className="text-minimal-accent">Limits</span>
          </motion.h1>
          <motion.p
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8, delay: 0.3 }}
            className="mt-8 text-lg lg:text-xl text-dark-text-secondary font-medium max-w-md leading-relaxed"
          >
            The brutalist, high-tech VPN engine designed for unparalleled performance, security, and raw speed.
          </motion.p>
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8, delay: 0.5 }}
            className="mt-10 flex items-center gap-4"
          >
            <button className="px-8 py-4 rounded-full bg-white text-dark-bg font-black uppercase tracking-widest text-xs hover:scale-105 transition-transform shadow-2xl">
              Start Free Trial
            </button>
            <button className="px-8 py-4 rounded-full bg-dark-surface border border-dark-border text-white font-black uppercase tracking-widest text-xs hover:bg-dark-border transition-colors">
              Compare Plans
            </button>
          </motion.div>
        </motion.div>

        {/* Right: Mockup & Globe Layer */}
        <div className="flex-1 w-full lg:w-1/2 h-[70vh] lg:h-screen relative flex items-center justify-center mt-12 lg:mt-0">

          {/* Globe 3D Background */}
          <motion.div
            style={{ scale: globeScale, y: globeY }}
            className="absolute inset-0 z-0 flex items-center justify-center pointer-events-none"
          >
            <div className="w-[120%] h-[120%] lg:w-[800px] lg:h-[800px]">
              <Canvas camera={{ position: [0, 0, 3], fov: 45 }}>
                <InteractiveGlobe />
              </Canvas>
            </div>
          </motion.div>

          {/* Floating Phone Mockup */}
          <motion.div
            initial={{ y: 50, opacity: 0, rotateZ: 5, rotateY: 10 }}
            animate={{ y: 0, opacity: 1, rotateZ: 12, rotateY: -5 }}
            transition={{ type: "spring", stiffness: 50, damping: 20, delay: 0.4 }}
            whileHover={{ rotateZ: 0, scale: 1.05 }}
            className="relative z-10 w-[300px] h-[650px] rounded-[3rem] shadow-[0_30px_100px_rgba(0,0,0,0.5)] border border-dark-border/50 bg-dark-bg overflow-hidden flex-shrink-0"
          >
            {/* Embedded App Mockup */}
            <div className="w-full h-full pointer-events-auto transform origin-top-left" style={{ zoom: '0.85' }}>
              <div className="w-[390px] h-[844px] absolute top-0 left-0">
                <AppMockup />
              </div>
            </div>

            {/* Glass reflection overlay */}
            <div className="absolute inset-0 pointer-events-none rounded-[3rem] bg-gradient-to-tr from-white/5 to-transparent z-50" />
          </motion.div>
        </div>
      </div>

      {/* --- FEATURES SECTION --- */}
      <div className="relative z-20 max-w-7xl mx-auto px-8 py-32">
        <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
          {features.map((feat, idx) => (
            <motion.div
              key={idx}
              initial={{ opacity: 0, y: 40 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ delay: idx * 0.1, duration: 0.6 }}
              whileHover={{ y: -10 }}
              className="group p-8 rounded-[2.5rem] bg-dark-surface/50 border border-dark-border hover:bg-dark-border transition-colors cursor-pointer"
            >
              <div className="w-14 h-14 rounded-2xl bg-dark-bg border border-dark-border flex items-center justify-center text-dark-text-secondary group-hover:bg-minimal-accent group-hover:text-white group-hover:border-transparent transition-all shadow-lg mb-6">
                {feat.icon}
              </div>
              <h3 className="text-xl font-black uppercase tracking-tight text-white mb-3 group-hover:text-minimal-accent transition-colors">{feat.title}</h3>
              <p className="text-dark-text-secondary font-medium text-sm leading-relaxed">{feat.desc}</p>
            </motion.div>
          ))}
        </div>
      </div>

      {/* --- COMPARISON SECTION --- */}
      <div className="relative z-20 max-w-4xl mx-auto px-8 py-20 mb-32">
        <motion.div
          initial={{ opacity: 0, y: 40 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          className="text-center mb-16"
        >
          <h2 className="text-4xl lg:text-5xl font-black uppercase tracking-tighter">SwimVPN+ vs Standard</h2>
        </motion.div>

        <motion.div
          initial={{ opacity: 0 }}
          whileInView={{ opacity: 1 }}
          viewport={{ once: true }}
          className="bg-dark-surface/30 rounded-[3rem] border border-dark-border overflow-hidden"
        >
          <div className="grid grid-cols-3 gap-4 p-8 border-b border-dark-border/50 text-[10px] font-black uppercase tracking-[0.2em] text-dark-text-secondary">
            <div>Feature</div>
            <div className="text-center text-white">Standard VPN</div>
            <div className="text-right text-minimal-accent">SwimVPN+</div>
          </div>

          {[
            { label: "Encryption", std: "AES-256", pro: "VLESS+Reality" },
            { label: "Obfuscation", std: "Basic TLS", pro: "Advanced Stealth v2" },
            { label: "Bandwidth Limit", std: "Capped / Throttled", pro: "Unlimited Raw Speed" },
            { label: "Client Engine", std: "Bloated / Slow", pro: "Brutalist High-Performance" }
          ].map((row, idx) => (
            <div key={idx} className="grid grid-cols-3 gap-4 p-8 border-b border-dark-border/30 last:border-0 hover:bg-white/5 transition-colors">
              <div className="font-bold text-sm text-white">{row.label}</div>
              <div className="text-center text-sm font-medium text-dark-text-secondary">{row.std}</div>
              <div className="text-right text-sm font-black text-minimal-accent">{row.pro}</div>
            </div>
          ))}
        </motion.div>
      </div>

    </div>
  );
}

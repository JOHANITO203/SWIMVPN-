import React, { useRef, useEffect, useState } from 'react';
import { motion, useScroll, useSpring, useTransform, AnimatePresence } from 'motion/react';
import { Canvas, useFrame } from '@react-three/fiber';
import { Points, PointMaterial, OrbitControls, Html } from '@react-three/drei';
import * as THREE from 'three';
import { Zap, Shield, Globe as GlobeIcon, Lock, Cpu, Activity, Download, CheckCircle, Plus, X, ShieldCheck } from 'lucide-react';
import AppMockup from './App';

// ============================================================================
// CONFIGURATION & CONSTANTS
// ============================================================================
const GLOBE_RADIUS = 0.85;
const BRAND = {
  primary: "#2E90FA",
  dark: "#0f172a",
  light: "#f8fafc",
  gradient: "bg-gradient-to-br from-[#ffffff] via-[#f4f7fb] to-[#eaf2f9]"
};

const CITIES = [
  { name: 'New York', lat: 40.7128, lon: -74.0060, ping: '12ms', load: '34%' },
  { name: 'London', lat: 51.5074, lon: -0.1278, ping: '8ms', load: '45%' },
  { name: 'Paris', lat: 48.8566, lon: 2.3522, ping: '9ms', load: '22%' },
  { name: 'Frankfurt', lat: 50.1109, lon: 8.6821, ping: '7ms', load: '68%' },
  { name: 'Tokyo', lat: 35.6762, lon: 139.6503, ping: '24ms', load: '51%' },
  { name: 'Singapore', lat: 1.3521, lon: 103.8198, ping: '15ms', load: '89%' },
  { name: 'Dubai', lat: 25.2048, lon: 55.2708, ping: '18ms', load: '41%' },
  { name: 'Sydney', lat: -33.8688, lon: 151.2093, ping: '32ms', load: '29%' },
  { name: 'São Paulo', lat: -23.5505, lon: -46.6333, ping: '28ms', load: '15%' },
  { name: 'Johannesburg', lat: -26.2041, lon: 28.0473, ping: '35ms', load: '10%' }
];

// ============================================================================
// ASSETS (SVG)
// ============================================================================
const SharkLogo = ({ className = "w-8 h-8" }: { className?: string }) => (
  <svg viewBox="0 0 100 100" className={className} fill="none" xmlns="http://www.w3.org/2000/svg">
    <rect width="100" height="100" rx="24" fill={BRAND.primary}/>
    <path d="M72 56C72 63.732 65.732 70 58 70H38C30.268 70 24 63.732 24 56C24 49.3093 28.6946 43.6874 35.0347 42.3484C36.8532 35.808 42.8273 31 50 31C57.1727 31 63.1468 35.808 64.9653 42.3484C68.4239 42.8631 72 45.7533 72 50V56Z" fill="white"/>
    <path d="M50 31L60 15L65 25L50 31Z" fill="white" opacity="0.8"/>
  </svg>
);

// ============================================================================
// 3D ECOSYSTEM: CYBER-GEOGRAPHIC GLOBE
// ============================================================================
const latLonToVector3 = (lat: number, lon: number, radius: number) => {
  // 3. Conversion Géodésique (Lat/Lon vers 3D)
  const phi = (90 - lat) * (Math.PI / 180);
  const theta = (lon + 180) * (Math.PI / 180);

  const x = -(radius * Math.sin(phi) * Math.cos(theta));
  const z = radius * Math.sin(phi) * Math.sin(theta);
  const y = radius * Math.cos(phi);

  return new THREE.Vector3(x, y, z);
};

const CityMarkers = () => {
  const [activeCity, setActiveCity] = useState<number | null>(null);

  return (
    <group>
      {CITIES.map((city, i) => {
        const pos = latLonToVector3(city.lat, city.lon, GLOBE_RADIUS);
        const isActive = activeCity === i;
        return (
          <group
            key={i}
            position={pos}
            onClick={(e) => { e.stopPropagation(); setActiveCity(isActive ? null : i); }}
            onPointerOver={() => document.body.style.cursor = 'pointer'}
            onPointerOut={() => document.body.style.cursor = 'auto'}
          >
            <mesh>
              <sphereGeometry args={[isActive ? 0.025 : 0.015, 16, 16]} />
              <meshBasicMaterial color={isActive ? "#ffffff" : BRAND.primary} transparent opacity={1} blending={THREE.AdditiveBlending} />
            </mesh>
            <Ring delay={i * 0.2} active={isActive} />
            <Ring delay={i * 0.2 + 0.5} active={isActive} />

            {isActive && (
              <Html center className="pointer-events-none">
                <div className="bg-white/90 backdrop-blur-xl px-5 py-4 rounded-2xl shadow-[0_12px_40px_rgba(46,144,250,0.25)] border border-white whitespace-nowrap transform translate-x-6 -translate-y-6 pointer-events-auto">
                  <p className="font-black text-sm uppercase tracking-widest text-[#0f172a] mb-2 flex items-center gap-2">
                    <span className="w-2.5 h-2.5 rounded-full bg-green-500 animate-pulse shadow-[0_0_8px_rgba(34,197,94,0.6)]" />
                    {city.name}
                  </p>
                  <div className="flex gap-4 text-[10px] font-bold text-gray-400 uppercase tracking-wider">
                    <span>Ping: <span className="text-[#2E90FA]">{city.ping}</span></span>
                    <span>Load: <span className="text-[#2E90FA]">{city.load}</span></span>
                  </div>
                </div>
              </Html>
            )}
          </group>
        );
      })}
      <ConnectionArcs cities={CITIES.map(c => latLonToVector3(c.lat, c.lon, GLOBE_RADIUS))} />
    </group>
  );
};

const Ring = ({ delay, active }: { delay: number, active?: boolean }) => {
  const meshRef = useRef<THREE.Mesh>(null);
  useFrame((state) => {
    if (meshRef.current) {
      const t = (state.clock.elapsedTime + delay) % 2;
      const s = 1 + t * 0.8;
      meshRef.current.scale.set(s, s, s);
      if (meshRef.current.material instanceof THREE.Material) {
        meshRef.current.material.opacity = Math.max(0, (1 - t / 2) * (active ? 0.8 : 0.4));
      }
    }
  });
  return (
    <mesh ref={meshRef} rotation={[Math.PI / 2, 0, 0]}>
      <ringGeometry args={[0.04, 0.05, 32]} />
      <meshBasicMaterial color={active ? "#ffffff" : BRAND.primary} transparent opacity={0.4} side={THREE.DoubleSide} blending={THREE.AdditiveBlending} />
    </mesh>
  );
};

const ConnectionArcs = ({ cities }: { cities: THREE.Vector3[] }) => {
  const arcs = [];
  for (let i = 0; i < cities.length; i++) {
    const start = cities[i];
    const end = cities[(i + 1) % cities.length];

    // 4. Algorithme des Flux (Courbes de Bézier 3D)
    const mid = new THREE.Vector3().lerpVectors(start, end, 0.5);
    const distance = start.distanceTo(end);
    mid.normalize().multiplyScalar(GLOBE_RADIUS * (1 + distance * 0.15));

    arcs.push(new THREE.QuadraticBezierCurve3(start, mid, end));
  }

  return (
    <group>
      {arcs.map((curve, i) => (
        <group key={i}>
          <line>
            <bufferGeometry attach="geometry" onUpdate={self => self.setFromPoints(curve.getPoints(64))} />
            <lineBasicMaterial attach="material" color={BRAND.primary} transparent opacity={0.1} />
          </line>
          {[0, 0.15, 0.3].map((offset) => (
            <DataPacket key={offset} curve={curve} delay={i * 0.8 + offset} opacity={0.8 - offset * 2} />
          ))}
        </group>
      ))}
    </group>
  );
};

const DataPacket = ({ curve, delay, opacity }: { curve: THREE.QuadraticBezierCurve3, delay: number, opacity: number }) => {
  const meshRef = useRef<THREE.Mesh>(null);
  useFrame((state) => {
    if (meshRef.current) {
      // 4. Interpolation temporelle (Flux)
      const vitesse = 0.5;
      const t = ((state.clock.elapsedTime * vitesse) + delay) % 1;
      meshRef.current.position.copy(curve.getPoint(t));
    }
  });
  return (
    <mesh ref={meshRef}>
      <sphereGeometry args={[0.012, 8, 8]} />
      <meshBasicMaterial color={BRAND.primary} transparent opacity={opacity} blending={THREE.AdditiveBlending} />
    </mesh>
  );
};

const Atmosphere = () => (
  <mesh>
    <sphereGeometry args={[GLOBE_RADIUS * 1.15, 64, 64]} />
    <meshBasicMaterial color={BRAND.primary} transparent opacity={0.05} side={THREE.BackSide} blending={THREE.AdditiveBlending} />
  </mesh>
);

const InteractiveGlobe = () => {
  const pointsRef = useRef<THREE.Points>(null);
  const [geometryData, setGeometryData] = useState<{ positions: Float32Array, colors: Float32Array, sizes: Float32Array } | null>(null);

  useEffect(() => {
    let mounted = true;
    const img = new Image();
    img.crossOrigin = "Anonymous";
    img.src = "https://unpkg.com/three-globe/example/img/earth-water.png";

    img.onload = () => {
      if (!mounted) return;
      const canvas = document.createElement('canvas');
      // Forcing internal scale for texture raycasting (Canvas Invisible)
      canvas.width = 1024;
      canvas.height = 512;
      const ctx = canvas.getContext('2d', { willReadFrequently: true });
      if (!ctx) return;

      ctx.drawImage(img, 0, 0, 1024, 512);
      const imgData = ctx.getImageData(0, 0, 1024, 512).data;

      const particleCount = 240000;

      // 5. Optimisation du Rendu (GPGPU-like via arrays)
      const positions = new Float32Array(particleCount * 3);
      const colors = new Float32Array(particleCount * 3);
      const sizes = new Float32Array(particleCount);

      // 1. Distribution Équidistante (Spirale de Fibonacci)
      const offset = 2 / particleCount;
      const increment = Math.PI * (3 - Math.sqrt(5));

      for (let i = 0; i < particleCount; i++) {
        const y = ((i * offset) - 1) + (offset / 2);
        const r = Math.sqrt(1 - Math.pow(y, 2));
        const phi = i * increment;

        const x = Math.cos(phi) * r;
        const z = Math.sin(phi) * r;

        positions[i*3] = x * GLOBE_RADIUS;
        positions[i*3+1] = y * GLOBE_RADIUS;
        positions[i*3+2] = z * GLOBE_RADIUS;

        // 2. Le Masquage des Continents (Raycasting de Texture)
        const lat = Math.asin(y);
        const lon = Math.atan2(z, x);

        const u = 0.5 + lon / (2 * Math.PI);
        const v = 0.5 - lat / Math.PI;

        const px = Math.max(0, Math.min(Math.floor(u * 1024), 1023));
        const py = Math.max(0, Math.min(Math.floor(v * 512), 511));

        const dataIndex = (py * 1024 + px) * 4;

        // Note: the external texture has dark land (0) and light water (255)
        // We invert brightness here so that 'gris clair -> Continent' logic applies correctly
        const brightness = 255 - imgData[dataIndex];
        const isLand = brightness > 50;

        if (isLand) {
          colors[i*3] = 46/255; colors[i*3+1] = 144/255; colors[i*3+2] = 250/255; // Cyan / Primary
          sizes[i] = 0.010; // Stronger presence for continents
        } else {
          colors[i*3] = 15/255; colors[i*3+1] = 23/255; colors[i*3+2] = 42/255; // Dark blue for ocean
          sizes[i] = 0.003; // Tiny size for ocean points
        }
      }

      setGeometryData({ positions, colors, sizes });
    };

    return () => { mounted = false; };
  }, []);

  if (!geometryData) {
    return (
      <group>
        <OrbitControls enableZoom={false} enablePan={false} autoRotate autoRotateSpeed={0.8} rotateSpeed={0.6} />
        <mesh>
          <sphereGeometry args={[GLOBE_RADIUS * 0.98, 32, 32]} />
          <meshBasicMaterial color={BRAND.primary} wireframe transparent opacity={0.05} />
        </mesh>
        <Atmosphere />
      </group>
    );
  }

  return (
    <group>
      <OrbitControls enableZoom={false} enablePan={false} autoRotate autoRotateSpeed={0.8} rotateSpeed={0.6} />
      {/* 5. Optimisation du Rendu : Single Draw Call */}
      <points ref={pointsRef}>
        <bufferGeometry>
          <bufferAttribute attach="attributes-position" count={240000} array={geometryData.positions} itemSize={3} />
          <bufferAttribute attach="attributes-color" count={240000} array={geometryData.colors} itemSize={3} />
          <bufferAttribute attach="attributes-size" count={240000} array={geometryData.sizes} itemSize={1} />
        </bufferGeometry>
        <shaderMaterial
          transparent
          vertexColors
          uniforms={{ opacity: { value: 0.95 } }}
          vertexShader={`
            attribute vec3 color;
            attribute float size;
            varying vec3 vColor;
            void main() {
              vColor = color;
              vec4 mvPosition = modelViewMatrix * vec4(position, 1.0);
              gl_PointSize = size * (300.0 / -mvPosition.z);
              gl_Position = projectionMatrix * mvPosition;
            }
          `}
          fragmentShader={`
            uniform float opacity;
            varying vec3 vColor;
            void main() {
              gl_FragColor = vec4(vColor, opacity);
            }
          `}
          blending={THREE.AdditiveBlending}
          depthWrite={false}
        />
      </points>
      <Atmosphere />
      <CityMarkers />
    </group>
  );
};


// ============================================================================
// UI COMPONENTS
// ============================================================================
const FeatureCard = ({ icon, title, desc }: { icon: React.ReactNode, title: string, desc: string }) => (
  <div className="p-10 bg-white rounded-[2.5rem] shadow-[0_4px_20px_rgba(0,0,0,0.02)] hover:shadow-[0_20px_40px_rgba(46,144,250,0.08)] transition-all duration-300 border border-slate-100 group">
    <div className="w-14 h-14 rounded-[1.2rem] bg-slate-50 flex items-center justify-center text-[#0f172a] mb-8 group-hover:bg-[#2E90FA] group-hover:text-white transition-colors duration-300">
      {icon}
    </div>
    <h3 className="text-2xl font-black uppercase tracking-tighter text-[#0f172a] mb-4">{title}</h3>
    <p className="text-[#0f172a]/60 text-sm font-medium leading-relaxed">{desc}</p>
  </div>
);

const FAQItem = ({ question, answer }: { question: string, answer: string }) => {
  const [isOpen, setIsOpen] = useState(false);
  return (
    <div className="bg-white rounded-[2rem] mb-4 shadow-[0_4px_20px_rgba(0,0,0,0.02)] border border-slate-100 overflow-hidden">
      <button onClick={() => setIsOpen(!isOpen)} className="w-full p-8 flex items-center justify-between text-left hover:bg-slate-50 transition-colors">
        <div className="flex items-center gap-6">
          <div className={`w-10 h-10 rounded-full border flex items-center justify-center transition-colors ${isOpen ? 'border-[#2E90FA] text-[#2E90FA]' : 'border-slate-200 text-slate-400'}`}>
            {isOpen ? <X size={18} /> : <Plus size={18} />}
          </div>
          <span className="text-base font-black uppercase tracking-tighter text-[#0f172a]">{question}</span>
        </div>
      </button>
      <AnimatePresence>
        {isOpen && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            className="px-8 pb-8 text-[#0f172a]/60 text-sm leading-relaxed font-medium ml-16"
          >
            {answer}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
};


// ============================================================================
// MAIN PAGE LAYOUT
// ============================================================================
export default function LandingPage() {
  const containerRef = useRef<HTMLDivElement>(null);

  return (
    <div ref={containerRef} className={`relative ${BRAND.light} text-[#0f172a] overflow-hidden font-sans selection:bg-[#2E90FA]/20`}>

      {/* Global Gradient & Ambient Glow */}
      <div className={`absolute inset-0 ${BRAND.gradient} z-[0]`} />
      <div className="absolute top-0 right-0 w-[1000px] h-[1000px] bg-[#2E90FA]/5 blur-[150px] rounded-full z-[0] pointer-events-none" />

      {/* --- NAVBAR --- */}
      <nav className="absolute top-0 left-0 right-0 z-[100] px-8 lg:px-12 py-8 bg-transparent">
        <div className="max-w-[90rem] mx-auto flex items-center justify-between">
          <div className="flex items-center gap-4">
            <SharkLogo className="w-[42px] h-[42px] shadow-[0_8px_20px_rgba(46,144,250,0.25)]" />
            <span className="font-black text-2xl tracking-tighter uppercase text-[#0f172a] mt-1">SwimVPN<span className="text-[#2E90FA]">+</span></span>
          </div>
          <div className="hidden md:flex items-center gap-12 text-[11px] font-black uppercase tracking-[0.2em] text-[#0f172a]/80 mt-1">
            <a href="#features" className="hover:text-[#2E90FA] transition-colors">Features</a>
            <a href="#download" className="hover:text-[#2E90FA] transition-colors">Download</a>
          </div>
        </div>
      </nav>

      {/* --- HERO SECTION --- */}
      <section className="relative min-h-[90vh] flex flex-col lg:flex-row items-center justify-between pt-32 lg:pt-0 px-8 lg:px-12 max-w-[90rem] mx-auto z-10">

        {/* Left Col: Typographic Headline */}
        <div className="w-full lg:w-[45%] z-20 flex flex-col justify-center pb-16 lg:pb-0">
          <div className="inline-flex items-center self-start px-5 py-2.5 rounded-full bg-[#2E90FA]/10 text-[10px] font-black uppercase tracking-[0.25em] text-[#2E90FA] mb-12 border border-[#2E90FA]/20 backdrop-blur-sm">
            V2 Deployment Ready
          </div>
          <h1 className="text-[4.5rem] lg:text-[6.5rem] font-black leading-[0.85] tracking-tighter uppercase text-[#0f172a] m-0 p-0">
            <div className="block">Global</div>
            <div className="block">Encryption.</div>
            <div className="block">Stay</div>
            <div className="block text-[#2E90FA]">Hidden.</div>
          </h1>
        </div>

        {/* Right Col: Pixel Globe */}
        <div className="w-full lg:w-[55%] relative flex items-center justify-center min-h-[500px] lg:min-h-[700px] z-10 pointer-events-none lg:pointer-events-auto">
          <div className="relative w-full max-w-[600px] aspect-square">
            <div className="absolute inset-0 z-0">
              <Canvas camera={{ position: [0, 0, 3.5], fov: 35 }}>
                <InteractiveGlobe />
              </Canvas>
            </div>

            {/* Status Card Overlay */}
            <div className="absolute top-[10%] -right-[5%] bg-white/80 backdrop-blur-xl p-6 rounded-[1.5rem] border border-white shadow-[0_20px_40px_rgba(0,0,0,0.05)] z-20 hidden lg:block pointer-events-none">
              <p className="text-[10px] font-black uppercase tracking-widest text-[#2E90FA] mb-3">Network Status</p>
              <div className="flex items-center gap-3 mb-2">
                <div className="w-2.5 h-2.5 bg-green-500 rounded-full animate-pulse shadow-[0_0_12px_rgba(34,197,94,0.6)]" />
                <p className="text-xs font-black uppercase tracking-wide text-[#0f172a]">Elite Nodes Ready</p>
              </div>
              <p className="text-[10px] text-[#0f172a]/50 mt-2 max-w-[160px] leading-[1.6] font-medium">Drag to rotate the globe. Tap any node to view live metrics.</p>
            </div>
          </div>
        </div>
      </section>

      {/* --- FEATURES SECTION --- */}
      <section id="features" className="py-32 lg:py-48 px-8 lg:px-12 relative z-10">
        <div className="max-w-[90rem] mx-auto">
          <div className="mb-24 max-w-2xl">
            <p className="text-[#2E90FA] text-[11px] font-black uppercase tracking-[0.3em] mb-6 flex items-center gap-4">
              <span className="w-8 h-[2px] bg-[#2E90FA]" /> Technical Excellence
            </p>
            <h2 className="text-[3.5rem] lg:text-[5rem] font-black uppercase tracking-tighter text-[#0f172a] leading-[0.9]">Power In Silence.</h2>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
            <FeatureCard icon={<Zap size={24} />} title="Electric Speed" desc="Optimized VLESS nodes with 10Gbps backbone for zero-latency streaming and gaming." />
            <FeatureCard icon={<Shield size={24} />} title="Shark Protection" desc="Advanced traffic obfuscation that makes your VPN signature look like regular web traffic." />
            <FeatureCard icon={<GlobeIcon size={24} />} title="Global Mesh" desc="Access 50+ locations worldwide with automatic smart-routing for best performance." />
            <FeatureCard icon={<Lock size={24} />} title="No-Log Engine" desc="RAM-only servers that wipe every session bit instantly. Your history never existed." />
            <FeatureCard icon={<Cpu size={24} />} title="Multi-Protocol" desc="Switch between VLESS, Reality, Trojan and SSH with a single tap for ultimate flexibility." />
            <FeatureCard icon={<Activity size={24} />} title="Kill Switch V2" desc="Next-gen protection that stops all traffic at the system level if a drop is detected." />
          </div>
        </div>
      </section>

      {/* --- STATS BAR --- */}
      <section className="py-24 bg-[#0f172a] relative overflow-hidden">
        <div className="absolute inset-0 bg-[#2E90FA]/10 blur-[100px]" />
        <div className="max-w-[90rem] mx-auto px-8 lg:px-12 grid grid-cols-2 lg:grid-cols-4 gap-16 text-white text-center relative z-10">
          {[
            { v: "2,400+", l: "Active Nodes" },
            { v: "800 Gbps", l: "Bandwidth" },
            { v: "99.9%", l: "Uptime" },
            { v: "48 PB+", l: "Data Encrypted" }
          ].map((stat, i) => (
            <div key={i}>
              <p className="text-[3rem] lg:text-[4rem] font-black tracking-tighter mb-2 leading-none text-[#f8fafc]">{stat.v}</p>
              <p className="text-[10px] font-black uppercase tracking-[0.2em] text-[#2E90FA]">{stat.l}</p>
            </div>
          ))}
        </div>
      </section>

      {/* --- ONBOARDING SECTION --- */}
      <section className="py-32 lg:py-48 px-8 lg:px-12">
        <div className="max-w-[90rem] mx-auto flex flex-col lg:flex-row gap-24 items-center">
          <div className="flex-1">
            <h2 className="text-[3.5rem] lg:text-[5rem] font-black uppercase tracking-tighter leading-[0.9] mb-16 text-[#0f172a]">
              Start Your<br />Anonymous<br />Journey.
            </h2>
            <div className="space-y-12 max-w-lg">
              {[
                { num: "01", title: "Download APK", desc: "Get the latest build for Android and install it instantly." },
                { num: "02", title: "Import Access", desc: "Scan a QR code or paste your VLESS subscription link." },
                { num: "03", title: "Swim Fast", desc: "Connect with a single tap and enjoy unlimited freedom." }
              ].map((s, i) => (
                <div key={i} className="flex gap-8 items-start group">
                  <span className="text-3xl font-black text-[#2E90FA]/20 group-hover:text-[#2E90FA] transition-colors">{s.num}</span>
                  <div>
                    <h4 className="text-xl font-black uppercase tracking-tighter text-[#0f172a] mb-2">{s.title}</h4>
                    <p className="text-[#0f172a]/60 text-sm font-medium">{s.desc}</p>
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div className="flex-1 w-full flex justify-center lg:justify-end">
            <div className="w-full max-w-[400px] aspect-[3/4] bg-white rounded-[3rem] p-12 border border-slate-100 shadow-[0_20px_60px_rgba(0,0,0,0.04)] relative overflow-hidden flex flex-col items-center justify-center group">
               <div className="relative z-10 flex flex-col items-center">
                  <div className="w-32 h-32 bg-[#f8fafc] rounded-3xl shadow-inner flex items-center justify-center mb-8 border border-white group-hover:scale-110 transition-transform duration-500">
                    <div className="w-16 h-16 bg-[#2E90FA]/10 rounded-2xl flex items-center justify-center">
                      <Download size={32} className="text-[#2E90FA]" />
                    </div>
                  </div>
                  <p className="text-[11px] font-black uppercase tracking-[0.3em] text-[#0f172a] mb-2">Scan To Install</p>
                  <p className="text-[10px] font-bold text-[#0f172a]/40 uppercase tracking-widest">Mobile Device Required</p>
               </div>
            </div>
          </div>
        </div>
      </section>

      {/* --- COMPARISON SECTION --- */}
      <section className="py-32 lg:py-48 px-8 lg:px-12 bg-white relative">
        <div className="absolute top-0 left-0 w-full h-[1px] bg-gradient-to-r from-transparent via-slate-200 to-transparent" />
        <div className="max-w-[70rem] mx-auto">
          <div className="text-center mb-24">
            <h2 className="text-[3.5rem] lg:text-[5rem] font-black uppercase tracking-tighter text-[#0f172a] leading-[0.9]">Better Than The Rest.</h2>
          </div>

          <div className="bg-[#f8fafc] rounded-[2.5rem] border border-slate-100 p-4 lg:p-8">
            <div className="bg-white rounded-[2rem] shadow-[0_10px_40px_rgba(0,0,0,0.02)] overflow-hidden">
              <div className="grid grid-cols-3 gap-0 border-b border-slate-50 text-[10px] font-black uppercase tracking-widest text-[#0f172a]/40">
                <div className="p-8 border-r border-slate-50">Feature</div>
                <div className="p-8 border-r border-slate-50 text-center text-[#2E90FA]">SwimVPN+</div>
                <div className="p-8 text-center">Standard VPN</div>
              </div>
              {[
                { label: "Shadow Stealth Obfuscation", swim: true, std: false },
                { label: "No-Click QR Import", swim: true, std: false },
                { label: "10Gbps RAM-Only Nodes", swim: true, std: "Maybe" },
                { label: "Strict Zero-Logs Policy", swim: true, std: true },
                { label: "Custom VLESS/Reality", swim: true, std: false }
              ].map((row, i) => (
                <div key={i} className="grid grid-cols-3 gap-0 border-b border-slate-50 last:border-0 hover:bg-slate-50/50 transition-colors items-center">
                  <div className="p-8 border-r border-slate-50 text-sm font-black uppercase tracking-tight text-[#0f172a]">{row.label}</div>
                  <div className="p-8 border-r border-slate-50 flex justify-center text-[#2E90FA]">
                    {row.swim === true ? <ShieldCheck size={24} /> : row.swim}
                  </div>
                  <div className="p-8 flex justify-center text-[#0f172a]/20">
                    {row.std === true ? <ShieldCheck size={24} className="opacity-50" /> : row.std === false ? <X size={24} className="opacity-30" /> : <span className="text-[10px] font-black uppercase">{row.std}</span>}
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </section>

      {/* --- FAQ SECTION --- */}
      <section id="faq" className="py-32 lg:py-48 px-8 lg:px-12">
        <div className="max-w-[50rem] mx-auto">
          <h2 className="text-[3.5rem] lg:text-[5rem] font-black uppercase tracking-tighter text-center mb-20 text-[#0f172a] leading-[0.9]">Got Questions?</h2>
          <div className="space-y-4">
            <FAQItem question="Is SwimVPN+ truly anonymous?" answer="Yes. Our RAM-only infrastructure ensures that no session data is ever written to a physical disk. Logs literally cannot exist." />
            <FAQItem question="Can I use it on multiple devices?" answer="Your subscription link can be used on up to 5 devices simultaneously across Android, iOS, and Desktop environments." />
            <FAQItem question="What protocols are supported?" answer="We specialize in VLESS, Reality, and Trojan-Go to ensure you bypass even the strictest deep packet inspection firewalls." />
          </div>
        </div>
      </section>

      {/* --- DOWNLOAD SECTION --- */}
      <section id="download" className="py-32 lg:py-48 px-8 lg:px-12 text-center relative overflow-hidden">
        <div className="absolute inset-0 bg-[#2E90FA] z-[0]" />
        <div className="max-w-[70rem] mx-auto relative z-10 py-24">
          <h2 className="text-[5rem] lg:text-[7rem] font-black uppercase tracking-tighter mb-8 text-white leading-[0.85]">Ready to Swim?</h2>
          <p className="text-white/70 text-lg font-medium mb-16 max-w-xl mx-auto">Download the latest APK for Android. Fast, lightweight, and battery efficient. Built for stealth.</p>
          <button className="px-16 py-8 bg-white text-[#0f172a] rounded-full font-black uppercase tracking-widest text-sm shadow-[0_20px_50px_rgba(0,0,0,0.2)] hover:scale-105 active:scale-95 transition-all flex items-center gap-4 mx-auto group">
            <Download size={24} className="group-hover:text-[#2E90FA] transition-colors" />
            Download V2.0.4 APK
          </button>
          <p className="mt-12 text-[10px] font-black uppercase tracking-[0.4em] text-white/40">
            Version: 2.0.4 • Size: 18.4MB • Updated: Today
          </p>
        </div>
      </section>

      {/* --- FOOTER --- */}
      <footer className="py-16 px-8 lg:px-12 bg-[#0f172a]">
        <div className="max-w-[90rem] mx-auto flex flex-col md:flex-row justify-between items-center gap-10">
          <div className="flex items-center gap-4 grayscale opacity-50">
            <SharkLogo className="w-8 h-8" />
            <span className="font-black text-xl tracking-tighter uppercase text-white mt-1">SwimVPN+</span>
          </div>
          <p className="text-[10px] font-black uppercase tracking-[0.4em] text-white/30">© 2024 SwimVPN+ Core System.</p>
          <div className="flex gap-12 text-[10px] font-black uppercase tracking-[0.2em] text-white/50">
            <a href="#" className="hover:text-white transition-colors">Privacy</a>
            <a href="#" className="hover:text-white transition-colors">Terms</a>
          </div>
        </div>
      </footer>
    </div>
  );
}
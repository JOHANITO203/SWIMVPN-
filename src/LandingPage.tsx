import React, { useRef, useEffect, useState } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { Canvas, useFrame } from '@react-three/fiber';
import { Points, PointMaterial, OrbitControls, Html } from '@react-three/drei';
import * as THREE from 'three';
import { Zap, Shield, Globe, Lock, Cpu, Activity, Download, CheckCircle, Plus, X, ArrowRight } from 'lucide-react';
import AppMockup from './App';

// --- Assets: Shark Logo ---
const SharkLogo = ({ className = "w-8 h-8" }: { className?: string }) => (
  <svg viewBox="0 0 100 100" className={className} fill="none" xmlns="http://www.w3.org/2000/svg">
    <rect width="100" height="100" rx="28" fill="#2E90FA"/>
    <path d="M72 56C72 63.732 65.732 70 58 70H38C30.268 70 24 63.732 24 56C24 49.3093 28.6946 43.6874 35.0347 42.3484C36.8532 35.808 42.8273 31 50 31C57.1727 31 63.1468 35.808 64.9653 42.3484C68.4239 42.8631 72 45.7533 72 50V56Z" fill="white"/>
  </svg>
);

// --- Three.js Components: Cyber-Geographic Pixel Globe ---

const GLOBE_RADIUS = 0.85;

const CityMarkers = () => {
  const [activeCity, setActiveCity] = useState<number | null>(null);

  const cities = [
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

  const latLonToVector3 = (lat: number, lon: number, R: number) => {
    const phi = (90 - lat) * (Math.PI / 180);
    const theta = (lon + 180) * (Math.PI / 180);
    const x = -(R * Math.sin(phi) * Math.cos(theta));
    const z = R * Math.sin(phi) * Math.sin(theta);
    const y = R * Math.cos(phi);
    return new THREE.Vector3(x, y, z);
  };

  return (
    <group>
      {cities.map((city, i) => {
        const pos = latLonToVector3(city.lat, city.lon, GLOBE_RADIUS);
        const isActive = activeCity === i;
        return (
          <group
            key={i}
            position={pos}
            onClick={(e) => {
              e.stopPropagation();
              setActiveCity(isActive ? null : i);
            }}
            onPointerOver={() => document.body.style.cursor = 'pointer'}
            onPointerOut={() => document.body.style.cursor = 'auto'}
          >
            {/* Hotspot */}
            <mesh>
              <sphereGeometry args={[isActive ? 0.025 : 0.015, 16, 16]} />
              <meshBasicMaterial color={isActive ? "#ffffff" : "#2E90FA"} transparent opacity={1} blending={THREE.AdditiveBlending} />
            </mesh>
            {/* Pulsing Rings */}
            <Ring delay={i * 0.2} active={isActive} />
            <Ring delay={i * 0.2 + 0.5} active={isActive} />

            {/* Interactive Tooltip */}
            {isActive && (
              <Html center className="pointer-events-none">
                <div className="bg-white/90 backdrop-blur-md px-4 py-3 rounded-2xl shadow-[0_10px_40px_rgba(46,144,250,0.3)] border border-white whitespace-nowrap transform translate-x-4 -translate-y-4 pointer-events-auto">
                  <p className="font-black text-sm uppercase tracking-widest text-[#0f172a] mb-1.5 flex items-center gap-2">
                    <span className="w-2 h-2 rounded-full bg-green-500 animate-pulse" />
                    {city.name}
                  </p>
                  <div className="flex gap-4 text-[10px] font-bold text-gray-400 uppercase">
                    <span>Ping: <span className="text-[#2E90FA]">{city.ping}</span></span>
                    <span>Load: <span className="text-[#2E90FA]">{city.load}</span></span>
                  </div>
                </div>
              </Html>
            )}
          </group>
        );
      })}
      <ConnectionArcs cities={cities.map(c => latLonToVector3(c.lat, c.lon, GLOBE_RADIUS))} />
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
      <meshBasicMaterial color={active ? "#ffffff" : "#2E90FA"} transparent opacity={0.4} side={THREE.DoubleSide} blending={THREE.AdditiveBlending} />
    </mesh>
  );
};

const ConnectionArcs = ({ cities }: { cities: THREE.Vector3[] }) => {
  const arcs = [];
  for (let i = 0; i < cities.length; i++) {
    const start = cities[i];
    const end = cities[(i + 1) % cities.length];

    const mid = new THREE.Vector3().addVectors(start, end).multiplyScalar(0.5);
    const distance = start.distanceTo(end);
    mid.normalize().multiplyScalar(GLOBE_RADIUS + distance * 0.4); // Arch height

    const curve = new THREE.QuadraticBezierCurve3(start, mid, end);
    arcs.push(curve);
  }

  return (
    <group>
      {arcs.map((curve, i) => (
        <group key={i}>
          <line>
            <bufferGeometry attach="geometry" onUpdate={self => self.setFromPoints(curve.getPoints(64))} />
            <lineBasicMaterial attach="material" color="#2E90FA" transparent opacity={0.1} />
          </line>
          {/* Multiple packets for trailing effect */}
          {[0, 0.1, 0.2].map((offset) => (
            <DataPacket key={offset} curve={curve} delay={i * 0.8 + offset} opacity={0.8 - offset * 3} />
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
      const t = ((state.clock.elapsedTime * 0.6) + delay) % 1;
      const pos = curve.getPoint(t);
      meshRef.current.position.copy(pos);
    }
  });
  return (
    <mesh ref={meshRef}>
      <sphereGeometry args={[0.012, 8, 8]} />
      <meshBasicMaterial color="#2E90FA" transparent opacity={opacity} blending={THREE.AdditiveBlending} />
    </mesh>
  );
};

const radius = 1.5;

const Atmosphere = () => {
  return (
    <mesh>
      <sphereGeometry args={[GLOBE_RADIUS * 1.15, 64, 64]} />
      <meshBasicMaterial
        color="#2E90FA"
        transparent
        opacity={0.05}
        side={THREE.BackSide}
        blending={THREE.AdditiveBlending}
      />
    </mesh>
  );
};

const InteractiveGlobe = () => {
  const pointsRef = useRef<THREE.Points>(null);
  const particleCount = 200000;
  const [currentCityIndex, setCurrentCityIndex] = useState(0);

  const cities = [
    { name: 'New York', lat: 40.7128, lon: -74.0060 },
    { name: 'London', lat: 51.5074, lon: -0.1278 },
    { name: 'Paris', lat: 48.8566, lon: 2.3522 },
    { name: 'Frankfurt', lat: 50.1109, lon: 8.6821 },
    { name: 'Tokyo', lat: 35.6762, lon: 139.6503 },
    { name: 'Singapore', lat: 1.3521, lon: 103.8198 },
    { name: 'Dubai', lat: 25.2048, lon: 55.2708 },
    { name: 'Sydney', lat: -33.8688, lon: 151.2093 },
    { name: 'São Paulo', lat: -23.5505, lon: -46.6333 },
    { name: 'Johannesburg', lat: -26.2041, lon: 28.0473 }
  ];

  // 1. Initialisation Instantanée (Full-Code Mathématique)
  // Même si l'image externe échoue, les continents s'afficheront grâce à cette formule mathématique.
  const [{ initialPositions, initialSizes, initialColors }] = useState(() => {
    const pos = new Float32Array(particleCount * 3);
    const siz = new Float32Array(particleCount);
    const col = new Float32Array(particleCount * 3);

    for (let i = 0; i < particleCount; i++) {
      const phi = Math.acos(-1 + (2 * i) / particleCount);
      const theta = Math.sqrt(particleCount * Math.PI) * phi;

      pos[i * 3] = GLOBE_RADIUS * Math.cos(theta) * Math.sin(phi);
      pos[i * 3 + 1] = GLOBE_RADIUS * Math.sin(theta) * Math.sin(phi);
      pos[i * 3 + 2] = GLOBE_RADIUS * Math.cos(phi);

      const lat = 90 - (phi * 180) / Math.PI;
      const lon = ((theta * 180) / Math.PI) % 360 - 180;

      // Approximations mathématiques des continents (Fallback robuste)
      const n = Math.sin(lat * 0.2) * Math.cos(lon * 0.2) + Math.sin(lat * 0.5) * Math.sin(lon * 0.5);
      const isLand = (
        (lat > 15 && lat < 75 && lon > -165 + n*5 && lon < -50 + n*5) || // Amérique Nord
        (lat > -55 && lat < 15 && lon > -85 + n*5 && lon < -35 + n*5) || // Amérique Sud
        (lat > 35 && lat < 70 && lon > -10 + n*5 && lon < 40 + n*5) ||   // Europe
        (lat > -35 && lat < 35 && lon > -20 + n*5 && lon < 50 + n*5) ||   // Afrique
        (lat > 5 && lat < 75 && lon > 40 + n*5 && lon < 180 + n*5) ||    // Asie
        (lat > -45 && lat < -10 && lon > 110 + n*5 && lon < 155 + n*5)   // Océanie
      );

      siz[i] = isLand ? 0.01 : 0.002;

      if (isLand) {
        col[i*3] = 46/255; col[i*3+1] = 144/255; col[i*3+2] = 250/255; // Bleu vif
      } else {
        col[i*3] = 15/255; col[i*3+1] = 23/255;  col[i*3+2] = 42/255;  // Bleu nuit
      }
    }
    return { initialPositions: pos, initialSizes: siz, initialColors: col };
  });

  const [sizes, setSizes] = useState(initialSizes);
  const [colors, setColors] = useState(initialColors);

  // 2. Affinage Haute-Définition (Contournage Pixel-Perfect)
  // Utilisation de fetch + blob pour esquiver totalement les erreurs de sécurité CORS des canvas
  useEffect(() => {
    let mounted = true;

    fetch("https://unpkg.com/three-globe/example/img/earth-water.png")
      .then(res => res.blob())
      .then(blob => {
        if (!mounted) return;
        const img = new Image();
        img.onload = () => {
          if (!mounted) return;
          const canvas = document.createElement('canvas');
          canvas.width = img.width;
          canvas.height = img.height;
          const ctx = canvas.getContext('2d');
          if (!ctx) return;

          ctx.drawImage(img, 0, 0);
          const imgData = ctx.getImageData(0, 0, canvas.width, canvas.height);

          const newSizes = new Float32Array(particleCount);
          const newColors = new Float32Array(particleCount * 3);

          for (let i = 0; i < particleCount; i++) {
            const x = initialPositions[i * 3];
            const y = initialPositions[i * 3 + 1];
            const z = initialPositions[i * 3 + 2];

            const lat = Math.asin(y / GLOBE_RADIUS);
            const lon = Math.atan2(z, x);

            const u = 0.5 + lon / (2 * Math.PI);
            const v = 0.5 - lat / Math.PI;

            const px = Math.max(0, Math.min(Math.floor(u * img.width), img.width - 1));
            const py = Math.max(0, Math.min(Math.floor(v * img.height), img.height - 1));
            const index = (py * img.width + px) * 4;

            // Noir (0) = Terre, Blanc (255) = Océan
            const isLand = imgData.data[index] < 128;

            newSizes[i] = isLand ? 0.015 : 0.003;

            if (isLand) {
                newColors[i*3] = 46/255; newColors[i*3+1] = 144/255; newColors[i*3+2] = 250/255; // #2E90FA
            } else {
                newColors[i*3] = 15/255; newColors[i*3+1] = 23/255;  newColors[i*3+2] = 42/255;  // #0F172A
            }
          }

          setSizes(newSizes);
          setColors(newColors);

          // Mise à jour immédiate du GPU
          if (pointsRef.current) {
            pointsRef.current.geometry.setAttribute('size', new THREE.BufferAttribute(newSizes, 1));
            pointsRef.current.geometry.setAttribute('color', new THREE.BufferAttribute(newColors, 3));
          }
        };
        img.src = URL.createObjectURL(blob);
      })
      .catch(e => console.warn("Conservation de la carte mathématique (mode hors-ligne actif)."));

    return () => { mounted = false; };
  }, [initialPositions]);

  useEffect(() => {
    const interval = setInterval(() => {
      setCurrentCityIndex((prev) => (prev + 1) % cities.length);
    }, 5000);
    return () => clearInterval(interval);
  }, [cities.length]);

  return (
    <group>
      <OrbitControls
        enableZoom={false}
        enablePan={false}
        autoRotate
        autoRotateSpeed={1.0}
        rotateSpeed={0.6}
      />
      <points ref={pointsRef}>
        <bufferGeometry>
          <bufferAttribute
            attach="attributes-position"
            count={particleCount}
            array={initialPositions}
            itemSize={3}
          />
          <bufferAttribute
            attach="attributes-size"
            count={particleCount}
            array={sizes}
            itemSize={1}
          />
          <bufferAttribute
            attach="attributes-color"
            count={particleCount}
            array={colors}
            itemSize={3}
          />
        </bufferGeometry>
        <shaderMaterial
          transparent
          vertexColors
          uniforms={{
            opacity: { value: 0.8 }
          }}
          vertexShader={`
            attribute float size;
            attribute vec3 color;
            varying vec3 vColor;
            varying float vSize;
            void main() {
              vSize = size;
              vColor = color;
              vec4 mvPosition = modelViewMatrix * vec4(position, 1.0);
              gl_PointSize = size * (300.0 / -mvPosition.z);
              gl_Position = projectionMatrix * mvPosition;
            }
          `}
          fragmentShader={`
            uniform float opacity;
            varying vec3 vColor;
            varying float vSize;
            void main() {
              if (length(gl_PointCoord - vec2(0.5)) > 0.5) discard;
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

// --- FAQ Component ---
const FAQItem = ({ question, answer }: { question: string, answer: string }) => {
  const [isOpen, setIsOpen] = useState(false);
  return (
    <div className="bg-white rounded-[2rem] mb-4 shadow-[0_4px_20px_rgba(0,0,0,0.03)] border border-gray-50 overflow-hidden">
      <button
        onClick={() => setIsOpen(!isOpen)}
        className="w-full p-8 flex items-center justify-between text-left hover:bg-gray-50 transition-colors"
      >
        <div className="flex items-center gap-4">
          <div className="w-8 h-8 rounded-full border border-blue-100 flex items-center justify-center text-blue-500">
            {isOpen ? <X size={16} /> : <Plus size={16} />}
          </div>
          <span className="text-sm font-black uppercase tracking-widest text-[#0f172a]">{question}</span>
        </div>
      </button>
      <AnimatePresence>
        {isOpen && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            className="px-8 pb-8 text-gray-500 text-sm leading-relaxed font-medium ml-12"
          >
            {answer}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
};

export default function LandingPage() {
  const containerRef = useRef<HTMLDivElement>(null);

  return (
    <div ref={containerRef} className="relative bg-[#f8fafc] text-[#0f172a] overflow-hidden font-sans selection:bg-blue-100">

      {/* Absolute Gradient Background to match Mockup */}
      <div className="absolute inset-0 bg-gradient-to-br from-[#ffffff] via-[#f4f7fb] to-[#eaf2f9] z-[0]" />
      <div className="absolute top-1/4 right-0 w-[800px] h-[800px] bg-[#2E90FA]/10 blur-[150px] rounded-full z-[0] pointer-events-none" />

      {/* --- NAVBAR --- */}
      <nav className="absolute top-0 left-0 right-0 z-[100] px-12 py-8 bg-transparent">
        <div className="max-w-[90rem] mx-auto flex items-center justify-between">
          <div className="flex items-center gap-4">
            <SharkLogo className="w-[42px] h-[42px] shadow-[0_8px_20px_rgba(46,144,250,0.25)] rounded-[12px]" />
            <span className="font-black text-2xl tracking-tighter uppercase text-[#0f172a] mt-1">SwimVPN<span className="text-[#2E90FA]">+</span></span>
          </div>
          <div className="hidden md:flex items-center gap-14 text-[11px] font-black uppercase tracking-[0.2em] text-[#0f172a]/80 mt-1">
            <a href="#features" className="hover:text-[#2E90FA] transition-colors">Features</a>
            <a href="#download" className="hover:text-[#2E90FA] transition-colors">Download</a>
          </div>
        </div>
      </nav>

      {/* --- HERO SECTION --- */}
      <section className="relative min-h-[90vh] flex flex-col lg:flex-row items-center justify-between pt-[160px] pb-20 px-8 lg:px-12 max-w-[90rem] mx-auto z-10 gap-16 lg:gap-24">

        {/* Left Col: Typographic Headline */}
        <div className="w-full lg:w-[45%] z-20 flex flex-col justify-center">
          <div className="inline-flex items-center self-start px-4 py-2 rounded-full bg-[#EAF2FF] text-[10px] font-black uppercase tracking-[0.2em] text-[#2E90FA] mb-10 shadow-sm border border-white">
            V2 Deployment Ready
          </div>
          <h1 className="text-[3.5rem] lg:text-[5rem] font-black leading-[0.85] tracking-tighter uppercase text-[#0f172a] m-0 p-0">
            <div className="block">Global</div>
            <div className="block">Encryption.</div>
            <div className="block">Stay</div>
            <div className="block text-[#2E90FA]">Hidden.</div>
          </h1>
        </div>

        {/* Right Col: Pixel Globe and Floating Elements */}
        <div className="w-full lg:w-[50%] relative flex items-center justify-center min-h-[500px] lg:min-h-[700px] z-10 pointer-events-none lg:pointer-events-auto">

          {/* Confidently Sized Globe Box */}
          <div className="relative w-full max-w-[700px] aspect-square">

            <div className="absolute inset-0 z-0">
              <Canvas camera={{ position: [0, 0, 3.2], fov: 40 }}>
                <InteractiveGlobe />
              </Canvas>
            </div>

            {/* Network Status Floating Card */}
            <div className="absolute top-[15%] -right-[5%] bg-white/80 backdrop-blur-xl p-6 rounded-[1.5rem] border border-white/60 shadow-[0_12px_40px_rgba(0,0,0,0.06)] z-20 hidden lg:block pointer-events-none">
              <p className="text-[10px] font-black uppercase tracking-widest text-[#2E90FA] mb-3">Network Status</p>
              <div className="flex items-center gap-3 mb-2">
                <div className="w-2.5 h-2.5 bg-[#22c55e] rounded-full animate-pulse shadow-[0_0_12px_rgba(34,197,94,0.6)]" />
                <p className="text-xs font-black uppercase tracking-wide text-[#0f172a]">Elite Nodes Ready</p>
              </div>
              <p className="text-[10px] text-gray-500 mt-2 max-w-[160px] leading-[1.6] font-medium">Drag to rotate the globe. Tap any node to view real-time stealth flow metrics.</p>
            </div>

          </div>
        </div>
      </section>

      {/* --- FEATURES SECTION --- */}
      <section id="features" className="py-40 px-10 bg-gray-50/50">
        <div className="max-w-7xl mx-auto">
          <div className="text-center mb-24">
            <p className="text-blue-500 text-[10px] font-black uppercase tracking-[0.4em] mb-4">Technical Excellence</p>
            <h2 className="text-5xl lg:text-7xl font-black uppercase tracking-tighter text-[#0f172a]">Power In Silence</h2>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
            {[
              { icon: <Zap />, title: "Electric Speed", desc: "Optimized VLESS nodes with 10Gbps backbone for zero-latency streaming." },
              { icon: <Shield />, title: "Shark Protection", desc: "Advanced traffic obfuscation that makes your VPN signature look like regular web traffic." },
              { icon: <Globe />, title: "Global Mesh", desc: "Access 50+ locations worldwide with automatic smart-routing for best performance." },
              { icon: <Lock />, title: "No-Log Engine", desc: "RAM-only servers that wipe every session bit instantly. Your history never existed." },
              { icon: <Cpu />, title: "Multi-Protocol", desc: "Switch between VLESS, Reality, Trojan and SSH with a single tap for ultimate flexibility." },
              { icon: <Activity />, title: "Kill Switch V2", desc: "Next-gen protection that stops all traffic if a connection drop is detected." }
            ].map((f, i) => (
              <div key={i} className="p-10 bg-white rounded-[2.5rem] shadow-sm hover:shadow-xl transition-all border border-gray-100 group">
                <div className="w-12 h-12 rounded-2xl bg-gray-50 flex items-center justify-center text-[#0f172a] mb-8 group-hover:bg-blue-500 group-hover:text-white transition-colors">
                  {f.icon}
                </div>
                <h3 className="text-2xl font-black uppercase tracking-tight mb-4">{f.title}</h3>
                <p className="text-gray-400 text-sm font-medium leading-relaxed">{f.desc}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* --- STATS BAR --- */}
      <section className="py-24 bg-blue-500">
        <div className="max-w-7xl mx-auto px-10 grid grid-cols-2 lg:grid-cols-4 gap-12 text-white text-center">
          <div>
            <p className="text-5xl font-black mb-1">2,400+</p>
            <p className="text-[10px] font-black uppercase tracking-widest opacity-70">Active Nodes</p>
          </div>
          <div>
            <p className="text-5xl font-black mb-1">800 Gbps</p>
            <p className="text-[10px] font-black uppercase tracking-widest opacity-70">Total Bandwidth</p>
          </div>
          <div>
            <p className="text-5xl font-black mb-1">99.9%</p>
            <p className="text-[10px] font-black uppercase tracking-widest opacity-70">Uptime</p>
          </div>
          <div>
            <p className="text-5xl font-black mb-1">48 PB+</p>
            <p className="text-[10px] font-black uppercase tracking-widest opacity-70">Encrypted Data</p>
          </div>
        </div>
      </section>

      {/* --- ONBOARDING SECTION --- */}
      <section className="py-40 px-10">
        <div className="max-w-7xl mx-auto flex flex-col lg:flex-row gap-20 items-center">
          <div className="flex-1">
            <h2 className="text-6xl lg:text-[5.5rem] font-black uppercase tracking-tighter leading-[0.9] mb-12 text-[#0f172a]">
              Start Your<br />Anonymous<br />Journey In<br />Seconds.
            </h2>
            <div className="space-y-12">
              {[
                { num: "01", title: "Download APK", desc: "Get the latest build for Android and install it instantly." },
                { num: "02", title: "Import Access", desc: "Scan a QR code or paste your VLESS subscription link." },
                { num: "03", title: "Swim Fast", desc: "Connect with a single tap and enjoy unlimited freedom." }
              ].map((s, i) => (
                <div key={i} className="flex gap-8 items-start">
                  <span className="text-2xl font-black text-blue-500/20">{s.num}</span>
                  <div>
                    <h4 className="text-xl font-black uppercase tracking-widest text-[#0f172a] mb-2">{s.title}</h4>
                    <p className="text-gray-400 text-sm font-medium">{s.desc}</p>
                  </div>
                </div>
              ))}
            </div>
          </div>
          <div className="flex-1 w-full bg-gray-50 rounded-[3rem] p-12 border border-gray-100 relative overflow-hidden group">
             <div className="relative z-10 flex flex-col items-center">
                <div className="w-40 h-40 bg-white rounded-3xl shadow-xl flex items-center justify-center mb-8 border border-white">
                  <div className="w-24 h-24 bg-blue-500/10 rounded-2xl flex items-center justify-center">
                    <Download size={48} className="text-blue-500" />
                  </div>
                </div>
                <p className="text-[10px] font-black uppercase tracking-[0.4em] text-blue-500 mb-2">Scan To Install</p>
                <p className="text-xs font-bold text-gray-400 uppercase tracking-widest">On your mobile device</p>
             </div>
             {/* Mock phone outline in background */}
             <div className="absolute bottom-[-100px] right-[-50px] w-full h-full border-2 border-dashed border-gray-200 rounded-[4rem] opacity-50 group-hover:rotate-6 transition-transform" />
          </div>
        </div>
      </section>

      {/* --- COMPARISON SECTION --- */}
      <section className="py-40 px-10 bg-gray-50/50">
        <div className="max-w-4xl mx-auto">
          <div className="text-center mb-24">
            <p className="text-blue-500 text-[10px] font-black uppercase tracking-[0.4em] mb-4">The Swim Advantage</p>
            <h2 className="text-5xl font-black uppercase tracking-tighter text-[#0f172a]">Better Than The Rest</h2>
          </div>

          <div className="bg-white rounded-[2.5rem] shadow-sm border border-gray-100 overflow-hidden">
            <div className="grid grid-cols-3 gap-0 border-b border-gray-50 text-[10px] font-black uppercase tracking-widest text-gray-400">
              <div className="p-8 border-r border-gray-50">Feature</div>
              <div className="p-8 border-r border-gray-50 text-center text-blue-500">SwimVPN+</div>
              <div className="p-8 text-center">Standard VPN</div>
            </div>
            {[
              { label: "Shadow Stealth Obfuscation", swim: true, std: false },
              { label: "No-Click QR Import", swim: true, std: false },
              { label: "10Gbps RAM-Only Nodes", swim: true, std: "Maybe" },
              { label: "Strict Zero-Logs Policy", swim: true, std: true },
              { label: "Custom VLESS/Reality Protocols", swim: true, std: false }
            ].map((row, i) => (
              <div key={i} className="grid grid-cols-3 gap-0 border-b border-gray-50 last:border-0 hover:bg-gray-50 transition-colors items-center">
                <div className="p-8 border-r border-gray-50 text-sm font-bold text-[#0f172a]">{row.label}</div>
                <div className="p-8 border-r border-gray-50 flex justify-center text-blue-500">
                   {row.swim === true ? <CheckCircle size={20} /> : row.swim}
                </div>
                <div className="p-8 flex justify-center text-gray-200">
                   {row.std === true ? <CheckCircle size={20} className="opacity-50" /> : row.std === false ? <X size={20} className="opacity-30" /> : <span className="text-[10px] font-black uppercase">{row.std}</span>}
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* --- FAQ SECTION --- */}
      <section id="faq" className="py-40 px-10">
        <div className="max-w-3xl mx-auto">
          <h2 className="text-5xl font-black uppercase tracking-tighter text-center mb-20 text-[#0f172a]">Got Questions?</h2>
          <div className="space-y-4">
            <FAQItem
              question="Is SwimVPN+ truly anonymous?"
              answer="Yes. Our RAM-only infrastructure ensures that no session data is ever written to a physical disk. Logs literally cannot exist."
            />
            <FAQItem
              question="Can I use it on multiple devices?"
              answer="Your subscription link can be used on up to 5 devices simultaneously across Android, iOS, and Desktop."
            />
            <FAQItem
              question="What protocols are supported?"
              answer="We specialize in VLESS, Reality, and Trojan-Go to ensure you bypass even the strictest firewalls."
            />
          </div>
        </div>
      </section>

      {/* --- DOWNLOAD SECTION --- */}
      <section id="download" className="py-40 px-10 text-center">
        <div className="max-w-4xl mx-auto p-20 rounded-[4rem] bg-gray-50 border border-gray-100">
          <h2 className="text-6xl lg:text-7xl font-black uppercase tracking-tighter mb-8 text-[#0f172a]">Ready to Swim?</h2>
          <p className="text-gray-500 text-lg font-medium mb-16">Download the latest APK for Android. Fast, lightweight, and battery efficient.</p>
          <button className="px-16 py-8 bg-blue-500 text-white rounded-[2.5rem] font-black uppercase tracking-widest text-sm shadow-2xl shadow-blue-500/30 hover:scale-105 active:scale-95 transition-all flex items-center gap-4 mx-auto">
            <Download size={24} />
            Download V2.0.4 APK
          </button>
          <p className="mt-12 text-[10px] font-black uppercase tracking-[0.4em] text-gray-300">
            Version: 2.0.4 • Size: 18.4MB • Updated: Today
          </p>
        </div>
      </section>

      <footer className="py-20 px-10 border-t border-gray-50">
        <div className="max-w-7xl mx-auto flex flex-col md:flex-row justify-between items-center gap-10">
          <div className="flex items-center gap-2 grayscale opacity-30">
            <Shield size={18} />
            <span className="font-black text-lg tracking-tighter uppercase text-[#0f172a]">SwimVPN<span className="text-blue-500">+</span></span>
          </div>
          <p className="text-[10px] font-black uppercase tracking-[0.4em] text-gray-300">© 2024 SwimVPN+ Core System.</p>
          <div className="flex gap-8 text-[10px] font-black uppercase tracking-widest text-gray-400">
            <a href="#" className="hover:text-blue-500 transition-colors">Privacy</a>
            <a href="#" className="hover:text-blue-500 transition-colors">Terms</a>
          </div>
        </div>
      </footer>
    </div>
  );
}

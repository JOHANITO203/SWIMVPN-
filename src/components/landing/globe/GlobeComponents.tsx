import React, { useEffect, useMemo, useRef, useState } from 'react';
import * as THREE from 'three';
import { useFrame, useLoader } from '@react-three/fiber';
import { Html } from '@react-three/drei';
import { CITIES, createArcCurve, latLonToVector3, City } from './globeUtils';

const GLOBE_RADIUS = 1;
const ROTATION_SPEED = 0.00035;

// Lowered point counts for a more minimalist "plexus" dot matrix aesthetic
// Increased point count for high-definition coastline rendering
const TOTAL_POINTS = 75000;
const CLOUD_COUNT = 500;

type MaskData = {
  data: Uint8ClampedArray;
  width: number;
  height: number;
};

function buildMaskData(texture: THREE.Texture): MaskData | null {
  if (typeof document === 'undefined' || !texture.image) return null;

  const canvas = document.createElement('canvas');
  canvas.width = 2048;
  canvas.height = 1024;

  const ctx = canvas.getContext('2d', { willReadFrequently: true });
  if (!ctx) return null;

  const image = texture.image;
  if (!image) return null;

  try {
    ctx.drawImage(image as CanvasImageSource, 0, 0, canvas.width, canvas.height);
    return {
      data: ctx.getImageData(0, 0, canvas.width, canvas.height).data,
      width: canvas.width,
      height: canvas.height,
    };
  } catch {
    return null;
  }
}

function sampleEarth(mask: MaskData, lat: number, lon: number) {
  const u = (lon + Math.PI) / (2 * Math.PI);
  const v = 1 - (lat + Math.PI / 2) / Math.PI;

  const getVal = (dx = 0, dy = 0) => {
    const x = Math.max(0, Math.min(mask.width - 1, Math.floor((u % 1) * mask.width) + dx));
    const y = Math.max(0, Math.min(mask.height - 1, Math.floor(v * mask.height) + dy));
    const idx = (y * mask.width + x) * 4;
    return mask.data[idx] ?? 0;
  };

  // Specular map: Oceans are bright (white), Land is dark (black)
  const val = getVal(0, 0);
  const isLand = val < 90; // Threshold for land

  let isCoastline = false;

  // Simple edge detection: if land, check if any neighbor 2 pixels away is water
  if (isLand) {
    const n1 = getVal(2, 0) < 90;
    const n2 = getVal(-2, 0) < 90;
    const n3 = getVal(0, 2) < 90;
    const n4 = getVal(0, -2) < 90;

    if (!n1 || !n2 || !n3 || !n4) {
      isCoastline = true;
    }
  }

  return { isLand, isCoastline };
}

export function GlobeWireframe() {
  const groupRef = useRef<THREE.Group>(null);

  useFrame(() => {
    if (groupRef.current) {
      groupRef.current.rotation.y += ROTATION_SPEED * 0.8;
      groupRef.current.rotation.x += ROTATION_SPEED * 0.1;
    }
  });

  return (
    <group ref={groupRef}>
      <mesh>
        {/* Classic Lat/Lon Radar Grid */}
        <sphereGeometry args={[GLOBE_RADIUS * 0.998, 36, 36]} />
        <meshBasicMaterial
          color="#003388"
          wireframe
          transparent
          opacity={0.12}
          blending={THREE.AdditiveBlending}
          depthWrite={false}
        />
      </mesh>
    </group>
  );
}

export function GlobeAtmosphere() {
  return (
    <group>
      {/* Deep black inner core */}
      <mesh>
        <sphereGeometry args={[GLOBE_RADIUS * 0.98, 64, 64]} />
        <meshBasicMaterial color="#010308" />
      </mesh>

      {/* Surface glow */}
      <mesh>
        <sphereGeometry args={[GLOBE_RADIUS * 1.015, 64, 64]} />
        <meshBasicMaterial
          color="#0055ff"
          transparent
          opacity={0.05}
          side={THREE.BackSide}
          blending={THREE.AdditiveBlending}
          depthWrite={false}
        />
      </mesh>

      {/* Atmospheric neon corona */}
      <mesh>
        <sphereGeometry args={[GLOBE_RADIUS * 1.18, 64, 64]} />
        <meshBasicMaterial
          color="#00aaff"
          transparent
          opacity={0.06}
          side={THREE.BackSide}
          blending={THREE.AdditiveBlending}
          depthWrite={false}
        />
      </mesh>
    </group>
  );
}

export function GlobePoints() {
  const groupRef = useRef<THREE.Group>(null);

  // Using specular map: no clouds, precise continental geometry
  const earthTexture = useLoader(
    THREE.TextureLoader,
    'https://raw.githubusercontent.com/mrdoob/three.js/master/examples/textures/planets/earth_specular_2048.jpg',
  );

  const { landPositions, landColors, shellPositions, shellColors } = useMemo(() => {
    const mask = buildMaskData(earthTexture);

    const landPos: number[] = [];
    const landCol: number[] = [];
    const shellPos: number[] = [];
    const shellCol: number[] = [];

    const goldenAngle = Math.PI * (3 - Math.sqrt(5));

    for (let i = 0; i < TOTAL_POINTS; i += 1) {
      const y = 1 - (i / (TOTAL_POINTS - 1)) * 2;
      const radiusAtY = Math.sqrt(Math.max(0, 1 - y * y));
      const theta = goldenAngle * i;

      const x = Math.cos(theta) * radiusAtY;
      const z = Math.sin(theta) * radiusAtY;

      const lat = Math.asin(y);
      const lon = Math.atan2(z, x);

      const px = x * GLOBE_RADIUS;
      const py = y * GLOBE_RADIUS;
      const pz = z * GLOBE_RADIUS;

      const sampled = mask
        ? sampleEarth(mask, lat, lon)
        : { isLand: Math.random() > 0.58, isCoastline: false };

      if (sampled.isLand) {
        landPos.push(px, py, pz);

        if (sampled.isCoastline) {
          // Brilliant Cyan for coastlines (Edge detection)
          landCol.push(0.0, 0.9, 1.0);
        } else {
          // Deep holographic blue for inland
          landCol.push(0.0, 0.2, 0.5);
        }
      } else {
        if (Math.random() > 0.12) continue; // Extremely sparse ocean points
        shellPos.push(px * 0.995, py * 0.995, pz * 0.995);
        shellCol.push(0.0, 0.05, 0.15);
      }
    }

    return {
      landPositions: new Float32Array(landPos),
      landColors: new Float32Array(landCol),
      shellPositions: new Float32Array(shellPos),
      shellColors: new Float32Array(shellCol),
    };
  }, [earthTexture]);

  useFrame(() => {
    if (groupRef.current) {
      groupRef.current.rotation.y += ROTATION_SPEED;
    }
  });

  return (
    <group ref={groupRef}>
      <points>
        <bufferGeometry>
          <bufferAttribute attach="attributes-position" args={[shellPositions, 3]} />
          <bufferAttribute attach="attributes-color" args={[shellColors, 3]} />
        </bufferGeometry>
        <pointsMaterial
          size={0.003}
          vertexColors
          transparent
          opacity={0.3}
          blending={THREE.AdditiveBlending}
          depthWrite={false}
          sizeAttenuation
        />
      </points>

      <points>
        <bufferGeometry>
          <bufferAttribute attach="attributes-position" args={[landPositions, 3]} />
          <bufferAttribute attach="attributes-color" args={[landColors, 3]} />
        </bufferGeometry>
        <pointsMaterial
          size={0.0035} // Fine points for high definition
          vertexColors
          transparent
          opacity={0.95}
          blending={THREE.AdditiveBlending}
          depthWrite={false}
          sizeAttenuation
        />
      </points>
    </group>
  );
}

function Marker({ city, index, isActive, onClick }: { city: City; index: number; isActive: boolean; onClick: (e: any) => void }) {
  const groupRef = useRef<THREE.Group>(null);
  const ringRef = useRef<THREE.Mesh>(null);

  const markerPos = useMemo(() => latLonToVector3(city.lat, city.lng, GLOBE_RADIUS * 1.002), [city.lat, city.lng]);

  // Déterminer un faux ping réaliste basé sur la ville pour l'illusion HUD
  const ping = useMemo(() => 12 + (city.name.length * 7) % 80, [city.name]);

  useEffect(() => {
    if (groupRef.current) {
      groupRef.current.position.copy(markerPos);
      groupRef.current.lookAt(new THREE.Vector3(0, 0, 0));
    }
  }, [markerPos]);

  useFrame((state) => {
    if (!ringRef.current) return;
    const t = state.clock.elapsedTime * 1.8 + index;
    const pulse = isActive ? 1 + Math.sin(t * 3) * 0.4 : 1 + Math.sin(t) * 0.3;
    ringRef.current.scale.setScalar(pulse);

    if (ringRef.current.material instanceof THREE.MeshBasicMaterial) {
      ringRef.current.material.opacity = isActive
        ? Math.max(0, (1 - Math.sin(t * 3))) * 0.6
        : Math.max(0, (1 - Math.sin(t))) * 0.4;
    }
  });

  return (
    <group
      ref={groupRef}
      onClick={onClick}
      onPointerOver={(e) => { e.stopPropagation(); document.body.style.cursor = 'pointer'; }}
      onPointerOut={(e) => { e.stopPropagation(); document.body.style.cursor = 'auto'; }}
    >
      {/* Zone de clic invisible élargie */}
      <mesh visible={false} scale={4}>
        <sphereGeometry args={[0.02, 8, 8]} />
        <meshBasicMaterial />
      </mesh>

      {/* Intense bright core */}
      <mesh name="hub-core">
        <sphereGeometry args={[0.012, 16, 16]} />
        <meshBasicMaterial color={isActive ? "#ffffff" : "#aaffff"} blending={THREE.AdditiveBlending} depthWrite={false} />
      </mesh>
      {/* Cyan halo */}
      <mesh name="hub-glow" scale={isActive ? 3.5 : 2.5}>
        <sphereGeometry args={[0.014, 16, 16]} />
        <meshBasicMaterial color="#00E5FF" transparent opacity={isActive ? 0.6 : 0.35} blending={THREE.AdditiveBlending} depthWrite={false} />
      </mesh>
      {/* Radar pulse ring */}
      <mesh ref={ringRef} name="pulse-ring">
        <ringGeometry args={[0.02, 0.024, 32]} />
        <meshBasicMaterial color="#00E5FF" transparent opacity={isActive ? 0.8 : 0.5} blending={THREE.AdditiveBlending} side={THREE.DoubleSide} depthWrite={false} />
      </mesh>

      {/* HUD Info Card Holographique */}
      {isActive && (
        <Html center zIndexRange={[100, 0]}>
          <div style={{
            background: 'rgba(2, 8, 19, 0.85)',
            border: '1px solid rgba(0, 229, 255, 0.3)',
            boxShadow: '0 0 15px rgba(0, 229, 255, 0.15), inset 0 0 20px rgba(0, 229, 255, 0.05)',
            backdropFilter: 'blur(8px)',
            borderRadius: '8px',
            padding: '12px 16px',
            color: '#fff',
            fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace',
            minWidth: '160px',
            transform: 'translate3d(20px, -50%, 0)',
            pointerEvents: 'none',
            userSelect: 'none',
          }}>
            <div style={{ fontSize: '10px', color: '#00E5FF', fontWeight: 'bold', letterSpacing: '0.1em', marginBottom: '6px' }}>// NODE_ACTIVE</div>
            <div style={{ fontSize: '18px', fontWeight: '900', textTransform: 'uppercase', marginBottom: '10px', letterSpacing: '-0.02em', textShadow: '0 0 10px rgba(0,229,255,0.3)' }}>{city.name}</div>
            <div style={{ display: 'flex', justifyContent: 'space-between', borderTop: '1px solid rgba(255,255,255,0.1)', paddingTop: '10px', fontSize: '12px' }}>
              <span style={{ color: '#64748B', fontWeight: 600 }}>LATENCY</span>
              <span style={{ color: '#00E5FF', fontWeight: 'bold' }}>{ping} ms</span>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', paddingTop: '6px', fontSize: '12px' }}>
              <span style={{ color: '#64748B', fontWeight: 600 }}>STATUS</span>
              <span style={{ color: '#10B981', fontWeight: 'bold', textShadow: '0 0 8px rgba(16,185,129,0.4)' }}>SECURE</span>
            </div>
          </div>
        </Html>
      )}
    </group>
  );
}

export function GlobeMarkers() {
  const groupRef = useRef<THREE.Group>(null);
  const [activeIndex, setActiveIndex] = useState<number | null>(null);

  useFrame(() => {
    if (!groupRef.current) return;
    groupRef.current.rotation.y += ROTATION_SPEED;
  });

  return (
    <group
      ref={groupRef}
      onPointerMissed={() => setActiveIndex(null)}
    >
      {CITIES.map((city, i) => (
        <Marker
          key={`${city.name}-${i}`}
          city={city}
          index={i}
          isActive={activeIndex === i}
          onClick={(e) => {
            e.stopPropagation();
            setActiveIndex(activeIndex === i ? null : i);
          }}
        />
      ))}
    </group>
  );
}

type ArcData = {
  curve: THREE.Curve<THREE.Vector3>;
  positions: Float32Array;
};

export function GlobeArcs() {
  const groupRef = useRef<THREE.Group>(null);

  const arcs = useMemo<ArcData[]>(() => {
    // A more globally distributed and complex network of connections
    const connections: [number, number][] = [
      [5, 3], // NY - London
      [5, 10], // NY - Frankfurt
      [6, 0], // SF - Tokyo
      [0, 12], // Tokyo - HK
      [12, 1], // HK - Singapore
      [1, 8], // Singapore - Sydney
      [2, 11], // Dubai - Mumbai
      [11, 1], // Mumbai - Singapore
      [3, 4], // London - Paris
      [4, 15], // Paris - Amsterdam
      [7, 5], // Sao Paulo - NY
      [7, 9], // Sao Paulo - Joburg
      [9, 2], // Joburg - Dubai
      [14, 6], // Toronto - SF
      [13, 0], // Seoul - Tokyo
      [2, 10], // Dubai - Frankfurt
      [3, 2], // London - Dubai
      [0, 1], // Tokyo - Singapore
      [5, 6], // NY - SF
    ];

    return connections
      .filter(([a, b]) => Boolean(CITIES[a] && CITIES[b]))
      .map(([a, b]) => {
        const from = CITIES[a];
        const to = CITIES[b];

        const p1 = latLonToVector3(from.lat, from.lng, GLOBE_RADIUS);
        const p2 = latLonToVector3(to.lat, to.lng, GLOBE_RADIUS);
        const curve = createArcCurve(p1, p2, GLOBE_RADIUS);
        const points = curve.getPoints(64);
        const positions = new Float32Array(points.flatMap((p) => [p.x, p.y, p.z]));

        return { curve, positions };
      });
  }, []);

  useFrame(() => {
    if (groupRef.current) {
      groupRef.current.rotation.y += ROTATION_SPEED;
    }
  });

  return (
    <group ref={groupRef}>
      {arcs.map((arc, i) => (
        <group key={i}>
          {/* Subtle dashed line for a digital radar feel */}
          <line>
            <bufferGeometry onUpdate={(self) => self.computeLineDistances()}>
              <bufferAttribute attach="attributes-position" args={[arc.positions, 3]} />
            </bufferGeometry>
            <lineDashedMaterial
              color="#00aaff"
              transparent
              opacity={0.12}
              dashSize={0.02}
              gapSize={0.02}
              blending={THREE.AdditiveBlending}
              depthWrite={false}
            />
          </line>

          {/* Very faint solid core line */}
          <line>
            <bufferGeometry>
              <bufferAttribute attach="attributes-position" args={[arc.positions, 3]} />
            </bufferGeometry>
            <lineBasicMaterial
              color="#0055ff"
              transparent
              opacity={0.08}
              blending={THREE.AdditiveBlending}
              depthWrite={false}
            />
          </line>

          {/* Comet Data Stream */}
          <DataStream curve={arc.curve} delay={i * 0.4} />
        </group>
      ))}
    </group>
  );
}

function DataStream({ curve, delay }: { curve: THREE.Curve<THREE.Vector3>; delay: number }) {
  const headRef = useRef<THREE.Mesh>(null);
  const tail1Ref = useRef<THREE.Mesh>(null);
  const tail2Ref = useRef<THREE.Mesh>(null);

  useFrame((state) => {
    if (!headRef.current || !tail1Ref.current || !tail2Ref.current) return;

    const speed = 0.2;
    const time = (state.clock.elapsedTime + delay) * speed;

    // Calculate positions slightly offset from each other
    const t1 = time % 1;
    const t2 = Math.max(0, t1 - 0.015);
    const t3 = Math.max(0, t1 - 0.03);

    const p1 = curve.getPoint(t1);
    const p2 = curve.getPoint(t2);
    const p3 = curve.getPoint(t3);

    headRef.current.position.copy(p1);
    tail1Ref.current.position.copy(p2);
    tail2Ref.current.position.copy(p3);

    // Fade in and out at the start and end of the arc
    const intensity = Math.sin(t1 * Math.PI);

    if (headRef.current.material instanceof THREE.MeshBasicMaterial) {
      headRef.current.material.opacity = intensity * 0.95;
    }
    if (tail1Ref.current.material instanceof THREE.MeshBasicMaterial) {
      tail1Ref.current.material.opacity = intensity * 0.6;
    }
    if (tail2Ref.current.material instanceof THREE.MeshBasicMaterial) {
      tail2Ref.current.material.opacity = intensity * 0.25;
    }
  });

  return (
    <group>
      {/* Bright comet head */}
      <mesh ref={headRef}>
        <sphereGeometry args={[0.007, 12, 12]} />
        <meshBasicMaterial
          color="#ffffff"
          transparent
          blending={THREE.AdditiveBlending}
          depthWrite={false}
        />
      </mesh>

      {/* Cyan mid tail */}
      <mesh ref={tail1Ref}>
        <sphereGeometry args={[0.012, 12, 12]} />
        <meshBasicMaterial
          color="#00E5FF"
          transparent
          blending={THREE.AdditiveBlending}
          depthWrite={false}
        />
      </mesh>

      {/* Dark blue trailing edge */}
      <mesh ref={tail2Ref}>
        <sphereGeometry args={[0.016, 12, 12]} />
        <meshBasicMaterial
          color="#0055ff"
          transparent
          blending={THREE.AdditiveBlending}
          depthWrite={false}
        />
      </mesh>
    </group>
  );
}

export function GlobeCloud() {
  const pointsRef = useRef<THREE.Points>(null);

  const { positions, colors } = useMemo(() => {
    const pos = new Float32Array(CLOUD_COUNT * 3);
    const col = new Float32Array(CLOUD_COUNT * 3);

    for (let i = 0; i < CLOUD_COUNT; i += 1) {
      const phi = Math.acos(2 * Math.random() - 1);
      const theta = 2 * Math.PI * Math.random();
      const r = GLOBE_RADIUS * (1.12 + Math.random() * 0.1); // Pushed further out

      pos[i * 3] = r * Math.sin(phi) * Math.cos(theta);
      pos[i * 3 + 1] = r * Math.cos(phi);
      pos[i * 3 + 2] = r * Math.sin(phi) * Math.sin(theta);

      col[i * 3] = 0.0;
      col[i * 3 + 1] = 0.4 + Math.random() * 0.4;
      col[i * 3 + 2] = 0.8 + Math.random() * 0.2;
    }

    return { positions: pos, colors: col };
  }, []);

  useFrame(() => {
    if (pointsRef.current) {
      // Counter-rotation for parallax cinematic feel
      pointsRef.current.rotation.y -= ROTATION_SPEED * 0.4;
      pointsRef.current.rotation.z += ROTATION_SPEED * 0.1;
    }
  });

  return (
    <points ref={pointsRef}>
      <bufferGeometry>
        <bufferAttribute attach="attributes-position" args={[positions, 3]} />
        <bufferAttribute attach="attributes-color" args={[colors, 3]} />
      </bufferGeometry>
      <pointsMaterial
        size={0.004}
        vertexColors
        transparent
        opacity={0.2}
        blending={THREE.AdditiveBlending}
        depthWrite={false}
        sizeAttenuation
      />
    </points>
  );
}
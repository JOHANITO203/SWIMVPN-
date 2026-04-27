import React, { useEffect, useMemo, useRef, useState } from 'react';
import * as THREE from 'three';
import { Html } from '@react-three/drei';
import { useFrame, useLoader } from '@react-three/fiber';
import { CITIES, createArcCurve, latLonToVector3 } from './globeUtils';

const GLOBE_RADIUS = 1;
const ROTATION_SPEED = 0.00045;

const LAND_SAMPLE_COUNT = 145000;
const SHELL_SAMPLE_COUNT = 26000;
const CLOUD_COUNT = 2200;

const HUD_OFFSETS: [number, number, number][] = [
  [0.22, 0.14, 0],
  [-0.26, 0.12, 0],
  [0.22, -0.04, 0],
  [-0.26, -0.14, 0],
];

const panelStyle: React.CSSProperties = {
  minWidth: 118,
  padding: '8px 10px',
  border: '1px solid rgba(0, 229, 255, 0.26)',
  background: 'linear-gradient(180deg, rgba(3,12,22,0.92) 0%, rgba(1,7,14,0.82) 100%)',
  boxShadow: '0 0 0 1px rgba(0,229,255,0.06) inset, 0 0 18px rgba(0,170,255,0.12)',
  borderRadius: 4,
  color: '#9cecff',
  fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace',
  textTransform: 'uppercase',
  letterSpacing: '0.08em',
  pointerEvents: 'none',
  userSelect: 'none',
  backdropFilter: 'blur(4px)',
};

const rowStyle: React.CSSProperties = {
  display: 'flex',
  justifyContent: 'space-between',
  gap: 8,
  fontSize: 8,
  lineHeight: 1.4,
  opacity: 0.9,
};

const titleStyle: React.CSSProperties = {
  fontSize: 10,
  fontWeight: 700,
  color: '#50e6ff',
  marginBottom: 6,
  textShadow: '0 0 10px rgba(0,229,255,0.25)',
};

const dividerStyle: React.CSSProperties = {
  height: 1,
  margin: '6px 0',
  background:
    'linear-gradient(90deg, rgba(0,229,255,0) 0%, rgba(0,229,255,0.45) 45%, rgba(0,229,255,0) 100%)',
};

type MaskData = {
  data: Uint8ClampedArray;
  width: number;
  height: number;
};

function buildMaskData(texture: THREE.Texture): MaskData | null {
  if (typeof document === 'undefined' || !texture.image) return null;

  const canvas = document.createElement('canvas');
  canvas.width = 1024;
  canvas.height = 512;

  const ctx = canvas.getContext('2d');
  if (!ctx) return null;

  const image = texture.image;
  if (
    !image ||
    !(
      image instanceof HTMLImageElement ||
      image instanceof HTMLCanvasElement ||
      image instanceof ImageBitmap ||
      image instanceof HTMLVideoElement
    )
  ) {
    return null;
  }

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

  const x = Math.max(0, Math.min(mask.width - 1, Math.floor((u % 1) * mask.width)));
  const y = Math.max(0, Math.min(mask.height - 1, Math.floor(v * mask.height)));
  const idx = (y * mask.width + x) * 4;

  const r = mask.data[idx] ?? 0;
  const g = mask.data[idx + 1] ?? 0;
  const b = mask.data[idx + 2] ?? 0;

  const brightness = (r + g + b) / 3;
  const isLand = brightness > 72;

  return { brightness, isLand };
}

export function GlobeAtmosphere() {
  return (
    <group>
      <mesh>
        <sphereGeometry args={[GLOBE_RADIUS * 1.018, 64, 64]} />
        <meshBasicMaterial
          color="#1ec8ff"
          transparent
          opacity={0.08}
          side={THREE.BackSide}
          blending={THREE.AdditiveBlending}
          depthWrite={false}
        />
      </mesh>

      <mesh>
        <sphereGeometry args={[GLOBE_RADIUS * 1.09, 64, 64]} />
        <meshBasicMaterial
          color="#003b73"
          transparent
          opacity={0.06}
          side={THREE.BackSide}
          blending={THREE.AdditiveBlending}
          depthWrite={false}
        />
      </mesh>

      <mesh rotation={[Math.PI / 2, 0, 0]}>
        <torusGeometry args={[GLOBE_RADIUS * 1.12, 0.0012, 8, 220]} />
        <meshBasicMaterial
          color="#34d7ff"
          transparent
          opacity={0.06}
          blending={THREE.AdditiveBlending}
          depthWrite={false}
        />
      </mesh>
    </group>
  );
}

export function GlobePoints() {
  const groupRef = useRef<THREE.Group>(null);

  const earthTexture = useLoader(
    THREE.TextureLoader,
    'https://raw.githubusercontent.com/mrdoob/three.js/master/examples/textures/planets/earth_atmos_2048.jpg',
  );

  const { landPositions, landColors, shellPositions, shellColors } = useMemo(() => {
    const mask = buildMaskData(earthTexture);

    const landPos: number[] = [];
    const landCol: number[] = [];
    const shellPos: number[] = [];
    const shellCol: number[] = [];

    const goldenAngle = Math.PI * (3 - Math.sqrt(5));
    const total = LAND_SAMPLE_COUNT + SHELL_SAMPLE_COUNT;

    for (let i = 0; i < total; i += 1) {
      const y = 1 - (i / (total - 1)) * 2;
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
        : { brightness: Math.random() * 255, isLand: Math.random() > 0.58 };

      if (sampled.isLand) {
        landPos.push(px, py, pz);

        const spark = Math.random() > 0.992;
        if (spark) {
          landCol.push(0.6, 0.95, 1);
        } else {
          const lift = THREE.MathUtils.clamp(
            THREE.MathUtils.mapLinear(sampled.brightness, 72, 255, 0, 1),
            0,
            1,
          );

          landCol.push(0.03 + lift * 0.05, 0.36 + lift * 0.25, 0.72 + lift * 0.2);
        }
      } else {
        if (Math.random() > 0.56) continue;

        shellPos.push(px * 0.998, py * 0.998, pz * 0.998);
        shellCol.push(0.015, 0.09, 0.22);
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
          size={0.0031}
          vertexColors
          transparent
          opacity={0.52}
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
          size={0.0042}
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

type HubCity = {
  name: string;
  lat: number;
  lng: number;
  ping: number;
  traffic: string;
  status: string;
  featured: boolean;
  offset: [number, number, number];
};

function HubLabel({ city }: { city: HubCity }) {
  return (
    <Html transform distanceFactor={1.25} position={city.offset}>
      <div style={panelStyle}>
        <div style={titleStyle}>{city.name} HUB</div>
        <div style={rowStyle}>
          <span>PING</span>
          <strong>{city.ping} ms</strong>
        </div>
        <div style={rowStyle}>
          <span>TRAFFIC</span>
          <strong>{city.traffic}</strong>
        </div>
        <div style={dividerStyle} />
        <div style={rowStyle}>
          <span>STATUS</span>
          <strong>{city.status}</strong>
        </div>
      </div>
    </Html>
  );
}

export function GlobeMarkers() {
  const groupRef = useRef<THREE.Group>(null);
  const [focusIndex, setFocusIndex] = useState(0);

  const cityData = useMemo(() => {
    return CITIES.slice(0, 8).map((city, i) => ({
      ...city,
      featured: i < 4,
    }));
  }, []);

  useEffect(() => {
    const timer = window.setInterval(() => {
      setFocusIndex((prev) => (prev + 1) % Math.min(4, cityData.length || 1));
    }, 2800);

    return () => window.clearInterval(timer);
  }, [cityData.length]);

  useFrame((state) => {
    if (!groupRef.current) return;

    groupRef.current.rotation.y += ROTATION_SPEED;

    groupRef.current.children.forEach((child, i) => {
      const ring = child.getObjectByName('pulse-ring');
      const glow = child.getObjectByName('hub-glow');

      const activeBoost = i === focusIndex ? 1 : 0;
      const pulse = 1 + Math.sin(state.clock.elapsedTime * 2.4 + i) * 0.08 + activeBoost * 0.12;

      if (ring) {
        ring.scale.setScalar(pulse);
      }

      if (glow instanceof THREE.Mesh && glow.material instanceof THREE.MeshBasicMaterial) {
        glow.material.opacity = i === focusIndex ? 0.34 : 0.18;
      }
    });
  });

  return (
    <group ref={groupRef}>
      {cityData.map((city, i) => {
        const pos = latLonToVector3(city.lat, city.lng, GLOBE_RADIUS * 1.008);

        return (
          <group key={`${city.name}-${i}`} position={pos}>
            <mesh name="hub-core">
              <sphereGeometry args={[0.0115, 12, 12]} />
              <meshBasicMaterial
                color="#7ef3ff"
                transparent
                opacity={0.95}
                blending={THREE.AdditiveBlending}
                depthWrite={false}
              />
            </mesh>

            <mesh name="hub-glow" scale={2.3}>
              <sphereGeometry args={[0.0145, 12, 12]} />
              <meshBasicMaterial
                color="#00b8ff"
                transparent
                opacity={0.18}
                blending={THREE.AdditiveBlending}
                depthWrite={false}
              />
            </mesh>

            <mesh name="pulse-ring" rotation={[Math.PI / 2, 0, 0]}>
              <ringGeometry args={[0.017, 0.021, 24]} />
              <meshBasicMaterial
                color="#5ce8ff"
                transparent
                opacity={0.55}
                blending={THREE.AdditiveBlending}
                side={THREE.DoubleSide}
                depthWrite={false}
              />
            </mesh>
          </group>
        );
      })}
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
    const connections: [number, number][] = [
      [0, 1],
      [1, 2],
      [2, 3],
      [3, 4],
      [4, 5],
      [5, 6],
      [6, 7],
      [7, 0],
      [0, 4],
      [1, 5],
      [2, 6],
    ];

    return connections
      .filter(([a, b]) => Boolean(CITIES[a] && CITIES[b]))
      .map(([a, b]) => {
        const from = CITIES[a];
        const to = CITIES[b];

        const p1 = latLonToVector3(from.lat, from.lng, GLOBE_RADIUS);
        const p2 = latLonToVector3(to.lat, to.lng, GLOBE_RADIUS);
        const curve = createArcCurve(p1, p2, GLOBE_RADIUS);
        const points = curve.getPoints(72);
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
          <line>
            <bufferGeometry>
              <bufferAttribute attach="attributes-position" args={[arc.positions, 3]} />
            </bufferGeometry>
            <lineBasicMaterial
              color="#7adfff"
              transparent
              opacity={0.18}
              blending={THREE.AdditiveBlending}
              depthWrite={false}
            />
          </line>

          <DataPacket curve={arc.curve} delay={i * 0.35} />
        </group>
      ))}
    </group>
  );
}

function DataPacket({ curve, delay }: { curve: THREE.Curve<THREE.Vector3>; delay: number }) {
  const meshRef = useRef<THREE.Mesh>(null);

  useFrame((state) => {
    if (!meshRef.current) return;

    const t = ((state.clock.elapsedTime + delay) * 0.22) % 1;
    const point = curve.getPoint(t);

    meshRef.current.position.copy(point);

    if (meshRef.current.material instanceof THREE.MeshBasicMaterial) {
      meshRef.current.material.opacity = 0.35 + Math.sin(t * Math.PI) * 0.45;
    }
  });

  return (
    <mesh ref={meshRef}>
      <sphereGeometry args={[0.0065, 10, 10]} />
      <meshBasicMaterial
        color="#a9f8ff"
        transparent
        opacity={0.75}
        blending={THREE.AdditiveBlending}
        depthWrite={false}
      />
    </mesh>
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
      const r = GLOBE_RADIUS * (1.08 + Math.random() * 0.08);

      const x = r * Math.sin(phi) * Math.cos(theta);
      const y = r * Math.cos(phi);
      const z = r * Math.sin(phi) * Math.sin(theta);

      pos[i * 3] = x;
      pos[i * 3 + 1] = y;
      pos[i * 3 + 2] = z;

      const lift = Math.random();
      col[i * 3] = 0.02;
      col[i * 3 + 1] = 0.15 + lift * 0.1;
      col[i * 3 + 2] = 0.35 + lift * 0.12;
    }

    return { positions: pos, colors: col };
  }, []);

  useFrame(() => {
    if (pointsRef.current) {
      pointsRef.current.rotation.y -= ROTATION_SPEED * 0.35;
    }
  });

  return (
    <points ref={pointsRef}>
      <bufferGeometry>
        <bufferAttribute attach="attributes-position" args={[positions, 3]} />
        <bufferAttribute attach="attributes-color" args={[colors, 3]} />
      </bufferGeometry>
      <pointsMaterial
        size={0.0026}
        vertexColors
        transparent
        opacity={0.16}
        blending={THREE.AdditiveBlending}
        depthWrite={false}
        sizeAttenuation
      />
    </points>
  );
}
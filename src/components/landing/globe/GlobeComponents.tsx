import React, { useEffect, useMemo, useRef } from 'react';
import * as THREE from 'three';
import { useFrame, useLoader } from '@react-three/fiber';
import { CITIES, createArcCurve, latLonToVector3 } from './globeUtils';

const GLOBE_RADIUS = 1;
const ROTATION_SPEED = 0.00035;

// Lowered point counts for a more minimalist "plexus" dot matrix aesthetic
const LAND_SAMPLE_COUNT = 15000;
const SHELL_SAMPLE_COUNT = 2000;
const CLOUD_COUNT = 600;

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

export function GlobeWireframe() {
  const groupRef = useRef<THREE.Group>(null);

  useFrame(() => {
    if (groupRef.current) {
      groupRef.current.rotation.y += ROTATION_SPEED * 0.8;
      groupRef.current.rotation.x += ROTATION_SPEED * 0.2;
    }
  });

  return (
    <group ref={groupRef}>
      <mesh>
        {/* Holographic structural plexus mesh */}
        <icosahedronGeometry args={[GLOBE_RADIUS * 0.998, 14]} />
        <meshBasicMaterial
          color="#0044aa"
          wireframe
          transparent
          opacity={0.06}
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
      {/* Deep black inner core for cinematic contrast */}
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
          opacity={0.06}
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
          opacity={0.08}
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

        const lift = THREE.MathUtils.clamp(
          THREE.MathUtils.mapLinear(sampled.brightness, 72, 255, 0, 1),
          0,
          1,
        );

        // Electric cyan/blue coloring
        const baseR = 0.0, baseG = 0.5, baseB = 0.8;
        const highR = 0.0, highG = 0.9, highB = 1.0;

        landCol.push(
          THREE.MathUtils.lerp(baseR, highR, lift),
          THREE.MathUtils.lerp(baseG, highG, lift),
          THREE.MathUtils.lerp(baseB, highB, lift)
        );
      } else {
        if (Math.random() > 0.4) continue;
        shellPos.push(px * 0.995, py * 0.995, pz * 0.995);
        shellCol.push(0.0, 0.1, 0.2);
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
          size={0.004}
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
          size={0.007} // Larger dots for minimalist look
          vertexColors
          transparent
          opacity={0.9}
          blending={THREE.AdditiveBlending}
          depthWrite={false}
          sizeAttenuation
        />
      </points>
    </group>
  );
}

function Marker({ lat, lng, index }: { lat: number; lng: number; index: number }) {
  const groupRef = useRef<THREE.Group>(null);
  const ringRef = useRef<THREE.Mesh>(null);

  const markerPos = useMemo(() => latLonToVector3(lat, lng, GLOBE_RADIUS * 1.002), [lat, lng]);

  useEffect(() => {
    if (groupRef.current) {
      groupRef.current.position.copy(markerPos);
      groupRef.current.lookAt(new THREE.Vector3(0, 0, 0));
    }
  }, [markerPos]);

  useFrame((state) => {
    if (!ringRef.current) return;
    const t = state.clock.elapsedTime * 1.8 + index;
    const pulse = 1 + Math.sin(t) * 0.3;
    ringRef.current.scale.setScalar(pulse);

    if (ringRef.current.material instanceof THREE.MeshBasicMaterial) {
      ringRef.current.material.opacity = Math.max(0, (1 - Math.sin(t))) * 0.4;
    }
  });

  return (
    <group ref={groupRef}>
      {/* Intense bright core */}
      <mesh name="hub-core">
        <sphereGeometry args={[0.012, 16, 16]} />
        <meshBasicMaterial color="#ffffff" blending={THREE.AdditiveBlending} depthWrite={false} />
      </mesh>
      {/* Cyan halo */}
      <mesh name="hub-glow" scale={2.5}>
        <sphereGeometry args={[0.014, 16, 16]} />
        <meshBasicMaterial color="#00E5FF" transparent opacity={0.35} blending={THREE.AdditiveBlending} depthWrite={false} />
      </mesh>
      {/* Radar pulse ring */}
      <mesh ref={ringRef} name="pulse-ring">
        <ringGeometry args={[0.02, 0.024, 32]} />
        <meshBasicMaterial color="#00E5FF" transparent opacity={0.5} blending={THREE.AdditiveBlending} side={THREE.DoubleSide} depthWrite={false} />
      </mesh>
    </group>
  );
}

export function GlobeMarkers() {
  const groupRef = useRef<THREE.Group>(null);

  useFrame(() => {
    if (!groupRef.current) return;
    groupRef.current.rotation.y += ROTATION_SPEED;
  });

  return (
    <group ref={groupRef}>
      {CITIES.slice(0, 10).map((city, i) => (
        <Marker key={`${city.name}-${i}`} lat={city.lat} lng={city.lng} index={i} />
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
    const connections: [number, number][] = [
      [0, 1], [1, 2], [2, 3], [3, 4], [4, 5], [5, 6], [6, 7], [7, 0],
      [0, 4], [1, 5], [2, 6], [3, 8], [4, 9], [6, 1],
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
          {/* Base connection line */}
          <line>
            <bufferGeometry>
              <bufferAttribute attach="attributes-position" args={[arc.positions, 3]} />
            </bufferGeometry>
            <lineBasicMaterial
              color="#00aaff"
              transparent
              opacity={0.12}
              blending={THREE.AdditiveBlending}
              depthWrite={false}
            />
          </line>
          <DataPacket curve={arc.curve} delay={i * 0.3} />
        </group>
      ))}
    </group>
  );
}

function DataPacket({ curve, delay }: { curve: THREE.Curve<THREE.Vector3>; delay: number }) {
  const meshRef = useRef<THREE.Mesh>(null);
  const glowRef = useRef<THREE.Mesh>(null);

  useFrame((state) => {
    if (!meshRef.current || !glowRef.current) return;

    const t = ((state.clock.elapsedTime + delay) * 0.12) % 1;
    const point = curve.getPoint(t);

    meshRef.current.position.copy(point);
    glowRef.current.position.copy(point);

    const intensity = Math.sin(t * Math.PI);

    if (meshRef.current.material instanceof THREE.MeshBasicMaterial) {
      meshRef.current.material.opacity = intensity * 0.9;
    }
    if (glowRef.current.material instanceof THREE.MeshBasicMaterial) {
      glowRef.current.material.opacity = intensity * 0.4;
    }
  });

  return (
    <group>
      <mesh ref={meshRef}>
        <sphereGeometry args={[0.008, 12, 12]} />
        <meshBasicMaterial
          color="#ffffff"
          transparent
          opacity={0.8}
          blending={THREE.AdditiveBlending}
          depthWrite={false}
        />
      </mesh>
      <mesh ref={glowRef}>
        <sphereGeometry args={[0.016, 12, 12]} />
        <meshBasicMaterial
          color="#00E5FF"
          transparent
          opacity={0.4}
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
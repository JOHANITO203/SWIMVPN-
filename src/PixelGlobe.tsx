import { useMemo, useRef, useState } from 'react';
import { Canvas, useFrame, useThree } from '@react-three/fiber';
import {
  Float,
  PerspectiveCamera,
  PointMaterial,
  Points,
  Stars
} from '@react-three/drei';
import * as THREE from 'three';
import { AnimatePresence, motion } from 'motion/react';

type City = {
  id: number;
  name: string;
  lat: number;
  lng: number;
  traffic: number;
};

const CITIES: City[] = [
  { id: 1, name: 'London', lat: 51.5074, lng: -0.1278, traffic: 0.8 },
  { id: 2, name: 'New York', lat: 40.7128, lng: -74.006, traffic: 0.9 },
  { id: 3, name: 'Tokyo', lat: 35.6762, lng: 139.6503, traffic: 0.7 },
  { id: 4, name: 'Paris', lat: 48.8566, lng: 2.3522, traffic: 0.6 },
  { id: 5, name: 'Singapore', lat: 1.3521, lng: 103.8198, traffic: 0.85 },
  { id: 6, name: 'Sydney', lat: -33.8688, lng: 151.2093, traffic: 0.4 },
  { id: 7, name: 'Berlin', lat: 52.52, lng: 13.405, traffic: 0.5 },
  { id: 8, name: 'San Francisco', lat: 37.7749, lng: -122.4194, traffic: 0.75 }
];

const COLORS = {
  cyan: '#00f3ff',
  cyanStrong: '#00cfff',
  cyanSoft: '#7df9ff',
  white: '#ffffff'
};

function latLngToVector3(lat: number, lng: number, radius: number) {
  const phi = (90 - lat) * (Math.PI / 180);
  const theta = (lng + 180) * (Math.PI / 180);

  return new THREE.Vector3(
    -radius * Math.sin(phi) * Math.cos(theta),
    radius * Math.cos(phi),
    radius * Math.sin(phi) * Math.sin(theta)
  );
}

function normalizeLng(lng: number) {
  let value = lng;
  while (value < -180) value += 360;
  while (value > 180) value -= 360;
  return value;
}

function isInsideEllipse(
  lat: number,
  lng: number,
  centerLat: number,
  centerLng: number,
  radiusLat: number,
  radiusLng: number,
  tilt = 0
) {
  const normalizedLng = normalizeLng(lng - centerLng);
  const normalizedLat = lat - centerLat;

  const angle = tilt * (Math.PI / 180);
  const cos = Math.cos(angle);
  const sin = Math.sin(angle);

  const x = normalizedLng * cos - normalizedLat * sin;
  const y = normalizedLng * sin + normalizedLat * cos;

  return (x * x) / (radiusLng * radiusLng) + (y * y) / (radiusLat * radiusLat) <= 1;
}

function isLandLike(lat: number, lng: number) {
  const landMasses = [
    // North America
    [48, -105, 28, 48, -18],
    [35, -85, 18, 35, -12],
    [60, -135, 16, 24, -25],

    // South America
    [-15, -60, 34, 20, 15],
    [-35, -70, 20, 12, 5],

    // Europe
    [52, 15, 16, 28, 8],
    [42, 10, 12, 20, -10],

    // Africa
    [5, 20, 34, 26, -8],
    [-25, 25, 20, 20, 5],

    // Asia
    [45, 75, 30, 62, 5],
    [25, 105, 22, 42, -10],
    [58, 105, 18, 45, 0],
    [20, 78, 14, 22, 5],

    // Australia
    [-25, 135, 14, 22, 5],

    // Greenland / Arctic blocks
    [72, -42, 10, 18, 0],

    // Antarctica visual base
    [-78, 0, 8, 180, 0]
  ];

  return landMasses.some(([centerLat, centerLng, radiusLat, radiusLng, tilt]) =>
    isInsideEllipse(lat, lng, centerLat, centerLng, radiusLat, radiusLng, tilt)
  );
}

function createGlobePoints(radius: number, isMobile: boolean) {
  const total = isMobile ? 12000 : 26000;
  const ocean: number[] = [];
  const land: number[] = [];
  const goldenAngle = Math.PI * (3 - Math.sqrt(5));

  for (let i = 0; i < total; i += 1) {
    const y = 1 - (i / (total - 1)) * 2;
    const horizontalRadius = Math.sqrt(1 - y * y);
    const theta = goldenAngle * i;

    const x = Math.cos(theta) * horizontalRadius;
    const z = Math.sin(theta) * horizontalRadius;

    const vector = new THREE.Vector3(x, y, z).multiplyScalar(radius);

    const lat = Math.asin(y) * (180 / Math.PI);
    const lng = Math.atan2(z, x) * (180 / Math.PI);

    const landPoint = isLandLike(lat, lng);

    if (landPoint) {
      land.push(vector.x, vector.y, vector.z);
    } else if (i % 3 === 0) {
      ocean.push(vector.x, vector.y, vector.z);
    }
  }

  return {
    ocean: new Float32Array(ocean),
    land: new Float32Array(land)
  };
}

const PulseRing = ({
  color,
  delay = 0,
  intensity = 1
}: {
  color: string;
  delay?: number;
  intensity?: number;
}) => {
  const meshRef = useRef<THREE.Mesh>(null);

  useFrame((state) => {
    if (!meshRef.current) return;

    const time = state.clock.getElapsedTime() + delay;
    const progress = (Math.sin(time * 2.8) + 1) / 2;
    const scale = 1 + progress * 2.2 * intensity;

    meshRef.current.scale.set(scale, scale, scale);

    if (meshRef.current.material instanceof THREE.MeshBasicMaterial) {
      meshRef.current.material.opacity = Math.max(0, 0.55 * (1 - progress));
    }
  });

  return (
    <mesh ref={meshRef}>
      <ringGeometry args={[0.08, 0.13, 48]} />
      <meshBasicMaterial
        color={color}
        transparent
        opacity={0.45}
        side={THREE.DoubleSide}
        depthWrite={false}
        blending={THREE.AdditiveBlending}
      />
    </mesh>
  );
};

const CityNode = ({
  city,
  radius,
  onSelect
}: {
  city: City;
  radius: number;
  onSelect: (id: number) => void;
}) => {
  const position = useMemo(
    () => latLngToVector3(city.lat, city.lng, radius + 0.04),
    [city.lat, city.lng, radius]
  );

  const quaternion = useMemo(() => {
    const normal = position.clone().normalize();
    const q = new THREE.Quaternion();
    q.setFromUnitVectors(new THREE.Vector3(0, 0, 1), normal);
    return q;
  }, [position]);

  const nodeSize = 0.045 + city.traffic * 0.035;

  return (
    <group position={position} quaternion={quaternion}>
      <mesh onClick={() => onSelect(city.id)}>
        <sphereGeometry args={[nodeSize, 24, 24]} />
        <meshBasicMaterial
          color={COLORS.white}
          transparent
          opacity={0.96}
          depthWrite={false}
        />
      </mesh>

      <mesh>
        <sphereGeometry args={[nodeSize * 2.5, 24, 24]} />
        <meshBasicMaterial
          color={COLORS.cyan}
          transparent
          opacity={0.18}
          depthWrite={false}
          blending={THREE.AdditiveBlending}
        />
      </mesh>

      <mesh>
        <ringGeometry args={[0.085, 0.13, 48]} />
        <meshBasicMaterial
          color={COLORS.cyan}
          transparent
          opacity={0.7}
          side={THREE.DoubleSide}
          depthWrite={false}
          blending={THREE.AdditiveBlending}
        />
      </mesh>

      <PulseRing color={COLORS.cyan} delay={city.id * 0.28} intensity={city.traffic} />
    </group>
  );
};

const Connections = ({ radius }: { radius: number }) => {
  const curves = useMemo(() => {
    return CITIES.map((city, index) => {
      const nextCity = CITIES[(index + 1) % CITIES.length];

      const start = latLngToVector3(city.lat, city.lng, radius + 0.03);
      const end = latLngToVector3(nextCity.lat, nextCity.lng, radius + 0.03);

      const mid = start
        .clone()
        .lerp(end, 0.5)
        .normalize()
        .multiplyScalar(radius * 1.45);

      const curve = new THREE.QuadraticBezierCurve3(start, mid, end);
      const points = curve.getPoints(80);

      return new Float32Array(points.flatMap((point) => [point.x, point.y, point.z]));
    });
  }, [radius]);

  return (
    <group>
      {curves.map((positions, index) => (
        <line key={index}>
          <bufferGeometry>
            <bufferAttribute
              attach="attributes-position"
              args={[positions, 3]}
              count={positions.length / 3}
              itemSize={3}
            />
          </bufferGeometry>
          <lineBasicMaterial
            color={COLORS.cyan}
            transparent
            opacity={0.32}
            depthWrite={false}
            blending={THREE.AdditiveBlending}
          />
        </line>
      ))}
    </group>
  );
};

const GlobeContent = ({ onSelect }: { onSelect: (cityId: number) => void }) => {
  const globeRef = useRef<THREE.Group>(null);
  const { size } = useThree();

  const isMobile = size.width < 768;
  const radius = isMobile ? 2.55 : 3.08;

  const points = useMemo(() => createGlobePoints(radius, isMobile), [radius, isMobile]);

  useFrame(() => {
    if (!globeRef.current) return;
    globeRef.current.rotation.y += 0.0017;
    globeRef.current.rotation.x = -0.12;
    globeRef.current.rotation.z = 0.03;
  });

  return (
    <group ref={globeRef}>
      {/* Halo interne */}
      <mesh>
        <sphereGeometry args={[radius * 0.985, 96, 96]} />
        <meshBasicMaterial
          color={COLORS.cyan}
          transparent
          opacity={0.12}
          depthWrite={false}
          blending={THREE.AdditiveBlending}
        />
      </mesh>

      {/* Halo périphérique */}
      <mesh scale={1.035}>
        <sphereGeometry args={[radius, 96, 96]} />
        <meshBasicMaterial
          color={COLORS.cyanSoft}
          transparent
          opacity={0.055}
          side={THREE.BackSide}
          depthWrite={false}
          blending={THREE.AdditiveBlending}
        />
      </mesh>

      {/* Océan digital */}
      <Points positions={points.ocean} stride={3}>
        <PointMaterial
          transparent
          color={COLORS.cyanSoft}
          size={isMobile ? 0.014 : 0.017}
          opacity={0.3}
          sizeAttenuation
          depthWrite={false}
          blending={THREE.AdditiveBlending}
        />
      </Points>

      {/* Continents simulés */}
      <Points positions={points.land} stride={3}>
        <PointMaterial
          transparent
          color={COLORS.cyanStrong}
          size={isMobile ? 0.02 : 0.026}
          opacity={0.95}
          sizeAttenuation
          depthWrite={false}
          blending={THREE.AdditiveBlending}
        />
      </Points>

      {/* Nodes */}
      {CITIES.map((city) => (
        <CityNode key={city.id} city={city} radius={radius} onSelect={onSelect} />
      ))}

      {/* Connexions */}
      <Connections radius={radius} />
    </group>
  );
};

export const PixelGlobe = ({
  onSelectCity
}: {
  onSelectCity?: (name: string) => void;
}) => {
  const [selectedId, setSelectedId] = useState<number | null>(null);

  const handleSelect = (id: number) => {
    setSelectedId(id);

    const city = CITIES.find((item) => item.id === id);

    if (city && onSelectCity) {
      onSelectCity(city.name);
    }
  };

  return (
    <div className="relative w-full h-full min-h-[520px] md:min-h-[700px] flex items-center justify-center overflow-hidden">
      <Canvas
        dpr={[1, 2]}
        gl={{
          alpha: true,
          antialias: true,
          powerPreference: 'high-performance'
        }}
        camera={{ position: [0, 0, 7], fov: 42 }}
      >
        <PerspectiveCamera makeDefault position={[0, 0, 7]} fov={42} />
        <ambientLight intensity={0.75} />
        <pointLight position={[8, 8, 8]} intensity={1.6} color={COLORS.cyan} />
        <pointLight position={[-8, -6, 4]} intensity={0.55} color={COLORS.cyanSoft} />

        <Stars
          radius={80}
          depth={40}
          count={2500}
          factor={3}
          saturation={0}
          fade
          speed={0.35}
        />

        <Float speed={1.2} rotationIntensity={0.25} floatIntensity={0.35}>
          <GlobeContent onSelect={handleSelect} />
        </Float>
      </Canvas>

      <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_center,rgba(0,243,255,0.10),transparent_58%)]" />

      <AnimatePresence>
        {selectedId && (
          <motion.div
            initial={{ opacity: 0, y: 20, scale: 0.96 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 20, scale: 0.96 }}
            className="absolute bottom-10 left-1/2 -translate-x-1/2 z-30 app-surface border app-border p-4 rounded-2xl shadow-2xl backdrop-blur-md min-w-[220px]"
          >
            <div className="flex items-center gap-3">
              <div className="w-2.5 h-2.5 rounded-full bg-minimal-accent animate-pulse" />
              <div>
                <p className="text-[10px] font-black uppercase tracking-widest text-minimal-accent">
                  Connected Node
                </p>
                <p className="text-lg font-black app-text-bold uppercase">
                  {CITIES.find((city) => city.id === selectedId)?.name}
                </p>
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
};
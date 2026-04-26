import { Suspense, useMemo, useRef, useState } from 'react';
import {
  Canvas,
  ThreeEvent,
  useFrame,
  useLoader,
  useThree
} from '@react-three/fiber';
import {
  Float,
  OrbitControls,
  PerspectiveCamera,
  Stars
} from '@react-three/drei';
import * as THREE from 'three';
import { AnimatePresence, motion } from 'motion/react';

type CityStatus = 'ACTIVE' | 'OPTIMAL' | 'BUSY';

type City = {
  id: number;
  name: string;
  lat: number;
  lng: number;
  traffic: number;
  ping: number;
  load: number;
  protocol: string;
  status: CityStatus;
};

type GlobeSurface = {
  surfacePositions: Float32Array;
  surfaceColors: Float32Array;
  landGlowPositions: Float32Array;
};

const TOPOLOGY_TEXTURE = '/textures/earth-topology.png';

const CITIES: City[] = [
  {
    id: 1,
    name: 'London',
    lat: 51.5074,
    lng: -0.1278,
    traffic: 0.8,
    ping: 28,
    load: 42,
    protocol: 'VLESS',
    status: 'ACTIVE'
  },
  {
    id: 2,
    name: 'New York',
    lat: 40.7128,
    lng: -74.006,
    traffic: 0.9,
    ping: 63,
    load: 58,
    protocol: 'REALITY',
    status: 'BUSY'
  },
  {
    id: 3,
    name: 'Tokyo',
    lat: 35.6762,
    lng: 139.6503,
    traffic: 0.7,
    ping: 91,
    load: 36,
    protocol: 'VLESS',
    status: 'ACTIVE'
  },
  {
    id: 4,
    name: 'Paris',
    lat: 48.8566,
    lng: 2.3522,
    traffic: 0.6,
    ping: 31,
    load: 29,
    protocol: 'VLESS',
    status: 'OPTIMAL'
  },
  {
    id: 5,
    name: 'Singapore',
    lat: 1.3521,
    lng: 103.8198,
    traffic: 0.85,
    ping: 74,
    load: 47,
    protocol: 'REALITY',
    status: 'ACTIVE'
  },
  {
    id: 6,
    name: 'Sydney',
    lat: -33.8688,
    lng: 151.2093,
    traffic: 0.4,
    ping: 112,
    load: 22,
    protocol: 'VLESS',
    status: 'OPTIMAL'
  },
  {
    id: 7,
    name: 'Berlin',
    lat: 52.52,
    lng: 13.405,
    traffic: 0.5,
    ping: 34,
    load: 33,
    protocol: 'VLESS',
    status: 'OPTIMAL'
  },
  {
    id: 8,
    name: 'San Francisco',
    lat: 37.7749,
    lng: -122.4194,
    traffic: 0.75,
    ping: 78,
    load: 45,
    protocol: 'REALITY',
    status: 'ACTIVE'
  },
  {
    id: 9,
    name: 'Moscow',
    lat: 55.7558,
    lng: 37.6173,
    traffic: 0.72,
    ping: 39,
    load: 41,
    protocol: 'VLESS',
    status: 'ACTIVE'
  },
  {
    id: 10,
    name: 'Dubai',
    lat: 25.2048,
    lng: 55.2708,
    traffic: 0.68,
    ping: 66,
    load: 38,
    protocol: 'REALITY',
    status: 'ACTIVE'
  }
];

const COLORS = {
  cyan: '#1ea3b4',
  cyanStrong: '#22d0e4',
  cyanSoft: '#156168',
  cyanWhite: '#acd6d6',
  oceanDark: '#3e7f99',
  oceanMid: '#01171d',
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

function textureXYFromLatLng(
  lat: number,
  lng: number,
  width: number,
  height: number
) {
  const normalizedLng = normalizeLng(lng);

  const x = Math.floor(((normalizedLng + 180) / 360) * (width - 1));
  const y = Math.floor(((90 - lat) / 180) * (height - 1));

  return {
    x: THREE.MathUtils.clamp(x, 0, width - 1),
    y: THREE.MathUtils.clamp(y, 0, height - 1)
  };
}

function getBrightnessAt(
  data: Uint8ClampedArray,
  width: number,
  height: number,
  x: number,
  y: number
) {
  const index = (y * width + x) * 4;

  const r = data[index] / 255;
  const g = data[index + 1] / 255;
  const b = data[index + 2] / 255;
  const a = data[index + 3] / 255;

  return ((r + g + b) / 3) * a;
}

function getLocalMaxBrightness(
  data: Uint8ClampedArray,
  width: number,
  height: number,
  x: number,
  y: number,
  radius: number
) {
  let maxBrightness = 0;

  for (let offsetY = -radius; offsetY <= radius; offsetY += 1) {
    for (let offsetX = -radius; offsetX <= radius; offsetX += 1) {
      const nx = THREE.MathUtils.clamp(x + offsetX, 0, width - 1);
      const ny = THREE.MathUtils.clamp(y + offsetY, 0, height - 1);

      const brightness = getBrightnessAt(data, width, height, nx, ny);

      if (brightness > maxBrightness) {
        maxBrightness = brightness;
      }
    }
  }

  return maxBrightness;
}

function createSurfaceFromTopology(
  image: HTMLImageElement | HTMLCanvasElement | ImageBitmap,
  radius: number,
  isMobile: boolean
): GlobeSurface {
  const canvas = document.createElement('canvas');
  const width = 'width' in image ? image.width : 2048;
  const height = 'height' in image ? image.height : 1024;

  canvas.width = width;
  canvas.height = height;

  const context = canvas.getContext('2d', { willReadFrequently: true });

  if (!context) {
    return {
      surfacePositions: new Float32Array(),
      surfaceColors: new Float32Array(),
      landGlowPositions: new Float32Array()
    };
  }

  context.drawImage(image as CanvasImageSource, 0, 0, width, height);

  const data = context.getImageData(0, 0, width, height).data;

  const totalPoints = isMobile ? 290000 : 840000;
  const goldenAngle = Math.PI * (3 - Math.sqrt(5));

  const surfacePositions = new Float32Array(totalPoints * 3);
  const surfaceColors = new Float32Array(totalPoints * 3);
  const landGlow: number[] = [];

  const oceanDark = new THREE.Color('#1f6581');
const oceanMid = new THREE.Color('#1167b8');
const landLow = new THREE.Color('#d4e8f5');
const landHigh = new THREE.Color('#FFFFFF');

  const color = new THREE.Color();

  for (let i = 0; i < totalPoints; i += 1) {
    const y = 1 - (i / (totalPoints - 1)) * 2;
    const horizontalRadius = Math.sqrt(Math.max(0, 1 - y * y));
    const theta = goldenAngle * i;

    const x = Math.cos(theta) * horizontalRadius;
    const z = Math.sin(theta) * horizontalRadius;

    const lat = Math.asin(y) * (180 / Math.PI);
    const lng = Math.atan2(z, x) * (180 / Math.PI);

    const { x: tx, y: ty } = textureXYFromLatLng(lat, lng, width, height);

    const brightness = getBrightnessAt(data, width, height, tx, ty);
    const localBrightness = getLocalMaxBrightness(data, width, height, tx, ty, 2);

    const detail = Math.max(brightness, localBrightness * 0.82);
    const landStrength = THREE.MathUtils.smoothstep(detail, 0.045, 0.22);
    const relief = Math.pow(THREE.MathUtils.clamp(detail, 0, 1), 0.72);

    const pointRadius = radius + landStrength * 0.028 + relief * 0.012;
    const point = new THREE.Vector3(x, y, z).multiplyScalar(pointRadius);

    surfacePositions[i * 3] = point.x;
    surfacePositions[i * 3 + 1] = point.y;
    surfacePositions[i * 3 + 2] = point.z;

    if (landStrength > 0.08) {
      color
        .copy(landLow)
        .lerp(landHigh, THREE.MathUtils.clamp(relief * 0.85, 0, 1));

      if (landStrength > 0.18 && i % (isMobile ? 3 : 2) === 0) {
        landGlow.push(point.x, point.y, point.z);
      }
    } else {
      color.copy(oceanDark).lerp(oceanMid, 0.25 + detail * 1.15);
    }

    surfaceColors[i * 3] = color.r;
    surfaceColors[i * 3 + 1] = color.g;
    surfaceColors[i * 3 + 2] = color.b;
  }

  return {
    surfacePositions,
    surfaceColors,
    landGlowPositions: new Float32Array(landGlow)
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
    const scale = 1 + progress * 2.1 * intensity;

    meshRef.current.scale.set(scale, scale, scale);

    if (meshRef.current.material instanceof THREE.MeshBasicMaterial) {
      meshRef.current.material.opacity = Math.max(0, 0.5 * (1 - progress));
    }
  });

  return (
    <mesh ref={meshRef}>
      <ringGeometry args={[0.075, 0.13, 48]} />
      <meshBasicMaterial
        color={color}
        transparent
        opacity={0.50}
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
  active,
  hovered,
  onSelect,
  onHover
}: {
  city: City;
  radius: number;
  active: boolean;
  hovered: boolean;
  onSelect: (city: City) => void;
  onHover: (city: City | null) => void;
}) => {
  const position = useMemo(
    () => latLngToVector3(city.lat, city.lng, radius + 0.075),
    [city.lat, city.lng, radius]
  );

  const quaternion = useMemo(() => {
    const normal = position.clone().normalize();
    const quaternionValue = new THREE.Quaternion();

    quaternionValue.setFromUnitVectors(new THREE.Vector3(0, 0, 1), normal);

    return quaternionValue;
  }, [position]);

  const nodeSize = active || hovered ? 0.09 : 0.055 + city.traffic * 0.02;

  const handleSelect = (event: ThreeEvent<MouseEvent | PointerEvent>) => {
    event.stopPropagation();
    onSelect(city);
  };

  const handlePointerOver = (event: ThreeEvent<PointerEvent>) => {
    event.stopPropagation();
    onHover(city);
    document.body.style.cursor = 'pointer';
  };

  const handlePointerOut = (event: ThreeEvent<PointerEvent>) => {
    event.stopPropagation();
    onHover(null);
    document.body.style.cursor = 'auto';
  };

  return (
    <group position={position} quaternion={quaternion}>
      <mesh
        onClick={handleSelect}
        onPointerOver={handlePointerOver}
        onPointerOut={handlePointerOut}
      >
        <sphereGeometry args={[nodeSize * 4.2, 18, 18]} />
        <meshBasicMaterial transparent opacity={0} depthWrite={false} />
      </mesh>

      <mesh
        onClick={handleSelect}
        onPointerOver={handlePointerOver}
        onPointerOut={handlePointerOut}
      >
        <sphereGeometry args={[nodeSize, 28, 28]} />
        <meshBasicMaterial
          color={active || hovered ? COLORS.cyan : COLORS.white}
          transparent
          opacity={1}
          depthWrite={false}
          blending={THREE.AdditiveBlending}
        />
      </mesh>

      <mesh>
        <sphereGeometry args={[nodeSize * 2.9, 28, 28]} />
        <meshBasicMaterial
          color={COLORS.cyan}
          transparent
          opacity={active || hovered ? 0.3 : 0.16}
          depthWrite={false}
          blending={THREE.AdditiveBlending}
        />
      </mesh>

      <mesh>
        <ringGeometry args={[0.08, 0.14, 56]} />
        <meshBasicMaterial
          color={COLORS.cyan}
          transparent
          opacity={active || hovered ? 0.86 : 0.5}
          side={THREE.DoubleSide}
          depthWrite={false}
          blending={THREE.AdditiveBlending}
        />
      </mesh>

      <PulseRing color={COLORS.cyan} delay={city.id * 0.27} intensity={city.traffic} />
    </group>
  );
};

const FlowPhoton = ({
  curve,
  delay
}: {
  curve: THREE.QuadraticBezierCurve3;
  delay: number;
}) => {
  const meshRef = useRef<THREE.Mesh>(null);

  useFrame((state) => {
    if (!meshRef.current) return;

    const time = state.clock.getElapsedTime() * 0.18 + delay;
    const t = time % 1;

    const point = curve.getPoint(t);
    meshRef.current.position.copy(point);

    const fade = Math.sin(t * Math.PI);

    meshRef.current.scale.setScalar(0.65 + fade * 0.8);

    if (meshRef.current.material instanceof THREE.MeshBasicMaterial) {
      meshRef.current.material.opacity = 0.12 + fade * 0.75;
    }
  });

  return (
    <mesh ref={meshRef}>
      <sphereGeometry args={[0.025, 12, 12]} />
      <meshBasicMaterial
        color={COLORS.cyanSoft}
        transparent
        opacity={0.60}
        depthWrite={false}
        blending={THREE.AdditiveBlending}
      />
    </mesh>
  );
};

const Connections = ({ radius }: { radius: number }) => {
  const curves = useMemo(() => {
    return CITIES.map((city, index) => {
      const nextCity = CITIES[(index + 1) % CITIES.length];

      const start = latLngToVector3(city.lat, city.lng, radius + 0.07);
      const end = latLngToVector3(nextCity.lat, nextCity.lng, radius + 0.07);

      const mid = start
        .clone()
        .lerp(end, 0.5)
        .normalize()
        .multiplyScalar(radius * 1.38);

      return new THREE.QuadraticBezierCurve3(start, mid, end);
    });
  }, [radius]);

  const lineGeometries = useMemo(() => {
    return curves.map((curve) => {
      const points = curve.getPoints(96);
      return new Float32Array(points.flatMap((point) => [point.x, point.y, point.z]));
    });
  }, [curves]);

  return (
    <group>
      {lineGeometries.map((positions, index) => (
        <line key={`line-${index}`}>
          <bufferGeometry>
            <bufferAttribute attach="attributes-position" args={[positions, 3]} />
          </bufferGeometry>
          <lineBasicMaterial
            color={COLORS.cyan}
            transparent
            opacity={0.1}
            depthWrite={false}
            blending={THREE.AdditiveBlending}
          />
        </line>
      ))}

      {curves.map((curve, index) => (
        <FlowPhoton key={`photon-${index}`} curve={curve} delay={index * 0.13} />
      ))}
    </group>
  );
};

const GlobeContent = ({
  selectedCity,
  hoveredCity,
  onSelectCity,
  onHoverCity
}: {
  selectedCity: City | null;
  hoveredCity: City | null;
  onSelectCity: (city: City) => void;
  onHoverCity: (city: City | null) => void;
}) => {
  const globeRef = useRef<THREE.Group>(null);
  const texture = useLoader(THREE.TextureLoader, TOPOLOGY_TEXTURE);
  const { size } = useThree();

  const isMobile = size.width < 768;

  const radius = isMobile ? 1.72 : 2.12;

  const surface = useMemo(() => {
    texture.colorSpace = THREE.SRGBColorSpace;
    texture.minFilter = THREE.LinearFilter;
    texture.magFilter = THREE.LinearFilter;
    texture.needsUpdate = true;

    return createSurfaceFromTopology(texture.image, radius, isMobile);
  }, [texture, radius, isMobile]);

  useFrame(() => {
    if (!globeRef.current) return;

    globeRef.current.rotation.y += 0.00135;
    globeRef.current.rotation.x = -0.1;
    globeRef.current.rotation.z = 0.025;
  });

  return (
    <group ref={globeRef}>
      <mesh>
        <sphereGeometry args={[radius * 0.955, 96, 96]} />
        <meshBasicMaterial
          color="#7fefff"
          transparent
          opacity={0.02}
          depthWrite={false}
          blending={THREE.AdditiveBlending}
        />
      </mesh>

      <mesh>
        <sphereGeometry args={[radius * 1.004, 52, 52]} />
        <meshBasicMaterial
          color="#fcfcfc"
          transparent
          opacity={0.02}
          wireframe
          depthWrite={false}
        />
      </mesh>

      <mesh scale={1.045}>
        <sphereGeometry args={[radius, 96, 96]} />
        <meshBasicMaterial
          color={COLORS.cyanSoft}
          transparent
          opacity={0.026}
          side={THREE.BackSide}
          depthWrite={false}
          blending={THREE.AdditiveBlending}
        />
      </mesh>

      <points>
        <bufferGeometry>
          <bufferAttribute
            attach="attributes-position"
            args={[surface.surfacePositions, 3]}
          />
          <bufferAttribute
            attach="attributes-color"
            args={[surface.surfaceColors, 3]}
          />
        </bufferGeometry>
        <pointsMaterial
  vertexColors
  transparent
  opacity={0.80}
  size={isMobile ? 0.012 : 0.015}
  sizeAttenuation
  depthWrite={false}
  blending={THREE.NormalBlending}
/>
      </points>

      <points>
        <bufferGeometry>
          <bufferAttribute
            attach="attributes-position"
            args={[surface.landGlowPositions, 3]}
          />
        </bufferGeometry>
        <pointsMaterial
          transparent
          color={COLORS.cyanStrong}
          opacity={0.16}
          size={isMobile ? 0.007 : 0.009}
          sizeAttenuation
          depthWrite={false}
          blending={THREE.AdditiveBlending}
        />
      </points>

      <Connections radius={radius} />

      {CITIES.map((city) => (
        <CityNode
          key={city.id}
          city={city}
          radius={radius}
          active={selectedCity?.id === city.id}
          hovered={hoveredCity?.id === city.id}
          onSelect={onSelectCity}
          onHover={onHoverCity}
        />
      ))}
    </group>
  );
};

export const PixelGlobe = ({
  onSelectCity
}: {
  onSelectCity?: (name: string) => void;
}) => {
  const [selectedCity, setSelectedCity] = useState<City | null>(null);
  const [hoveredCity, setHoveredCity] = useState<City | null>(null);

  const visibleCity = hoveredCity || selectedCity;

  const handleSelectCity = (city: City) => {
    setSelectedCity(city);

    if (onSelectCity) {
      onSelectCity(city.name);
    }
  };

  return (
    <div className="relative w-full h-full min-h-[500px] md:min-h-[640px] flex items-center justify-center overflow-hidden touch-none">
      <Canvas
        className="touch-none"
        dpr={[1, 1.6]}
        gl={{
          alpha: true,
          antialias: true,
          powerPreference: 'high-performance'
        }}
        camera={{
          position: [0, 0, 7.7],
          fov: 38
        }}
      >
        <PerspectiveCamera makeDefault position={[0, 0, 7.7]} fov={38} />

        <ambientLight intensity={0.72} />
        <pointLight position={[8, 8, 8]} intensity={1.25} color={COLORS.cyan} />
        <pointLight position={[-8, -6, 4]} intensity={0.38} color={COLORS.cyanSoft} />

        <Stars
          radius={70}
          depth={36}
          count={1800}
          factor={2.6}
          saturation={0}
          fade
          speed={0.28}
        />

        <Suspense fallback={null}>
          <Float speed={1.05} rotationIntensity={0.18} floatIntensity={0.28}>
            <GlobeContent
              selectedCity={selectedCity}
              hoveredCity={hoveredCity}
              onSelectCity={handleSelectCity}
              onHoverCity={setHoveredCity}
            />
          </Float>
        </Suspense>

        <OrbitControls
          makeDefault
          enableZoom={false}
          enablePan={false}
          enableRotate
          rotateSpeed={0.65}
          touches={{
            ONE: THREE.TOUCH.ROTATE,
            TWO: THREE.TOUCH.DOLLY_ROTATE
          }}
          mouseButtons={{
            LEFT: THREE.MOUSE.ROTATE
          }}
          minPolarAngle={Math.PI * 0.12}
          maxPolarAngle={Math.PI * 0.88}
        />
      </Canvas>

      <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_center,rgba(105,243,255,0.055),transparent_58%)]" />

      <AnimatePresence>
        {visibleCity && (
          <motion.div
            key={visibleCity.id}
            initial={{ opacity: 0, y: 16, scale: 0.96 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 16, scale: 0.96 }}
            className="pointer-events-none absolute bottom-8 left-1/2 z-30 min-w-[250px] -translate-x-1/2 rounded-3xl border app-border app-surface/90 p-5 shadow-2xl backdrop-blur-xl"
          >
            <div className="mb-4 flex items-center justify-between gap-4">
              <div>
                <p className="text-[9px] font-black uppercase tracking-[0.28em] text-minimal-accent">
                  Active Node
                </p>
                <p className="text-2xl font-black uppercase tracking-tighter app-text-bold">
                  {visibleCity.name}
                </p>
              </div>

              <div className="h-3 w-3 rounded-full bg-minimal-accent shadow-[0_0_20px_rgba(46,144,250,0.9)]" />
            </div>

            <div className="grid grid-cols-3 gap-3">
              <div className="rounded-2xl app-bg p-3 text-center">
                <p className="text-[8px] font-black uppercase tracking-widest app-text-muted">
                  Ping
                </p>
                <p className="text-sm font-black app-text-bold">
                  {visibleCity.ping}ms
                </p>
              </div>

              <div className="rounded-2xl app-bg p-3 text-center">
                <p className="text-[8px] font-black uppercase tracking-widest app-text-muted">
                  Load
                </p>
                <p className="text-sm font-black app-text-bold">
                  {visibleCity.load}%
                </p>
              </div>

              <div className="rounded-2xl app-bg p-3 text-center">
                <p className="text-[8px] font-black uppercase tracking-widest app-text-muted">
                  Mode
                </p>
                <p className="text-sm font-black app-text-bold">
                  {visibleCity.protocol}
                </p>
              </div>
            </div>

            <div className="mt-4 flex items-center justify-between">
              <p className="text-[9px] font-black uppercase tracking-[0.22em] app-text-secondary">
                {visibleCity.status}
              </p>

              <p className="text-[9px] font-black uppercase tracking-[0.22em] text-minimal-accent">
                Mock telemetry
              </p>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
};
import React, { useMemo, useRef, useState } from 'react';
import * as THREE from 'three';
import { useFrame, useLoader } from '@react-three/fiber';
import { Html } from '@react-three/drei';
import { latLonToVector3, CITIES, createArcCurve } from './globeUtils';

const GLOBE_RADIUS = 1.0;
const POINTS_COUNT = 250000; // Slightly reduced for cleaner look
const CLOUD_COUNT = 10000;

export const GlobeAtmosphere = () => {
  return (
    <group>
      {/* Subtle Inner Glow - tighter to the surface */}
      <mesh>
        <sphereGeometry args={[GLOBE_RADIUS * 1.01, 64, 64]} />
        <meshBasicMaterial
          color="#0066cc"
          transparent
          opacity={0.15}
          side={THREE.BackSide}
          blending={THREE.AdditiveBlending}
        />
      </mesh>
      {/* Outer Atmospheric Haze - very soft and deep blue */}
      <mesh>
        <sphereGeometry args={[GLOBE_RADIUS * 1.15, 64, 64]} />
        <meshBasicMaterial
          color="#002244"
          transparent
          opacity={0.08}
          side={THREE.BackSide}
          blending={THREE.AdditiveBlending}
        />
      </mesh>
      
      {/* Refined subtle scanning ring */}
      <mesh rotation={[Math.PI / 2, 0, 0]}>
        <torusGeometry args={[GLOBE_RADIUS * 1.1, 0.0003, 16, 150]} />
        <meshBasicMaterial color="#00ffff" transparent opacity={0.05} blending={THREE.AdditiveBlending} />
      </mesh>
    </group>
  );
};

export const GlobePoints = () => {
  const pointsRef = useRef<THREE.Points>(null);
  const landMask = useLoader(THREE.TextureLoader, 'https://raw.githubusercontent.com/mrdoob/three.js/master/examples/textures/planets/earth_atmos_2048.jpg');

  const [positions, opacities, colors] = useMemo(() => {
    const pos = new Float32Array(POINTS_COUNT * 3);
    const op = new Float32Array(POINTS_COUNT);
    const col = new Float32Array(POINTS_COUNT * 3);

    const canvas = document.createElement('canvas');
    canvas.width = 1024;
    canvas.height = 512;
    const ctx = canvas.getContext('2d');
    ctx?.drawImage(landMask.image, 0, 0, 1024, 512);
    const imageData = ctx?.getImageData(0, 0, canvas.width, canvas.height).data;

    for (let i = 0; i < POINTS_COUNT; i++) {
      const y = 1 - (i / (POINTS_COUNT - 1)) * 2;
      const radiusAtY = Math.sqrt(1 - y * y);
      const theta = 2 * Math.PI * (i / ((1 + Math.sqrt(5)) / 2));

      const lon = theta;
      const lat = Math.asin(y);
      
      const u = (lon + Math.PI) / (2 * Math.PI);
      const v = 1 - (lat + Math.PI / 2) / Math.PI;
      
      let isLand = false;
      let lightValue = 0;
      
      if (imageData) {
        const tx = Math.floor((u % 1) * canvas.width);
        const ty = Math.floor(v * canvas.height);
        const index = (ty * canvas.width + tx) * 4;
        const r = imageData[index];
        const g = imageData[index + 1];
        
        // Strict land detection
        isLand = r > 40 || g > 40; 
        lightValue = (r + g) / 512;
      }
      
      const currentRadius = GLOBE_RADIUS;
      const x = currentRadius * Math.cos(theta) * radiusAtY;
      const z = currentRadius * Math.sin(theta) * radiusAtY;
      const currentY = currentRadius * y;
      
      pos[i * 3] = x;
      pos[i * 3 + 1] = currentY;
      pos[i * 3 + 2] = z;

      if (isLand) {
        // Holographic continent density
        op[i] = 0.2 + (lightValue * 0.4) + (Math.random() * 0.1);
        
        // Deep Navy base with Cyan/Blue micro-sparks
        if (Math.random() > 0.985) {
          // Electric Accent Spark
          col[i * 3] = 0.0;
          col[i * 3 + 1] = 0.8;
          col[i * 3 + 2] = 1.0;
          op[i] = 0.8;
        } else {
          // Deep Data Blue
          col[i * 3] = 0.02;
          col[i * 3 + 1] = 0.15;
          col[i * 3 + 2] = 0.45;
        }
      } else {
        // Dark Void for Oceans
        op[i] = 0.0;
      }
    }
    return [pos, op, col];
  }, [landMask]);

  useFrame(() => {
    if (pointsRef.current) {
      pointsRef.current.rotation.y += 0.0004;
    }
  });

  return (
    <points ref={pointsRef}>
      <bufferGeometry>
        <bufferAttribute
          attach="attributes-position"
          count={positions.length / 3}
          array={positions}
          itemSize={3}
        />
        <bufferAttribute
          attach="attributes-opacity"
          count={opacities.length}
          array={opacities}
          itemSize={1}
        />
        <bufferAttribute
          attach="attributes-color"
          count={colors.length / 3}
          array={colors}
          itemSize={3}
        />
      </bufferGeometry>
      <pointsMaterial
        size={0.0035}
        vertexColors
        transparent
        opacity={0.8}
        blending={THREE.AdditiveBlending}
        depthWrite={false}
        sizeAttenuation={true}
      />
    </points>
  );
};

export const GlobeMarkers = () => {
  const groupRef = useRef<THREE.Group>(null);

  useFrame((state) => {
    if (groupRef.current) {
      groupRef.current.rotation.y += 0.0004;
      groupRef.current.children.forEach((child, i) => {
        // Individual pulse for markers
        const s = 1 + Math.sin(state.clock.elapsedTime * 2 + i) * 0.1;
        const core = child.children[0];
        if (core) core.scale.setScalar(s);
      });
    }
  });

  const cityData = useMemo(() => {
    // Only use the requested 8 major hubs
    return CITIES.slice(0, 8).map((city, i) => ({
      ...city,
      ping: Math.floor(12 + Math.random() * 40),
      status: i % 4 === 0 ? 'STABLE' : 'HEALTHY',
      hasHUD: i < 5 // Only show a few labels at once
    }));
  }, []);

  return (
    <group ref={groupRef}>
      {cityData.map((city, i) => {
        const pos = latLonToVector3(city.lat, city.lng, GLOBE_RADIUS * 1.01);
        return (
          <group key={city.name} position={pos}>
            {/* Minimal Core Node */}
            <mesh>
              <sphereGeometry args={[0.012, 12, 12]} />
              <meshBasicMaterial color="#00ffff" opacity={0.9} transparent blending={THREE.AdditiveBlending} />
            </mesh>
            {/* Soft Hub Glow */}
            <mesh scale={2.5}>
              <sphereGeometry args={[0.015, 12, 12]} />
              <meshBasicMaterial color="#0099ff" transparent opacity={0.2} blending={THREE.AdditiveBlending} />
            </mesh>

            {city.hasHUD && (
              <Html distanceFactor={8} position={[0, 0.1, 0]} center>
                <div className="pointer-events-none select-none opacity-90 transition-opacity">
                  {/* Ultra-minimal HUD Label */}
                  <div className="text-cyan-400 font-mono text-[9px] tracking-[0.2em] text-center uppercase whitespace-nowrap">
                    <div className="font-bold flex items-center justify-center gap-1.5 mb-1 text-glow-cyan">
                      <span className="w-1 h-1 bg-cyan-400 rounded-full" />
                      {city.name}
                    </div>
                    <div className="text-[7px] font-medium opacity-60">
                      {city.ping}MS / {city.status}
                    </div>
                  </div>
                </div>
              </Html>
            )}
          </group>
        );
      })}
    </group>
  );
};

export const GlobeArcs = () => {
  const groupRef = useRef<THREE.Group>(null);

  const arcs = useMemo(() => {
    const lines = [];
    // Selective refined connections between major hubs
    const connections = [
      [0, 1], [1, 2], [2, 3], [3, 4], [4, 5], [5, 6], [6, 7], [7, 0],
      [0, 5], [1, 4], [2, 7]
    ];
    
    connections.forEach(([i, j]) => {
      if (CITIES[i] && CITIES[j]) {
        const p1 = latLonToVector3(CITIES[i].lat, CITIES[i].lng, GLOBE_RADIUS);
        const p2 = latLonToVector3(CITIES[j].lat, CITIES[j].lng, GLOBE_RADIUS);
        const curve = createArcCurve(p1, p2, GLOBE_RADIUS);
        const curvePoints = curve.getPoints(80);
        lines.push({
          points: curvePoints,
          curve: curve
        });
      }
    });
    return lines;
  }, []);

  useFrame((state) => {
    if (groupRef.current) {
      groupRef.current.rotation.y += 0.0004;
    }
  });

  return (
    <group ref={groupRef}>
      {arcs.map((arc, i) => (
        <group key={i}>
          {/* Static Arc Line */}
          <line>
            <bufferGeometry>
               <bufferAttribute
                 attach="attributes-position"
                 count={arc.points.length}
                 array={new Float32Array(arc.points.flatMap(p => [p.x, p.y, p.z]))}
                 itemSize={3}
               />
            </bufferGeometry>
            <lineBasicMaterial color="#00aaff" transparent opacity={0.08} blending={THREE.AdditiveBlending} />
          </line>
          
          {/* Animated Data Packet */}
          <DataPacket curve={arc.curve} delay={i * 0.5} />
        </group>
      ))}
    </group>
  );
};

const DataPacket = ({ curve, delay }: { curve: THREE.Curve<THREE.Vector3>, delay: number }) => {
  const meshRef = useRef<THREE.Mesh>(null);
  
  useFrame((state) => {
    if (meshRef.current) {
      const t = ((state.clock.elapsedTime + delay) * 0.3) % 1;
      const point = curve.getPoint(t);
      meshRef.current.position.copy(point);
      
      // Pulse opacity
      const opacity = Math.sin(t * Math.PI) * 0.8;
      (meshRef.current.material as THREE.MeshBasicMaterial).opacity = opacity;
    }
  });

  return (
    <mesh ref={meshRef}>
      <sphereGeometry args={[0.005, 8, 8]} />
      <meshBasicMaterial color="#00ffff" transparent opacity={0.4} blending={THREE.AdditiveBlending} />
    </mesh>
  );
};

export const GlobeCloud = () => {
  const pointsRef = useRef<THREE.Points>(null);
  
  const [positions, opacities] = useMemo(() => {
    const pos = new Float32Array(CLOUD_COUNT * 3);
    const op = new Float32Array(CLOUD_COUNT);
    
    for (let i = 0; i < CLOUD_COUNT; i++) {
      const phi = Math.acos(2 * Math.random() - 1);
      const theta = 2 * Math.PI * Math.random();
      const r = GLOBE_RADIUS * (1.1 + Math.random() * 0.3);
      
      pos[i * 3] = r * Math.sin(phi) * Math.cos(theta);
      pos[i * 3 + 1] = r * Math.sin(phi) * Math.sin(theta);
      pos[i * 3 + 2] = r * Math.cos(phi);
      
      op[i] = Math.random() * 0.15;
    }
    return [pos, op];
  }, []);

  useFrame((state) => {
    if (pointsRef.current) {
      pointsRef.current.rotation.y -= 0.0002;
    }
  });

  return (
    <points ref={pointsRef}>
      <bufferGeometry>
        <bufferAttribute
          attach="attributes-position"
          count={positions.length / 3}
          array={positions}
          itemSize={3}
        />
      </bufferGeometry>
      <pointsMaterial
        size={0.002}
        color="#006699"
        transparent
        opacity={0.15}
        blending={THREE.AdditiveBlending}
        depthWrite={false}
        sizeAttenuation={true}
      />
    </points>
  );
};

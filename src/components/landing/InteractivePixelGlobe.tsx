import React, { Suspense, useEffect, useMemo, useState } from 'react';
import { Canvas } from '@react-three/fiber';
import { OrbitControls, PerspectiveCamera, Preload } from '@react-three/drei';
import {
  GlobePoints,
  GlobeMarkers,
  GlobeArcs,
  GlobeAtmosphere,
  GlobeCloud,
  GlobeWireframe,
} from './globe/GlobeComponents';

const MOBILE_BREAKPOINT = 768;

function useDeviceType() {
  const [device, setDevice] = useState<'mobile' | 'tablet' | 'desktop'>('desktop');

  useEffect(() => {
    if (typeof window === 'undefined') return;

    const update = () => {
      if (window.innerWidth < 640) setDevice('mobile');
      else if (window.innerWidth < 1024) setDevice('tablet');
      else setDevice('desktop');
    };

    update();
    window.addEventListener('resize', update);
    return () => window.removeEventListener('resize', update);
  }, []);

  return device;
}

function GlobeScene({ device }: { device: 'mobile' | 'tablet' | 'desktop' }) {
  const cameraConfig = useMemo(() => {
    if (device === 'mobile') {
      // Very close and large on mobile to ensure it fills the space
      return { position: [0, 0, 5.5] as [number, number, number], fov: 42, scale: 1.35 };
    }
    if (device === 'tablet') {
      // Generous sizing for tablets
      return { position: [0, 0, 6.0] as [number, number, number], fov: 38, scale: 1.25 };
    }
    // Desktop layout (shifted right visually)
    return { position: [0, 0, 7.0] as [number, number, number], fov: 35, scale: 1.15 };
  }, [device]);

  return (
    <>
      {/* Cinematic Fog for Depth of Field illusion */}
      <fogExp2 attach="fog" color="#010308" density={0.11} />

      <PerspectiveCamera
        makeDefault
        position={cameraConfig.position}
        fov={cameraConfig.fov}
      />

      {/* Very subtle ambient lighting, although points are unlit */}
      <ambientLight intensity={0.1} />

      <Suspense fallback={null}>
        <group rotation={[0.25, -0.4, 0]} scale={cameraConfig.scale}>
          <GlobeAtmosphere />
          <GlobeWireframe />
          <GlobePoints />
          <GlobeCloud />
          <GlobeMarkers />
          <GlobeArcs />
        </group>
      </Suspense>

      {!device.includes('mobile') && (
        <OrbitControls
          enableZoom={false}
          enablePan={false}
          enableRotate={true}
          rotateSpeed={0.4}
          dampingFactor={0.05}
          enableDamping
          autoRotate={false} /* Globe layers rotate themselves */
        />
      )}

      <Preload all />
    </>
  );
}

export const InteractivePixelGlobe = () => {
  const device = useDeviceType();

  return (
    <div
      className="
        relative
        w-full
        h-full
        max-w-[400px]
        sm:max-w-[500px]
        md:max-w-[600px]
        mx-auto
        overflow-visible
        lg:cursor-grab
        lg:active:cursor-grabbing
      "
      style={{
        touchAction: 'pan-y',
        pointerEvents: 'auto',
      }}
      aria-hidden="true"
    >
      <Canvas
        dpr={device === 'desktop' ? [1, 2] : [1, 1.2]}
        frameloop="always"
        gl={{
          antialias: device === 'desktop',
          alpha: true,
          powerPreference: 'high-performance',
          preserveDrawingBuffer: false,
        }}
        onCreated={({ gl }) => {
          gl.setClearColor(0x000000, 0);
        }}
        className="w-full h-full"
        style={{
          background: 'transparent',
          touchAction: 'pan-y',
          pointerEvents: 'auto',
        }}
      >
        <GlobeScene device={device} />
      </Canvas>
    </div>
  );
};

export default InteractivePixelGlobe;
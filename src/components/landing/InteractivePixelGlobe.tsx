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

function GlobeScene({
  device,
  interactive,
}: {
  device: 'mobile' | 'tablet' | 'desktop';
  interactive: boolean;
}) {
  const cameraConfig = useMemo(() => {
    if (device === 'mobile') {
      return { position: [0, 0, 7.4] as [number, number, number], fov: 40, scale: 1.08 };
    }
    if (device === 'tablet') {
      return { position: [0, 0, 7.1] as [number, number, number], fov: 37, scale: 1.12 };
    }
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

      {interactive && device !== 'mobile' && (
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

export const InteractivePixelGlobe = ({
  interactive = true,
  performanceMode = 'live',
}: {
  interactive?: boolean;
  performanceMode?: 'live' | 'static';
}) => {
  const device = useDeviceType();
  const isStatic = performanceMode === 'static';

  return (
    <div
      className={[
        'relative',
        'h-full',
        'w-full',
        'max-w-[600px]',
        'mx-auto',
        'overflow-visible',
        interactive ? 'lg:cursor-grab lg:active:cursor-grabbing' : '',
      ].join(' ')}
      style={{
        touchAction: 'pan-y',
        pointerEvents: interactive ? 'auto' : 'none',
      }}
      aria-hidden="true"
    >
      <Canvas
        dpr={
          isStatic
            ? [1, 1.15]
            : device === 'mobile'
              ? [1, 1.4]
              : device === 'tablet'
                ? [1, 1.6]
                : [1, 2]
        }
        frameloop={isStatic ? 'demand' : 'always'}
        gl={{
          antialias: !isStatic,
          alpha: true,
          powerPreference: isStatic ? 'low-power' : 'high-performance',
          preserveDrawingBuffer: false,
        }}
        onCreated={({ gl }) => {
          gl.setClearColor(0x000000, 0);
        }}
        className="w-full h-full"
        style={{
          background: 'transparent',
          touchAction: 'pan-y',
          pointerEvents: interactive ? 'auto' : 'none',
        }}
      >
        <GlobeScene device={device} interactive={interactive} />
      </Canvas>
    </div>
  );
};

export default InteractivePixelGlobe;

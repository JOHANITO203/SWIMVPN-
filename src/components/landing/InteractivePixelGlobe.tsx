import React, { Suspense, useEffect, useMemo, useState } from 'react';
import { Canvas } from '@react-three/fiber';
import { OrbitControls, PerspectiveCamera, Preload } from '@react-three/drei';
import {
  GlobePoints,
  GlobeMarkers,
  GlobeArcs,
  GlobeAtmosphere,
  GlobeCloud,
} from './globe/GlobeComponents';

const MOBILE_BREAKPOINT = 768;

function useIsMobile() {
  const [isMobile, setIsMobile] = useState(false);

  useEffect(() => {
    if (typeof window === 'undefined') return;

    const mediaQuery = window.matchMedia(`(max-width: ${MOBILE_BREAKPOINT - 1}px)`);

    const update = () => {
      setIsMobile(mediaQuery.matches);
    };

    update();
    mediaQuery.addEventListener('change', update);

    return () => {
      mediaQuery.removeEventListener('change', update);
    };
  }, []);

  return isMobile;
}

function GlobeScene({ isMobile }: { isMobile: boolean }) {
  const cameraConfig = useMemo(
    () => ({
      // Caméra rapprochée pour apprécier les détails sans agrandir le canvas.
      position: isMobile
        ? ([0, 0, 8.8] as [number, number, number])
        : ([0, 0, 7.9] as [number, number, number]),

      // FOV serré pour un rendu plus premium.
      fov: isMobile ? 36 : 30,

      // Globe légèrement agrandi.
      scale: isMobile ? 1.12 : 1.38,
    }),
    [isMobile],
  );

  return (
    <>
      <PerspectiveCamera
        makeDefault
        position={cameraConfig.position}
        fov={cameraConfig.fov}
      />

      <ambientLight intensity={0.12} />
      <pointLight position={[10, 10, 10]} intensity={1.45} color="#00E5FF" />
      <pointLight position={[-10, -10, -10]} intensity={0.75} color="#0080FF" />

      <Suspense fallback={null}>
        <group rotation={[0.3, 0, 0]} scale={cameraConfig.scale}>
          <GlobePoints />
          <GlobeCloud />
          <GlobeMarkers />
          <GlobeArcs />
          <GlobeAtmosphere />
        </group>
      </Suspense>

      {!isMobile && (
        <OrbitControls
          enableZoom={false}
          enablePan={false}
          rotateSpeed={0.65}
          dampingFactor={0.06}
          enableDamping
          autoRotate={false}
          makeDefault
        />
      )}

      <Preload all />
    </>
  );
}

export const InteractivePixelGlobe = () => {
  const isMobile = useIsMobile();

  return (
    <div
      className="
        relative
        mx-auto
        w-full

        max-w-[300px]
        sm:max-w-[410px]
        md:max-w-[500px]
        lg:max-w-[540px]
        xl:max-w-[580px]

        h-[200px]
        sm:h-[290px]
        md:h-[350px]
        lg:h-[360px]
        xl:h-[390px]

        min-h-0
        overflow-hidden

        lg:translate-y-[96px]
        xl:translate-y-[108px]

        lg:-translate-x-[16px]
        xl:-translate-x-[28px]

        lg:cursor-grab
        lg:active:cursor-grabbing
      "
      style={{
        touchAction: 'pan-y',
        pointerEvents: isMobile ? 'none' : 'auto',
      }}
      aria-hidden="true"
    >
      <Canvas
        dpr={isMobile ? [1, 1.15] : [1, 1.65]}
        frameloop="always"
        gl={{
          antialias: !isMobile,
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
          pointerEvents: isMobile ? 'none' : 'auto',
        }}
      >
        <GlobeScene isMobile={isMobile} />
      </Canvas>
    </div>
  );
};

export default InteractivePixelGlobe;
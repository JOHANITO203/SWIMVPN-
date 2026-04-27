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
      position: isMobile
        ? ([0, 0, 7.5] as [number, number, number])
        : ([0, 0, 6.2] as [number, number, number]),
      fov: isMobile ? 40 : 35,
      scale: isMobile ? 1.05 : 1.25,
    }),
    [isMobile],
  );

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

      {!isMobile && (
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
        overflow-visible

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
        dpr={isMobile ? [1, 1.2] : [1, 2]}
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
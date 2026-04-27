import React, { Suspense, useEffect, useState } from 'react';
import { Canvas } from '@react-three/fiber';
import { OrbitControls, PerspectiveCamera } from '@react-three/drei';
import { GlobePoints, GlobeMarkers, GlobeArcs, GlobeAtmosphere, GlobeCloud } from './globe/GlobeComponents';

export const InteractivePixelGlobe = () => {
  const [isMobile, setIsMobile] = useState(false);

  useEffect(() => {
    const checkViewport = () => {
      setIsMobile(window.innerWidth < 768);
    };

    checkViewport();
    window.addEventListener('resize', checkViewport);

    return () => window.removeEventListener('resize', checkViewport);
  }, []);

  return (
    <div
      className="relative w-full h-full min-h-0 overflow-visible lg:cursor-grab lg:active:cursor-grabbing"
      style={{
        touchAction: 'pan-y',
        pointerEvents: isMobile ? 'none' : 'auto',
      }}
    >
      <Canvas
        dpr={isMobile ? [1, 1.25] : [1, 2]}
        gl={{
          antialias: true,
          alpha: true,
          powerPreference: 'high-performance',
        }}
        className="w-full h-full"
        style={{
          background: 'transparent',
          touchAction: 'pan-y',
          pointerEvents: isMobile ? 'none' : 'auto',
        }}
      >
        <PerspectiveCamera
          makeDefault
          position={isMobile ? [0, 0, 12] : [0, 0, 11]}
          fov={isMobile ? 42 : 35}
        />

        <ambientLight intensity={0.1} />
        <pointLight position={[10, 10, 10]} intensity={1.5} color="#00E5FF" />
        <pointLight position={[-10, -10, -10]} intensity={0.8} color="#0080FF" />

        <Suspense fallback={null}>
          <group
            rotation={[0.3, 0, 0]}
            scale={isMobile ? 0.78 : 1}
          >
            <GlobePoints />
            <GlobeCloud />
            <GlobeMarkers />
            <GlobeArcs />
            <GlobeAtmosphere />
          </group>
        </Suspense>

        <OrbitControls
          enabled={!isMobile}
          enableZoom={false}
          enablePan={false}
          rotateSpeed={0.8}
          dampingFactor={0.05}
          enableDamping={true}
          autoRotate={false}
          makeDefault
        />
      </Canvas>
    </div>
  );
};

export default InteractivePixelGlobe;
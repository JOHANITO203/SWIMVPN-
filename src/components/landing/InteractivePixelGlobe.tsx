import React, { Suspense } from 'react';
import { Canvas } from '@react-three/fiber';
import { OrbitControls, PerspectiveCamera, Float } from '@react-three/drei';
import { GlobePoints, GlobeMarkers, GlobeArcs, GlobeAtmosphere, GlobeCloud } from './globe/GlobeComponents';

export const InteractivePixelGlobe = () => {
  return (
    <div className="w-full h-full min-h-[500px] lg:min-h-[700px] relative cursor-grab active:cursor-grabbing">
      <Canvas
        dpr={[1, 2]}
        gl={{ 
          antialias: true, 
          alpha: true,
          powerPreference: "high-performance"
        }}
        className="w-full h-full"
      >
        <PerspectiveCamera makeDefault position={[0, 0, 11]} fov={35} />
        <ambientLight intensity={0.1} />
        <pointLight position={[10, 10, 10]} intensity={1.5} color="#00E5FF" />
        <pointLight position={[-10, -10, -10]} intensity={0.8} color="#0080FF" />
        
        <Suspense fallback={null}>
          <group rotation={[0.3, 0, 0]}>
            <GlobePoints />
            <GlobeCloud />
            <GlobeMarkers />
            <GlobeArcs />
            <GlobeAtmosphere />
          </group>
        </Suspense>

        <OrbitControls
          enableZoom={false}
          enablePan={false}
          rotateSpeed={0.8}
          dampingFactor={0.05}
          enableDamping={true}
          autoRotate={false}
          makeDefault
        />
      </Canvas>
      
      {/* Removed Cinematic Blending Overlays to ensure direct integration with Hero background */}
    </div>
  );
};

export default InteractivePixelGlobe;

import React, { useState, useEffect, useRef } from 'react';
import { motion, AnimatePresence } from 'motion/react';

// Simplified World Map Data (Pixel based)
const WORLD_MAP = [
  "   XXXXX      XXXXX   ",
  "  XXXXXXX    XXXXXXX  ",
  " XXXXXXXXX  XXXXXXXXX ",
  "XXXXXXXXXXXXXXXXXXXXXX",
  " XXXXXXXXXXXXXXXXXXXX ",
  "  XXXXXXXXXXXXXXXXXX  ",
  "   XXXXXXXXXXXXXXXX   ",
  "    XXXXXXXXXXXXXX    ",
  "      XXXXXXXXXX      ",
  "        XXXXXX        "
].map(row => row.split('').map(char => char === 'X'));

const NODES = [
  { id: 1, x: 25, y: 70, label: "New York" },
  { id: 2, x: 48, y: 65, label: "London" },
  { id: 3, x: 75, y: 72, label: "Tokyo" },
  { id: 4, x: 55, y: 85, label: "Dubai" },
  { id: 5, x: 15, y: 82, label: "Los Angeles" },
];

export const PixelGlobe = () => {
  const [activeNode, setActiveNode] = useState<number | null>(null);
  const [hoveredNode, setHoveredNode] = useState<number | null>(null);
  const [rotation, setRotation] = useState(0);

  useEffect(() => {
    const interval = setInterval(() => {
      setRotation(prev => (prev + 0.2) % 360);
    }, 50);
    return () => clearInterval(interval);
  }, []);

  return (
    <div className="w-full h-full flex flex-col items-center justify-center select-none perspective-1000">
      <div 
        className="relative w-[300px] md:w-[450px] aspect-square transition-transform duration-1000 ease-out"
        style={{ transform: `rotateY(${rotation}deg)` }}
      >
        {/* Globe Structure */}
        <div className="absolute inset-0 rounded-full border border-minimal-accent/5 dark:border-dark-accent/5" />
        
        {/* Pixel Grids (Simulating Spherical Projection) */}
        <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
          <div className="grid grid-cols-20 gap-1 opacity-20 dark:opacity-10">
            {Array.from({ length: 400 }).map((_, i) => (
              <div key={i} className="w-1 h-1 bg-current rounded-full" />
            ))}
          </div>
        </div>

        {/* Interaction Nodes */}
        {NODES.map((node) => (
          <motion.div
            key={node.id}
            className="absolute z-30 cursor-pointer group"
            style={{ 
              left: `${node.x}%`, 
              top: `${node.y}%`,
              transform: 'translate(-50%, -50%)'
            }}
            onClick={() => setActiveNode(node.id)}
            onHoverStart={() => setHoveredNode(node.id)}
            onHoverEnd={() => setHoveredNode(null)}
          >
            <div className={`w-3 h-3 rounded-full border-2 transition-all duration-300 ${activeNode === node.id ? 'bg-minimal-accent border-white scale-150 shadow-glow' : 'bg-transparent border-minimal-accent group-hover:scale-125'}`} />
            
            <AnimatePresence>
              {(hoveredNode === node.id || activeNode === node.id) && (
                <motion.div
                  initial={{ opacity: 0, y: -10 }}
                  animate={{ opacity: 1, y: -30 }}
                  exit={{ opacity: 0 }}
                  className="absolute left-1/2 -translate-x-1/2 whitespace-nowrap bg-black text-white text-[8px] font-black uppercase tracking-widest px-2 py-1 rounded-sm"
                >
                  {node.label}
                </motion.div>
              )}
            </AnimatePresence>
          </motion.div>
        ))}

        {/* Connection Arcs (Simple SVG) */}
        <svg className="absolute inset-0 w-full h-full pointer-events-none overflow-visible z-20">
          {activeNode && NODES.filter(n => n.id !== activeNode).map((n, i) => {
            const start = NODES.find(node => node.id === activeNode)!;
            return (
              <motion.path
                key={i}
                initial={{ pathLength: 0, opacity: 0 }}
                animate={{ pathLength: 1, opacity: 0.4 }}
                d={`M ${start.x * 3} ${start.y * 3} Q ${(start.x + n.x) * 1.5} ${(start.y + n.y) * 1.5 - 50} ${n.x * 3} ${n.y * 3}`}
                fill="none"
                stroke="var(--color-minimal-accent)"
                strokeWidth="1"
                strokeDasharray="4 4"
              />
            );
          })}
        </svg>
      </div>

      {/* Controller Feedback */}
      <div className="mt-8 flex gap-4">
         {NODES.map(node => (
            <button 
               key={node.id}
               onClick={() => setActiveNode(node.id)}
               className={`w-2 h-2 rounded-full transition-all ${activeNode === node.id ? 'bg-minimal-accent scale-150' : 'bg-gray-300 dark:bg-gray-700'}`}
            />
         ))}
      </div>
    </div>
  );
};
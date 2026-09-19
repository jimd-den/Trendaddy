import React, { useRef, useState, useEffect } from 'react';

interface VirtualJoystickProps {
  onMove: (dx: number, dy: number) => void;
  onStop: () => void;
  size?: number;
}

export const VirtualJoystick: React.FC<VirtualJoystickProps> = ({
  onMove,
  onStop,
  size = 120,
}) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const [knobPos, setKnobPos] = useState({ x: 0, y: 0 });
  const [active, setActive] = useState(false);

  const radius = size / 2;
  const knobRadius = 24;

  const handlePointer = (clientX: number, clientY: number) => {
    if (!containerRef.current) return;
    const rect = containerRef.current.getBoundingClientRect();
    const centerX = rect.left + radius;
    const centerY = rect.top + radius;

    const dx = clientX - centerX;
    const dy = clientY - centerY;
    const dist = Math.hypot(dx, dy);
    const maxDist = radius - knobRadius;

    let clampedX = dx;
    let clampedY = dy;
    if (dist > maxDist) {
      clampedX = (dx / dist) * maxDist;
      clampedY = (dy / dist) * maxDist;
    }

    setKnobPos({ x: clampedX, y: clampedY });
    onMove(clampedX / maxDist, clampedY / maxDist);
  };

  const handlePointerDown = (e: React.PointerEvent) => {
    setActive(true);
    e.currentTarget.setPointerCapture(e.pointerId);
    handlePointer(e.clientX, e.clientY);
  };

  const handlePointerMove = (e: React.PointerEvent) => {
    if (!active) return;
    handlePointer(e.clientX, e.clientY);
  };

  const handlePointerUp = (e: React.PointerEvent) => {
    setActive(false);
    setKnobPos({ x: 0, y: 0 });
    onStop();
  };

  return (
    <div
      ref={containerRef}
      onPointerDown={handlePointerDown}
      onPointerMove={handlePointerMove}
      onPointerUp={handlePointerUp}
      onPointerCancel={handlePointerUp}
      className="relative rounded-full border-2 border-[#CD7F32]/40 bg-[#14110E]/80 backdrop-blur-sm touch-none select-none shadow-lg shadow-black/60 flex items-center justify-center cursor-grab active:cursor-grabbing"
      style={{ width: size, height: size }}
    >
      {/* Center crosshair */}
      <div className="absolute w-2 h-2 rounded-full bg-[#F4EBDC]/20" />
      
      {/* Knob */}
      <div
        className="absolute rounded-full bg-gradient-to-br from-[#CD7F32] to-[#8C521E] border border-[#F4EBDC]/40 shadow-md shadow-black/80 flex items-center justify-center"
        style={{
          width: knobRadius * 2,
          height: knobRadius * 2,
          transform: `translate(${knobPos.x}px, ${knobPos.y}px)`,
          transition: active ? 'none' : 'transform 0.15s ease-out',
        }}
      >
        <div className="w-2.5 h-2.5 rounded-full bg-[#F4EBDC]" />
      </div>
    </div>
  );
};

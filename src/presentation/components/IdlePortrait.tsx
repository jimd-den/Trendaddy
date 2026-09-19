import React, { useEffect, useState } from 'react';
import { CharacterAggregate } from '../../domain/models/Character';

interface IdlePortraitProps {
  character?: CharacterAggregate;
  size?: number;
  className?: string;
}

export const IdlePortrait: React.FC<IdlePortraitProps> = ({
  character,
  size = 120,
  className = '',
}) => {
  const [frameIndex, setFrameIndex] = useState(0);

  // Cycle idle frames if available
  useEffect(() => {
    const timer = setInterval(() => {
      setFrameIndex(prev => (prev + 1) % 8);
    }, 140);
    return () => clearInterval(timer);
  }, []);

  const idlePoses = character
    ? Object.values(character.poses).filter(p => p.state === 'IDLE')
    : [];

  const currentPose = idlePoses[frameIndex % (idlePoses.length || 1)];
  const imgSrc = currentPose?.imageData || character?.sheetImageData;

  return (
    <div
      className={`relative inline-flex items-center justify-center cut-corner-md bg-[#0D0B09] border border-[#CD7F32]/40 shadow-inner overflow-hidden ${className}`}
      style={{ width: size, height: size }}
    >
      {/* Subtle radial bronze background glow */}
      <div className="absolute inset-0 bg-radial from-[#CD7F32]/15 to-transparent pointer-events-none" />

      {imgSrc ? (
        <img
          src={imgSrc}
          alt={character?.name || 'Character'}
          className="w-full h-full object-contain pixelated rendering-pixelated scale-125 select-none pointer-events-none transition-transform duration-100"
          style={{ imageRendering: 'pixelated' }}
        />
      ) : (
        <div className="flex flex-col items-center justify-center text-center p-2">
          <span className="text-2xl">👤</span>
          <span className="text-[10px] text-[#A1907A] uppercase mt-1">Empty Rig</span>
        </div>
      )}

      {/* Frame overlay */}
      <div className="absolute inset-0 border border-[#F4EBDC]/10 pointer-events-none cut-corner-md" />
    </div>
  );
};

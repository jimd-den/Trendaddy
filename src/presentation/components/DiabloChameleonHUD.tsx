import React from 'react';
import { HudStyle } from '../../data/repositories/SettingsRepository';

interface DiabloChameleonHUDProps {
  style: HudStyle;
  health: number;
  maxHealth: number;
  resource: number;
  maxResource: number;
  resourceName: string;
  level: number;
  cowries: number;
  skills: any[];
  cooldowns: Record<string, number>;
  onCastSkill: (idx: number) => void;
  onAttack: () => void;
  onDodge: () => void;
  dodgeCooldown: number;
}

export const DiabloChameleonHUD: React.FC<DiabloChameleonHUDProps> = ({
  style,
  health,
  maxHealth,
  resource,
  maxResource,
  resourceName,
  level,
  cowries,
  skills,
  cooldowns,
  onCastSkill,
  onAttack,
  onDodge,
  dodgeCooldown,
}) => {
  const hpPercent = Math.min(100, Math.max(0, (health / maxHealth) * 100));
  const resPercent = Math.min(100, Math.max(0, (resource / maxResource) * 100));

  // Determine globe / bar styling based on Chameleon style
  if (style === 'diablo1' || style === 'diablo2') {
    return (
      <div className="relative flex items-end justify-center w-full px-4 pointer-events-none">
        <div className="flex items-center justify-between w-full max-w-2xl bg-[#0D0B09]/95 border-t-2 border-[#5C4A3B] px-4 py-3 shadow-2xl pointer-events-auto rounded-t-xl">
          {/* Health Globe */}
          <div className="flex flex-col items-center">
            <div className="relative w-20 h-20 rounded-full border-4 border-[#3D3126] bg-[#140808] overflow-hidden shadow-inner flex items-end justify-center">
              <div
                className="w-full bg-gradient-to-t from-[#8B0000] via-[#C1453B] to-[#FF4D4D] transition-all duration-200"
                style={{ height: `${hpPercent}%` }}
              />
              <div className="absolute inset-0 flex items-center justify-center font-bold text-xs text-white drop-shadow-md">
                {Math.round(health)}
              </div>
              <div className="absolute top-1 left-2 w-4 h-4 bg-white/20 rounded-full blur-[1px]" />
            </div>
            <span className="text-[10px] text-[#A1907A] font-serif uppercase tracking-widest mt-1">Life</span>
          </div>

          {/* Center Skills Bar */}
          <div className="flex flex-col items-center gap-2">
            <div className="flex items-center gap-3 text-xs text-[#CD7F32]">
              <span className="font-serif">LVL {level}</span>
              <span>•</span>
              <span className="font-mono text-[#F4EBDC]">{cowries} 𓆉</span>
            </div>

            <div className="flex items-center gap-2">
              {/* Melee Strike */}
              <button
                onClick={onAttack}
                className="w-12 h-12 bg-[#211C16] border-2 border-[#CD7F32] rounded flex flex-col items-center justify-center hover:bg-[#3D3126] active:scale-95 cursor-pointer shadow-md"
              >
                <span className="text-sm">⚔</span>
                <span className="text-[9px] text-[#CD7F32] font-mono">[SPC]</span>
              </button>

              {/* Skills */}
              {skills.slice(0, 4).map((sk, idx) => {
                const cd = cooldowns[sk.id] ?? 0;
                return (
                  <button
                    key={sk.id}
                    onClick={() => onCastSkill(idx)}
                    disabled={cd > 0 || resource < sk.cost}
                    className="relative w-11 h-11 bg-[#1A1612] border border-[#F4EBDC]/30 rounded flex flex-col items-center justify-center hover:border-[#CD7F32] active:scale-95 disabled:opacity-40 cursor-pointer"
                  >
                    <span className="text-xs font-bold" style={{ color: sk.color }}>{idx + 1}</span>
                    <span className="text-[8px] text-[#A1907A] truncate max-w-[36px]">{sk.name.split(' ')[0]}</span>
                    {cd > 0 && (
                      <div className="absolute inset-0 bg-black/75 rounded flex items-center justify-center text-xs font-mono text-[#FF8E85]">
                        {cd.toFixed(1)}
                      </div>
                    )}
                  </button>
                );
              })}

              {/* Dodge */}
              <button
                onClick={onDodge}
                disabled={dodgeCooldown > 0}
                className="w-11 h-11 bg-[#1A1612] border border-[#00B8A9]/50 rounded flex flex-col items-center justify-center hover:bg-[#211C16] active:scale-95 disabled:opacity-40 cursor-pointer"
              >
                <span className="text-xs text-[#00B8A9]">⚡</span>
                <span className="text-[9px] text-[#A1907A] font-mono">[SHF]</span>
              </button>
            </div>
          </div>

          {/* Resource / Mana Globe */}
          <div className="flex flex-col items-center">
            <div className="relative w-20 h-20 rounded-full border-4 border-[#3D3126] bg-[#0A101C] overflow-hidden shadow-inner flex items-end justify-center">
              <div
                className="w-full bg-gradient-to-t from-[#1D4ED8] via-[#2563EB] to-[#60A5FA] transition-all duration-200"
                style={{ height: `${resPercent}%` }}
              />
              <div className="absolute inset-0 flex items-center justify-center font-bold text-xs text-white drop-shadow-md">
                {Math.round(resource)}
              </div>
              <div className="absolute top-1 left-2 w-4 h-4 bg-white/20 rounded-full blur-[1px]" />
            </div>
            <span className="text-[10px] text-[#A1907A] font-serif uppercase tracking-widest mt-1">{resourceName}</span>
          </div>
        </div>
      </div>
    );
  }

  // Default Stratum / Diablo 3-4 Vitals Bar
  return (
    <div className="flex items-center justify-between w-full max-w-xl mx-auto px-4 py-2 bg-[#14110E]/95 border-t border-[#F4EBDC]/15 cut-corner-lg shadow-2xl backdrop-blur-md pointer-events-auto">
      {/* Health Gauge */}
      <div className="flex-1 max-w-[150px]">
        <div className="flex justify-between text-[11px] font-bold text-[#F4EBDC] mb-1">
          <span className="text-[#FF8E85]">VITALITY</span>
          <span>{Math.round(health)}/{maxHealth}</span>
        </div>
        <div className="w-full h-3 bg-[#0D0B09] border border-[#F4EBDC]/20 cut-corner-sm overflow-hidden p-0.5">
          <div
            className="h-full bg-gradient-to-r from-[#8B0000] to-[#C1453B] transition-all duration-200"
            style={{ width: `${hpPercent}%` }}
          />
        </div>
      </div>

      {/* Action Buttons */}
      <div className="flex items-center gap-1.5 px-3">
        <button
          onClick={onAttack}
          className="px-3 py-2 bg-[#CD7F32] hover:bg-[#E5984A] text-[#14110E] font-bold text-xs uppercase cut-corner-sm flex items-center gap-1 active:scale-95 cursor-pointer shadow"
        >
          <span>⚔</span>
          <span>STRIKE</span>
        </button>

        {skills.slice(0, 3).map((sk, idx) => {
          const cd = cooldowns[sk.id] ?? 0;
          return (
            <button
              key={sk.id}
              onClick={() => onCastSkill(idx)}
              disabled={cd > 0 || resource < sk.cost}
              className="relative w-9 h-9 bg-[#211C16] border border-[#F4EBDC]/20 hover:border-[#CD7F32] cut-corner-sm flex items-center justify-center text-xs font-bold active:scale-95 disabled:opacity-40 cursor-pointer"
            >
              <span style={{ color: sk.color }}>{idx + 1}</span>
              {cd > 0 && (
                <div className="absolute inset-0 bg-black/80 flex items-center justify-center text-[10px] text-amber-300 font-mono">
                  {cd.toFixed(1)}
                </div>
              )}
            </button>
          );
        })}

        <button
          onClick={onDodge}
          disabled={dodgeCooldown > 0}
          className="px-2.5 py-2 bg-[#211C16] hover:bg-[#2D261E] border border-[#00B8A9]/60 text-[#00B8A9] font-bold text-xs uppercase cut-corner-sm active:scale-95 disabled:opacity-40 cursor-pointer"
        >
          ROLL
        </button>
      </div>

      {/* Resource Gauge */}
      <div className="flex-1 max-w-[150px]">
        <div className="flex justify-between text-[11px] font-bold text-[#F4EBDC] mb-1">
          <span className="text-[#70E7DC]">{resourceName.toUpperCase()}</span>
          <span>{Math.round(resource)}/{maxResource}</span>
        </div>
        <div className="w-full h-3 bg-[#0D0B09] border border-[#F4EBDC]/20 cut-corner-sm overflow-hidden p-0.5">
          <div
            className="h-full bg-gradient-to-r from-[#008075] to-[#00B8A9] transition-all duration-200"
            style={{ width: `${resPercent}%` }}
          />
        </div>
      </div>
    </div>
  );
};

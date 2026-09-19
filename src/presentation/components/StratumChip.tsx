import React from 'react';

interface StratumChipProps {
  label: string;
  selected?: boolean;
  onClick?: () => void;
  icon?: React.ReactNode;
  badge?: string | number;
  colorSwatch?: string;
  roleBadge?: 'hero' | 'enemy';
  unpackedNotice?: boolean;
  className?: string;
}

export const StratumChip: React.FC<StratumChipProps> = ({
  label,
  selected = false,
  onClick,
  icon,
  badge,
  colorSwatch,
  roleBadge,
  unpackedNotice,
  className = '',
}) => {
  return (
    <button
      onClick={onClick}
      className={`relative inline-flex items-center gap-2 px-3 py-2 text-xs font-semibold tracking-wide uppercase transition-all duration-150 cut-corner-sm cursor-pointer whitespace-nowrap border ${
        selected
          ? 'bg-[#CD7F32]/20 border-[#CD7F32] text-[#F4EBDC] shadow-md shadow-[#CD7F32]/10'
          : 'bg-[#1A1612] border-[#F4EBDC]/10 hover:border-[#F4EBDC]/30 text-[#A1907A] hover:text-[#F4EBDC]'
      } ${className}`}
    >
      {colorSwatch && (
        <span
          className="w-3 h-3 rounded-full shrink-0 border border-black/40"
          style={{ backgroundColor: colorSwatch }}
        />
      )}
      {icon && <span className="shrink-0">{icon}</span>}
      <span>{label}</span>
      {roleBadge && (
        <span
          className={`px-1.5 py-0.5 text-[9px] font-bold rounded ${
            roleBadge === 'enemy' ? 'bg-[#C1453B]/30 text-[#FF8E85]' : 'bg-[#00B8A9]/30 text-[#70E7DC]'
          }`}
        >
          {roleBadge}
        </span>
      )}
      {unpackedNotice && (
        <span className="w-2 h-2 rounded-full bg-amber-400 animate-pulse" title="Poses drawn but not packed" />
      )}
      {badge !== undefined && (
        <span className="ml-1 px-1.5 py-0.5 text-[10px] bg-black/40 text-[#F4EBDC] rounded font-mono">
          {badge}
        </span>
      )}
    </button>
  );
};

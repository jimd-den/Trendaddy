import React from 'react';

interface StratumPanelProps {
  children: React.ReactNode;
  className?: string;
  cut?: 'sm' | 'md' | 'lg' | 'none';
  variant?: 'raised' | 'sunken' | 'base';
  title?: string;
  subtitle?: string;
  actions?: React.ReactNode;
}

export const StratumPanel: React.FC<StratumPanelProps> = ({
  children,
  className = '',
  cut = 'md',
  variant = 'raised',
  title,
  subtitle,
  actions,
}) => {
  const cutClass = {
    sm: 'cut-corner-sm',
    md: 'cut-corner-md',
    lg: 'cut-corner-lg',
    none: '',
  }[cut];

  const variantClass = {
    raised: 'bg-[#211C16] border border-[#F4EBDC]/12 shadow-xl shadow-black/60',
    sunken: 'bg-[#0D0B09] border border-[#F4EBDC]/8 inset-shadow-sm',
    base: 'bg-[#14110E] border border-[#F4EBDC]/10',
  }[variant];

  return (
    <div className={`relative ${cutClass} ${variantClass} ${className}`}>
      {(title || subtitle || actions) && (
        <div className="flex items-center justify-between px-4 py-3 border-b border-[#F4EBDC]/10 bg-[#1A1612]/50">
          <div>
            {title && (
              <h3 className="font-bold text-sm tracking-wider uppercase text-[#F4EBDC]">
                {title}
              </h3>
            )}
            {subtitle && (
              <p className="text-xs text-[#A1907A] tracking-normal font-normal">
                {subtitle}
              </p>
            )}
          </div>
          {actions && <div className="flex items-center gap-2">{actions}</div>}
        </div>
      )}
      <div className="p-4">{children}</div>
    </div>
  );
};

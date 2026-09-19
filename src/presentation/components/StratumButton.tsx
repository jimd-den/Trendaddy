import React from 'react';

export type ButtonVariant = 'primary' | 'secondary' | 'accent' | 'danger' | 'quiet';

interface StratumButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: 'sm' | 'md' | 'lg';
  icon?: React.ReactNode;
  children: React.ReactNode;
}

export const StratumButton: React.FC<StratumButtonProps> = ({
  variant = 'primary',
  size = 'md',
  icon,
  children,
  className = '',
  disabled,
  ...props
}) => {
  const baseClasses = 'inline-flex items-center justify-center font-bold tracking-wide uppercase transition-all duration-150 active:scale-[0.98] cursor-pointer disabled:opacity-40 disabled:pointer-events-none whitespace-nowrap';

  const sizeClasses = {
    sm: 'text-xs px-3 py-1.5 gap-1.5 cut-corner-sm',
    md: 'text-sm px-5 py-2.5 gap-2 cut-corner-md',
    lg: 'text-base px-7 py-3.5 gap-2.5 cut-corner-lg',
  }[size];

  const variantClasses = {
    primary: 'bg-[#CD7F32] hover:bg-[#E5984A] text-[#14110E] border-t border-[#F4EBDC]/30 shadow-md shadow-black/40',
    secondary: 'bg-[#211C16] hover:bg-[#2D261E] text-[#F4EBDC] border border-[#F4EBDC]/15 hover:border-[#CD7F32]/50',
    accent: 'bg-[#00B8A9] hover:bg-[#00D4C3] text-[#14110E] border-t border-white/30 shadow-md shadow-black/40',
    danger: 'bg-[#C1453B] hover:bg-[#D6544A] text-[#F4EBDC] border border-red-400/20',
    quiet: 'bg-transparent hover:bg-[#211C16]/60 text-[#A1907A] hover:text-[#F4EBDC]',
  }[variant];

  return (
    <button
      className={`${baseClasses} ${sizeClasses} ${variantClasses} ${className}`}
      disabled={disabled}
      {...props}
    >
      {icon && <span className="shrink-0">{icon}</span>}
      <span>{children}</span>
    </button>
  );
};

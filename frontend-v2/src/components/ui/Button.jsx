import { cn } from '@/lib/utils'

const variants = {
  primary: 'bg-gradient-to-r from-cyan-500 to-blue-500 text-white hover:brightness-105 active:brightness-95 shadow-[0_8px_20px_-8px_rgba(14,165,233,0.55)]',
  navy: 'bg-[#1e3a5f] text-white hover:bg-[#2a4f82] active:bg-[#152a47] shadow-sm',
  outline: 'border border-slate-200 text-[#1e3a5f] bg-white hover:bg-slate-50 hover:border-slate-300',
  ghost: 'text-slate-500 hover:bg-slate-100 hover:text-slate-800',
  danger: 'bg-red-600 text-white hover:bg-red-700 active:bg-red-800 shadow-sm',
  'outline-danger': 'border border-red-200 text-red-600 bg-white hover:bg-red-50 hover:border-red-300',
}

const sizes = {
  sm: 'h-8 px-3 text-xs rounded-lg',
  md: 'h-10 px-5 text-sm rounded-xl',
  lg: 'h-12 px-7 text-sm rounded-2xl',
  icon: 'h-10 w-10 rounded-xl',
}

export function Button({
  children,
  variant = 'primary',
  size = 'md',
  className,
  disabled,
  loading,
  ...props
}) {
  return (
    <button
      disabled={disabled || loading}
      className={cn(
        'inline-flex items-center justify-center gap-2 font-semibold tracking-wide transition-all duration-150',
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#38bdf8] focus-visible:ring-offset-2',
        'disabled:pointer-events-none disabled:opacity-50 cursor-pointer',
        variants[variant],
        sizes[size],
        className
      )}
      {...props}
    >
      {loading && (
        <svg className="h-4 w-4 animate-spin" fill="none" viewBox="0 0 24 24">
          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
        </svg>
      )}
      {children}
    </button>
  )
}

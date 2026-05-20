import { cn } from '@/lib/utils'

export function Input({ className, label, error, ...props }) {
  return (
    <div className="flex flex-col gap-1.5">
      {label && (
        <label className="text-[11px] font-semibold text-slate-500 uppercase tracking-wide">{label}</label>
      )}
      <input
        className={cn(
          'h-11 w-full rounded-2xl border border-slate-200 bg-slate-50 px-3.5 py-2 text-sm text-[#1f2c4a] placeholder:text-slate-400',
          'transition-all duration-150',
          'focus:outline-none focus:bg-white focus:border-blue-400 focus:ring-4 focus:ring-blue-100',
          'disabled:cursor-not-allowed disabled:opacity-60',
          error && 'border-red-300 focus:ring-red-100 focus:border-red-400',
          className
        )}
        {...props}
      />
      {error && <p className="text-xs text-red-500 font-medium">{error}</p>}
    </div>
  )
}

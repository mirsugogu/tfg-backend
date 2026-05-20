import { cn } from '@/lib/utils'

export function Textarea({ label, error, className, ...props }) {
  return (
    <div className="flex flex-col gap-1.5">
      {label && (
        <label className="text-[11px] font-semibold text-slate-500 uppercase tracking-wide">{label}</label>
      )}
      <textarea
        className={cn(
          'w-full rounded-2xl border border-slate-200 bg-slate-50 px-3.5 py-3 text-sm text-[#1f2c4a] placeholder:text-slate-400 resize-none',
          'transition-all duration-150',
          'focus:outline-none focus:bg-white focus:border-blue-400 focus:ring-4 focus:ring-blue-100',
          'disabled:cursor-not-allowed disabled:opacity-60',
          error && 'border-red-300',
          className
        )}
        rows={3}
        {...props}
      />
      {error && <p className="text-xs text-red-500 font-medium">{error}</p>}
    </div>
  )
}

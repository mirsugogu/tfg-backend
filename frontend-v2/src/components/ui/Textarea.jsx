import { useId } from 'react'
import { cn } from '@/lib/utils'

/** Campo de texto multilínea con etiqueta y mensaje de error opcionales. */
export function Textarea({ label, error, className, id, ...props }) {
  // useId asocia la <label> con el <textarea> (htmlFor/id) para accesibilidad.
  const autoId = useId()
  const fieldId = id ?? autoId
  return (
    <div className="flex flex-col gap-1.5">
      {label && (
        <label htmlFor={fieldId} className="text-[11px] font-semibold text-slate-500 uppercase tracking-wide">{label}</label>
      )}
      <textarea
        id={fieldId}
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

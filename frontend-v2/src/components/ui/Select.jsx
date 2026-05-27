import { useId } from 'react'
import { cn } from '@/lib/utils'
import { ChevronDown } from 'lucide-react'

/**
 * Desplegable estilizado con etiqueta y mensaje de error opcionales,
 * alineado visualmente con `Input` y `Textarea`. Las `<option>` se pasan
 * como `children` para no atar la API del componente a un schema fijo.
 */
export function Select({ label, error, className, children, id, ...props }) {
  // useId asocia la <label> con el <select> (htmlFor/id) para accesibilidad.
  const autoId = useId()
  const fieldId = id ?? autoId
  return (
    <div className="flex flex-col gap-1.5">
      {label && (
        <label htmlFor={fieldId} className="text-[11px] font-semibold text-slate-500 uppercase tracking-wide">{label}</label>
      )}
      <div className="relative">
        <select
          id={fieldId}
          className={cn(
            'h-11 w-full appearance-none rounded-2xl border border-slate-200 bg-slate-50 px-3.5 pr-10 text-sm text-[#1f2c4a]',
            'transition-all duration-150',
            'focus:outline-none focus:bg-white focus:border-blue-400 focus:ring-4 focus:ring-blue-100',
            'disabled:cursor-not-allowed disabled:opacity-60',
            error && 'border-red-300',
            className
          )}
          {...props}
        >
          {children}
        </select>
        <ChevronDown size={15} className="pointer-events-none absolute right-3.5 top-1/2 -translate-y-1/2 text-slate-400" />
      </div>
      {error && <p className="text-xs text-red-500 font-medium">{error}</p>}
    </div>
  )
}

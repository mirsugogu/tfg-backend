import { useId } from 'react'
import { cn } from '@/lib/utils'

/** Reglas de limpieza para campos de entrada */
export const INPUT_SANITIZE = {
  DIGITS_ONLY: /[^0-9]/g,
  PHONE: /[^0-9+\s()\-]/g,
}

// Teclas de edicion/navegacion que nunca se filtran
const CONTROL_KEYS = new Set([
  'Backspace', 'Delete', 'Tab', 'Escape', 'Enter',
  'ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown',
  'Home', 'End',
])

/** Filtra teclas no numericas respetando atajos de edicion */
function saneNumericKey(e, allowDecimal, userOnKeyDown) {
  if (CONTROL_KEYS.has(e.key)) {
    userOnKeyDown?.(e)
    return
  }
  if ((e.ctrlKey || e.metaKey) && ['a', 'c', 'v', 'x'].includes(e.key.toLowerCase())) {
    userOnKeyDown?.(e)
    return
  }
  const isDigit = e.key.length === 1 && e.key >= '0' && e.key <= '9'
  const isAllowedDot = allowDecimal && e.key === '.'
  if (!isDigit && !isAllowedDot) {
    e.preventDefault()
    return
  }
  userOnKeyDown?.(e)
}

/** Campo con etiqueta, error y limpieza opcional */
export function Input({ className, label, error, id, onKeyDown, onChange, sanitize, ...props }) {
  const autoId = useId()
  const fieldId = id ?? autoId

  const isNumber = props.type === 'number'
  const allowDecimal = isNumber && props.step != null && String(props.step).includes('.')
  const handleKeyDown = isNumber
    ? (e) => saneNumericKey(e, allowDecimal, onKeyDown)
    : onKeyDown

  // Tambien cubre pegado y autocompletado
  const handleChange = (e) => {
    if (sanitize) {
      const cleaned = e.target.value.replace(sanitize, '')
      if (cleaned !== e.target.value) {
        const setter = Object.getOwnPropertyDescriptor(
          window.HTMLInputElement.prototype, 'value',
        )?.set
        setter?.call(e.target, cleaned)
      }
    }
    onChange?.(e)
  }

  return (
    <div className="flex flex-col gap-1.5">
      {label && (
        <label htmlFor={fieldId} className="text-[11px] font-semibold text-slate-500 uppercase tracking-wide">{label}</label>
      )}
      <input
        id={fieldId}
        onKeyDown={handleKeyDown}
        onChange={handleChange}
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

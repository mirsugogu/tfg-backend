import { useId } from 'react'
import { cn } from '@/lib/utils'

/**
 * Sanitizadores predefinidos que se pueden pasar a la prop `sanitize` del
 * Input. Cada uno es una regex que MATCHEA los caracteres a eliminar (la
 * regex se aplica con .replace(regex, '')).
 *
 * - DIGITS_ONLY: solo digitos (codigo postal, DNI numerico, etc.).
 * - PHONE: digitos + simbolos comunes de telefonia internacional. Permite
 *   formatos como "+34 600 000 000" o "(91) 555 0000" sin imponer un pais.
 *
 * Aplicar uno de estos a un Input garantiza que el usuario no pueda
 * teclear (ni pegar) caracteres invalidos: el setter nativo del <input>
 * reescribe el value antes de que llegue al onChange del caller.
 */
export const INPUT_SANITIZE = {
  DIGITS_ONLY: /[^0-9]/g,
  PHONE: /[^0-9+\s()\-]/g,
}

/**
 * Teclas de edición/navegación que NUNCA se filtran, sea cual sea el type.
 * Sin esta lista, el handler que sanea inputs numéricos rompería el borrado
 * y el desplazamiento con flechas.
 */
const CONTROL_KEYS = new Set([
  'Backspace', 'Delete', 'Tab', 'Escape', 'Enter',
  'ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown',
  'Home', 'End',
])

/**
 * `<input type="number">` admite por defecto la notación científica (`e`, `E`)
 * y los signos `+`/`-`, lo que permite teclear "letras" en un campo numérico
 * y romper la validación visual. Este handler filtra todo lo que no sea
 * dígito, con la excepción del punto decimal cuando el `step` es decimal.
 *
 * Pega-segura: Ctrl+A/C/V/X y las teclas de edición pasan tal cual.
 *
 * @param e             evento keydown
 * @param allowDecimal  permitir el punto (cuando el caller indica step="0.x")
 * @param userOnKeyDown handler original del caller, para no perder cadenas
 */
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

export function Input({ className, label, error, id, onKeyDown, onChange, sanitize, ...props }) {
  // useId genera un id estable y unico por instancia: asi la <label> queda
  // asociada al <input> (htmlFor/id) y los lectores de pantalla la anuncian
  // al enfocar el campo. Si el caller pasa `id` explicito, ese tiene prioridad.
  const autoId = useId()
  const fieldId = id ?? autoId

  // Para los campos type=number aplicamos un saneamiento de tecla: bloquea
  // 'e', 'E', '+', '-', ',' y cualquier carácter no dígito. El punto decimal
  // se permite solo si el caller indicó un step decimal (p. ej. step="0.01"
  // para precios o porcentajes).
  const isNumber = props.type === 'number'
  const allowDecimal = isNumber && props.step != null && String(props.step).includes('.')
  const handleKeyDown = isNumber
    ? (e) => saneNumericKey(e, allowDecimal, onKeyDown)
    : onKeyDown

  // Sanitize: cubre lo que el filtro de tecla no llega a tapar (pegar con
  // raton, autocompletar del navegador, drag&drop, IME). Reescribe el
  // value DOM con el setter nativo para que React detecte el cambio
  // sintetico y propague el onChange con el valor ya limpio.
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

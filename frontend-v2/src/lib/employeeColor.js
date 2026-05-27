/* Helpers para la firma de color del empleado, coherente con el calendario. */
import { paletteByName, PALETTES } from '@/components/calendar/utils'

/** Clase Tailwind del dot del empleado; usa `color` si está asignado, si no determinista por id. */
export function employeeDotClass(employee) {
  if (!employee) return 'bg-slate-400'
  const p = paletteByName(employee.color)
  if (p) return p.dot
  return PALETTES[(employee.id ?? 0) % PALETTES.length].dot
}

/** Variante para callers que solo tienen el color y el id (no el objeto entero). */
export function dotClassFromColorAndId(color, id) {
  const p = paletteByName(color)
  if (p) return p.dot
  return PALETTES[((id ?? 0) % PALETTES.length)].dot
}

/**
 * Helpers para mostrar la firma de color de un empleado en la UI de forma
 * coherente con el calendario.
 *
 * El backend persiste `memberships.color` como string corto (cyan, amber,
 * emerald, indigo, pink, sky, violet, teal) o null. La paleta esta definida
 * en components/calendar/utils.js y la reutilizamos aqui para no duplicar
 * los colores Tailwind. Si la membership no tiene color asignado, se cae a
 * un color automatico determinista por id, igual que hace el calendario en
 * styleFor() cuando colorBy='employee'.
 *
 * No es un componente: solo devuelve strings de clases para que cada caller
 * decida que poner alrededor (un span con .rounded-full, un anillo, etc.).
 */
import { paletteByName, PALETTES } from '@/components/calendar/utils'

/**
 * Devuelve la clase Tailwind del "dot" (fondo saturado -500) para el
 * empleado, lista para meter en un span redondo de cualquier tamano.
 *
 * @param employee  objeto con `.color` (opcional) y `.id` para el fallback.
 *                  Pasar null/undefined devuelve un gris neutro.
 */
export function employeeDotClass(employee) {
  if (!employee) return 'bg-slate-400'
  const p = paletteByName(employee.color)
  if (p) return p.dot
  return PALETTES[(employee.id ?? 0) % PALETTES.length].dot
}

/**
 * Variante para callers que solo tienen el id y el color (p. ej. el wizard
 * o el detalle de una cita, donde el empleado se aplana en
 * `membershipId + employeeColor`). Es el mismo algoritmo que arriba.
 */
export function dotClassFromColorAndId(color, id) {
  const p = paletteByName(color)
  if (p) return p.dot
  return PALETTES[((id ?? 0) % PALETTES.length)].dot
}

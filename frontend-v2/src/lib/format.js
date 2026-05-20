/**
 * Helpers de formato compartidos por las pantallas y modales de citas
 * (Citas, Calendario, AppointmentWizard, AppointmentDetailModal).
 */

/**
 * El backend serializa LocalTime como "HH:mm" (cuando los segundos son 0)
 * o "HH:mm:ss". Normaliza siempre a "HH:mm:ss" para poder construir un
 * startDateTime válido.
 */
export const toHms = (t) => {
  const parts = String(t ?? '').split(':')
  while (parts.length < 3) parts.push('00')
  return parts.slice(0, 3).map((p) => p.padStart(2, '0')).join(':')
}

/** "HH:mm" para mostrar una hora. */
export const hhmm = (t) => toHms(t).slice(0, 5)

/** Fecha + hora legible a partir de un LocalDateTime ISO del backend. */
export const formatDateTime = (dt) =>
  new Date(dt).toLocaleString('es-ES', {
    day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
  })

/** Fecha larga ("lunes, 25 de mayo de 2026") a partir de un "YYYY-MM-DD". */
export const formatDateLong = (ymd) =>
  new Date(`${ymd}T00:00:00`).toLocaleDateString('es-ES', {
    weekday: 'long', day: '2-digit', month: 'long', year: 'numeric',
  })

/** Suma de los precios congelados de los servicios reservados de una cita. */
export const totalBooked = (bookedServices) =>
  ((bookedServices ?? []).reduce((acc, s) => acc + Number(s.appliedPrice || 0), 0)).toFixed(2)

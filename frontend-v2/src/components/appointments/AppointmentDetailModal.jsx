import { useState } from 'react'
import { MapPin, Pencil, Ban } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { StatusBadge, Badge } from '@/components/ui/Badge'
import { useToast } from '@/components/ui/Toast'
import { useCatalog } from '@/context/CatalogContext'
import api, { getErrorMessage } from '@/lib/api'
import { formatDateTime, totalBooked } from '@/lib/format'
import { dotClassFromColorAndId } from '@/lib/employeeColor'

/**
 * Estados en los que NO aparece el botón "Editar cita".
 * Espejo del Set NON_EDITABLE_STATUSES de AppointmentService.java (P9):
 * solo COMPLETED es no-editable, una cita realizada no se reagenda.
 * CANCELLED y NO_SHOW SI son editables: el caso de uso real es "el cliente
 * del 21 no se presentó, hoy es 24, lo reagendo al 28". Al guardar, el
 * backend reseteará automáticamente la cita a PENDING.
 */
const NON_EDITABLE_STATUSES = new Set(['COMPLETED'])

/**
 * Máquina de estados de las citas. Copia EXACTA de
 * AppointmentValidator.VALID_TRANSITIONS del backend (auditoría
 * docs/audit/00_MAPA_REAL.md sección 5). COMPLETED, CANCELLED y NO_SHOW
 * son finales. El modal solo ofrece estas transiciones, así que es
 * imposible elegir una inválida desde la UI.
 */
const VALID_TRANSITIONS = {
  PENDING:     ['CONFIRMED', 'CANCELLED'],
  CONFIRMED:   ['IN_PROGRESS', 'CANCELLED', 'NO_SHOW'],
  IN_PROGRESS: ['COMPLETED', 'CANCELLED'],
  COMPLETED:   [],
  CANCELLED:   [],
  NO_SHOW:     [],
}

/**
 * AppointmentDetailModal — detalle de una cita, con cambio de estado
 * (en línea), marcado de pago y acceso al modo edición (P9). Reutilizable
 * por Citas y Calendario.
 *
 * Props:
 *   appointment   la cita a mostrar; null = modal cerrado.
 *   bId           businessId.
 *   onClose       cerrar el modal.
 *   onChanged     callback tras cambiar estado o pago (el padre refresca).
 *   onEdit        opcional; si viene, muestra el botón "Editar cita" para
 *                 las citas no terminales. Recibe la cita actual y es el
 *                 padre quien decide qué hacer (típicamente: cerrar este
 *                 modal y abrir el AppointmentWizard en modo edición).
 *   appliedBlock   opcional; schedule_block aplicable a esta cita (global,
 *                  por empleado o por cabina) calculado por el padre con
 *                  blockForAppointment(). Si viene, se muestra un aviso
 *                  destacado y se sugiere reagendar. Citas.jsx no lo pasa
 *                  (no carga bloqueos); Calendario.jsx sí.
 *   appliedAbsence opcional; EmployeeAbsence que solapa con esta cita y
 *                  apunta al mismo empleado. Mismo banner que appliedBlock
 *                  pero motivo "ausencia del empleado". Solo lo pasa
 *                  Calendario.jsx (Citas no carga ausencias).
 *   employeeColor  opcional; nombre de la paleta del empleado (cyan,
 *                  amber...). Lo pasa el padre desde su empColorMap. Si
 *                  viene, se pinta un punto del color al lado del campo
 *                  "Empleado", para coherencia con el calendario.
 */
export function AppointmentDetailModal({
  appointment, bId, onClose, onChanged, onEdit,
  appliedBlock, appliedAbsence, employeeColor,
}) {
  const toast = useToast()
  const { statusLabel } = useCatalog()

  // Copia local de la cita. Se sincroniza de forma síncrona cuando el
  // padre selecciona otra cita distinta (sin parpadeo); tras un PATCH NO
  // se resincroniza, porque el id no cambia y 'current' ya tiene la
  // versión actualizada que devolvió el backend.
  const [current, setCurrent] = useState(appointment)
  const [trackedId, setTrackedId] = useState(appointment?.id ?? null)
  const [saving, setSaving] = useState(false)

  if ((appointment?.id ?? null) !== trackedId) {
    setTrackedId(appointment?.id ?? null)
    setCurrent(appointment)
  }

  const changeStatus = async (statusName) => {
    setSaving(true)
    try {
      const { data } = await api.patch(
        `/api/businesses/${bId}/appointments/${current.id}/status`,
        { statusName },
      )
      setCurrent(data)
      toast({ type: 'success', message: `Estado actualizado a ${statusLabel(statusName)}.` })
      onChanged?.()
    } catch (err) {
      toast({ type: 'error', message: getErrorMessage(err, 'No se pudo cambiar el estado.') })
    } finally {
      setSaving(false)
    }
  }

  const togglePayment = async () => {
    setSaving(true)
    try {
      const { data } = await api.patch(
        `/api/businesses/${bId}/appointments/${current.id}/payment`,
        { isPaid: !current.isPaid },
      )
      setCurrent(data)
      toast({ type: 'success', message: data.isPaid ? 'Marcado como pagado.' : 'Marcado como pendiente.' })
      onChanged?.()
    } catch (err) {
      toast({ type: 'error', message: getErrorMessage(err, 'No se pudo actualizar el pago.') })
    } finally {
      setSaving(false)
    }
  }

  const transitions = current ? (VALID_TRANSITIONS[current.statusName] ?? []) : []

  // El motivo del bloqueo se calcula en el padre (Calendario): aquí solo se
  // muestra. Si llega sin reason, se etiqueta segun el tipo del bloqueo.
  const blockLabel = appliedBlock
    ? (appliedBlock.reason && appliedBlock.reason.trim()
        ? appliedBlock.reason
        : appliedBlock.membershipId != null ? 'Empleado bloqueado'
        : appliedBlock.boothId != null ? 'Cabina bloqueada'
        : 'Día bloqueado')
    : null

  return (
    <Modal open={Boolean(appointment)} onClose={onClose} title="Detalle de cita" size="lg">
      {current && (
        <div className="space-y-5">
          {appliedBlock && (
            <div className="rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 flex items-start gap-3">
              <div className="shrink-0 w-9 h-9 rounded-xl bg-rose-100 flex items-center justify-center text-rose-600">
                <Ban size={18} />
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-[10px] font-bold text-rose-700 uppercase tracking-wider">
                  Esta cita está en un día bloqueado
                </p>
                <p className="text-sm font-semibold text-rose-800 mt-0.5 truncate">
                  Motivo: {blockLabel}
                </p>
                <p className="text-xs text-rose-700/80 mt-1">
                  Edita la cita y cambia la fecha; el bloqueo impide mantenerla este día.
                </p>
              </div>
            </div>
          )}

          {appliedAbsence && (
            <div className="rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 flex items-start gap-3">
              <div className="shrink-0 w-9 h-9 rounded-xl bg-rose-100 flex items-center justify-center text-rose-600">
                <Ban size={18} />
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-[10px] font-bold text-rose-700 uppercase tracking-wider">
                  El empleado tiene una ausencia registrada
                </p>
                <p className="text-sm font-semibold text-rose-800 mt-0.5 truncate">
                  Motivo: {appliedAbsence.reason?.trim() ? appliedAbsence.reason : 'Ausencia del empleado'}
                </p>
                <p className="text-xs text-rose-700/80 mt-1">
                  Edita la cita y cambia hora o empleado; la ausencia impide mantenerla así.
                </p>
              </div>
            </div>
          )}

          <div className="grid sm:grid-cols-2 gap-4">
            {[
              { label: 'Cliente',  value: current.clientName },
              {
                label: 'Empleado',
                value: current.userFullName,
                dot: dotClassFromColorAndId(employeeColor, current.membershipId),
              },
              { label: 'Inicio',   value: formatDateTime(current.startDateTime) },
              { label: 'Fin',      value: formatDateTime(current.endDateTime) },
            ].map(({ label, value, dot }) => (
              <div key={label} className="rounded-2xl bg-slate-50 border border-slate-100 px-4 py-3">
                <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-1">{label}</p>
                <p className="text-sm font-semibold text-[#1e3a5f] flex items-center gap-2">
                  {dot && <span className={`w-2 h-2 rounded-full shrink-0 ${dot}`} aria-hidden />}
                  <span className="truncate">{value}</span>
                </p>
              </div>
            ))}
          </div>

          <div className="grid grid-cols-3 gap-3">
            <div className="rounded-2xl bg-slate-50 border border-slate-100 px-4 py-3">
              <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-1.5">Estado</p>
              <StatusBadge status={current.statusName} />
            </div>
            <div className="rounded-2xl bg-slate-50 border border-slate-100 px-4 py-3">
              <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-1.5">Pago</p>
              <div className="flex items-center gap-2">
                <Badge variant={current.isPaid ? 'success' : 'warning'}>
                  {current.isPaid ? 'Pagado' : 'Pendiente'}
                </Badge>
                <button
                  onClick={togglePayment}
                  disabled={saving}
                  className="text-[10px] font-semibold text-blue-600 hover:text-blue-700 bg-blue-50 hover:bg-blue-100 rounded-lg px-2 py-1 transition-colors disabled:opacity-50"
                >
                  {current.isPaid ? 'Desmarcar' : 'Marcar pagado'}
                </button>
              </div>
            </div>
            <div className="rounded-2xl bg-slate-50 border border-slate-100 px-4 py-3">
              <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-1.5">Cabina</p>
              <p className="text-sm font-semibold text-[#1e3a5f] flex items-center gap-1.5">
                {current.boothName
                  ? <><MapPin size={13} className="text-slate-400" />{current.boothName}</>
                  : <span className="text-slate-300">Sin cabina</span>}
              </p>
            </div>
          </div>

          {current.notes && (
            <div className="rounded-2xl bg-slate-50 border border-slate-100 px-4 py-3">
              <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-1">Notas</p>
              <p className="text-sm text-slate-600">{current.notes}</p>
            </div>
          )}

          {current.bookedServices?.length > 0 && (
            <div>
              <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-3">Servicios contratados</p>
              <div className="space-y-2">
                {current.bookedServices.map((s) => (
                  <div key={s.id} className="flex justify-between items-center text-sm bg-slate-50 rounded-2xl border border-slate-100 px-4 py-3">
                    <span className="font-medium text-slate-700">{s.serviceName}</span>
                    <div className="text-right">
                      <span className="font-bold text-[#1e3a5f]">{Number(s.appliedPrice).toFixed(2)} €</span>
                      <span className="text-xs text-slate-400 ml-2">IVA {s.appliedTaxPercentage}%</span>
                    </div>
                  </div>
                ))}
                <div className="flex justify-between items-center font-bold text-[#1e3a5f] border-t border-slate-200 pt-3 px-1">
                  <span>Total</span>
                  <span className="text-lg">{totalBooked(current.bookedServices)} €</span>
                </div>
              </div>
            </div>
          )}

          {transitions.length > 0 ? (
            <div className="rounded-2xl bg-slate-50 border border-slate-100 px-4 py-3">
              <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-2">Cambiar estado</p>
              <div className="flex flex-wrap gap-2">
                {transitions.map((s) => (
                  <Button key={s} variant="outline" size="sm" onClick={() => changeStatus(s)} disabled={saving}>
                    {statusLabel(s)}
                  </Button>
                ))}
              </div>
            </div>
          ) : (
            <p className="text-xs text-slate-400 text-center">
              Esta cita está en un estado final y no admite más cambios de estado.
            </p>
          )}

          {onEdit && !NON_EDITABLE_STATUSES.has(current.statusName) && (
            <div className="pt-2 border-t border-slate-100 flex justify-end">
              <Button
                variant="outline"
                size="sm"
                onClick={() => onEdit(current)}
                disabled={saving}
                className="gap-1.5"
              >
                <Pencil size={13} /> Editar cita
              </Button>
            </div>
          )}
        </div>
      )}
    </Modal>
  )
}

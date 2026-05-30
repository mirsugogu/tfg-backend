// Asistente de 3 pasos para crear o editar citas con consulta de disponibilidad
import { useEffect, useMemo, useState } from 'react'
import { Check, ArrowRight, ArrowLeft, User, CalendarDays, Clock, MapPin, Lock } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { Select } from '@/components/ui/Select'
import { Textarea } from '@/components/ui/Textarea'
import { Modal } from '@/components/ui/Modal'
import { useToast } from '@/components/ui/Toast'
import api, { getErrorMessage } from '@/lib/api'
import { toHms, hhmm, formatDateLong } from '@/lib/format'
import { ClientPicker } from './ClientPicker'

// Fecha de hoy en 'YYYY-MM-DD' local; toISOString() usaria UTC y desplazaria el dia
const todayStr = () => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
const emptyForm = { clientId: '', clientName: '', membershipId: '', boothId: '', serviceIds: [], date: todayStr(), notes: '' }

/** Indicador visual de los 3 pasos del asistente */
function Stepper({ step }) {
  const labels = ['Datos', 'Disponibilidad', 'Confirmar']
  return (
    <div className="flex items-center mb-6">
      {labels.map((label, i) => {
        const n = i + 1
        const done = n < step
        const active = n === step
        return (
          <div key={label} className="flex items-center flex-1 last:flex-none">
            <div className="flex items-center gap-2 shrink-0">
              <div
                className={`flex h-7 w-7 items-center justify-center rounded-full text-xs font-bold transition-colors ${
                  done ? 'bg-emerald-500 text-white'
                    : active ? 'bg-[#1e3a5f] text-white'
                    : 'bg-slate-100 text-slate-400'
                }`}
              >
                {done ? <Check size={14} /> : n}
              </div>
              <span className={`text-xs font-semibold ${active ? 'text-[#1e3a5f]' : 'text-slate-400'}`}>
                {label}
              </span>
            </div>
            {i < labels.length - 1 && (
              <div className={`flex-1 h-px mx-3 ${done ? 'bg-emerald-300' : 'bg-slate-200'}`} />
            )}
          </div>
        )
      })}
    </div>
  )
}

/** Asistente de 3 pasos (datos - disponibilidad - confirmar) para crear o editar una cita */
export function AppointmentWizard({
  open, onClose, onCreated, bId,
  prefillDate, prefillTime, prefillClientId,
  appointmentToEdit,
}) {
  const toast = useToast()

  const [aux, setAux] = useState({ employees: [], services: [], booths: [] })
  const [step, setStep] = useState(1)
  const [form, setForm] = useState(emptyForm)
  const [slots, setSlots] = useState([])
  const [slotsLoading, setSlotsLoading] = useState(false)
  // Detecta si falta horario semanal
  const [noSchedule, setNoSchedule] = useState(false)
  const [totalDuration, setTotalDuration] = useState(0)
  const [selectedSlot, setSelectedSlot] = useState(null)
  const [saving, setSaving] = useState(false)

  // Datos para los selectores (una sola vez); size=100 = tope del servidor
  useEffect(() => {
    if (!bId) return
    Promise.allSettled([
      api.get(`/api/businesses/${bId}/users?size=100`),
      api.get(`/api/businesses/${bId}/services?size=100`),
      api.get(`/api/businesses/${bId}/booths?size=100`),
    ]).then((r) => {
      setAux({
        employees: r[0].status === 'fulfilled' ? r[0].value.data.content : [],
        services:  r[1].status === 'fulfilled' ? r[1].value.data.content : [],
        booths:    r[2].status === 'fulfilled' ? r[2].value.data.content : [],
      })
      const failed = r.find((x) => x.status === 'rejected')
      if (failed) {
        toast({ type: 'error', message: getErrorMessage(failed.reason, 'No se pudieron cargar los datos del asistente.') })
      }
    })
  }, [bId, toast])

  // Reinicia al paso 1 al abrir; en modo editar precarga la cita, en modo crear aplica prefills
  // Dependencia por id (no por objeto) para no pisar el form si el padre rerenderiza
  const editingId = appointmentToEdit?.id ?? null
  useEffect(() => {
    if (!open) return
    if (appointmentToEdit) {
      setForm({
        clientId:     appointmentToEdit.clientId,
        clientName:   appointmentToEdit.clientName ?? '',
        membershipId: String(appointmentToEdit.membershipId ?? ''),
        boothId:      appointmentToEdit.boothId != null ? String(appointmentToEdit.boothId) : '',
        serviceIds:   (appointmentToEdit.bookedServices ?? []).map((b) => b.serviceId),
        date:         appointmentToEdit.startDateTime.slice(0, 10),
        notes:        appointmentToEdit.notes ?? '',
      })
    } else {
      setForm({ ...emptyForm, date: prefillDate || todayStr(), clientId: prefillClientId || '' })
    }
    setStep(1)
    setSlots([])
    setSelectedSlot(null)
    setTotalDuration(0)
    setNoSchedule(false)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, prefillDate, prefillClientId, editingId])

  // En editar, el prefillTime es la hora de la cita para preseleccionar el slot original
  const isEdit = Boolean(appointmentToEdit)
  const effectivePrefillTime = useMemo(
    () => isEdit ? appointmentToEdit.startDateTime.slice(11, 16) : prefillTime,
    [isEdit, appointmentToEdit, prefillTime],
  )

  const toggleService = (id) => {
    setForm((p) => ({
      ...p,
      serviceIds: p.serviceIds.includes(id)
        ? p.serviceIds.filter((s) => s !== id)
        : [...p.serviceIds, id],
    }))
  }

  const employeeName = (id) => aux.employees.find((e) => String(e.id) === String(id))?.fullName || '—'
  const chosenServices = aux.services.filter((s) => form.serviceIds.includes(s.id))
  const chosenTotal = chosenServices.reduce((acc, s) => acc + Number(s.price || 0), 0)

  /** Paso 1 - 2: valida los datos basicos y pide los huecos al servidor */
  const goToSlots = async () => {
    const { clientId, membershipId, serviceIds, date } = form
    if (!clientId || !membershipId || !serviceIds.length || !date) {
      toast({ type: 'error', message: 'Completa cliente, empleado, servicios y fecha.' })
      return
    }
    setSlotsLoading(true)
    setSelectedSlot(null)
    setSlots([])
    setNoSchedule(false)
    setStep(2)
    try {
      // serviceIds como parametros repetidos (serviceIds=1&serviceIds=2), nunca CSV
      const qs = new URLSearchParams()
      qs.set('date', date)
      serviceIds.forEach((id) => qs.append('serviceIds', id))
      qs.set('membershipId', membershipId)
      if (form.boothId) qs.set('boothId', form.boothId)
      // En editar, el slot actual de la cita no debe contar como ocupado
      if (isEdit) qs.set('excludeAppointmentId', appointmentToEdit.id)

      const { data } = await api.get(`/api/businesses/${bId}/availability?${qs.toString()}`)

      // Si la fecha es hoy, descarta huecos cuya hora ya ha pasado
      const now = Date.now()
      const fresh = (data.slots ?? []).filter(
        (s) => new Date(`${date}T${toHms(s.startTime)}`).getTime() >= now,
      )
      setSlots(fresh)
      setTotalDuration(data.totalDurationMinutes ?? 0)
      // Preselecciona el slot indicado por prefill (hora actual en editar, hueco clicado en crear)
      if (effectivePrefillTime) {
        setSelectedSlot(fresh.find((s) => hhmm(s.startTime) === effectivePrefillTime) ?? null)
      }
      // Sin huecos: distingue "empleado sin horario" del resto para dar un mensaje accionable
      if (fresh.length === 0) {
        try {
          const { data: sch } = await api.get(`/api/businesses/${bId}/users/${membershipId}/schedules`)
          setNoSchedule(Array.isArray(sch) && sch.length === 0)
        } catch { /* mejor esfuerzo: si falla, se muestra el mensaje generico */ }
      }
    } catch (err) {
      toast({ type: 'error', message: getErrorMessage(err, 'No se pudo consultar la disponibilidad.') })
      setStep(1)
    } finally {
      setSlotsLoading(false)
    }
  }

  /** Paso 3: crea o edita la cita con los datos del hueco elegido */
  const handleSave = async () => {
    if (!selectedSlot) {
      toast({ type: 'error', message: 'Selecciona un hueco disponible.' })
      return
    }
    setSaving(true)
    try {
      // membershipId/boothId desde el SLOT (verdad de huecos libres); startDateTime sin 'Z' (local)
      // En editar, el clientId no viaja: la cita pertenece al cliente original
      const body = {
        membershipId:  selectedSlot.membershipId,
        boothId:       selectedSlot.boothId ?? null,
        startDateTime: `${form.date}T${toHms(selectedSlot.startTime)}`,
        serviceIds:    form.serviceIds.map(Number),
        notes:         form.notes || null,
      }
      if (isEdit) {
        await api.put(`/api/businesses/${bId}/appointments/${appointmentToEdit.id}`, body)
        toast({ type: 'success', message: 'Cita actualizada correctamente.' })
      } else {
        await api.post(`/api/businesses/${bId}/appointments`, {
          ...body,
          clientId: Number(form.clientId),
        })
        toast({ type: 'success', message: 'Cita creada correctamente.' })
      }
      onCreated?.()
      onClose()
    } catch (err) {
      const fallback = isEdit ? 'No se pudo guardar la cita.' : 'No se pudo crear la cita.'
      toast({ type: 'error', message: getErrorMessage(err, fallback) })
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal open={open} onClose={onClose} title={isEdit ? 'Editar cita' : 'Nueva cita'} size="lg">
      <Stepper step={step} />

      {step === 1 && (
        <div className="space-y-4">
          <div className="grid sm:grid-cols-2 gap-4">
            {isEdit ? (
              // En editar, el cliente queda fijo; se muestra como panel en lugar del picker
              <div>
                <label className="text-[11px] font-semibold text-slate-500 uppercase tracking-wide block mb-2">
                  Cliente
                </label>
                <div className="h-11 rounded-2xl border border-slate-200 bg-slate-50 px-3.5 flex items-center justify-between gap-2 text-sm">
                  <span className="font-semibold text-[#1e3a5f] truncate">
                    {form.clientName || '—'}
                  </span>
                  <span
                    className="inline-flex items-center gap-1 text-[10px] font-semibold text-slate-400 uppercase tracking-wider shrink-0"
                    title="El cliente no se puede cambiar al editar una cita"
                  >
                    <Lock size={11} /> Fijo
                  </span>
                </div>
              </div>
            ) : (
              <ClientPicker
                bId={bId}
                label="Cliente *"
                value={form.clientId}
                onChange={(c) => setForm((p) => ({ ...p, clientId: c?.id ?? '', clientName: c?.fullName ?? '' }))}
              />
            )}
            <Select
              label="Empleado *"
              value={form.membershipId}
              onChange={(e) => setForm((p) => ({ ...p, membershipId: e.target.value }))}
            >
              <option value="">Selecciona empleado</option>
              {aux.employees.map((e) => <option key={e.id} value={e.id}>{e.fullName}</option>)}
            </Select>
          </div>
          <div className="grid sm:grid-cols-2 gap-4">
            <Select
              label="Cabina (opcional)"
              value={form.boothId}
              onChange={(e) => setForm((p) => ({ ...p, boothId: e.target.value }))}
            >
              <option value="">Sin preferencia</option>
              {aux.booths.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
            </Select>
            <div>
              <Input
                label="Fecha *"
                type="date"
                min={todayStr()}
                value={form.date}
                onChange={(e) => setForm((p) => ({ ...p, date: e.target.value }))}
              />
              <p className="text-[11px] text-slate-400 mt-1.5">
                La hora se elige en el siguiente paso según la disponibilidad real.
              </p>
            </div>
          </div>
          <div>
            <label className="text-[11px] font-semibold text-slate-500 uppercase tracking-wide block mb-2">
              Servicios *
            </label>
            <div className="grid sm:grid-cols-2 gap-2 max-h-48 overflow-y-auto border border-slate-200 rounded-2xl p-2 bg-slate-50">
              {aux.services.length === 0 && (
                <p className="col-span-2 text-xs text-slate-400 italic py-3 text-center">
                  No hay servicios disponibles. Crea alguno en el Catálogo.
                </p>
              )}
              {aux.services.map((s) => (
                <label
                  key={s.id}
                  className={`flex items-center gap-2.5 rounded-xl px-3 py-2.5 cursor-pointer transition-all text-sm ${
                    form.serviceIds.includes(s.id)
                      ? 'bg-blue-50 border border-blue-200 text-blue-700'
                      : 'hover:bg-white border border-transparent text-slate-600'
                  }`}
                >
                  <input
                    type="checkbox"
                    className="accent-[#1e3a5f] h-4 w-4"
                    checked={form.serviceIds.includes(s.id)}
                    onChange={() => toggleService(s.id)}
                  />
                  <span className="flex-1 truncate font-medium">{s.name}</span>
                  <span className="text-xs text-slate-400 shrink-0">{s.durationMinutes}m · {s.price}€</span>
                </label>
              ))}
            </div>
          </div>
          <div className="flex gap-3 pt-1">
            <Button variant="outline" onClick={onClose} className="flex-1">Cancelar</Button>
            <Button onClick={goToSlots} className="flex-1 gap-1.5">
              Ver disponibilidad <ArrowRight size={16} />
            </Button>
          </div>
        </div>
      )}

      {step === 2 && (
        <div className="space-y-4">
          <div className="rounded-2xl bg-slate-50 border border-slate-100 px-4 py-3 text-xs text-slate-500 flex flex-wrap gap-x-4 gap-y-1">
            <span className="font-semibold text-[#1e3a5f]">{form.clientName || '—'}</span>
            <span className="flex items-center gap-1"><User size={12} />{employeeName(form.membershipId)}</span>
            <span className="flex items-center gap-1"><CalendarDays size={12} />{formatDateLong(form.date)}</span>
            {totalDuration > 0 && (
              <span className="flex items-center gap-1"><Clock size={12} />Duración total: {totalDuration} min</span>
            )}
          </div>

          {slotsLoading ? (
            <div className="grid grid-cols-3 sm:grid-cols-4 gap-2">
              {[...Array(8)].map((_, i) => (
                <div key={i} className="h-16 rounded-xl bg-slate-100 animate-pulse" />
              ))}
            </div>
          ) : slots.length === 0 ? (
            <div className="py-10 text-center">
              {noSchedule ? (
                <>
                  <p className="text-sm text-slate-500 font-medium">
                    Este empleado no tiene horario semanal configurado.
                  </p>
                  <p className="text-xs text-slate-400 mt-1">
                    Ve a Empleados, abre su ficha y añade su horario para poder citarlo.
                  </p>
                </>
              ) : (
                <>
                  <p className="text-sm text-slate-500 font-medium">No hay huecos disponibles.</p>
                  <p className="text-xs text-slate-400 mt-1">
                    Prueba con otra fecha, otro empleado o reduce los servicios.
                  </p>
                </>
              )}
            </div>
          ) : (
            <div>
              <p className="text-[11px] font-semibold text-slate-500 uppercase tracking-wide mb-2">
                {slots.length} hueco{slots.length === 1 ? '' : 's'} disponible{slots.length === 1 ? '' : 's'}
              </p>
              <div className="grid grid-cols-3 sm:grid-cols-4 gap-2 max-h-72 overflow-y-auto pr-1">
                {slots.map((slot) => (
                  <button
                    key={`${slot.startTime}_${slot.boothId}`}
                    type="button"
                    onClick={() => setSelectedSlot(slot)}
                    className={`flex flex-col items-center justify-center rounded-xl border px-2 py-2.5 transition-all ${
                      selectedSlot === slot
                        ? 'border-blue-400 bg-blue-50 text-blue-700 ring-2 ring-blue-100'
                        : 'border-slate-200 bg-white text-[#1e3a5f] hover:border-blue-300 hover:bg-slate-50'
                    }`}
                  >
                    <span className="text-sm font-bold">{hhmm(slot.startTime)}</span>
                    <span className="text-[10px] text-slate-400">hasta {hhmm(slot.endTime)}</span>
                    {slot.boothName && (
                      <span className="text-[10px] text-slate-400 mt-0.5 flex items-center gap-0.5 max-w-full truncate">
                        <MapPin size={9} /> {slot.boothName}
                      </span>
                    )}
                  </button>
                ))}
              </div>
            </div>
          )}

          <div className="flex gap-3 pt-1">
            <Button variant="outline" onClick={() => setStep(1)} className="flex-1 gap-1.5">
              <ArrowLeft size={16} /> Atrás
            </Button>
            <Button onClick={() => setStep(3)} disabled={!selectedSlot} className="flex-1 gap-1.5">
              Continuar <ArrowRight size={16} />
            </Button>
          </div>
        </div>
      )}

      {step === 3 && selectedSlot && (
        <div className="space-y-4">
          <div className="grid sm:grid-cols-2 gap-3">
            {[
              { label: 'Cliente',  value: form.clientName || '—' },
              { label: 'Empleado', value: selectedSlot.userFullName || employeeName(form.membershipId) },
              { label: 'Fecha',    value: formatDateLong(form.date) },
              { label: 'Hora',     value: `${hhmm(selectedSlot.startTime)} – ${hhmm(selectedSlot.endTime)}` },
              { label: 'Cabina',   value: selectedSlot.boothName || 'Sin cabina' },
              { label: 'Duración', value: `${totalDuration} min` },
            ].map(({ label, value }) => (
              <div key={label} className="rounded-2xl bg-slate-50 border border-slate-100 px-4 py-3">
                <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-1">{label}</p>
                <p className="text-sm font-semibold text-[#1e3a5f] capitalize">{value}</p>
              </div>
            ))}
          </div>

          <div>
            <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-2">Servicios</p>
            <div className="space-y-2">
              {chosenServices.map((s) => (
                <div key={s.id} className="flex justify-between items-center text-sm bg-slate-50 rounded-2xl border border-slate-100 px-4 py-2.5">
                  <span className="font-medium text-slate-700">{s.name}</span>
                  <span className="font-bold text-[#1e3a5f]">{Number(s.price).toFixed(2)} €</span>
                </div>
              ))}
              <div className="flex justify-between items-center font-bold text-[#1e3a5f] border-t border-slate-200 pt-2.5 px-1">
                <span>Total estimado</span>
                <span className="text-lg">{chosenTotal.toFixed(2)} €</span>
              </div>
            </div>
          </div>

          <Textarea
            label="Notas (opcional)"
            value={form.notes}
            onChange={(e) => setForm((p) => ({ ...p, notes: e.target.value }))}
            placeholder="Observaciones adicionales…"
            maxLength={1000}
          />

          <div className="flex gap-3 pt-1">
            <Button variant="outline" onClick={() => setStep(2)} className="flex-1 gap-1.5" disabled={saving}>
              <ArrowLeft size={16} /> Atrás
            </Button>
            <Button onClick={handleSave} loading={saving} className="flex-1">
              {isEdit ? 'Guardar cambios' : 'Crear cita'}
            </Button>
          </div>
        </div>
      )}
    </Modal>
  )
}

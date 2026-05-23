import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  ChevronLeft, ChevronRight, Plus, RefreshCw, Printer,
  Palette, SlidersHorizontal, X,
} from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { AppointmentWizard } from '@/components/appointments/AppointmentWizard'
import { AppointmentDetailModal } from '@/components/appointments/AppointmentDetailModal'
import { useAuth } from '@/context/AuthContext'
import { useCatalog } from '@/context/CatalogContext'
import { useToast } from '@/components/ui/Toast'
import api, { getErrorMessage } from '@/lib/api'
import { totalBooked } from '@/lib/format'

import {
  MONTHS_ES, DAYS_ES_SHORT, VIEW_MODES,
  DEFAULT_DAY_START, DEFAULT_DAY_END, HOUR_PX,
  PALETTES, GRAY_PALETTE, STATUS_STYLES,
  pad2, keyOf, isSameDay, startOfWeek, buildMonthGrid, rangeFor,
  apptDate, minutesOf, apptDuration, layoutEvents, openRangesFor,
  blocksForCell, labelForBlock, blockForAppointment, absenceForAppointment,
} from '@/components/calendar/utils'
import {
  HourColumn, HourSlots, NowLine, PositionedEvent, EventChip, BlockOverlay, AbsenceOverlay,
} from '@/components/calendar/cells'
import { ResourceDayGrid }  from '@/components/calendar/ResourceDayGrid'
import { WeekResourceGrid } from '@/components/calendar/WeekResourceGrid'
import { MiniCalendarPopover } from '@/components/calendar/MiniCalendar'

/* ============================================================
   CALENDARIO
   ============================================================ */

/** Abreviatura de cabina para las cabeceras de columna: "Cabina 3" → "C3";
 *  si el nombre no encaja, las 3 primeras letras en mayúsculas. Usa
 *  String.match en vez del estado global frágil `RegExp.$1`. */
const boothShort = (name) => {
  const m = (name || '').match(/^cabina\s*(\d+)$/i)
  return m ? `C${m[1]}` : (name || '').slice(0, 3).toUpperCase()
}

export default function Calendario() {
  const { user } = useAuth()
  const { statusLabel } = useCatalog()
  const toast = useToast()
  const bId = user?.businessId

  /* ---- Preferencias persistidas ---- */
  const [view, setView] = useState(() => localStorage.getItem('optima_cal_view') || 'Mes')
  useEffect(() => { localStorage.setItem('optima_cal_view', view) }, [view])

  // [I] El selector de densidad se retiro: HOUR_PX es ahora constante. La
  // clave de localStorage `optima_cal_density` se deja morir; no se limpia
  // explicitamente (es benigno, ocupa < 20 bytes y desaparece al reset).
  const hourPx = HOUR_PX

  const [colorBy, setColorBy] = useState(() => localStorage.getItem('optima_cal_colorby') || 'status')
  useEffect(() => { localStorage.setItem('optima_cal_colorby', colorBy) }, [colorBy])

  // Filtros de CONTENIDO (qué citas se ven): NO se persisten. Si sobreviven al
  // cierre de sesión, un filtro olvidado hace "desaparecer" citas reales — p. ej.
  // una cita nueva (nace en PENDING) con el filtro pegado en otro estado.
  const [employeeFilter, setEmployeeFilter] = useState('')
  const [boothFilter, setBoothFilter] = useState('')
  const [statusFilter, setStatusFilter] = useState('')

  // Agrupación del Día/Semana: 'time' (cronologica) | 'booth' | 'employee'
  const [groupBy, setGroupBy] = useState(() => localStorage.getItem('optima_cal_groupby') || 'booth')
  useEffect(() => { localStorage.setItem('optima_cal_groupby', groupBy) }, [groupBy])

  /* ---- Estado base ---- */
  const [cursor, setCursor] = useState(() => { const d = new Date(); return new Date(d.getFullYear(), d.getMonth(), d.getDate()) })
  const [appointments, setAppointments] = useState([])
  const [loading, setLoading] = useState(true)
  const [reloadFlag, setReloadFlag] = useState(0)
  const today = useMemo(() => new Date(), [])

  // tick cada minuto para la línea "ahora"
  const [now, setNow] = useState(new Date())
  useEffect(() => { const t = setInterval(() => setNow(new Date()), 60_000); return () => clearInterval(t) }, [])

  /* ---- Carga de listas auxiliares ---- */
  const [employees, setEmployees] = useState([])
  const [booths, setBooths] = useState([])
  const [businessHours, setBusinessHours] = useState([])
  // schedule_blocks del negocio (festivos/vacaciones/mantenimiento). Se
  // cargan completos (cardinalidad baja, ~decenas) y se filtran al rango
  // visible en `blocksInRange`.
  const [scheduleBlocks, setScheduleBlocks] = useState([])
  // Ausencias de empleados (vacaciones, bajas) que solapan con el rango
  // visible. Se piden filtradas por rango al endpoint dedicado para no
  // traer toda la historia del negocio.
  const [absences, setAbsences] = useState([])
  // appointmentInterval del negocio (15/30/45/60). Lo usa el drag para
  // snapear la hora de drop al multiplo correcto.
  const [appointmentInterval, setAppointmentInterval] = useState(30)

  useEffect(() => {
    if (!bId) return
    Promise.allSettled([
      api.get(`/api/businesses/${bId}/users?size=100`),
      api.get(`/api/businesses/${bId}/booths?size=100`),
      api.get(`/api/businesses/${bId}/hours`),
      api.get(`/api/businesses/${bId}/schedule-blocks?size=100`),
      api.get(`/api/businesses/${bId}`),
    ]).then(([emp, bo, hrs, blks, biz]) => {
      if (emp.status === 'fulfilled') setEmployees(emp.value.data.content ?? [])
      if (bo.status  === 'fulfilled') setBooths(bo.value.data.content ?? [])
      if (hrs.status === 'fulfilled') {
        const data = Array.isArray(hrs.value.data) ? hrs.value.data : (hrs.value.data?.content ?? [])
        setBusinessHours(data)
      }
      if (blks.status === 'fulfilled') setScheduleBlocks(blks.value.data.content ?? [])
      if (biz.status === 'fulfilled') setAppointmentInterval(biz.value.data?.appointmentInterval ?? 30)
    })
  }, [bId, reloadFlag])

  // Rango horario dinámico de la rejilla. Cubre el horario del negocio Y
  // todas las citas cargadas: así ninguna cita queda fuera de la rejilla
  // (p. ej. una cita que empieza después de la hora de cierre). Sin ningún
  // dato, defaults 8-21.
  const { dayStart, dayEnd } = useMemo(() => {
    let minStart = 24, maxEnd = 0
    businessHours.forEach((h) => {
      if (h.isClosed || !h.startTime || !h.endTime) return
      const [sh, sm] = h.startTime.slice(0, 5).split(':').map(Number)
      const [eh, em] = h.endTime.slice(0, 5).split(':').map(Number)
      const s = sh + sm / 60
      const e = eh + em / 60
      if (s < minStart) minStart = s
      if (e > maxEnd)   maxEnd   = e
    })
    // Extiende el rango para que toda cita cargada tenga celdas de rejilla.
    appointments.forEach((a) => {
      const s = minutesOf(a.startDateTime) / 60
      const e = minutesOf(a.endDateTime) / 60
      if (s < minStart) minStart = s
      if (e > maxEnd)   maxEnd   = e
    })
    if (minStart === 24 || maxEnd === 0) return { dayStart: DEFAULT_DAY_START, dayEnd: DEFAULT_DAY_END }
    return { dayStart: Math.floor(minStart), dayEnd: Math.ceil(maxEnd) }
  }, [businessHours, appointments])

  // Carga de citas del rango visible
  useEffect(() => {
    if (!bId) { setLoading(false); return }
    let cancelled = false
    const { from, to } = rangeFor(view, cursor)
    setLoading(true)
    // size 100: el backend cappea Pageable en 100 (spring.data.web.pageable
    // .max-page-size). Un rango con mas de 100 citas se veria parcial.
    const params = { from, to, size: 100, sort: 'startDateTime,asc' }
    if (employeeFilter) params.membershipId = employeeFilter
    api.get(`/api/businesses/${bId}/appointments`, { params })
      .then((r) => { if (!cancelled) setAppointments(r.data.content ?? []) })
      .catch((err) => { if (!cancelled) toast({ type: 'error', message: getErrorMessage(err, 'No se pudieron cargar las citas.') }) })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [bId, view, cursor, employeeFilter, reloadFlag, toast])

  const refetch = useCallback(() => setReloadFlag((v) => v + 1), [])

  // Mapa membershipId -> color asignado al empleado (memberships.color).
  // "Color por Empleado" pinta las citas con el color que el admin haya
  // elegido; los empleados sin color caen al automatico.
  const empColorMap = useMemo(
    () => new Map(employees.map((e) => [e.id, e.color])),
    [employees],
  )
  // [L] Mapa boothId -> color asignado a la cabina (booths.color). Igual
  // patron que empColorMap: simetria empleado/cabina en el calendario.
  const boothColorMap = useMemo(
    () => new Map(booths.map((b) => [b.id, b.color])),
    [booths],
  )

  // Filtros client-side (estado + cabina). Ademas anexa employeeColor y
  // boothColor a cada cita para que styleFor pueda usar el color asignado.
  const filtered = useMemo(() => appointments
    .filter((a) => {
      if (statusFilter && a.statusName !== statusFilter) return false
      if (boothFilter && String(a.boothId ?? '') !== boothFilter) return false
      return true
    })
    .map((a) => ({
      ...a,
      employeeColor: empColorMap.get(a.membershipId) ?? null,
      boothColor: a.boothId != null ? (boothColorMap.get(a.boothId) ?? null) : null,
    })),
    [appointments, statusFilter, boothFilter, empColorMap, boothColorMap])

  // Agrupado por día
  const eventsByDay = useMemo(() => {
    const map = new Map()
    filtered.forEach((a) => {
      const k = apptDate(a)
      if (!map.has(k)) map.set(k, [])
      map.get(k).push(a)
    })
    return map
  }, [filtered])

  // Subconjunto de blocks cuyas fechas intersectan el rango visible. Reduce
  // el trabajo de las grids: en vez de filtrar todos los blocks por celda,
  // solo iteramos sobre los que de verdad pueden aplicar a esta vista.
  const blocksInRange = useMemo(() => {
    const r = rangeFor(view, cursor)
    return scheduleBlocks.filter((b) => b.endDate >= r.from && b.startDate <= r.to)
  }, [scheduleBlocks, view, cursor])

  // Fetch de ausencias del negocio que solapan con el rango visible. El
  // endpoint dedicado ya devuelve solo las que aplican, asi que no hace
  // falta filtro extra en el cliente.
  useEffect(() => {
    if (!bId) return
    let cancelled = false
    const r = rangeFor(view, cursor)
    api.get(`/api/businesses/${bId}/absences`, { params: { from: r.from, to: r.to } })
      .then((res) => { if (!cancelled) setAbsences(res.data ?? []) })
      .catch(() => { if (!cancelled) setAbsences([]) })
    return () => { cancelled = true }
  }, [bId, view, cursor, reloadFlag])

  // Stats del rango activo
  const rangeStats = useMemo(() => {
    const considered = filtered.filter((a) => a.statusName !== 'CANCELLED' && a.statusName !== 'NO_SHOW')
    const revenue = considered.reduce((acc, a) => acc + parseFloat(totalBooked(a.bookedServices)), 0)
    const minutesBusy = considered.reduce((acc, a) => acc + apptDuration(a), 0)
    const r = rangeFor(view, cursor)
    const start = new Date(`${r.from}T00:00:00`)
    const end   = new Date(`${r.to}T23:59:59`)
    let openMinutes = 0
    for (let d = new Date(start); d <= end; d.setDate(d.getDate() + 1)) {
      const dow = d.getDay() === 0 ? 7 : d.getDay()
      const h = businessHours.find((x) => x.dayOfWeek === dow)
      if (h && !h.isClosed && h.startTime && h.endTime) {
        openMinutes += (new Date(`1970-01-01T${h.endTime}`) - new Date(`1970-01-01T${h.startTime}`)) / 60000
      }
    }
    const occupancy = openMinutes > 0 ? Math.round((minutesBusy / openMinutes) * 100) : null
    const inProgressCount = filtered.filter((a) =>
      a.statusName === 'IN_PROGRESS' &&
      new Date(a.startDateTime) <= now && now <= new Date(a.endDateTime),
    ).length
    return { count: filtered.length, revenue, occupancy, inProgressCount }
  }, [filtered, view, cursor, businessHours, now])

  /* ---- Navegación ---- */
  const goPrev  = useCallback(() => {
    if (view === 'Mes')         setCursor((c) => new Date(c.getFullYear(), c.getMonth() - 1, 1))
    else if (view === 'Semana') setCursor((c) => { const d = new Date(c); d.setDate(c.getDate() - 7); return d })
    else                        setCursor((c) => { const d = new Date(c); d.setDate(c.getDate() - 1); return d })
  }, [view])
  const goNext = useCallback(() => {
    if (view === 'Mes')         setCursor((c) => new Date(c.getFullYear(), c.getMonth() + 1, 1))
    else if (view === 'Semana') setCursor((c) => { const d = new Date(c); d.setDate(c.getDate() + 7); return d })
    else                        setCursor((c) => { const d = new Date(c); d.setDate(c.getDate() + 1); return d })
  }, [view])
  const goToday = useCallback(() => { const d = new Date(); setCursor(new Date(d.getFullYear(), d.getMonth(), d.getDate())) }, [])

  /* ---- Modales ----
     `wizard` cubre tanto "Nueva cita" (date/time precargados) como "Editar
     cita" (appt presente => modo edición del AppointmentWizard). El detail
     modal cede el flujo al wizard al pulsar "Editar cita". */
  const [wizard, setWizard] = useState({ open: false, date: null, time: null, appt: null })
  const [detailAppt, setDetailAppt] = useState(null)
  // Drag-and-drop: cuando el usuario suelta una cita en otro slot/recurso,
  // se guarda aqui la pre-vista del cambio y el modal pide confirmacion.
  // Si confirma -> PUT al endpoint de edicion (P9). Si cancela -> nada.
  const [pendingDrop, setPendingDrop] = useState(null)
  const [droppingSaving, setDroppingSaving] = useState(false)
  const openWizard = useCallback((date = null, time = null) => setWizard({ open: true, date, time, appt: null }), [])
  const closeWizard = useCallback(() => setWizard({ open: false, date: null, time: null, appt: null }), [])

  // Bloqueo aplicable a la cita abierta en el detail modal (null si la cita
  // no esta en un dia bloqueado). Sirve para el aviso visual del modal y
  // para que openEditWizard lance un toast guia antes de abrir el wizard.
  const detailApptBlock = useMemo(
    () => blockForAppointment(blocksInRange, detailAppt),
    [blocksInRange, detailAppt],
  )
  // Ausencia del empleado aplicable a la cita abierta (null si no coincide
  // con ninguna). Misma idea que detailApptBlock pero para ausencias.
  const detailApptAbsence = useMemo(
    () => absenceForAppointment(absences, detailAppt),
    [absences, detailAppt],
  )

  // Drag-and-drop: arma el `pendingDrop` con la info que mostrará el modal
  // de confirmacion. El handler decide si el drop cambio algo (mismo slot
  // y mismo recurso = no hace nada).
  const handleDropAppointment = useCallback(({ appt, newStartDateTime, newResourceType, newResourceId }) => {
    if (!appt) return
    // Calcular nuevos membershipId / boothId segun el tipo de la celda destino.
    const newMembershipId = newResourceType === 'employee'
      ? (newResourceId ?? appt.membershipId)
      : appt.membershipId
    const newBoothId = newResourceType === 'booth'
      ? newResourceId
      : appt.boothId
    // Si nada cambio (drop en el mismo slot y mismo recurso), salir sin
    // pedir confirmacion ni hacer PUT.
    if (newStartDateTime === appt.startDateTime?.slice(0, 19)
        && newMembershipId === appt.membershipId
        && (newBoothId ?? null) === (appt.boothId ?? null)) {
      return
    }
    setPendingDrop({ appt, newStartDateTime, newMembershipId, newBoothId })
  }, [])

  const confirmDrop = useCallback(async () => {
    if (!pendingDrop) return
    const { appt, newStartDateTime, newMembershipId, newBoothId } = pendingDrop
    setDroppingSaving(true)
    try {
      await api.put(`/api/businesses/${bId}/appointments/${appt.id}`, {
        membershipId: newMembershipId,
        boothId: newBoothId ?? null,
        startDateTime: newStartDateTime,
        serviceIds: (appt.bookedServices ?? []).map((b) => b.serviceId),
        notes: appt.notes ?? null,
      })
      toast({ type: 'success', message: 'Cita reagendada.' })
      setPendingDrop(null)
      refetch()
    } catch (err) {
      toast({ type: 'error', message: getErrorMessage(err, 'No se pudo reagendar la cita.') })
    } finally {
      setDroppingSaving(false)
    }
  }, [pendingDrop, bId, toast, refetch])

  const openEditWizard = useCallback((appt) => {
    const block = blockForAppointment(blocksInRange, appt)
    if (block) {
      const motivo = (block.reason && block.reason.trim())
        || (block.membershipId != null ? 'empleado bloqueado'
          : block.boothId != null ? 'cabina bloqueada'
          : 'día bloqueado')
      toast({
        type: 'info',
        message: `Esta cita cae en un bloqueo (${motivo}). Cambia la fecha al guardar.`,
      })
    }
    const absence = absenceForAppointment(absences, appt)
    if (absence) {
      const motivo = (absence.reason && absence.reason.trim()) || 'ausencia del empleado'
      toast({
        type: 'info',
        message: `Esta cita coincide con una ausencia (${motivo}). Cambia hora o empleado al guardar.`,
      })
    }
    setDetailAppt(null)
    setWizard({ open: true, date: null, time: null, appt })
  }, [blocksInRange, absences, toast])

  const onSlotClick = useCallback((dayKey, hour) => openWizard(dayKey, `${pad2(hour)}:00`), [openWizard])
  const onCellClick = useCallback((dayKey) => openWizard(dayKey, null), [openWizard])
  const onOpenDay   = useCallback((date) => { setCursor(date); setView('Día') }, [])

  /* ---- Atajos de teclado ---- */
  useEffect(() => {
    const handler = (e) => {
      // No interceptes si el foco está en un input o si hay un modal abierto
      const tag = (e.target?.tagName || '').toLowerCase()
      if (tag === 'input' || tag === 'select' || tag === 'textarea') return
      if (wizard.open || detailAppt) return
      if (e.key === 'ArrowLeft')  { e.preventDefault(); goPrev() }
      else if (e.key === 'ArrowRight') { e.preventDefault(); goNext() }
      else if (e.key === 't' || e.key === 'T') { e.preventDefault(); goToday() }
      else if (e.key === 'm' || e.key === 'M') { setView('Mes') }
      else if (e.key === 'w' || e.key === 'W') { setView('Semana') }
      else if (e.key === 'd' || e.key === 'D') { setView('Día') }
    }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [goPrev, goNext, goToday, wizard.open, detailAppt])

  /* ---- Texto de cabecera ---- */
  let headerText = `${MONTHS_ES[cursor.getMonth()]} ${cursor.getFullYear()}`
  if (view === 'Semana') {
    const ws = startOfWeek(cursor); const we = new Date(ws); we.setDate(ws.getDate() + 6)
    headerText = ws.getMonth() === we.getMonth()
      ? `${ws.getDate()} – ${we.getDate()} ${MONTHS_ES[ws.getMonth()]} ${ws.getFullYear()}`
      : `${ws.getDate()} ${MONTHS_ES[ws.getMonth()].slice(0,3)} – ${we.getDate()} ${MONTHS_ES[we.getMonth()].slice(0,3)} ${we.getFullYear()}`
  } else if (view === 'Día') {
    headerText = cursor.toLocaleDateString('es-ES', { weekday:'long', day:'2-digit', month:'long', year:'numeric' })
  }

  /* ---- Recursos para vistas agrupadas ---- */
  const boothResources = useMemo(
    () => booths.map((b) => ({
      id: b.id,
      name: b.name,
      short: b.short || boothShort(b.name),
      accent: b.id,
      color: b.color,  // [L] permite que paletteByName decida la cabecera
    })),
    [booths],
  )
  const employeeResources = useMemo(
    () => employees.map((e) => ({
      id: e.id,
      name: e.fullName,
      short: (e.fullName || '').trim().split(/\s+/).map((s) => s[0]).slice(0, 2).join(''),
      accent: e.id,
      color: e.color,
    })),
    [employees],
  )

  return (
    <div className="px-4 sm:px-6 lg:px-8 xl:px-10 py-8 print:p-0 print:max-w-none">

      {/* Estilos de impresion (K del audit):
          - Oculta toolbars, sidebar y cualquier elemento marcado no-print.
          - Resetea sombras y bordes del calendario; el papel ya delimita.
          - Fuerza print-color-adjust: exact para que los chips de cita
            mantengan su color identificativo (sin esto, muchos navegadores
            sustituyen los backgrounds por blanco al imprimir).
          - Reduce padding global y maximiza el ancho del calendario.
          - El header propio de impresion lleva nombre del negocio,
            rango impreso y fecha de impresion. */}
      <style>{`
        @media print {
          @page { margin: 12mm; }
          body, html { background: white !important; }
          *, *::before, *::after {
            -webkit-print-color-adjust: exact !important;
            print-color-adjust: exact !important;
          }
          aside.sidebar, .no-print { display: none !important; }
          .print-area {
            box-shadow: none !important;
            border: none !important;
            border-radius: 0 !important;
          }
          /* las cabeceras sticky deshabilitan sticky al imprimir (cada pagina
             tendria su propia capa) */
          .sticky { position: static !important; }
        }
      `}</style>

      {/* Header solo en impresion: titulo + rango + fecha de impresion */}
      <div className="hidden print:block mb-3">
        <div className="flex items-baseline justify-between border-b-2 border-slate-300 pb-2">
          <div>
            <p className="text-xs uppercase tracking-[0.18em] text-slate-500 font-semibold">Calendario</p>
            <h2 className="text-2xl font-bold text-[#1e3a5f] capitalize">{headerText}</h2>
          </div>
          <p className="text-[10px] text-slate-500 font-mono">
            Impreso {new Date().toLocaleString('es-ES', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })}
          </p>
        </div>
      </div>

      <div className="mb-6 flex flex-wrap items-start justify-between gap-4 no-print">
        <div>
          <h1 className="text-3xl font-bold text-[#1e3a5f] tracking-tight">Calendario</h1>
          <p className="text-sm text-slate-500 mt-1.5 font-medium">
            Pulsa una cita para ver el detalle, o un hueco libre para crear una nueva.
          </p>
        </div>
        <div className="flex shrink-0 items-center gap-2">
          <button
            type="button"
            onClick={refetch}
            title="Refrescar"
            className="inline-flex items-center justify-center w-11 h-11 rounded-xl border border-slate-200 bg-white text-slate-500 hover:bg-slate-50 hover:text-[#1e3a5f] transition"
          >
            <RefreshCw size={16} className={loading ? 'animate-spin' : ''} />
          </button>
          <button
            type="button"
            onClick={() => window.print()}
            title="Imprimir agenda"
            className="inline-flex items-center justify-center w-11 h-11 rounded-xl border border-slate-200 bg-white text-slate-500 hover:bg-slate-50 hover:text-[#1e3a5f] transition"
          >
            <Printer size={16} />
          </button>
          <Button onClick={() => openWizard(keyOf(cursor), null)} className="gap-2">
            <Plus size={16} /> Nueva cita
          </Button>
        </div>
      </div>

      {/* Stats strip del rango activo */}
      <div className="mb-5 grid grid-cols-2 md:grid-cols-4 gap-3 no-print">
        <StatTile label={`Citas (${view.toLowerCase()})`} value={rangeStats.count} />
        <StatTile label="€ previstos" value={`${rangeStats.revenue.toLocaleString('es-ES', { minimumFractionDigits: 0, maximumFractionDigits: 0 })} €`} />
        <StatTile label="Ocupación" value={rangeStats.occupancy != null ? `${rangeStats.occupancy}%` : '—'} tone="success" />
        <StatTile label="En curso ahora" value={rangeStats.inProgressCount} tone="cyan" />
      </div>

      <div className="bg-white rounded-2xl border border-slate-100/80 shadow-[0_2px_12px_-2px_rgba(15,23,42,0.06)] overflow-hidden print-area">

        {/* Toolbar fila 1: navegación + vista */}
        <div className="flex flex-wrap items-center gap-3 px-5 py-4 border-b border-slate-100">
          <div className="flex items-center gap-1.5">
            <button onClick={goPrev} title="Anterior (←)" aria-label="Anterior" className="w-9 h-9 rounded-xl border border-slate-200 text-slate-500 hover:text-[#1e3a5f] hover:border-blue-300 hover:bg-blue-50 transition flex items-center justify-center"><ChevronLeft size={16} /></button>
            <button onClick={goNext} title="Siguiente (→)" aria-label="Siguiente" className="w-9 h-9 rounded-xl border border-slate-200 text-slate-500 hover:text-[#1e3a5f] hover:border-blue-300 hover:bg-blue-50 transition flex items-center justify-center"><ChevronRight size={16} /></button>
            <button onClick={goToday} title="Hoy (T)" className="ml-1 px-3 h-9 rounded-xl border border-slate-200 text-xs font-semibold text-slate-600 hover:text-[#1e3a5f] hover:border-blue-300 hover:bg-blue-50 transition">Hoy</button>
            <MiniCalendarPopover cursor={cursor} today={today} onPick={setCursor} />
          </div>
          <div className="flex-1 min-w-[180px] text-center">
            <span className="text-xl md:text-2xl font-bold text-[#1e3a5f] capitalize">{headerText}</span>
          </div>
          <div className="flex items-center gap-1 bg-slate-100 rounded-xl p-1">
            {VIEW_MODES.map((v) => (
              <button
                key={v}
                onClick={() => setView(v)}
                className={`text-xs font-semibold px-3 py-1.5 rounded-lg transition ${
                  view === v ? 'bg-white text-[#1e3a5f] shadow-[0_1px_4px_rgba(15,23,42,0.08)]' : 'text-slate-500 hover:text-[#1e3a5f]'
                }`}
              >
                {v}
              </button>
            ))}
          </div>
          <span className="text-[10px] text-slate-400 hidden md:inline">←/→ navegar · T hoy · M/W/D vista</span>
        </div>

        {/* Toolbar fila 2: filtros (popover) + agrupar + densidad */}
        <FiltersBar
          employees={employees}
          booths={booths}
          employeeFilter={employeeFilter}    setEmployeeFilter={setEmployeeFilter}
          boothFilter={boothFilter}          setBoothFilter={setBoothFilter}
          statusFilter={statusFilter}        setStatusFilter={setStatusFilter}
          colorBy={colorBy}                  setColorBy={setColorBy}
          view={view}
          groupBy={groupBy}                  setGroupBy={setGroupBy}
          statusLabel={statusLabel}
        />

        {loading ? (
          <div className="p-16 text-center text-slate-400 text-sm">Cargando agenda…</div>
        ) : view === 'Mes' ? (
          <MonthGrid
            cursor={cursor} today={today} eventsByDay={eventsByDay} colorBy={colorBy}
            onCellClick={onCellClick} onSelectEvent={setDetailAppt} onOpenDay={onOpenDay}
            blocks={blocksInRange}
          />
        ) : view === 'Semana' && groupBy === 'booth' ? (
          <WeekResourceGrid
            cursor={cursor} today={today} eventsByDay={eventsByDay} colorBy={colorBy}
            onSelectEvent={setDetailAppt} onSlotClick={onSlotClick}
            dayStart={dayStart} dayEnd={dayEnd} hourPx={hourPx}
            businessHours={businessHours} now={now}
            resources={boothResources}
            resourceFor={(a) => a.boothId}
            unassignedShort="S/C"
            blocks={blocksInRange}
            resourceType="booth"
            onDropAppointment={handleDropAppointment}
            appointmentInterval={appointmentInterval}
          />
        ) : view === 'Semana' && groupBy === 'employee' ? (
          <WeekResourceGrid
            cursor={cursor} today={today} eventsByDay={eventsByDay} colorBy={colorBy}
            onSelectEvent={setDetailAppt} onSlotClick={onSlotClick}
            dayStart={dayStart} dayEnd={dayEnd} hourPx={hourPx}
            businessHours={businessHours} now={now}
            resources={employeeResources}
            resourceFor={(a) => a.membershipId}
            unassignedShort="S/E"
            blocks={blocksInRange}
            resourceType="employee"
            absences={absences}
            onDropAppointment={handleDropAppointment}
            appointmentInterval={appointmentInterval}
          />
        ) : view === 'Semana' ? (
          <WeekGrid
            cursor={cursor} today={today} eventsByDay={eventsByDay} colorBy={colorBy}
            onSelectEvent={setDetailAppt} onSlotClick={onSlotClick}
            dayStart={dayStart} dayEnd={dayEnd} hourPx={hourPx}
            businessHours={businessHours} now={now}
            blocks={blocksInRange}
            onDropAppointment={handleDropAppointment}
            appointmentInterval={appointmentInterval}
          />
        ) : groupBy === 'booth' ? (
          <ResourceDayGrid
            cursor={cursor} eventsByDay={eventsByDay} colorBy={colorBy}
            onSelectEvent={setDetailAppt} onSlotClick={onSlotClick}
            dayStart={dayStart} dayEnd={dayEnd} hourPx={hourPx}
            businessHours={businessHours} now={now}
            resources={boothResources}
            resourceFor={(a) => a.boothId}
            unassignedLabel="Sin cabina"
            blocks={blocksInRange}
            resourceType="booth"
            onDropAppointment={handleDropAppointment}
            appointmentInterval={appointmentInterval}
          />
        ) : groupBy === 'employee' ? (
          <ResourceDayGrid
            cursor={cursor} eventsByDay={eventsByDay} colorBy={colorBy}
            onSelectEvent={setDetailAppt} onSlotClick={onSlotClick}
            dayStart={dayStart} dayEnd={dayEnd} hourPx={hourPx}
            businessHours={businessHours} now={now}
            resources={employeeResources}
            resourceFor={(a) => a.membershipId}
            unassignedLabel="Sin empleado"
            blocks={blocksInRange}
            resourceType="employee"
            absences={absences}
            onDropAppointment={handleDropAppointment}
            appointmentInterval={appointmentInterval}
          />
        ) : (
          <DayGrid
            cursor={cursor} eventsByDay={eventsByDay} colorBy={colorBy}
            onSelectEvent={setDetailAppt} onSlotClick={onSlotClick}
            statusLabel={statusLabel} dayStart={dayStart} dayEnd={dayEnd} hourPx={hourPx}
            businessHours={businessHours} now={now}
            blocks={blocksInRange}
            onDropAppointment={handleDropAppointment}
            appointmentInterval={appointmentInterval}
          />
        )}
      </div>

      <AppointmentWizard
        open={wizard.open}
        onClose={closeWizard}
        onCreated={refetch}
        bId={bId}
        prefillDate={wizard.date}
        prefillTime={wizard.time}
        appointmentToEdit={wizard.appt}
      />
      <AppointmentDetailModal
        appointment={detailAppt}
        bId={bId}
        onClose={() => setDetailAppt(null)}
        onChanged={refetch}
        onEdit={openEditWizard}
        appliedBlock={detailApptBlock}
        appliedAbsence={detailApptAbsence}
        employeeColor={detailAppt?.employeeColor}
      />

      <ConfirmDropModal
        pending={pendingDrop}
        employeeResources={employeeResources}
        boothResources={boothResources}
        saving={droppingSaving}
        onCancel={() => setPendingDrop(null)}
        onConfirm={confirmDrop}
      />
    </div>
  )
}

/* ============================================================
   MODAL DE CONFIRMACION DEL DRAG-AND-DROP
   ============================================================
   El usuario arrastra una cita y la suelta en otro slot/recurso. Antes
   de hacer el PUT pedimos confirmacion porque mover una cita es una
   accion destructiva (sobreescribe membershipId, boothId y la hora). El
   componente formatea las diferencias en lenguaje natural ("X -> Y") y
   solo muestra las filas que realmente cambian. */
function ConfirmDropModal({ pending, employeeResources, boothResources, saving, onCancel, onConfirm }) {
  if (!pending) return null
  const { appt, newStartDateTime, newMembershipId, newBoothId } = pending

  const oldDate = new Date(appt.startDateTime)
  const newDate = new Date(newStartDateTime)
  const fmtDate = (d) => d.toLocaleDateString('es-ES', { weekday: 'short', day: '2-digit', month: 'short' })
  const fmtTime = (d) => d.toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' })

  const findEmp = (id) => employeeResources.find((r) => r.id === id)?.name ?? 'Sin empleado'
  const findBoo = (id) => id == null ? 'Sin cabina' : (boothResources.find((r) => r.id === id)?.name ?? 'Sin cabina')

  const empChanged = newMembershipId !== appt.membershipId
  const booChanged = (newBoothId ?? null) !== (appt.boothId ?? null)
  const dateChanged = oldDate.toDateString() !== newDate.toDateString()
  const timeChanged = oldDate.getTime() !== newDate.getTime()

  return (
    <Modal open onClose={saving ? () => {} : onCancel} title="Mover cita" size="sm">
      <div className="space-y-4">
        <p className="text-sm text-slate-600">
          Vas a reagendar la cita de <strong>{appt.clientName ?? 'el cliente'}</strong>.
          La cita conserva sus servicios y notas; solo cambia lo que se muestra abajo.
        </p>
        <div className="rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm">
          {(dateChanged || timeChanged) && (
            <div className="flex items-baseline justify-between gap-3 py-1">
              <span className="text-slate-500">Cuándo</span>
              <span className="font-medium text-[#1e3a5f] tabular-nums">
                {fmtDate(oldDate)} {fmtTime(oldDate)} <span className="text-slate-400">→</span> {fmtDate(newDate)} {fmtTime(newDate)}
              </span>
            </div>
          )}
          {empChanged && (
            <div className="flex items-baseline justify-between gap-3 py-1">
              <span className="text-slate-500">Empleado</span>
              <span className="font-medium text-[#1e3a5f]">
                {findEmp(appt.membershipId)} <span className="text-slate-400">→</span> {findEmp(newMembershipId)}
              </span>
            </div>
          )}
          {booChanged && (
            <div className="flex items-baseline justify-between gap-3 py-1">
              <span className="text-slate-500">Cabina</span>
              <span className="font-medium text-[#1e3a5f]">
                {findBoo(appt.boothId)} <span className="text-slate-400">→</span> {findBoo(newBoothId)}
              </span>
            </div>
          )}
        </div>
        <div className="flex justify-end gap-2 pt-1">
          <Button variant="ghost" onClick={onCancel} disabled={saving}>Cancelar</Button>
          <Button onClick={onConfirm} disabled={saving}>
            {saving ? 'Moviendo…' : 'Confirmar'}
          </Button>
        </div>
      </div>
    </Modal>
  )
}

/* ============================================================
   SUBCOMPONENTES DE VISTAS CRONOLÓGICAS
   ============================================================ */

function StatTile({ label, value, tone }) {
  const tones = { default: 'text-[#1e3a5f]', success: 'text-emerald-600', cyan: 'text-cyan-600' }
  return (
    <div className="bg-white rounded-2xl border border-slate-100 p-4">
      <div className="text-[10px] font-semibold text-slate-400 uppercase tracking-wider">{label}</div>
      <div className={`text-2xl font-extrabold mt-1 tabular-nums ${tones[tone] || tones.default}`}>{value}</div>
    </div>
  )
}

function MonthGrid({ cursor, today, eventsByDay, colorBy, onCellClick, onSelectEvent, onOpenDay, blocks = [] }) {
  const cells = buildMonthGrid(cursor.getFullYear(), cursor.getMonth())
  const month = cursor.getMonth()
  return (
    <>
      <div className="grid grid-cols-7 border-b border-slate-100 bg-slate-50/40">
        {DAYS_ES_SHORT.map((d, i) => (
          <div key={d} className={`px-3 py-3 text-[11px] uppercase tracking-[0.14em] font-semibold ${i >= 5 ? 'text-blue-600' : 'text-slate-500'}`}>{d}</div>
        ))}
      </div>
      <div className="grid grid-cols-7 grid-rows-6 bg-slate-100 gap-px">
        {cells.map((date, idx) => {
          const inMonth = date.getMonth() === month
          const isToday = isSameDay(date, today)
          const dayEvents = eventsByDay.get(keyOf(date)) || []
          const overflow = dayEvents.length - 3
          const shown = overflow > 0 ? dayEvents.slice(0, 2) : dayEvents.slice(0, 3)
          const activeAppts = dayEvents.filter((a) => a.statusName !== 'CANCELLED' && a.statusName !== 'NO_SHOW')
          const dayRevenue = activeAppts.reduce((acc, a) => acc + parseFloat(totalBooked(a.bookedServices)), 0)
          // [J] Heatmap por densidad de citas activas: cuanto mas ocupado el
          // dia, mas saturado el fondo. Umbrales 1/3/6 ajustados a un negocio
          // pequeno-mediano. Dias fuera del mes mantienen su gris claro y
          // hoy conserva su acento (chip con el numero del dia).
          const heat = activeAppts.length
          const heatBg = !inMonth
            ? 'bg-slate-50/60 hover:bg-slate-50'
            : heat >= 6 ? 'bg-blue-100/80 hover:bg-blue-200/60'
            : heat >= 3 ? 'bg-blue-50/70 hover:bg-blue-100/60'
            : heat >= 1 ? 'bg-blue-50/40 hover:bg-blue-50/70'
            : 'bg-white hover:bg-blue-50/40'
          // En la vista Mes no hay sub-columnas por recurso, asi que
          // mostramos cualquier bloqueo que aplique al dia (global o no);
          // el badge se imprime con el primer reason encontrado.
          const dayBlocks = blocksForCell(blocks, date)
            .concat(blocks.filter((b) => (b.membershipId != null || b.boothId != null)
                                          && keyOf(date) >= b.startDate
                                          && keyOf(date) <= b.endDate))
          // Deduplica por id (los globales pueden colarse dos veces).
          const seenIds = new Set()
          const uniqueDayBlocks = dayBlocks.filter((b) => {
            if (seenIds.has(b.id)) return false
            seenIds.add(b.id); return true
          })
          const hasBlock = uniqueDayBlocks.length > 0
          return (
            <div
              key={idx}
              onClick={() => onOpenDay(date)}
              className={`group relative min-h-[120px] p-2 transition ${heatBg} ${hasBlock ? 'ring-1 ring-inset ring-rose-200 cursor-not-allowed' : 'cursor-pointer'}`}
              title={hasBlock ? labelForBlock(uniqueDayBlocks[0]) : 'Ver el día'}
              style={hasBlock ? { backgroundImage: 'repeating-linear-gradient(45deg, rgba(244,63,94,0.08) 0 6px, transparent 6px 14px)' } : undefined}
            >
              <div className="flex items-center justify-between">
                <div className={`inline-flex items-center justify-center min-w-[24px] h-6 px-1.5 rounded-full text-xs font-semibold ${
                  isToday
                    ? 'bg-gradient-to-br from-cyan-500 to-blue-500 text-white shadow-[0_4px_10px_-2px_rgba(14,165,233,0.55)]'
                    : inMonth ? 'text-[#1e3a5f]' : 'text-slate-400'
                }`}>{date.getDate()}</div>
                {/* El boton "+" desaparece si el dia tiene un bloqueo
                    aplicable: no tiene sentido invitar a crear cita
                    cuando el backend (y los overlays) lo rechazarian. */}
                {!hasBlock && (
                  <button
                    type="button"
                    onClick={(e) => { e.stopPropagation(); onCellClick(keyOf(date)) }}
                    title="Crear cita este día"
                    className="opacity-0 group-hover:opacity-100 text-blue-500 hover:bg-blue-100 rounded p-0.5 transition"
                  >
                    <Plus size={14} />
                  </button>
                )}
              </div>
              <div className="mt-1.5 space-y-1">
                {hasBlock && (
                  <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded-md bg-rose-600 text-white text-[9px] font-bold uppercase tracking-wider shadow-sm">
                    <span className="truncate max-w-[100px]">{labelForBlock(uniqueDayBlocks[0])}</span>
                  </span>
                )}
                {shown.map((a) => <EventChip key={a.id} appt={a} onClick={onSelectEvent} colorBy={colorBy} variant="grid" />)}
                {overflow > 0 && (
                  <button
                    onClick={(e) => { e.stopPropagation(); onOpenDay(date) }}
                    className="w-full text-[11px] font-semibold text-blue-600 hover:bg-blue-50 rounded-md px-1.5 py-1 text-left transition"
                  >
                    +{overflow + 1} más
                  </button>
                )}
              </div>
              {inMonth && dayEvents.length > 0 && (
                <div className="absolute bottom-1 right-2 text-[9px] font-bold text-slate-400 tabular-nums">
                  {dayEvents.length} · {dayRevenue.toLocaleString('es-ES', { minimumFractionDigits: 0, maximumFractionDigits: 0 })} €
                </div>
              )}
            </div>
          )
        })}
      </div>
    </>
  )
}

function WeekGrid({ cursor, today, eventsByDay, colorBy, onSelectEvent, onSlotClick, dayStart, dayEnd, hourPx, businessHours, now, blocks = [], onDropAppointment, appointmentInterval = 30 }) {
  const ws = startOfWeek(cursor)
  const days = Array.from({ length: 7 }, (_, i) => { const d = new Date(ws); d.setDate(ws.getDate() + i); return d })
  return (
    <div className="overflow-auto" style={{ maxHeight: '68vh' }}>
      <div className="flex min-w-[720px]">
        <HourColumn dayStart={dayStart} dayEnd={dayEnd} hourPx={hourPx} />
        <div className="flex-1 grid grid-cols-7">
          {days.map((d, i) => {
            const dayKey = keyOf(d)
            const laidOut = layoutEvents(eventsByDay.get(dayKey) || [])
            const isToday = isSameDay(d, today)
            const openRanges = openRangesFor(businessHours, d)
            // En la vista Semana cronologica solo se renderizan los bloqueos
            // globales: una columna sin sub-columnas no puede separar un
            // bloqueo por empleado o por cabina. Los parciales se ven en
            // la vista Semana agrupada por recurso.
            const dayBlocks = blocksForCell(blocks, d)
            const dayIsBlocked = dayBlocks.length > 0
            const dayBlockLabel = dayIsBlocked ? labelForBlock(dayBlocks[0]) : null
            return (
              <div key={i} className="relative border-r-2 border-slate-300 last:border-r-0">
                <div className={`h-10 border-b-2 border-slate-300 flex items-center justify-center gap-2 sticky top-0 z-10 ${isToday ? 'bg-blue-100' : 'bg-white'}`}>
                  <span className={`text-[10px] uppercase tracking-wider font-semibold ${i >= 5 ? 'text-blue-600' : 'text-slate-500'}`}>{DAYS_ES_SHORT[i]}</span>
                  <span className={`inline-flex items-center justify-center min-w-[22px] h-6 px-1.5 rounded-full text-xs font-bold ${isToday ? 'bg-gradient-to-br from-cyan-500 to-blue-500 text-white' : 'text-[#1e3a5f]'}`}>{d.getDate()}</span>
                </div>
                <div
                  data-cal-cell
                  data-day={dayKey}
                  data-resource-type="none"
                  data-resource-id="__none__"
                  data-hour-px={hourPx}
                  data-day-start={dayStart}
                  data-interval={appointmentInterval}
                  className="relative"
                >
                  <HourSlots dayKey={dayKey} onSlotClick={onSlotClick} dayStart={dayStart} dayEnd={dayEnd} hourPx={hourPx} closedRanges={openRanges}
                             isBlocked={dayIsBlocked} blockedReason={dayBlockLabel} />
                  {laidOut.map(({ a, col, cols }) => (
                    <PositionedEvent key={a.id} appt={a} onClick={onSelectEvent} onDrop={onDropAppointment} col={col} cols={cols} colorBy={colorBy} dayStart={dayStart} hourPx={hourPx} />
                  ))}
                  {isToday && <NowLine now={now} dayStart={dayStart} dayEnd={dayEnd} hourPx={hourPx} />}
                  <BlockOverlay blocks={dayBlocks} />
                </div>
              </div>
            )
          })}
        </div>
      </div>
    </div>
  )
}

function DayGrid({ cursor, eventsByDay, colorBy, onSelectEvent, onSlotClick, statusLabel, dayStart, dayEnd, hourPx, businessHours, now, blocks = [], onDropAppointment, appointmentInterval = 30 }) {
  const dayKey = keyOf(cursor)
  const dayEvents = eventsByDay.get(dayKey) || []
  const sorted = [...dayEvents].sort((a, b) => minutesOf(a.startDateTime) - minutesOf(b.startDateTime))
  const laidOut = layoutEvents(dayEvents)
  const openRanges = openRangesFor(businessHours, cursor)
  const isToday = isSameDay(cursor, new Date())
  // Vista Dia cronologica: solo se muestran los bloqueos globales (no hay
  // sub-columnas para los bloqueos por recurso, que se ven al pasar a
  // agrupacion por cabina/empleado).
  const dayBlocks = blocksForCell(blocks, cursor)
  const dayIsBlocked = dayBlocks.length > 0
  const dayBlockLabel = dayIsBlocked ? labelForBlock(dayBlocks[0]) : null

  return (
    <div className="grid grid-cols-1 lg:grid-cols-[1fr_300px] gap-5 p-5">
      <div className="border border-slate-100 rounded-2xl overflow-hidden">
        <div className="overflow-auto" style={{ maxHeight: '64vh' }}>
          <div className="flex">
            <HourColumn withHeader={false} dayStart={dayStart} dayEnd={dayEnd} hourPx={hourPx} />
            <div
              data-cal-cell
              data-day={dayKey}
              data-resource-type="none"
              data-resource-id="__none__"
              data-hour-px={hourPx}
              data-day-start={dayStart}
              data-interval={appointmentInterval}
              className="flex-1 relative"
            >
              <HourSlots dayKey={dayKey} onSlotClick={onSlotClick} dayStart={dayStart} dayEnd={dayEnd} hourPx={hourPx} closedRanges={openRanges}
                         isBlocked={dayIsBlocked} blockedReason={dayBlockLabel} />
              {laidOut.map(({ a, col, cols }) => (
                <PositionedEvent key={a.id} appt={a} onClick={onSelectEvent} onDrop={onDropAppointment} col={col} cols={cols} colorBy={colorBy} dayStart={dayStart} hourPx={hourPx} />
              ))}
              {isToday && <NowLine now={now} dayStart={dayStart} dayEnd={dayEnd} hourPx={hourPx} />}
              <BlockOverlay blocks={dayBlocks} />
            </div>
          </div>
        </div>
      </div>

      <aside className="space-y-4 no-print">
        <div className="bg-white border border-slate-100 rounded-2xl p-5">
          <p className="text-xs uppercase tracking-[0.14em] text-slate-400 font-semibold mb-1">Resumen del día</p>
          <p className="text-3xl font-bold text-[#1e3a5f]">{dayEvents.length}</p>
          <p className="text-sm text-slate-500">cita{dayEvents.length === 1 ? '' : 's'} programada{dayEvents.length === 1 ? '' : 's'}</p>
          <div className="mt-4 space-y-2">
            {dayEvents.length === 0 && <p className="text-sm text-slate-400">Sin citas este día.</p>}
            {Object.keys(STATUS_STYLES).map((k) => {
              const n = dayEvents.filter((e) => e.statusName === k).length
              if (n === 0) return null
              const s = STATUS_STYLES[k]
              return (
                <div key={k} className="flex items-center justify-between text-sm">
                  <span className="flex items-center gap-2">
                    <span className={`w-2 h-2 rounded-full ${s.dot}`} />
                    <span className="text-slate-600">{statusLabel(k)}</span>
                  </span>
                  <span className={`font-semibold ${s.text}`}>{n}</span>
                </div>
              )
            })}
          </div>
        </div>

        <div className="bg-white border border-slate-100 rounded-2xl p-5">
          <p className="text-xs uppercase tracking-[0.14em] text-slate-400 font-semibold mb-3">Agenda</p>
          {sorted.length === 0 ? (
            <p className="text-sm text-slate-400">No hay citas este día.</p>
          ) : (
            <div className="space-y-2">
              {sorted.map((a) => <EventChip key={a.id} appt={a} onClick={onSelectEvent} colorBy={colorBy} />)}
            </div>
          )}
        </div>
      </aside>
    </div>
  )
}

/* ============================================================
   TOOLBAR DE FILTROS (popover compacto)
   ============================================================ */

const STATUS_FILTER_OPTIONS = [
  { key: '',            label: 'Todos' },
  { key: 'PENDING',     dot: 'bg-amber-500' },
  { key: 'CONFIRMED',   dot: 'bg-blue-500' },
  { key: 'IN_PROGRESS', dot: 'bg-cyan-500' },
  { key: 'COMPLETED',   dot: 'bg-emerald-500' },
  { key: 'CANCELLED',   dot: 'bg-slate-400' },
  { key: 'NO_SHOW',     dot: 'bg-rose-500' },
]

function FiltersBar({
  employees, booths,
  employeeFilter, setEmployeeFilter,
  boothFilter,    setBoothFilter,
  statusFilter,   setStatusFilter,
  colorBy,        setColorBy,
  view,
  groupBy,        setGroupBy,
  statusLabel,
}) {
  const [open, setOpen] = useState(false)
  const popRef = useRef(null)
  const btnRef = useRef(null)

  // Cerrar al pulsar fuera o ESC
  useEffect(() => {
    if (!open) return
    const onClick = (e) => {
      if (popRef.current?.contains(e.target)) return
      if (btnRef.current?.contains(e.target)) return
      setOpen(false)
    }
    const onKey = (e) => { if (e.key === 'Escape') setOpen(false) }
    document.addEventListener('mousedown', onClick)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onClick)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  const activeCount =
    (employeeFilter ? 1 : 0) +
    (boothFilter ? 1 : 0) +
    (statusFilter ? 1 : 0)

  const clearAll = () => {
    setEmployeeFilter('')
    setBoothFilter('')
    setStatusFilter('')
  }

  const employeeLabel = employees.find((e) => String(e.id) === String(employeeFilter))?.fullName
  const boothLabelActive = booths.find((b) => String(b.id) === String(boothFilter))?.name
  const statusOpt     = STATUS_FILTER_OPTIONS.find((s) => s.key === statusFilter)

  return (
    <div className="flex flex-wrap items-center gap-3 px-5 py-3 border-b border-slate-100 bg-slate-50/40 no-print">
      {/* Botón Filtros */}
      <div className="relative">
        <button
          ref={btnRef}
          onClick={() => setOpen((v) => !v)}
          className={`h-9 inline-flex items-center gap-2 rounded-xl border px-3 text-xs font-semibold transition ${
            open || activeCount > 0
              ? 'border-blue-300 bg-blue-50 text-[#1e3a5f]'
              : 'border-slate-200 bg-white text-slate-600 hover:border-blue-300 hover:text-[#1e3a5f]'
          }`}
        >
          <SlidersHorizontal size={14} />
          Filtros
          {activeCount > 0 && (
            <span className="inline-flex items-center justify-center min-w-[18px] h-[18px] rounded-full bg-[#1e3a5f] text-white text-[10px] font-bold tabular-nums px-1">
              {activeCount}
            </span>
          )}
        </button>

        {open && (
          <div
            ref={popRef}
            className="absolute left-0 top-[calc(100%+8px)] z-30 w-[320px] bg-white rounded-2xl border border-slate-200 shadow-[0_20px_50px_-10px_rgba(15,23,42,0.18)] p-4"
          >
            <div className="space-y-4">
              <FilterRow label="Empleado">
                <select
                  value={employeeFilter}
                  onChange={(e) => setEmployeeFilter(e.target.value)}
                  className="w-full h-9 rounded-lg border border-slate-200 bg-white px-3 text-xs font-semibold text-[#1e3a5f] focus:outline-none focus:border-blue-400 focus:ring-4 focus:ring-blue-100 transition"
                >
                  <option value="">Todos los empleados</option>
                  {employees.map((e) => (<option key={e.id} value={e.id}>{e.fullName}</option>))}
                </select>
              </FilterRow>

              <FilterRow label="Cabina">
                <select
                  value={boothFilter}
                  onChange={(e) => setBoothFilter(e.target.value)}
                  className="w-full h-9 rounded-lg border border-slate-200 bg-white px-3 text-xs font-semibold text-[#1e3a5f] focus:outline-none focus:border-blue-400 focus:ring-4 focus:ring-blue-100 transition"
                >
                  <option value="">Todas las cabinas</option>
                  {booths.map((b) => (<option key={b.id} value={b.id}>{b.name}</option>))}
                </select>
              </FilterRow>

              <FilterRow label="Estado">
                <div className="flex flex-wrap gap-1.5">
                  {STATUS_FILTER_OPTIONS.map(({ key, label, dot }) => (
                    <button
                      key={key || 'ALL'}
                      onClick={() => setStatusFilter(key)}
                      className={`text-[11px] font-semibold px-2.5 py-1 rounded-full transition inline-flex items-center gap-1 ${
                        statusFilter === key
                          ? 'bg-[#1e3a5f] text-white'
                          : 'bg-slate-50 border border-slate-200 text-slate-600 hover:border-blue-300'
                      }`}
                    >
                      {dot && <span className={`w-1.5 h-1.5 rounded-full ${dot}`} />}
                      {label ?? statusLabel(key)}
                    </button>
                  ))}
                </div>
              </FilterRow>
            </div>

            <div className="mt-4 pt-3 border-t border-slate-100 flex items-center justify-between gap-2">
              <button
                onClick={clearAll}
                disabled={activeCount === 0}
                className="text-[11px] font-semibold text-slate-500 hover:text-[#1e3a5f] disabled:opacity-40 disabled:cursor-not-allowed transition"
              >
                Limpiar filtros
              </button>
              <button
                onClick={() => setOpen(false)}
                className="text-[11px] font-semibold text-white bg-[#1e3a5f] hover:bg-[#2a4f82] rounded-lg px-3 py-1.5 transition"
              >
                Hecho
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Chips activos (resumen rápido junto al botón) */}
      {employeeLabel && (
        <ActiveChip label={employeeLabel} onClear={() => setEmployeeFilter('')} />
      )}
      {boothLabelActive && (
        <ActiveChip label={boothLabelActive} onClear={() => setBoothFilter('')} />
      )}
      {statusOpt && statusOpt.key && (
        <ActiveChip
          label={statusLabel(statusOpt.key)}
          dot={statusOpt.dot}
          onClear={() => setStatusFilter('')}
        />
      )}

      {/* Color (preferencia visual, en todas las vistas) */}
      <div className="flex items-center gap-1.5 ml-1">
        <span className="text-[10px] font-bold text-slate-400 uppercase tracking-[0.16em]">Color</span>
        <div className="inline-flex items-center bg-slate-100 rounded-lg p-1">
          {[
            { key: 'status',   label: 'Estado' },
            { key: 'employee', label: 'Empleado' },
            { key: 'booth',    label: 'Cabina' },
          ].map(({ key, label }) => (
            <button
              key={key}
              onClick={() => setColorBy(key)}
              className={`px-2.5 py-1 rounded-md text-[11px] font-semibold transition ${colorBy === key ? 'bg-white text-[#1e3a5f] shadow-[0_1px_4px_rgba(15,23,42,0.08)]' : 'text-slate-500 hover:text-[#1e3a5f]'}`}
            >{label}</button>
          ))}
        </div>
      </div>

      {/* Agrupar (solo en Día/Semana) */}
      {(view === 'Día' || view === 'Semana') && (
        <div className="flex items-center gap-1.5 ml-1">
          <span className="text-[10px] font-bold text-slate-400 uppercase tracking-[0.16em]">Agrupar</span>
          <div className="inline-flex items-center bg-slate-100 rounded-lg p-1">
            {[
              { key: 'time',     label: 'Cronol.' },
              { key: 'booth',    label: 'Cabinas' },
              { key: 'employee', label: 'Empleados' },
            ].map(({ key, label }) => (
              <button
                key={key}
                onClick={() => setGroupBy(key)}
                className={`px-2.5 py-1 rounded-md text-[11px] font-semibold transition ${groupBy === key ? 'bg-white text-[#1e3a5f] shadow-[0_1px_4px_rgba(15,23,42,0.08)]' : 'text-slate-500 hover:text-[#1e3a5f]'}`}
              >{label}</button>
            ))}
          </div>
        </div>
      )}

    </div>
  )
}

function FilterRow({ label, children }) {
  return (
    <div>
      <p className="text-[10px] font-bold text-slate-400 uppercase tracking-[0.16em] mb-1.5">{label}</p>
      {children}
    </div>
  )
}

function ActiveChip({ label, dot, onClear }) {
  return (
    <span className="inline-flex items-center gap-1.5 h-7 pl-2.5 pr-1 rounded-full bg-white border border-slate-200 text-[11px] font-semibold text-[#1e3a5f]">
      {dot && <span className={`w-1.5 h-1.5 rounded-full ${dot}`} />}
      <span className="truncate max-w-[140px]">{label}</span>
      <button
        onClick={onClear}
        aria-label={`Quitar filtro: ${label}`}
        className="w-5 h-5 rounded-full flex items-center justify-center text-slate-400 hover:bg-slate-100 hover:text-slate-700 transition"
      >
        <X size={11} />
      </button>
    </span>
  )
}

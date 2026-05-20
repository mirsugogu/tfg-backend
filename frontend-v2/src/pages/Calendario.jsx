import { useEffect, useMemo, useState } from 'react'
import { ChevronLeft, ChevronRight, Plus } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { AppointmentWizard } from '@/components/appointments/AppointmentWizard'
import { AppointmentDetailModal } from '@/components/appointments/AppointmentDetailModal'
import { useAuth } from '@/context/AuthContext'
import { useCatalog } from '@/context/CatalogContext'
import { useToast } from '@/components/ui/Toast'
import api, { getErrorMessage } from '@/lib/api'

const MONTHS_ES = ['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio', 'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre']
const DAYS_ES_SHORT = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom']
const VIEW_MODES = ['Mes', 'Semana', 'Día']

// Franja horaria visible en las vistas Semana y Día.
const DAY_START = 8
const DAY_END = 21
const HOUR_PX = 64

// Colores por estado — los 6 del backend. CANCELLED y NO_SHOW en gris/rosa
// apagado para que las citas activas destaquen.
const STATUS_STYLES = {
  PENDING:     { bar: 'bg-amber-500',   bg: 'bg-amber-50',   text: 'text-amber-700',   ring: 'ring-amber-200',   hover: 'hover:bg-amber-100',  dot: 'bg-amber-500' },
  CONFIRMED:   { bar: 'bg-blue-500',    bg: 'bg-blue-50',    text: 'text-blue-700',    ring: 'ring-blue-200',    hover: 'hover:bg-blue-100',   dot: 'bg-blue-500' },
  IN_PROGRESS: { bar: 'bg-cyan-500',    bg: 'bg-cyan-50',    text: 'text-cyan-700',    ring: 'ring-cyan-200',    hover: 'hover:bg-cyan-100',   dot: 'bg-cyan-500' },
  COMPLETED:   { bar: 'bg-emerald-500', bg: 'bg-emerald-50', text: 'text-emerald-700', ring: 'ring-emerald-200', hover: 'hover:bg-emerald-100', dot: 'bg-emerald-500' },
  CANCELLED:   { bar: 'bg-slate-400',   bg: 'bg-slate-100',  text: 'text-slate-500',   ring: 'ring-slate-200',   hover: 'hover:bg-slate-200',  dot: 'bg-slate-400' },
  NO_SHOW:     { bar: 'bg-rose-400',    bg: 'bg-rose-50',    text: 'text-rose-600',    ring: 'ring-rose-200',    hover: 'hover:bg-rose-100',   dot: 'bg-rose-400' },
}
const styleOf = (name) => STATUS_STYLES[name] || STATUS_STYLES.PENDING

// ───────── helpers de fecha ─────────
const pad2 = (n) => String(n).padStart(2, '0')
const keyOf = (d) => `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`
const isSameDay = (a, b) =>
  a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate()

function startOfWeek(d) {
  const dow = (d.getDay() + 6) % 7   // lunes = 0
  const r = new Date(d)
  r.setDate(d.getDate() - dow)
  r.setHours(0, 0, 0, 0)
  return r
}

function buildMonthGrid(year, month) {
  const first = new Date(year, month, 1)
  const dow = (first.getDay() + 6) % 7
  const start = new Date(year, month, 1 - dow)
  return Array.from({ length: 42 }, (_, i) => {
    const d = new Date(start)
    d.setDate(start.getDate() + i)
    return d
  })
}

// Rango de fechas que hay que pedir al backend según la vista activa.
function rangeFor(view, cursor) {
  if (view === 'Mes') {
    const cells = buildMonthGrid(cursor.getFullYear(), cursor.getMonth())
    return { from: keyOf(cells[0]), to: keyOf(cells[41]) }
  }
  if (view === 'Semana') {
    const ws = startOfWeek(cursor)
    const we = new Date(ws)
    we.setDate(ws.getDate() + 6)
    return { from: keyOf(ws), to: keyOf(we) }
  }
  return { from: keyOf(cursor), to: keyOf(cursor) }
}

// ───────── helpers de cita ─────────
// startDateTime/endDateTime llegan como "YYYY-MM-DDTHH:mm:ss" (hora local).
const apptDate = (a) => a.startDateTime.slice(0, 10)
const apptHHMM = (iso) => iso.slice(11, 16)
const minutesOf = (iso) => {
  const [h, m] = iso.slice(11, 16).split(':')
  return Number(h) * 60 + Number(m)
}
const apptDuration = (a) => Math.max(15, minutesOf(a.endDateTime) - minutesOf(a.startDateTime))

/**
 * Coloca en columnas paralelas las citas que se solapan en el tiempo
 * (vistas Semana y Día). Devuelve cada cita con { col, cols } para
 * calcular su anchura y desplazamiento horizontal.
 */
function layoutEvents(events) {
  const items = events
    .map((a) => {
      const startMin = minutesOf(a.startDateTime)
      return { a, startMin, endMin: startMin + apptDuration(a) }
    })
    .sort((x, y) => x.startMin - y.startMin || x.endMin - y.endMin)

  const groups = []
  let current = []
  let currentEnd = -1
  items.forEach((it) => {
    if (it.startMin < currentEnd) {
      current.push(it)
      currentEnd = Math.max(currentEnd, it.endMin)
    } else {
      if (current.length) groups.push(current)
      current = [it]
      currentEnd = it.endMin
    }
  })
  if (current.length) groups.push(current)

  const out = []
  groups.forEach((group) => {
    const cols = []
    group.forEach((it) => {
      let placed = -1
      for (let i = 0; i < cols.length; i++) {
        if (cols[i] <= it.startMin) { cols[i] = it.endMin; placed = i; break }
      }
      if (placed === -1) { cols.push(it.endMin); placed = cols.length - 1 }
      it.col = placed
    })
    group.forEach((it) => { it.cols = cols.length; out.push(it) })
  })
  return out
}

// ───────── chip de cita (vista Mes y lista del día) ─────────
function EventChip({ appt, onClick }) {
  const s = styleOf(appt.statusName)
  return (
    <button
      onClick={(e) => { e.stopPropagation(); onClick(appt) }}
      className={`w-full flex items-center gap-1.5 ${s.bg} ${s.hover} ${s.text} text-[11px] font-medium rounded-md px-1.5 py-1 text-left transition`}
      title={`${appt.clientName} · ${apptHHMM(appt.startDateTime)}`}
    >
      <span className={`w-0.5 self-stretch ${s.bar} rounded-full shrink-0`} />
      <span className="truncate flex-1">{appt.clientName}</span>
      <span className="text-[10px] opacity-70 hidden xl:inline">{apptHHMM(appt.startDateTime)}</span>
    </button>
  )
}

// ───────── cita posicionada (vistas Semana y Día) ─────────
function PositionedEvent({ appt, onClick, col, cols }) {
  const topPx = ((minutesOf(appt.startDateTime) - DAY_START * 60) / 60) * HOUR_PX
  const heightPx = Math.max(22, (apptDuration(appt) / 60) * HOUR_PX - 4)
  const widthPct = 100 / cols
  const s = styleOf(appt.statusName)
  return (
    <button
      onClick={(e) => { e.stopPropagation(); onClick(appt) }}
      style={{
        top: `${topPx}px`, height: `${heightPx}px`,
        left: `calc(${col * widthPct}% + 2px)`, width: `calc(${widthPct}% - 4px)`,
      }}
      className={`absolute ${s.bg} ${s.hover} ${s.text} ring-1 ${s.ring} rounded-lg pl-2.5 pr-2 py-1 text-left overflow-hidden transition shadow-[0_2px_8px_-4px_rgba(15,23,42,0.15)]`}
    >
      <span className={`absolute left-0 top-0 bottom-0 w-1 ${s.bar} rounded-l-lg`} />
      <div className="text-[11px] font-semibold truncate leading-tight">{appt.clientName}</div>
      {heightPx > 40 && (
        <div className="text-[10px] opacity-75 truncate leading-tight">{appt.userFullName}</div>
      )}
      <div className="text-[10px] opacity-70 leading-tight">
        {apptHHMM(appt.startDateTime)} – {apptHHMM(appt.endDateTime)}
      </div>
    </button>
  )
}

// Columna de horas (etiquetas) compartida por Semana y Día.
function HourColumn({ withHeader = true }) {
  const hours = []
  for (let h = DAY_START; h < DAY_END; h++) hours.push(h)
  return (
    <div className="w-14 shrink-0 border-r border-slate-100 bg-white">
      {withHeader && <div className="h-10 border-b border-slate-100" />}
      {hours.map((h) => (
        <div key={h} style={{ height: HOUR_PX }} className="relative">
          <span className="absolute -top-2 right-1.5 text-[10px] font-semibold text-slate-400 bg-white px-1">
            {pad2(h)}:00
          </span>
        </div>
      ))}
    </div>
  )
}

// Rejilla horaria de un día: cada hora es un hueco pulsable (crear cita).
function HourSlots({ dayKey, onSlotClick }) {
  const hours = []
  for (let h = DAY_START; h < DAY_END; h++) hours.push(h)
  return (
    <>
      {hours.map((h) => (
        <div
          key={h}
          onClick={() => onSlotClick(dayKey, h)}
          style={{ height: HOUR_PX }}
          className="border-b border-slate-100 hover:bg-blue-50/40 cursor-pointer relative"
          title="Crear cita a esta hora"
        >
          <div className="absolute left-0 right-0 border-t border-dashed border-slate-100" style={{ top: HOUR_PX / 2 }} />
        </div>
      ))}
    </>
  )
}

// ───────── vista Mes ─────────
function MonthGrid({ cursor, today, eventsByDay, onCellClick, onSelectEvent, onOpenDay }) {
  const cells = buildMonthGrid(cursor.getFullYear(), cursor.getMonth())
  const month = cursor.getMonth()
  return (
    <>
      <div className="grid grid-cols-7 border-b border-slate-100 bg-slate-50/40">
        {DAYS_ES_SHORT.map((d, i) => (
          <div key={d} className={`px-3 py-3 text-[11px] uppercase tracking-[0.14em] font-semibold ${i >= 5 ? 'text-blue-600' : 'text-slate-500'}`}>
            {d}
          </div>
        ))}
      </div>
      <div className="grid grid-cols-7 grid-rows-6 bg-slate-100 gap-px">
        {cells.map((date, idx) => {
          const inMonth = date.getMonth() === month
          const isToday = isSameDay(date, today)
          const dayEvents = eventsByDay.get(keyOf(date)) || []
          const overflow = dayEvents.length - 3
          const shown = overflow > 0 ? dayEvents.slice(0, 2) : dayEvents.slice(0, 3)
          return (
            <div
              key={idx}
              onClick={() => onCellClick(keyOf(date))}
              className={`group relative min-h-[112px] p-2 cursor-pointer transition ${inMonth ? 'bg-white hover:bg-blue-50/40' : 'bg-slate-50/60 hover:bg-slate-50'}`}
              title="Crear cita este día"
            >
              <div className="flex items-center justify-between">
                <div className={`inline-flex items-center justify-center min-w-[24px] h-6 px-1.5 rounded-full text-xs font-semibold ${
                  isToday
                    ? 'bg-gradient-to-br from-cyan-500 to-blue-500 text-white shadow-[0_4px_10px_-2px_rgba(14,165,233,0.55)]'
                    : inMonth ? 'text-[#1e3a5f]' : 'text-slate-400'
                }`}>
                  {date.getDate()}
                </div>
                <Plus size={13} className="opacity-0 group-hover:opacity-60 text-blue-500" />
              </div>
              <div className="mt-1.5 space-y-1">
                {shown.map((a) => <EventChip key={a.id} appt={a} onClick={onSelectEvent} />)}
                {overflow > 0 && (
                  <button
                    onClick={(e) => { e.stopPropagation(); onOpenDay(date) }}
                    className="w-full text-[11px] font-semibold text-blue-600 hover:bg-blue-50 rounded-md px-1.5 py-1 text-left transition"
                  >
                    +{overflow + 1} más
                  </button>
                )}
              </div>
            </div>
          )
        })}
      </div>
    </>
  )
}

// ───────── vista Semana ─────────
function WeekGrid({ cursor, today, eventsByDay, onSelectEvent, onSlotClick }) {
  const ws = startOfWeek(cursor)
  const days = Array.from({ length: 7 }, (_, i) => {
    const d = new Date(ws)
    d.setDate(ws.getDate() + i)
    return d
  })
  return (
    <div className="overflow-auto" style={{ maxHeight: '68vh' }}>
      <div className="flex min-w-[720px]">
        <HourColumn />
        <div className="flex-1 grid grid-cols-7">
          {days.map((d, i) => {
            const dayKey = keyOf(d)
            const laidOut = layoutEvents(eventsByDay.get(dayKey) || [])
            const isToday = isSameDay(d, today)
            return (
              <div key={i} className="relative border-r border-slate-100 last:border-r-0">
                <div className={`h-10 border-b border-slate-100 flex items-center justify-center gap-2 sticky top-0 z-10 ${isToday ? 'bg-blue-50/80' : 'bg-white'}`}>
                  <span className={`text-[10px] uppercase tracking-wider font-semibold ${i >= 5 ? 'text-blue-600' : 'text-slate-500'}`}>
                    {DAYS_ES_SHORT[i]}
                  </span>
                  <span className={`inline-flex items-center justify-center min-w-[22px] h-6 px-1.5 rounded-full text-xs font-bold ${isToday ? 'bg-gradient-to-br from-cyan-500 to-blue-500 text-white' : 'text-[#1e3a5f]'}`}>
                    {d.getDate()}
                  </span>
                </div>
                <div className="relative">
                  <HourSlots dayKey={dayKey} onSlotClick={onSlotClick} />
                  {laidOut.map(({ a, col, cols }) => (
                    <PositionedEvent key={a.id} appt={a} onClick={onSelectEvent} col={col} cols={cols} />
                  ))}
                </div>
              </div>
            )
          })}
        </div>
      </div>
    </div>
  )
}

// ───────── vista Día ─────────
function DayGrid({ cursor, eventsByDay, onSelectEvent, onSlotClick, statusLabel }) {
  const dayKey = keyOf(cursor)
  const dayEvents = eventsByDay.get(dayKey) || []
  const sorted = [...dayEvents].sort((a, b) => minutesOf(a.startDateTime) - minutesOf(b.startDateTime))
  const laidOut = layoutEvents(dayEvents)
  return (
    <div className="grid grid-cols-1 lg:grid-cols-[1fr_300px] gap-5 p-5">
      <div className="border border-slate-100 rounded-2xl overflow-hidden">
        <div className="overflow-auto" style={{ maxHeight: '64vh' }}>
          <div className="flex">
            <HourColumn withHeader={false} />
            <div className="flex-1 relative">
              <HourSlots dayKey={dayKey} onSlotClick={onSlotClick} />
              {laidOut.map(({ a, col, cols }) => (
                <PositionedEvent key={a.id} appt={a} onClick={onSelectEvent} col={col} cols={cols} />
              ))}
            </div>
          </div>
        </div>
      </div>

      <aside className="space-y-4">
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
              {sorted.map((a) => <EventChip key={a.id} appt={a} onClick={onSelectEvent} />)}
            </div>
          )}
        </div>
      </aside>
    </div>
  )
}

/**
 * Calendario — vista de agenda de las citas con 3 modos (Mes / Semana /
 * Día). Adaptado del prototipo visual docs/visual/src/CalendarView.jsx a
 * datos reales del backend.
 *
 * - Carga las citas del rango visible con GET /appointments?from=&to=.
 * - Pulsar una cita abre el detalle; pulsar un hueco abre el asistente
 *   de creación con la fecha (y hora, en Semana/Día) precargadas.
 * - Reutiliza AppointmentWizard y AppointmentDetailModal (los mismos
 *   componentes que la pantalla Citas).
 */
export default function Calendario() {
  const { user } = useAuth()
  const { statusLabel } = useCatalog()
  const toast = useToast()
  const bId = user?.businessId

  const [cursor, setCursor] = useState(() => {
    const d = new Date()
    return new Date(d.getFullYear(), d.getMonth(), d.getDate())
  })
  const [view, setView] = useState('Mes')
  const [appointments, setAppointments] = useState([])
  const [loading, setLoading] = useState(true)
  const [reloadFlag, setReloadFlag] = useState(0)
  const today = useMemo(() => new Date(), [])

  const [wizard, setWizard] = useState({ open: false, date: null, time: null })
  const [detailAppt, setDetailAppt] = useState(null)

  // Carga las citas del rango visible. Se vuelve a pedir al cambiar de
  // vista, de fecha de referencia o tras crear/modificar una cita.
  useEffect(() => {
    if (!bId) return
    let cancelled = false
    const { from, to } = rangeFor(view, cursor)
    setLoading(true)
    api.get(`/api/businesses/${bId}/appointments`, {
      params: { from, to, size: 100, sort: 'startDateTime,asc' },
    })
      .then((r) => { if (!cancelled) setAppointments(r.data.content ?? []) })
      .catch((err) => {
        if (!cancelled) toast({ type: 'error', message: getErrorMessage(err, 'No se pudieron cargar las citas.') })
      })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [bId, view, cursor, reloadFlag, toast])

  const refetch = () => setReloadFlag((v) => v + 1)

  // Agrupa las citas por día ("YYYY-MM-DD") para pintarlas en la rejilla.
  const eventsByDay = useMemo(() => {
    const map = new Map()
    appointments.forEach((a) => {
      const k = apptDate(a)
      if (!map.has(k)) map.set(k, [])
      map.get(k).push(a)
    })
    return map
  }, [appointments])

  const goPrev = () => {
    if (view === 'Mes') setCursor((c) => new Date(c.getFullYear(), c.getMonth() - 1, 1))
    else if (view === 'Semana') setCursor((c) => { const d = new Date(c); d.setDate(c.getDate() - 7); return d })
    else setCursor((c) => { const d = new Date(c); d.setDate(c.getDate() - 1); return d })
  }
  const goNext = () => {
    if (view === 'Mes') setCursor((c) => new Date(c.getFullYear(), c.getMonth() + 1, 1))
    else if (view === 'Semana') setCursor((c) => { const d = new Date(c); d.setDate(c.getDate() + 7); return d })
    else setCursor((c) => { const d = new Date(c); d.setDate(c.getDate() + 1); return d })
  }
  const goToday = () => {
    const d = new Date()
    setCursor(new Date(d.getFullYear(), d.getMonth(), d.getDate()))
  }

  const openWizard = (date = null, time = null) => setWizard({ open: true, date, time })
  const closeWizard = () => setWizard({ open: false, date: null, time: null })

  const onSlotClick = (dayKey, hour) => openWizard(dayKey, `${pad2(hour)}:00`)
  const onCellClick = (dayKey) => openWizard(dayKey, null)
  const onOpenDay = (date) => { setCursor(date); setView('Día') }

  // Texto de la cabecera, adaptado a la vista activa.
  let headerText = `${MONTHS_ES[cursor.getMonth()]} ${cursor.getFullYear()}`
  if (view === 'Semana') {
    const ws = startOfWeek(cursor)
    const we = new Date(ws)
    we.setDate(ws.getDate() + 6)
    headerText = ws.getMonth() === we.getMonth()
      ? `${ws.getDate()} – ${we.getDate()} ${MONTHS_ES[ws.getMonth()]} ${ws.getFullYear()}`
      : `${ws.getDate()} ${MONTHS_ES[ws.getMonth()].slice(0, 3)} – ${we.getDate()} ${MONTHS_ES[we.getMonth()].slice(0, 3)} ${we.getFullYear()}`
  } else if (view === 'Día') {
    headerText = cursor.toLocaleDateString('es-ES', { weekday: 'long', day: '2-digit', month: 'long', year: 'numeric' })
  }

  return (
    <div className="p-8 max-w-7xl mx-auto">
      <div className="mb-6">
        <h1 className="text-3xl font-bold text-[#1e3a5f] tracking-tight">Calendario</h1>
        <p className="text-sm text-slate-500 mt-1.5 font-medium">
          Pulsa una cita para ver el detalle, o un hueco libre para crear una nueva.
        </p>
      </div>

      <div className="bg-white rounded-2xl border border-slate-100/80 shadow-[0_2px_12px_-2px_rgba(15,23,42,0.06)] overflow-hidden">
        {/* Barra de herramientas */}
        <div className="flex flex-wrap items-center gap-3 px-5 py-4 border-b border-slate-100">
          <div className="flex items-center gap-1.5">
            <button onClick={goPrev} aria-label="Anterior" className="w-9 h-9 rounded-xl border border-slate-200 text-slate-500 hover:text-[#1e3a5f] hover:border-blue-300 hover:bg-blue-50 transition flex items-center justify-center">
              <ChevronLeft size={16} />
            </button>
            <button onClick={goNext} aria-label="Siguiente" className="w-9 h-9 rounded-xl border border-slate-200 text-slate-500 hover:text-[#1e3a5f] hover:border-blue-300 hover:bg-blue-50 transition flex items-center justify-center">
              <ChevronRight size={16} />
            </button>
            <button onClick={goToday} className="ml-1 px-3 h-9 rounded-xl border border-slate-200 text-xs font-semibold text-slate-600 hover:text-[#1e3a5f] hover:border-blue-300 hover:bg-blue-50 transition">
              Hoy
            </button>
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

          <Button onClick={() => openWizard(keyOf(cursor), null)} className="gap-2">
            <Plus size={16} /> Nueva cita
          </Button>
        </div>

        {loading ? (
          <div className="p-16 text-center text-slate-400 text-sm">Cargando agenda…</div>
        ) : view === 'Mes' ? (
          <MonthGrid
            cursor={cursor} today={today} eventsByDay={eventsByDay}
            onCellClick={onCellClick} onSelectEvent={setDetailAppt} onOpenDay={onOpenDay}
          />
        ) : view === 'Semana' ? (
          <WeekGrid
            cursor={cursor} today={today} eventsByDay={eventsByDay}
            onSelectEvent={setDetailAppt} onSlotClick={onSlotClick}
          />
        ) : (
          <DayGrid
            cursor={cursor} eventsByDay={eventsByDay}
            onSelectEvent={setDetailAppt} onSlotClick={onSlotClick} statusLabel={statusLabel}
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
      />
      <AppointmentDetailModal
        appointment={detailAppt}
        bId={bId}
        onClose={() => setDetailAppt(null)}
        onChanged={refetch}
      />
    </div>
  )
}

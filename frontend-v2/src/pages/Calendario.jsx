import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  ChevronLeft, ChevronRight, Plus, RefreshCw, Printer,
  Users as UsersIcon, MapPin as MapPinIcon, Palette,
} from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { AppointmentWizard } from '@/components/appointments/AppointmentWizard'
import { AppointmentDetailModal } from '@/components/appointments/AppointmentDetailModal'
import { useAuth } from '@/context/AuthContext'
import { useCatalog } from '@/context/CatalogContext'
import { useToast } from '@/components/ui/Toast'
import api, { getErrorMessage } from '@/lib/api'
import { totalBooked } from '@/lib/format'

/* ============================================================
   CONSTANTES Y HELPERS
   ============================================================ */

const MONTHS_ES = ['Enero','Febrero','Marzo','Abril','Mayo','Junio','Julio','Agosto','Septiembre','Octubre','Noviembre','Diciembre']
const DAYS_ES_SHORT = ['Lun','Mar','Mié','Jue','Vie','Sáb','Dom']
const VIEW_MODES = ['Mes','Semana','Día']

// Defaults — luego se ajustan dinámicamente desde business_hours
const DEFAULT_DAY_START = 8
const DEFAULT_DAY_END   = 21

// Densidad: px por hora
const HOUR_PX = { comfortable: 64, compact: 40 }

// Paleta determinista para "Color por" Empleado / Cabina
const PALETTES = [
  { bg: 'bg-cyan-50',     text: 'text-cyan-700',     bar: 'bg-cyan-500',     ring: 'ring-cyan-200',     hover: 'hover:bg-cyan-100',     dot: 'bg-cyan-500' },
  { bg: 'bg-amber-50',    text: 'text-amber-700',    bar: 'bg-amber-500',    ring: 'ring-amber-200',    hover: 'hover:bg-amber-100',    dot: 'bg-amber-500' },
  { bg: 'bg-emerald-50',  text: 'text-emerald-700',  bar: 'bg-emerald-500',  ring: 'ring-emerald-200',  hover: 'hover:bg-emerald-100',  dot: 'bg-emerald-500' },
  { bg: 'bg-indigo-50',   text: 'text-indigo-700',   bar: 'bg-indigo-500',   ring: 'ring-indigo-200',   hover: 'hover:bg-indigo-100',   dot: 'bg-indigo-500' },
  { bg: 'bg-pink-50',     text: 'text-pink-700',     bar: 'bg-pink-500',     ring: 'ring-pink-200',     hover: 'hover:bg-pink-100',     dot: 'bg-pink-500' },
  { bg: 'bg-sky-50',      text: 'text-sky-700',      bar: 'bg-sky-500',      ring: 'ring-sky-200',      hover: 'hover:bg-sky-100',      dot: 'bg-sky-500' },
  { bg: 'bg-violet-50',   text: 'text-violet-700',   bar: 'bg-violet-500',   ring: 'ring-violet-200',   hover: 'hover:bg-violet-100',   dot: 'bg-violet-500' },
  { bg: 'bg-teal-50',     text: 'text-teal-700',     bar: 'bg-teal-500',     ring: 'ring-teal-200',     hover: 'hover:bg-teal-100',     dot: 'bg-teal-500' },
]

// Por estado: 6 colores fijos (los de antes)
const STATUS_STYLES = {
  PENDING:     { bg:'bg-amber-50',   text:'text-amber-700',   bar:'bg-amber-500',   ring:'ring-amber-200',   hover:'hover:bg-amber-100',  dot:'bg-amber-500' },
  CONFIRMED:   { bg:'bg-blue-50',    text:'text-blue-700',    bar:'bg-blue-500',    ring:'ring-blue-200',    hover:'hover:bg-blue-100',   dot:'bg-blue-500' },
  IN_PROGRESS: { bg:'bg-cyan-50',    text:'text-cyan-700',    bar:'bg-cyan-500',    ring:'ring-cyan-200',    hover:'hover:bg-cyan-100',   dot:'bg-cyan-500' },
  COMPLETED:   { bg:'bg-emerald-50', text:'text-emerald-700', bar:'bg-emerald-500', ring:'ring-emerald-200', hover:'hover:bg-emerald-100',dot:'bg-emerald-500' },
  CANCELLED:   { bg:'bg-slate-100',  text:'text-slate-500',   bar:'bg-slate-400',   ring:'ring-slate-200',   hover:'hover:bg-slate-200',  dot:'bg-slate-400' },
  NO_SHOW:     { bg:'bg-rose-50',    text:'text-rose-600',    bar:'bg-rose-400',    ring:'ring-rose-200',    hover:'hover:bg-rose-100',   dot:'bg-rose-400' },
}
const GRAY = { bg:'bg-slate-100', text:'text-slate-500', bar:'bg-slate-400', ring:'ring-slate-200', hover:'hover:bg-slate-200', dot:'bg-slate-400' }

// Devuelve los estilos del evento según el modo "Color por"
const styleFor = (appt, colorBy) => {
  if (colorBy === 'status')   return STATUS_STYLES[appt.statusName] || STATUS_STYLES.PENDING
  if (colorBy === 'employee') return PALETTES[(appt.membershipId ?? 0) % PALETTES.length]
  if (colorBy === 'booth')    return appt.boothId ? PALETTES[(appt.boothId) % PALETTES.length] : GRAY
  return STATUS_STYLES[appt.statusName] || STATUS_STYLES.PENDING
}

// helpers fecha
const pad2 = (n) => String(n).padStart(2, '0')
const keyOf = (d) => `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`
const isSameDay = (a, b) =>
  a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate()
const startOfWeek = (d) => {
  const dow = (d.getDay() + 6) % 7
  const r = new Date(d); r.setDate(d.getDate() - dow); r.setHours(0, 0, 0, 0); return r
}
const buildMonthGrid = (year, month) => {
  const first = new Date(year, month, 1)
  const dow = (first.getDay() + 6) % 7
  const start = new Date(year, month, 1 - dow)
  return Array.from({ length: 42 }, (_, i) => { const d = new Date(start); d.setDate(start.getDate() + i); return d })
}
const rangeFor = (view, cursor) => {
  if (view === 'Mes') {
    const cells = buildMonthGrid(cursor.getFullYear(), cursor.getMonth())
    return { from: keyOf(cells[0]), to: keyOf(cells[41]) }
  }
  if (view === 'Semana') {
    const ws = startOfWeek(cursor); const we = new Date(ws); we.setDate(ws.getDate() + 6)
    return { from: keyOf(ws), to: keyOf(we) }
  }
  return { from: keyOf(cursor), to: keyOf(cursor) }
}

// helpers cita
const apptDate = (a) => a.startDateTime.slice(0, 10)
const apptHHMM = (iso) => iso.slice(11, 16)
const minutesOf = (iso) => { const [h, m] = iso.slice(11, 16).split(':'); return Number(h) * 60 + Number(m) }
const apptDuration = (a) => Math.max(15, minutesOf(a.endDateTime) - minutesOf(a.startDateTime))

const layoutEvents = (events) => {
  const items = events
    .map((a) => { const startMin = minutesOf(a.startDateTime); return { a, startMin, endMin: startMin + apptDuration(a) } })
    .sort((x, y) => x.startMin - y.startMin || x.endMin - y.endMin)
  const groups = []
  let current = [], currentEnd = -1
  items.forEach((it) => {
    if (it.startMin < currentEnd) { current.push(it); currentEnd = Math.max(currentEnd, it.endMin) }
    else { if (current.length) groups.push(current); current = [it]; currentEnd = it.endMin }
  })
  if (current.length) groups.push(current)
  const out = []
  groups.forEach((group) => {
    const cols = []
    group.forEach((it) => {
      let placed = -1
      for (let i = 0; i < cols.length; i++) if (cols[i] <= it.startMin) { cols[i] = it.endMin; placed = i; break }
      if (placed === -1) { cols.push(it.endMin); placed = cols.length - 1 }
      it.col = placed
    })
    group.forEach((it) => { it.cols = cols.length; out.push(it) })
  })
  return out
}

/* ============================================================
   CALENDARIO
   ============================================================ */

export default function Calendario() {
  const { user } = useAuth()
  const { statusLabel } = useCatalog()
  const toast = useToast()
  const bId = user?.businessId

  /* ---- Preferencias persistidas ---- */
  const [view, setView] = useState(() => localStorage.getItem('optima_cal_view') || 'Mes')
  useEffect(() => { localStorage.setItem('optima_cal_view', view) }, [view])

  const [density, setDensity] = useState(() => localStorage.getItem('optima_cal_density') || 'comfortable')
  useEffect(() => { localStorage.setItem('optima_cal_density', density) }, [density])
  const hourPx = HOUR_PX[density]

  const [colorBy, setColorBy] = useState(() => localStorage.getItem('optima_cal_colorby') || 'status')
  useEffect(() => { localStorage.setItem('optima_cal_colorby', colorBy) }, [colorBy])

  const [employeeFilter, setEmployeeFilter] = useState(() => localStorage.getItem('optima_cal_emp') || '')
  useEffect(() => { localStorage.setItem('optima_cal_emp', employeeFilter) }, [employeeFilter])

  const [boothFilter, setBoothFilter] = useState(() => localStorage.getItem('optima_cal_booth') || '')
  useEffect(() => { localStorage.setItem('optima_cal_booth', boothFilter) }, [boothFilter])

  const [statusFilter, setStatusFilter] = useState(() => localStorage.getItem('optima_cal_status') || '')
  useEffect(() => { localStorage.setItem('optima_cal_status', statusFilter) }, [statusFilter])

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

  useEffect(() => {
    if (!bId) return
    Promise.allSettled([
      api.get(`/api/businesses/${bId}/users?size=100`),
      api.get(`/api/businesses/${bId}/booths?size=100`),
      api.get(`/api/businesses/${bId}/hours`),
    ]).then(([emp, bo, hrs]) => {
      if (emp.status === 'fulfilled') setEmployees(emp.value.data.content ?? [])
      if (bo.status  === 'fulfilled') setBooths(bo.value.data.content ?? [])
      if (hrs.status === 'fulfilled') {
        const data = Array.isArray(hrs.value.data) ? hrs.value.data : (hrs.value.data?.content ?? [])
        setBusinessHours(data)
      }
    })
  }, [bId])

  // Rango horario dinámico desde business_hours (max apertura entre todos los días abiertos).
  // Si no hay horario configurado, defaults 8-21.
  const { dayStart, dayEnd } = useMemo(() => {
    if (!businessHours.length) return { dayStart: DEFAULT_DAY_START, dayEnd: DEFAULT_DAY_END }
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
    if (minStart === 24 || maxEnd === 0) return { dayStart: DEFAULT_DAY_START, dayEnd: DEFAULT_DAY_END }
    return { dayStart: Math.floor(minStart), dayEnd: Math.ceil(maxEnd) }
  }, [businessHours])

  // Carga de citas del rango visible
  useEffect(() => {
    if (!bId) return
    let cancelled = false
    const { from, to } = rangeFor(view, cursor)
    setLoading(true)
    const params = { from, to, size: 200, sort: 'startDateTime,asc' }
    if (employeeFilter) params.membershipId = employeeFilter
    api.get(`/api/businesses/${bId}/appointments`, { params })
      .then((r) => { if (!cancelled) setAppointments(r.data.content ?? []) })
      .catch((err) => { if (!cancelled) toast({ type: 'error', message: getErrorMessage(err, 'No se pudieron cargar las citas.') }) })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [bId, view, cursor, employeeFilter, reloadFlag, toast])

  const refetch = useCallback(() => setReloadFlag((v) => v + 1), [])

  // Filtros client-side (estado + cabina)
  const filtered = useMemo(() => appointments.filter((a) => {
    if (statusFilter && a.statusName !== statusFilter) return false
    if (boothFilter && String(a.boothId ?? '') !== boothFilter) return false
    return true
  }), [appointments, statusFilter, boothFilter])

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

  // Stats del rango activo
  const rangeStats = useMemo(() => {
    const considered = filtered.filter((a) => a.statusName !== 'CANCELLED' && a.statusName !== 'NO_SHOW')
    const revenue = considered.reduce((acc, a) => acc + parseFloat(totalBooked(a.bookedServices)), 0)
    const minutesBusy = considered.reduce((acc, a) => acc + apptDuration(a), 0)
    // ocupación: minutos ocupados / minutos abiertos en el rango
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

  /* ---- Modales ---- */
  const [wizard, setWizard] = useState({ open: false, date: null, time: null })
  const [detailAppt, setDetailAppt] = useState(null)
  const openWizard = useCallback((date = null, time = null) => setWizard({ open: true, date, time }), [])
  const closeWizard = useCallback(() => setWizard({ open: false, date: null, time: null }), [])

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

  return (
    <div className="p-8 max-w-7xl mx-auto print:p-0">

      {/* Estilos de impresión */}
      <style>{`
        @media print {
          aside.sidebar, .no-print { display: none !important; }
          body { background: white !important; }
          .print-area { box-shadow: none !important; border: none !important; }
        }
      `}</style>

      <div className="mb-6 flex items-start justify-between gap-4 no-print">
        <div>
          <h1 className="text-3xl font-bold text-[#1e3a5f] tracking-tight">Calendario</h1>
          <p className="text-sm text-slate-500 mt-1.5 font-medium">
            Pulsa una cita para ver el detalle, o un hueco libre para crear una nueva.
          </p>
        </div>
        <div className="flex items-center gap-2">
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

        {/* Toolbar fila 2: filtros y opciones (oculta al imprimir) */}
        <div className="flex flex-wrap items-center gap-3 px-5 py-3 border-b border-slate-100 bg-slate-50/40 no-print">
          <select
            value={employeeFilter}
            onChange={(e) => setEmployeeFilter(e.target.value)}
            className="h-9 rounded-xl border border-slate-200 bg-white px-3 text-xs font-semibold text-[#1e3a5f] focus:outline-none focus:border-blue-400 focus:ring-4 focus:ring-blue-100 transition"
          >
            <option value="">Todos los empleados</option>
            {employees.map((e) => (<option key={e.id} value={e.id}>{e.fullName}</option>))}
          </select>
          <select
            value={boothFilter}
            onChange={(e) => setBoothFilter(e.target.value)}
            className="h-9 rounded-xl border border-slate-200 bg-white px-3 text-xs font-semibold text-[#1e3a5f] focus:outline-none focus:border-blue-400 focus:ring-4 focus:ring-blue-100 transition"
          >
            <option value="">Todas las cabinas</option>
            {booths.map((b) => (<option key={b.id} value={b.id}>{b.name}</option>))}
          </select>

          <div className="flex items-center gap-1.5">
            <span className="text-[10px] font-bold text-slate-400 uppercase tracking-[0.16em] mr-0.5">Estado</span>
            {[
              { key: '',            label: 'Todos' },
              { key: 'PENDING',     label: 'Pend.',   dot: 'bg-amber-500' },
              { key: 'CONFIRMED',   label: 'Conf.',   dot: 'bg-blue-500' },
              { key: 'IN_PROGRESS', label: 'Curso',   dot: 'bg-cyan-500' },
              { key: 'COMPLETED',   label: 'Compl.',  dot: 'bg-emerald-500' },
              { key: 'CANCELLED',   label: 'Canc.',   dot: 'bg-slate-400' },
              { key: 'NO_SHOW',     label: 'No show', dot: 'bg-rose-500' },
            ].map(({ key, label, dot }) => (
              <button
                key={key || 'ALL'}
                onClick={() => setStatusFilter(key)}
                className={`text-[11px] font-semibold px-2.5 py-1 rounded-full transition inline-flex items-center gap-1 ${
                  statusFilter === key
                    ? 'bg-[#1e3a5f] text-white'
                    : 'bg-white border border-slate-200 text-slate-600 hover:border-blue-300'
                }`}
              >
                {dot && <span className={`w-1.5 h-1.5 rounded-full ${dot}`} />}
                {label}
              </button>
            ))}
          </div>

          <div className="ml-auto flex items-center gap-2 flex-wrap">
            <span className="text-[10px] text-slate-400 uppercase tracking-wider font-semibold inline-flex items-center gap-1"><Palette size={12} /> Color por</span>
            <select
              value={colorBy}
              onChange={(e) => setColorBy(e.target.value)}
              className="h-9 rounded-xl border border-slate-200 bg-white px-3 text-xs font-semibold text-[#1e3a5f] focus:outline-none focus:border-blue-400 focus:ring-4 focus:ring-blue-100 transition"
            >
              <option value="status">Estado</option>
              <option value="employee">Empleado</option>
              <option value="booth">Cabina</option>
            </select>
            <div className="inline-flex items-center bg-slate-100 rounded-lg p-1">
              <button
                onClick={() => setDensity('comfortable')}
                className={`px-2 py-1 rounded-md text-[11px] font-semibold transition ${density === 'comfortable' ? 'bg-white text-[#1e3a5f] shadow-[0_1px_4px_rgba(15,23,42,0.08)]' : 'text-slate-500 hover:text-[#1e3a5f]'}`}
              >Cómodo</button>
              <button
                onClick={() => setDensity('compact')}
                className={`px-2 py-1 rounded-md text-[11px] font-semibold transition ${density === 'compact' ? 'bg-white text-[#1e3a5f] shadow-[0_1px_4px_rgba(15,23,42,0.08)]' : 'text-slate-500 hover:text-[#1e3a5f]'}`}
              >Compacto</button>
            </div>
          </div>
        </div>

        {loading ? (
          <div className="p-16 text-center text-slate-400 text-sm">Cargando agenda…</div>
        ) : view === 'Mes' ? (
          <MonthGrid
            cursor={cursor} today={today} eventsByDay={eventsByDay} colorBy={colorBy}
            onCellClick={onCellClick} onSelectEvent={setDetailAppt} onOpenDay={onOpenDay}
            now={now}
          />
        ) : view === 'Semana' ? (
          <WeekGrid
            cursor={cursor} today={today} eventsByDay={eventsByDay} colorBy={colorBy}
            onSelectEvent={setDetailAppt} onSlotClick={onSlotClick}
            dayStart={dayStart} dayEnd={dayEnd} hourPx={hourPx}
            businessHours={businessHours} now={now}
          />
        ) : (
          <DayGrid
            cursor={cursor} eventsByDay={eventsByDay} colorBy={colorBy}
            onSelectEvent={setDetailAppt} onSlotClick={onSlotClick}
            statusLabel={statusLabel} dayStart={dayStart} dayEnd={dayEnd} hourPx={hourPx}
            businessHours={businessHours} now={now}
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

/* ============================================================
   SUBCOMPONENTES
   ============================================================ */

function StatTile({ label, value, tone }) {
  const tones = {
    default: 'text-[#1e3a5f]',
    success: 'text-emerald-600',
    cyan:    'text-cyan-600',
  }
  return (
    <div className="bg-white rounded-2xl border border-slate-100 p-4">
      <div className="text-[10px] font-semibold text-slate-400 uppercase tracking-wider">{label}</div>
      <div className={`text-2xl font-extrabold mt-1 tabular-nums ${tones[tone] || tones.default}`}>{value}</div>
    </div>
  )
}

function EventChip({ appt, onClick, colorBy }) {
  const s = styleFor(appt, colorBy)
  const isInProgress = appt.statusName === 'IN_PROGRESS'
  const tooltip = `${appt.clientName} · ${apptHHMM(appt.startDateTime)}–${apptHHMM(appt.endDateTime)} · ${appt.userFullName}${appt.boothName ? ' · ' + appt.boothName : ''}`
  return (
    <button
      onClick={(e) => { e.stopPropagation(); onClick(appt) }}
      title={tooltip}
      className={`w-full flex items-center gap-1.5 ${s.bg} ${s.hover} ${s.text} text-[11px] font-medium rounded-md px-1.5 py-1 text-left transition ${isInProgress ? 'ring-1 ring-cyan-300 animate-pulse' : ''}`}
    >
      <span className={`w-0.5 self-stretch ${s.bar} rounded-full shrink-0`} />
      <span className="truncate flex-1">{appt.clientName}</span>
      <span className="text-[10px] opacity-70 hidden xl:inline">{apptHHMM(appt.startDateTime)}</span>
    </button>
  )
}

function PositionedEvent({ appt, onClick, col, cols, colorBy, dayStart, hourPx }) {
  const topPx = ((minutesOf(appt.startDateTime) - dayStart * 60) / 60) * hourPx
  const heightPx = Math.max(22, (apptDuration(appt) / 60) * hourPx - 4)
  const widthPct = 100 / cols
  const s = styleFor(appt, colorBy)
  const isInProgress = appt.statusName === 'IN_PROGRESS'
  const tooltip = `${appt.clientName} · ${apptHHMM(appt.startDateTime)}–${apptHHMM(appt.endDateTime)} · ${appt.userFullName}${appt.boothName ? ' · ' + appt.boothName : ''}`
  return (
    <button
      onClick={(e) => { e.stopPropagation(); onClick(appt) }}
      title={tooltip}
      style={{
        top: `${topPx}px`, height: `${heightPx}px`,
        left: `calc(${col * widthPct}% + 2px)`, width: `calc(${widthPct}% - 4px)`,
      }}
      className={`absolute ${s.bg} ${s.hover} ${s.text} ring-1 ${s.ring} rounded-lg pl-2.5 pr-2 py-1 text-left overflow-hidden transition shadow-[0_2px_8px_-4px_rgba(15,23,42,0.15)] ${isInProgress ? 'animate-pulse' : ''}`}
    >
      <span className={`absolute left-0 top-0 bottom-0 w-1 ${s.bar} rounded-l-lg`} />
      <div className="text-[11px] font-semibold truncate leading-tight">{appt.clientName}</div>
      {heightPx > 40 && (<div className="text-[10px] opacity-75 truncate leading-tight">{appt.userFullName}</div>)}
      <div className="text-[10px] opacity-70 leading-tight">
        {apptHHMM(appt.startDateTime)} – {apptHHMM(appt.endDateTime)}
      </div>
    </button>
  )
}

function HourColumn({ withHeader = true, dayStart, dayEnd, hourPx }) {
  const hours = []
  for (let h = dayStart; h < dayEnd; h++) hours.push(h)
  return (
    <div className="w-14 shrink-0 border-r border-slate-100 bg-white">
      {withHeader && <div className="h-10 border-b border-slate-100" />}
      {hours.map((h) => (
        <div key={h} style={{ height: hourPx }} className="relative">
          <span className="absolute -top-2 right-1.5 text-[10px] font-semibold text-slate-400 bg-white px-1">
            {pad2(h)}:00
          </span>
        </div>
      ))}
    </div>
  )
}

function HourSlots({ dayKey, onSlotClick, dayStart, dayEnd, hourPx, closedRanges }) {
  const hours = []
  for (let h = dayStart; h < dayEnd; h++) hours.push(h)
  const isClosed = (h) => closedRanges.some(([s, e]) => h < s || h >= e)
  return (
    <>
      {hours.map((h) => {
        const closed = isClosed(h)
        return (
          <div
            key={h}
            onClick={() => !closed && onSlotClick(dayKey, h)}
            style={{ height: hourPx }}
            className={`border-b border-slate-100 relative ${closed ? 'bg-slate-50/80 cursor-not-allowed' : 'hover:bg-blue-50/40 cursor-pointer'}`}
            title={closed ? 'Fuera de horario' : 'Crear cita a esta hora'}
          >
            <div className="absolute left-0 right-0 border-t border-dashed border-slate-100" style={{ top: hourPx / 2 }} />
          </div>
        )
      })}
    </>
  )
}

// Devuelve los rangos abiertos del día (puede haber un solo rango por now)
const openRangesFor = (businessHours, date) => {
  const dow = date.getDay() === 0 ? 7 : date.getDay()
  const h = businessHours.find((x) => x.dayOfWeek === dow)
  if (!h || h.isClosed || !h.startTime || !h.endTime) return []
  const [sh, sm] = h.startTime.slice(0, 5).split(':').map(Number)
  const [eh, em] = h.endTime.slice(0, 5).split(':').map(Number)
  return [[sh + sm / 60, eh + em / 60]]
}

function NowLine({ now, dayStart, dayEnd, hourPx }) {
  const minutes = now.getHours() * 60 + now.getMinutes() - dayStart * 60
  const total = (dayEnd - dayStart) * 60
  if (minutes < 0 || minutes > total) return null
  const top = (minutes / 60) * hourPx
  return (
    <div className="absolute left-0 right-0 z-20 pointer-events-none" style={{ top }}>
      <div className="absolute -left-1 -top-1.5 w-3 h-3 rounded-full bg-rose-500 shadow" />
      <div className="h-0.5 bg-rose-500" />
    </div>
  )
}

function MonthGrid({ cursor, today, eventsByDay, colorBy, onCellClick, onSelectEvent, onOpenDay }) {
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
          const dayRevenue = dayEvents
            .filter((a) => a.statusName !== 'CANCELLED' && a.statusName !== 'NO_SHOW')
            .reduce((acc, a) => acc + parseFloat(totalBooked(a.bookedServices)), 0)
          return (
            <div
              key={idx}
              onClick={() => onCellClick(keyOf(date))}
              className={`group relative min-h-[120px] p-2 cursor-pointer transition ${inMonth ? 'bg-white hover:bg-blue-50/40' : 'bg-slate-50/60 hover:bg-slate-50'}`}
              title="Crear cita este día"
            >
              <div className="flex items-center justify-between">
                <div className={`inline-flex items-center justify-center min-w-[24px] h-6 px-1.5 rounded-full text-xs font-semibold ${
                  isToday
                    ? 'bg-gradient-to-br from-cyan-500 to-blue-500 text-white shadow-[0_4px_10px_-2px_rgba(14,165,233,0.55)]'
                    : inMonth ? 'text-[#1e3a5f]' : 'text-slate-400'
                }`}>{date.getDate()}</div>
                <Plus size={13} className="opacity-0 group-hover:opacity-60 text-blue-500" />
              </div>
              <div className="mt-1.5 space-y-1">
                {shown.map((a) => <EventChip key={a.id} appt={a} onClick={onSelectEvent} colorBy={colorBy} />)}
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

function WeekGrid({ cursor, today, eventsByDay, colorBy, onSelectEvent, onSlotClick, dayStart, dayEnd, hourPx, businessHours, now }) {
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
            return (
              <div key={i} className="relative border-r border-slate-100 last:border-r-0">
                <div className={`h-10 border-b border-slate-100 flex items-center justify-center gap-2 sticky top-0 z-10 ${isToday ? 'bg-blue-50/80' : 'bg-white'}`}>
                  <span className={`text-[10px] uppercase tracking-wider font-semibold ${i >= 5 ? 'text-blue-600' : 'text-slate-500'}`}>{DAYS_ES_SHORT[i]}</span>
                  <span className={`inline-flex items-center justify-center min-w-[22px] h-6 px-1.5 rounded-full text-xs font-bold ${isToday ? 'bg-gradient-to-br from-cyan-500 to-blue-500 text-white' : 'text-[#1e3a5f]'}`}>{d.getDate()}</span>
                </div>
                <div className="relative">
                  <HourSlots dayKey={dayKey} onSlotClick={onSlotClick} dayStart={dayStart} dayEnd={dayEnd} hourPx={hourPx} closedRanges={openRanges} />
                  {laidOut.map(({ a, col, cols }) => (
                    <PositionedEvent key={a.id} appt={a} onClick={onSelectEvent} col={col} cols={cols} colorBy={colorBy} dayStart={dayStart} hourPx={hourPx} />
                  ))}
                  {isToday && <NowLine now={now} dayStart={dayStart} dayEnd={dayEnd} hourPx={hourPx} />}
                </div>
              </div>
            )
          })}
        </div>
      </div>
    </div>
  )
}

function DayGrid({ cursor, eventsByDay, colorBy, onSelectEvent, onSlotClick, statusLabel, dayStart, dayEnd, hourPx, businessHours, now }) {
  const dayKey = keyOf(cursor)
  const dayEvents = eventsByDay.get(dayKey) || []
  const sorted = [...dayEvents].sort((a, b) => minutesOf(a.startDateTime) - minutesOf(b.startDateTime))
  const laidOut = layoutEvents(dayEvents)
  const openRanges = openRangesFor(businessHours, cursor)
  const isToday = isSameDay(cursor, new Date())

  return (
    <div className="grid grid-cols-1 lg:grid-cols-[1fr_300px] gap-5 p-5">
      <div className="border border-slate-100 rounded-2xl overflow-hidden">
        <div className="overflow-auto" style={{ maxHeight: '64vh' }}>
          <div className="flex">
            <HourColumn withHeader={false} dayStart={dayStart} dayEnd={dayEnd} hourPx={hourPx} />
            <div className="flex-1 relative">
              <HourSlots dayKey={dayKey} onSlotClick={onSlotClick} dayStart={dayStart} dayEnd={dayEnd} hourPx={hourPx} closedRanges={openRanges} />
              {laidOut.map(({ a, col, cols }) => (
                <PositionedEvent key={a.id} appt={a} onClick={onSelectEvent} col={col} cols={cols} colorBy={colorBy} dayStart={dayStart} hourPx={hourPx} />
              ))}
              {isToday && <NowLine now={now} dayStart={dayStart} dayEnd={dayEnd} hourPx={hourPx} />}
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

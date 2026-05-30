// Listado paginado de citas con filtros por fecha, estado, empleado y wizard de creacion
import { useEffect, useMemo, useState } from 'react'
import {
  Plus, Search, CalendarDays, Clock, User, Scissors, MapPin, FilterX,
  AlertCircle, Zap, ArrowRight, RefreshCw, List, Calendar,
} from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { Select } from '@/components/ui/Select'
import { StatusBadge, Badge } from '@/components/ui/Badge'
import { Pagination } from '@/components/ui/Pagination'
import { EmptyState } from '@/components/ui/EmptyState'
import { AppointmentWizard } from '@/components/appointments/AppointmentWizard'
import { AppointmentDetailModal } from '@/components/appointments/AppointmentDetailModal'
import { useAuth } from '@/context/AuthContext'
import { useCatalog } from '@/context/CatalogContext'
import { useToast } from '@/components/ui/Toast'
import { usePagedFetch } from '@/hooks/usePagedFetch'
import api, { getErrorMessage } from '@/lib/api'
import { formatDateTime, totalBooked } from '@/lib/format'
import { dotClassFromColorAndId } from '@/lib/employeeColor'


// Degradado del bloque de fecha de cada tarjeta, segun el estado
const TINTS = {
  PENDING:     'from-amber-400 to-orange-500',
  CONFIRMED:   'from-blue-500 to-indigo-500',
  IN_PROGRESS: 'from-cyan-400 to-blue-500',
  COMPLETED:   'from-emerald-400 to-teal-500',
  CANCELLED:   'from-slate-400 to-slate-500',
  NO_SHOW:     'from-slate-400 to-slate-500',
}

const STATUS_DOT = {
  PENDING:     'bg-orange-500',
  CONFIRMED:   'bg-blue-500',
  IN_PROGRESS: 'bg-sky-500',
  COMPLETED:   'bg-emerald-500',
  CANCELLED:   'bg-slate-400',
  NO_SHOW:     'bg-rose-500',
}

const AVATAR_COLORS = [
  'from-cyan-400 to-blue-500',
  'from-amber-400 to-orange-500',
  'from-emerald-400 to-teal-500',
  'from-indigo-400 to-violet-500',
  'from-pink-400 to-rose-500',
  'from-sky-400 to-blue-500',
]
const avatarColor = (id) => AVATAR_COLORS[(id ?? 0) % AVATAR_COLORS.length]
const initials = (name) =>
  name?.trim().split(/\s+/).slice(0, 2).map((s) => s[0]?.toUpperCase()).join('') || '?'

const ymd = (d) => {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const da = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${da}`
}

// Devuelve {from, to} para un preset
const datePreset = (key) => {
  const today = new Date()
  const mondayOf = (d) => {
    const x = new Date(d)
    const wd = x.getDay() === 0 ? 7 : x.getDay()
    x.setDate(x.getDate() - (wd - 1))
    return x
  }
  switch (key) {
    case 'today':     return { from: ymd(today), to: ymd(today) }
    case 'tomorrow': {
      const t = new Date(today); t.setDate(t.getDate() + 1)
      return { from: ymd(t), to: ymd(t) }
    }
    case 'week': {
      const mon = mondayOf(today)
      const sun = new Date(mon); sun.setDate(mon.getDate() + 6)
      return { from: ymd(mon), to: ymd(sun) }
    }
    case 'next7': {
      const end = new Date(today); end.setDate(today.getDate() + 6)
      return { from: ymd(today), to: ymd(end) }
    }
    case 'month': {
      const first = new Date(today.getFullYear(), today.getMonth(), 1)
      const last  = new Date(today.getFullYear(), today.getMonth() + 1, 0)
      return { from: ymd(first), to: ymd(last) }
    }
    case 'lastMonth': {
      const first = new Date(today.getFullYear(), today.getMonth() - 1, 1)
      const last  = new Date(today.getFullYear(), today.getMonth(), 0)
      return { from: ymd(first), to: ymd(last) }
    }
    default: return { from: '', to: '' }
  }
}

const minutesBetween = (a, b) => (new Date(b) - new Date(a)) / 60000
const formatDuration = (a, b) => {
  const m = minutesBetween(a, b)
  const h = Math.floor(m / 60)
  const r = m % 60
  if (h <= 0) return `${m} min`
  return r ? `${h} h ${r} min` : `${h} h`
}
const fmtMinsLeft = (m) => {
  if (m <= 0) return 'ahora mismo'
  if (m < 60) return `${m} min`
  const h = Math.floor(m / 60)
  const r = m % 60
  return r ? `${h} h ${r} min` : `${h} h`
}

/** Listado de citas con filtros por fecha, empleado y estado, y wizard para crear o editar */
export default function Citas() {
  const { user } = useAuth()
  const { statuses, statusLabel } = useCatalog()
  const toast = useToast()
  const bId = user?.businessId

  // Tick cada minuto para countdown del banner "EN CURSO"
  const [now, setNow] = useState(new Date())
  useEffect(() => {
    const t = setInterval(() => setNow(new Date()), 60_000)
    return () => clearInterval(t)
  }, [])

  const [pageSize, setPageSize] = useState(() => parseInt(localStorage.getItem('optima_citas_size') || '20', 10))
  useEffect(() => { localStorage.setItem('optima_citas_size', String(pageSize)) }, [pageSize])

  // D2: selector temporal "Proximas" (futuras asc) / "Pasadas" (pasadas desc)
  // Sustituye al "asc/desc" ambiguo anterior La key cambia para no heredar
  // valores antiguos del almacen local
  const [timeMode, setTimeMode] = useState(() => localStorage.getItem('optima_citas_timemode') || 'upcoming')
  useEffect(() => { localStorage.setItem('optima_citas_timemode', timeMode) }, [timeMode])

  const [view, setView] = useState(() => localStorage.getItem('optima_citas_view') || 'list')
  useEffect(() => { localStorage.setItem('optima_citas_view', view) }, [view])

  // Filtros en servidor persistidos (rango y empleado)
  const [fromDate, setFromDate]         = useState(() => localStorage.getItem('optima_citas_from') || '')
  const [toDate, setToDate]             = useState(() => localStorage.getItem('optima_citas_to') || '')
  const [employeeFilter, setEmployeeFilter] = useState(() => localStorage.getItem('optima_citas_emp') || '')
  useEffect(() => { localStorage.setItem('optima_citas_from', fromDate) }, [fromDate])
  useEffect(() => { localStorage.setItem('optima_citas_to',   toDate) },   [toDate])
  useEffect(() => { localStorage.setItem('optima_citas_emp',  employeeFilter) }, [employeeFilter])

  // Solo se envian claves con valor
  // sort: la auditoria I010 dice que solo `startDateTime` es seguro como sort
  // El timeMode "upcoming" -> asc, "past" -> desc
  const listParams = useMemo(() => {
    const p = { sort: `startDateTime,${timeMode === 'past' ? 'desc' : 'asc'}` }
    if (fromDate) p.from = fromDate
    if (toDate)   p.to = toDate
    if (employeeFilter) p.membershipId = employeeFilter
    return p
  }, [fromDate, toDate, employeeFilter, timeMode])

  // Se cargan TODAS las citas del rango (size=100, tope del servidor) para que
  // la busqueda y la paginacion operen sobre el conjunto completo
  const {
    items: appointments, totalElements, loading, refresh,
  } = usePagedFetch(bId ? `/api/businesses/${bId}/appointments` : null, { size: 100, params: listParams })

  // Empleados para el desplegable
  const [employees, setEmployees] = useState([])
  useEffect(() => {
    if (!bId) return
    api.get(`/api/businesses/${bId}/users?size=100`)
      .then((r) => setEmployees(r.data.content))
      .catch((err) => toast({ type: 'error', message: getErrorMessage(err, 'No se pudieron cargar los empleados.') }))
  }, [bId, toast])

  // Map membershipId -> color asignado al empleado Sirve para pintar el
  // punto de identidad visual del empleado en cada cita (tarjeta y detalle)
  // de forma coherente con el calendario F del audit de interfaz
  const empColorMap = useMemo(
    () => new Map(employees.map((e) => [e.id, e.color])),
    [employees],
  )

  const [search, setSearch] = useState('')
  const [filterStatus, setFilterStatus] = useState('')
  const [unpaidOnly, setUnpaidOnly] = useState(false)

  // Frontera temporal: las citas de HOY se consideran "Proximas" todo el dia
  // (no se mueven de modo durante la jornada) Comparar contra startOfToday
  // en lugar de `now` evita reordenes y movimientos inesperados
  const startOfToday = useMemo(() => {
    const d = new Date(); d.setHours(0, 0, 0, 0); return d.getTime()
  }, [now])

  const visible = appointments.filter((a) => {
    const startMs = new Date(a.startDateTime).getTime()
    if (timeMode === 'upcoming' && startMs < startOfToday) return false
    if (timeMode === 'past' && startMs >= startOfToday) return false
    const matchSearch = !search ||
      a.clientName?.toLowerCase().includes(search.toLowerCase()) ||
      a.userFullName?.toLowerCase().includes(search.toLowerCase())
    const matchStatus = !filterStatus || a.statusName === filterStatus
    const matchPaid   = !unpaidOnly || !a.isPaid
    return matchSearch && matchStatus && matchPaid
  })

  const [page, setPage] = useState(0)
  const totalPages = Math.max(1, Math.ceil(visible.length / pageSize))
  const safePage = Math.min(page, totalPages - 1)
  const paged = visible.slice(safePage * pageSize, safePage * pageSize + pageSize)

  const inProgressNow = useMemo(
    () =>
      appointments.find(
        (a) => a.statusName === 'IN_PROGRESS' &&
               new Date(a.startDateTime) <= now &&
               now <= new Date(a.endDateTime),
      ),
    [appointments, now],
  )
  const inProgressMinsLeft = inProgressNow
    ? Math.max(0, Math.round((new Date(inProgressNow.endDateTime) - now) / 60000))
    : null

  const pageStats = useMemo(() => {
    const considered = visible.filter((a) => a.statusName !== 'CANCELLED' && a.statusName !== 'NO_SHOW')
    const total = considered.reduce((acc, a) => acc + parseFloat(totalBooked(a.bookedServices)), 0)
    const paid  = considered.filter((a) => a.isPaid)
                            .reduce((acc, a) => acc + parseFloat(totalBooked(a.bookedServices)), 0)
    const unpaidCount = considered.filter((a) => !a.isPaid).length
    const lost = visible.filter((a) => a.statusName === 'CANCELLED' || a.statusName === 'NO_SHOW').length
    return { count: visible.length, total, paid, unpaidCount, lost }
  }, [visible])

  const statusTabs = [
    { key: '', label: 'Todos' },
    ...statuses.map((s) => ({ key: s.name, label: statusLabel(s.name) })),
  ]

  const presetsConfig = [
    { key: 'today',     label: 'Hoy' },
    { key: 'tomorrow',  label: 'Mañana' },
    { key: 'week',      label: 'Esta semana' },
    { key: 'next7',     label: 'Próximos 7 días' },
    { key: 'month',     label: 'Este mes' },
    { key: 'lastMonth', label: 'Mes pasado' },
  ]
  const applyPreset = (key) => {
    const { from, to } = datePreset(key)
    setFromDate(from); setToDate(to)
  }

  const clearAllFilters = () => {
    setFromDate(''); setToDate(''); setEmployeeFilter('')
    setSearch(''); setFilterStatus(''); setUnpaidOnly(false)
  }
  const hasAnyFilter = Boolean(
    fromDate || toDate || employeeFilter || search || filterStatus || unpaidOnly,
  )
  const hasServerFilters = Boolean(fromDate || toDate || employeeFilter)

  const [wizardOpen, setWizardOpen] = useState(false)
  const [editingAppt, setEditingAppt] = useState(null)
  const [detailAppt, setDetailAppt] = useState(null)
  // startEditing apaga explicitamente cualquier otro estado que pudiera
  // mantener el wizard abierto en modo crear; asi no hay solape de modos
  const startEditing = (appt) => {
    setDetailAppt(null)
    setWizardOpen(false)
    setEditingAppt(appt)
  }
  const closeWizard = () => { setWizardOpen(false); setEditingAppt(null) }


  return (
    <div className="px-4 sm:px-6 lg:px-8 xl:px-10 py-8">

      <div className="mb-7 flex items-start justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold text-[#1e3a5f] tracking-tight">Citas</h1>
          <p className="text-sm text-slate-500 mt-1.5 font-medium flex items-center gap-1.5">
            <CalendarDays size={14} />
            {loading ? '…' : `${totalElements} cita${totalElements === 1 ? '' : 's'} registrada${totalElements === 1 ? '' : 's'}`}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={refresh}
            title="Refrescar"
            className="inline-flex items-center justify-center w-11 h-11 rounded-xl border border-slate-200 bg-white text-slate-500 hover:bg-slate-50 hover:text-[#1e3a5f] transition"
          >
            <RefreshCw size={16} className={loading ? 'animate-spin' : ''} />
          </button>
          <Button onClick={() => setWizardOpen(true)} className="gap-2">
            <Plus size={16} /> Nueva cita
          </Button>
        </div>
      </div>

      <div className="mb-5 grid grid-cols-2 md:grid-cols-5 gap-3">
        <StatTile label="En esta página" value={pageStats.count} tone="default" />
        <StatTile label="€ previstos"    value={`${pageStats.total.toLocaleString('es-ES', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} €`} tone="default" />
        <StatTile label="€ cobrados"     value={`${pageStats.paid.toLocaleString('es-ES', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} €`}  tone="success" />
        <StatTile label="Sin cobrar"     value={pageStats.unpaidCount} tone="warning" />
        <StatTile label="Canceladas / no show" value={pageStats.lost} tone="danger" />
      </div>

      <div className="mb-3 flex flex-wrap items-center gap-3">
        <div className="relative flex-1 min-w-[220px] max-w-sm">
          <Search size={15} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Buscar cliente o empleado…"
            className="h-11 w-full rounded-xl border border-slate-200 bg-white pl-10 pr-3.5 text-sm text-[#1f2c4a] placeholder:text-slate-400 focus:outline-none focus:border-blue-400 focus:ring-4 focus:ring-blue-100 transition-all"
          />
        </div>

        <select
          value={timeMode}
          onChange={(e) => setTimeMode(e.target.value)}
          className="h-11 rounded-xl border border-slate-200 bg-white px-3 text-sm font-semibold text-[#1e3a5f] focus:outline-none focus:border-blue-400 focus:ring-4 focus:ring-blue-100 transition"
        >
          <option value="upcoming">Próximas</option>
          <option value="past">Pasadas</option>
        </select>

        <div className="inline-flex items-center bg-slate-100 rounded-xl p-1">
          <button
            type="button"
            onClick={() => setView('list')}
            className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold transition ${
              view === 'list'
                ? 'bg-white text-[#1e3a5f] shadow-[0_1px_4px_rgba(15,23,42,0.08)]'
                : 'text-slate-500 hover:text-[#1e3a5f]'
            }`}
          >
            <List size={14} /> Lista
          </button>
          <button
            type="button"
            onClick={() => setView('day')}
            className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold transition ${
              view === 'day'
                ? 'bg-white text-[#1e3a5f] shadow-[0_1px_4px_rgba(15,23,42,0.08)]'
                : 'text-slate-500 hover:text-[#1e3a5f]'
            }`}
          >
            <Calendar size={14} /> Por día
          </button>
        </div>

        <div className="ml-auto flex items-center gap-2 text-xs text-slate-500">
          <span>Mostrar</span>
          <select
            value={pageSize}
            onChange={(e) => { setPageSize(parseInt(e.target.value, 10)); setPage(0) }}
            className="h-11 rounded-xl border border-slate-200 bg-white px-3 text-sm font-semibold text-[#1e3a5f] focus:outline-none focus:border-blue-400 focus:ring-4 focus:ring-blue-100 transition"
          >
            <option value={10}>10</option>
            <option value={20}>20</option>
            <option value={50}>50</option>
          </select>
        </div>
      </div>

      <div className="mb-3 flex flex-wrap items-center gap-2">
        <span className="text-[10px] font-bold text-slate-400 uppercase tracking-[0.16em] mr-1">Estado</span>
        {statusTabs.map(({ key, label }) => (
          <button
            key={key || 'ALL'}
            onClick={() => setFilterStatus(key)}
            className={`text-xs font-semibold px-3 py-1.5 rounded-full transition inline-flex items-center gap-1.5 ${
              filterStatus === key
                ? 'bg-[#1e3a5f] text-white'
                : 'bg-white border border-slate-200 text-slate-600 hover:border-blue-300 hover:text-[#1e3a5f]'
            }`}
          >
            {key && <span className={`w-1.5 h-1.5 rounded-full ${STATUS_DOT[key]}`} />}
            {label}
          </button>
        ))}
        <span className="mx-2 h-5 w-px bg-slate-200" />
        <button
          onClick={() => setUnpaidOnly((v) => !v)}
          className={`text-xs font-semibold px-3 py-1.5 rounded-full transition inline-flex items-center gap-1.5 ${
            unpaidOnly
              ? 'bg-amber-500 text-white'
              : 'bg-white border border-slate-200 text-amber-700 hover:border-amber-300 hover:bg-amber-50'
          }`}
        >
          <AlertCircle size={12} /> Solo pendientes de pago
        </button>
      </div>

      <div className="mb-5 bg-white border border-slate-100 rounded-2xl px-4 py-3 shadow-[0_2px_12px_-2px_rgba(15,23,42,0.05)] space-y-3">
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-[10px] font-bold text-slate-400 uppercase tracking-[0.16em] mr-1">Atajos</span>
          {presetsConfig.map(({ key, label }) => (
            <button
              key={key}
              onClick={() => applyPreset(key)}
              className="text-xs font-semibold px-3 py-1.5 rounded-full bg-slate-50 text-slate-600 border border-slate-100 hover:bg-blue-50 hover:text-blue-700 hover:border-blue-100 transition"
            >
              {label}
            </button>
          ))}
        </div>

        <div className="flex flex-wrap gap-3 items-end">
          <div className="w-40">
            <Input label="Desde" type="date" value={fromDate} onChange={(e) => setFromDate(e.target.value)} />
          </div>
          <div className="w-40">
            <Input label="Hasta" type="date" value={toDate} onChange={(e) => setToDate(e.target.value)} />
          </div>
          <div className="w-56">
            <Select label="Empleado" value={employeeFilter} onChange={(e) => setEmployeeFilter(e.target.value)}>
              <option value="">Todos los empleados</option>
              {employees.map((e) => (
                <option key={e.id} value={e.id}>{e.fullName}</option>
              ))}
            </Select>
          </div>
          {hasAnyFilter && (
            <Button variant="ghost" size="sm" onClick={clearAllFilters} className="gap-1.5">
              <FilterX size={14} /> Limpiar todo
            </Button>
          )}
          <span className="ml-auto text-[11px] text-slate-400 font-medium pb-2.5">
            <strong>Rango/empleado</strong> filtran todo el listado · <strong>Estado/pago/búsqueda</strong> solo esta página
          </span>
        </div>
      </div>

      {inProgressNow && (
        <div className="mb-4 bg-gradient-to-br from-sky-50 via-cyan-50 to-blue-50 border border-sky-200 rounded-2xl p-4 flex items-center gap-4 shadow-[0_4px_16px_-4px_rgba(14,165,233,0.25)]">
          <div className="w-12 h-12 rounded-2xl bg-gradient-to-br from-cyan-400 to-blue-500 flex items-center justify-center text-white shrink-0 animate-pulse">
            <Zap size={20} />
          </div>
          <div className="flex-1 min-w-0">
            <div className="text-[10px] font-bold text-sky-700 uppercase tracking-[0.18em]">Ahora en curso</div>
            <div className="text-sm font-bold text-[#1e3a5f] mt-0.5 truncate">
              {inProgressNow.clientName}
              {' · '}{inProgressNow.bookedServices?.map((b) => b.serviceName).join(' + ') || 'Sin servicios'}
              {' · '}{inProgressNow.userFullName}
            </div>
            <div className="text-xs text-slate-500 mt-0.5">
              Termina en <strong className="text-sky-700">{fmtMinsLeft(inProgressMinsLeft)}</strong>
              {' · '}{inProgressNow.startDateTime.slice(11, 16)} – {inProgressNow.endDateTime.slice(11, 16)}
              {inProgressNow.boothName && ` · ${inProgressNow.boothName}`}
            </div>
          </div>
          <button
            onClick={() => setDetailAppt(inProgressNow)}
            className="inline-flex items-center gap-1.5 rounded-xl bg-white border border-sky-200 px-3 py-2 text-xs font-semibold text-sky-700 hover:bg-sky-100 transition"
          >
            Ver detalle <ArrowRight size={12} />
          </button>
        </div>
      )}

      {loading ? (
        <div className="appt-grid">
          {[...Array(5)].map((_, i) => (
            <div key={i} className="h-24 rounded-2xl bg-white border border-slate-100 animate-pulse" />
          ))}
        </div>
      ) : appointments.length === 0 ? (
        hasServerFilters ? (
          <div className="py-20 text-center text-slate-400">
            No hay citas en el rango seleccionado.
          </div>
        ) : (
          <EmptyState
            icon={CalendarDays}
            title="Aún no hay citas"
            description="Crea la primera cita con el asistente: cliente, servicios y un hueco disponible."
            actionLabel="Nueva cita"
            onAction={() => setWizardOpen(true)}
          />
        )
      ) : visible.length === 0 ? (
        <div className="py-20 text-center text-slate-400">
          Sin resultados con esos filtros.
        </div>
      ) : view === 'day' ? (
        <DayGroupedList list={paged} onOpen={setDetailAppt} empColorMap={empColorMap} />
      ) : (
        // Rejilla en vez de lista vertical: en 2K/4K una sola columna estiraba
        // cada tarjeta a 3000+ px dejando un hueco enorme entre datos e importe
        <div className="appt-grid">
          {paged.map((a) => (
            <AppointmentCard
              key={a.id}
              appointment={a}
              onClick={() => setDetailAppt(a)}
              employeeColor={empColorMap.get(a.membershipId)}
            />
          ))}
        </div>
      )}

      {totalPages > 1 && (
        <div className="mt-6 bg-white rounded-2xl border border-slate-100 overflow-hidden shadow-[0_2px_12px_-2px_rgba(15,23,42,0.06)]">
          <Pagination page={safePage} totalPages={totalPages} totalElements={visible.length} onChange={setPage} />
        </div>
      )}

      <AppointmentWizard
        open={wizardOpen || Boolean(editingAppt)}
        onClose={closeWizard}
        onCreated={refresh}
        bId={bId}
        appointmentToEdit={editingAppt}
      />
      <AppointmentDetailModal
        appointment={detailAppt}
        bId={bId}
        onClose={() => setDetailAppt(null)}
        onChanged={refresh}
        onEdit={startEditing}
        employeeColor={detailAppt ? empColorMap.get(detailAppt.membershipId) : undefined}
      />
    </div>
  )
}


/** Tarjeta de KPI del strip superior */
function StatTile({ label, value, tone }) {
  const tones = {
    default: 'text-[#1e3a5f]',
    success: 'text-emerald-600',
    warning: 'text-orange-600',
    danger:  'text-rose-500',
  }
  return (
    <div className="bg-white rounded-2xl border border-slate-100 p-4">
      <div className="text-[10px] font-semibold text-slate-400 uppercase tracking-wider">{label}</div>
      <div className={`text-2xl font-extrabold mt-1 tabular-nums ${tones[tone]}`}>{value}</div>
    </div>
  )
}

// D3: estados terminales en los que NO se muestra el chip "Pasada" (la cita
// ya fue cerrada manualmente por el admin, su pasado es esperado)
const STATES_CERRADOS = new Set(['COMPLETED', 'CANCELLED', 'NO_SHOW'])

/** Tarjeta de cita con bloque de fecha, datos del cliente y resumen del estado */
function AppointmentCard({ appointment: a, onClick, employeeColor }) {
  const isInProgress = a.statusName === 'IN_PROGRESS'
  const servicesLabel = a.bookedServices?.map((b) => b.serviceName).join(' + ') || 'Sin servicios'
  const showUnpaidChip = !a.isPaid && (a.statusName === 'COMPLETED' || a.statusName === 'IN_PROGRESS')
  // Chip "Pasada": SOLO indicador visual, no cambia el estado en BD Avisa
  // al admin que la cita esta sin cerrar y deberia marcarla manualmente
  // (COMPLETED / CANCELLED / NO_SHOW segun lo que ocurrio)
  const isOverdue = new Date(a.endDateTime).getTime() < Date.now()
                    && !STATES_CERRADOS.has(a.statusName)
  // Punto del color del empleado (F del audit): si el admin asigno color
  // en Empleadosjsx, ese; si no, color automatico por membershipId
  const empDot = dotClassFromColorAndId(employeeColor, a.membershipId)
  return (
    <div
      onClick={onClick}
      className={`bg-white rounded-2xl border ${isInProgress ? 'border-sky-200' : 'border-slate-100/80'} shadow-[0_2px_12px_-2px_rgba(15,23,42,0.06)] p-5 hover:shadow-[0_4px_16px_-4px_rgba(15,23,42,0.1)] hover:border-blue-200/60 transition-all cursor-pointer relative overflow-hidden`}
    >
      {isInProgress && <div className="absolute left-0 top-0 bottom-0 w-1 bg-gradient-to-b from-cyan-400 to-blue-500" />}
      <div className="flex items-center gap-5">
        <div className={`flex flex-col items-center justify-center w-16 h-16 rounded-2xl bg-gradient-to-br ${TINTS[a.statusName] || 'from-cyan-400 to-blue-500'} shrink-0 shadow-[0_8px_20px_-8px_rgba(14,165,233,0.4)]`}>
          <span className="text-2xl font-bold text-white leading-none">{new Date(a.startDateTime).getDate()}</span>
          <span className="text-[10px] text-white/80 uppercase font-semibold tracking-wide">
            {new Date(a.startDateTime).toLocaleDateString('es-ES', { month: 'short' })}
          </span>
        </div>

        <div className={`w-10 h-10 rounded-full bg-gradient-to-br ${avatarColor(a.clientId)} flex items-center justify-center text-white text-xs font-bold shrink-0`}>
          {initials(a.clientName)}
        </div>

        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2.5 mb-1.5 flex-wrap">
            <p className="font-bold text-[#1e3a5f] text-base truncate">{a.clientName}</p>
            <StatusBadge status={a.statusName} />
            {a.isPaid && <Badge variant="success">Pagada</Badge>}
            {showUnpaidChip && (
              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wider bg-amber-50 text-amber-700 ring-1 ring-amber-200">
                Pendiente cobro
              </span>
            )}
            {isOverdue && (
              <span
                title="La cita pasó sin marcarse como completada, cancelada ni ausencia. Ciérrala manualmente."
                className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wider bg-rose-50 text-rose-700 ring-1 ring-rose-200"
              >
                Pasada sin cerrar
              </span>
            )}
          </div>

          <div className="text-xs text-slate-500 font-medium mb-1 truncate">
            <Scissors size={11} className="inline -mt-0.5 mr-1 text-slate-400" /> {servicesLabel}
          </div>

          <div className="flex flex-wrap gap-3 text-xs text-slate-400 font-medium">
            <span className="flex items-center gap-1.5">
              <Clock size={12} />
              {formatDateTime(a.startDateTime)} · {formatDuration(a.startDateTime, a.endDateTime)}
            </span>
            <span className="flex items-center gap-1.5">
              <span className={`w-2 h-2 rounded-full shrink-0 ${empDot}`} aria-hidden />
              <User size={12} />{a.userFullName}
            </span>
            {a.boothName && <span className="flex items-center gap-1.5"><MapPin size={12} />{a.boothName}</span>}
          </div>
        </div>

        <div className="shrink-0 text-right">
          <p className={`text-xl font-bold tabular-nums ${a.isPaid ? 'text-[#1e3a5f]' : (showUnpaidChip ? 'text-orange-600' : 'text-[#1e3a5f]')}`}>
            {totalBooked(a.bookedServices)} €
          </p>
          <p className="text-[11px] text-slate-400 mt-0.5">Ver detalle</p>
        </div>
      </div>
    </div>
  )
}

/** Lista agrupada por dia con cabeceras y AppointmentCard por cita */
function DayGroupedList({ list, onOpen, empColorMap }) {
  // Agrupa por fecha (yyyy-mm-dd) manteniendo el orden de `list`
  const groups = useMemo(() => {
    const m = new Map()
    list.forEach((a) => {
      const key = a.startDateTime.slice(0, 10)
      if (!m.has(key)) m.set(key, [])
      m.get(key).push(a)
    })
    return [...m.entries()]
  }, [list])

  const fmtDayHeader = (ymdStr) => {
    const d = new Date(`${ymdStr}T00:00:00`)
    const today = new Date(); today.setHours(0, 0, 0, 0)
    const tomorrow = new Date(today); tomorrow.setDate(today.getDate() + 1)
    const tag =
      d.getTime() === today.getTime()    ? 'Hoy'    :
      d.getTime() === tomorrow.getTime() ? 'Mañana' : ''
    const long = d.toLocaleDateString('es-ES', { weekday: 'long', day: '2-digit', month: 'long' })
    return tag ? `${tag} · ${long}` : long
  }

  return (
    <div className="space-y-6">
      {groups.map(([day, list]) => (
        <section key={day}>
          <div className="flex items-center gap-3 mb-3">
            <h2 className="text-xs font-bold text-[#1e3a5f] uppercase tracking-wider capitalize">
              {fmtDayHeader(day)}
            </h2>
            <span className="text-xs text-slate-400">· {list.length} cita{list.length === 1 ? '' : 's'}</span>
            <div className="flex-1 h-px bg-slate-100" />
          </div>
          <div className="appt-grid">
            {list.map((a) => (
              <AppointmentCard
                key={a.id}
                appointment={a}
                onClick={() => onOpen(a)}
                employeeColor={empColorMap?.get(a.membershipId)}
              />
            ))}
          </div>
        </section>
      ))}
    </div>
  )
}

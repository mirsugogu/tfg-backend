import { useEffect, useMemo, useState, useCallback } from 'react'
import { Link } from 'react-router-dom'
import {
  Users, UserCheck, CalendarCheck, Banknote, AlertCircle,
  Plus, Search, Clock, Scissors, MapPin, User, ArrowRight,
  ChevronRight, CalendarDays,
} from 'lucide-react'
import { useAuth } from '@/context/AuthContext'
import { useToast } from '@/components/ui/Toast'
import { AppointmentDetailModal } from '@/components/appointments/AppointmentDetailModal'
import { OnboardingChecklist } from '@/components/dashboard/OnboardingChecklist'
import api, { getErrorMessage } from '@/lib/api'
import { totalBooked } from '@/lib/format'

/* ============================================================
   CONSTANTES Y HELPERS
   ============================================================ */

const ACTIVE_STATUSES = ['PENDING', 'CONFIRMED', 'IN_PROGRESS']

const STATUS_LABELS = {
  PENDING:     'Pendiente',
  CONFIRMED:   'Confirmada',
  IN_PROGRESS: 'En curso',
  COMPLETED:   'Completada',
  CANCELLED:   'Cancelada',
  NO_SHOW:     'No presentado',
}

// Colores para timeline y donut (paleta original del Dashboard)
const STATUS_COLORS = {
  PENDING:     '#f97316',
  CONFIRMED:   '#3b82f6',
  IN_PROGRESS: '#0ea5e9',
  COMPLETED:   '#10b981',
  CANCELLED:   '#94a3b8',
  NO_SHOW:     '#f43f5e',
}

const STATUS_CHIP = {
  PENDING:     'bg-orange-50 text-orange-700 ring-1 ring-orange-200',
  CONFIRMED:   'bg-blue-50 text-blue-700 ring-1 ring-blue-200',
  IN_PROGRESS: 'bg-sky-50 text-sky-700 ring-1 ring-sky-200',
  COMPLETED:   'bg-emerald-50 text-emerald-700 ring-1 ring-emerald-200',
  CANCELLED:   'bg-slate-100 text-slate-600 ring-1 ring-slate-200',
  NO_SHOW:     'bg-rose-50 text-rose-700 ring-1 ring-rose-200',
}

const STATUS_TABS = [
  { key: 'Todas',       label: 'Todas' },
  { key: 'PENDING',     label: 'Pendiente' },
  { key: 'CONFIRMED',   label: 'Confirmada' },
  { key: 'IN_PROGRESS', label: 'En curso' },
  { key: 'COMPLETED',   label: 'Completada' },
  { key: 'CANCELLED',   label: 'Cancelada' },
  { key: 'NO_SHOW',     label: 'No presentado' },
]

const AVATAR_GRADIENTS = [
  'from-cyan-400 to-blue-500',
  'from-amber-400 to-orange-500',
  'from-emerald-400 to-teal-500',
  'from-indigo-400 to-violet-500',
  'from-pink-400 to-rose-500',
  'from-sky-400 to-blue-500',
]
const avatarGradient = (id) => AVATAR_GRADIENTS[(id ?? 0) % AVATAR_GRADIENTS.length]
const initials = (name) =>
  name?.trim().split(/\s+/).filter(Boolean).slice(0, 2).map((s) => s[0]?.toUpperCase()).join('') || '?'

const ymd = (d) => {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const da = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${da}`
}
const minutesBetween = (a, b) => (new Date(b) - new Date(a)) / 60000
const hhmm = (iso) => String(iso).slice(11, 16)

const fmtEur = (n) =>
  Number(n).toLocaleString('es-ES', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + ' €'

const fullDate = (d) =>
  d.toLocaleDateString('es-ES', { weekday: 'long', day: '2-digit', month: 'long', year: 'numeric' })
   .replace(/^\w/, (c) => c.toUpperCase())

const greeting = (d = new Date()) => {
  const h = d.getHours()
  if (h < 6)  return 'Buenas noches'
  if (h < 13) return 'Buenos días'
  if (h < 21) return 'Buenas tardes'
  return 'Buenas noches'
}

const countdownLabel = (mins) => {
  if (mins == null) return ''
  if (mins <= 0)    return 'ahora mismo'
  if (mins < 60)    return `en ${mins} min`
  const h = Math.floor(mins / 60)
  const m = mins % 60
  return m ? `en ${h} h ${m} min` : `en ${h} h`
}

/* ============================================================
   SUBCOMPONENTES
   ============================================================ */

function StatCard({ label, value, sub, icon: Icon, tint = 'cyan-blue', attention = false }) {
  const tints = {
    'cyan-blue':   'from-cyan-400 to-blue-500',
    'blue-indigo': 'from-blue-500 to-indigo-500',
    'teal-cyan':   'from-teal-400 to-cyan-500',
    'amber':       'from-amber-400 to-orange-500',
  }
  return (
    <div className="bg-white rounded-2xl p-5 border border-slate-100/80 shadow-[0_2px_12px_-2px_rgba(15,23,42,0.06)] flex flex-col gap-3 hover:shadow-[0_12px_40px_-12px_rgba(15,23,42,0.15)] transition-shadow min-h-[132px]">
      <div className="flex items-center justify-between">
        <div className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">{label}</div>
        <div className={`w-11 h-11 rounded-xl bg-gradient-to-br ${tints[tint]} flex items-center justify-center text-white shadow-[0_8px_20px_-6px_rgba(14,165,233,0.4)]`}>
          <Icon size={20} strokeWidth={1.8} />
        </div>
      </div>
      <div className={`text-3xl font-extrabold tracking-tight leading-none tabular-nums ${attention ? 'text-orange-600' : 'text-[#1e3a5f]'}`}>
        {value}
      </div>
      {sub && <div className="text-xs text-slate-500">{sub}</div>}
    </div>
  )
}

function NextAppointmentHero({ appt, mins, onOpen }) {
  return (
    <div className="relative overflow-hidden rounded-2xl p-5 text-white shadow-[0_20px_50px_-20px_rgba(14,165,233,0.45)]"
         style={{ background: 'linear-gradient(135deg, #1e3a5f 0%, #1d4ed8 60%, #0ea5e9 100%)' }}>
      <div className="absolute -top-10 -right-8 w-40 h-40 rounded-full bg-white/5 pointer-events-none" />
      <div className="relative flex flex-col gap-3">
        <span className="inline-flex items-center gap-1.5 text-[10px] font-bold uppercase tracking-[0.18em] text-blue-200">
          <Clock size={12} /> Próxima cita
        </span>
        <div className="text-2xl font-extrabold tracking-tight tabular-nums">{countdownLabel(mins)}</div>
        <div>
          <div className="text-base font-semibold">{appt.clientName}</div>
          <div className="mt-1 flex flex-wrap gap-x-4 gap-y-1 text-xs text-blue-200">
            {appt.bookedServices?.[0]?.serviceName && (
              <span className="inline-flex items-center gap-1"><Scissors size={13} /> {appt.bookedServices.map(b => b.serviceName).join(', ')}</span>
            )}
            <span className="inline-flex items-center gap-1"><User size={13} /> {appt.userFullName}</span>
            {appt.boothName && <span className="inline-flex items-center gap-1"><MapPin size={13} /> {appt.boothName}</span>}
          </div>
        </div>
        <div className="flex gap-2 mt-1">
          <button
            onClick={onOpen}
            className="flex-1 px-3 py-2 rounded-xl bg-white text-[#1e3a5f] text-xs font-semibold hover:bg-blue-50 transition"
          >
            Ver detalle
          </button>
          <span className="flex-1 px-3 py-2 rounded-xl bg-white/10 border border-white/15 text-white text-xs font-semibold text-center tabular-nums">
            {hhmm(appt.startDateTime)} – {hhmm(appt.endDateTime)}
          </span>
        </div>
      </div>
    </div>
  )
}

function StatusDonut({ buckets, total }) {
  // Construye el conic-gradient en el orden de STATUS_LABELS
  const slices = []
  let deg = 0
  Object.keys(STATUS_LABELS).forEach((k) => {
    const portion = total > 0 ? ((buckets[k] ?? 0) / total) * 360 : 0
    if (portion > 0) slices.push(`${STATUS_COLORS[k]} ${deg}deg ${deg + portion}deg`)
    deg += portion
  })
  const gradient = slices.length
    ? `conic-gradient(${slices.join(', ')})`
    : 'conic-gradient(#e2e8f0 0deg 360deg)'

  return (
    <div className="bg-white rounded-2xl p-5 border border-slate-100/80 shadow-[0_2px_12px_-2px_rgba(15,23,42,0.06)]">
      <div className="mb-4">
        <div className="text-base font-bold text-[#1e3a5f]">Estado de las citas</div>
        <div className="text-xs text-slate-400 mt-0.5">Este mes · {total} totales</div>
      </div>
      <div className="flex items-center gap-5">
        <div className="relative w-28 h-28 shrink-0">
          <div className="w-full h-full rounded-full" style={{ background: gradient }} />
          <div className="absolute inset-4 rounded-full bg-white flex flex-col items-center justify-center">
            <div className="text-[9px] text-slate-400 uppercase tracking-wider font-semibold">Total</div>
            <div className="text-xl font-extrabold text-[#1e3a5f] leading-none tabular-nums">{total}</div>
          </div>
        </div>
        <div className="flex-1 grid grid-cols-1 gap-2 min-w-0">
          {Object.keys(STATUS_LABELS).map((k) => (
            <div key={k} className="flex items-center gap-2 text-xs">
              <span className="w-2 h-2 rounded-sm shrink-0" style={{ background: STATUS_COLORS[k] }} />
              <span className="flex-1 text-slate-500 truncate">{STATUS_LABELS[k]}</span>
              <span className="font-bold text-[#1e3a5f] tabular-nums">{buckets[k] ?? 0}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

function TodayTimeline({ list, now, onSelect, dayStart = 9, dayEnd = 21 }) {
  // El rango horario se deriva de business_hours (lo pasa el Dashboard);
  // 9-21 es solo el fallback si el negocio no tiene horario configurado.
  const DAY_START = dayStart, DAY_END = dayEnd
  const totalMin = (DAY_END - DAY_START) * 60
  const minutesFrom = (iso) => {
    const t = String(iso).slice(11, 16).split(':')
    return (parseInt(t[0], 10) - DAY_START) * 60 + parseInt(t[1], 10)
  }
  const nowMin = (now.getHours() - DAY_START) * 60 + now.getMinutes()
  const nowPct = Math.max(0, Math.min(100, (nowMin / totalMin) * 100))

  return (
    <div className="mx-5 mb-4 border border-slate-100 rounded-xl bg-slate-50/60 p-4">
      <div className="flex justify-between text-[10px] text-slate-400 tabular-nums font-medium mb-2">
        {Array.from({ length: DAY_END - DAY_START + 1 }, (_, i) => (
          <span key={i}>{String(DAY_START + i).padStart(2, '0')}</span>
        ))}
      </div>
      <div className="relative h-14 bg-white border border-slate-100 rounded-lg">
        {list.map((a) => {
          const startM = minutesFrom(a.startDateTime)
          const durM = minutesBetween(a.startDateTime, a.endDateTime)
          if (startM + durM <= 0 || startM >= totalMin) return null
          const left = Math.max(0, (startM / totalMin) * 100)
          const width = Math.min(100 - left, (durM / totalMin) * 100)
          return (
            <button
              key={a.id}
              onClick={() => onSelect(a)}
              className="absolute top-1.5 bottom-1.5 rounded text-[10px] font-semibold text-white px-1.5 overflow-hidden whitespace-nowrap text-ellipsis hover:-translate-y-0.5 hover:shadow-md transition"
              style={{ left: `${left}%`, width: `${width}%`, background: STATUS_COLORS[a.statusName] }}
              title={`${a.clientName} · ${hhmm(a.startDateTime)}`}
            >
              {a.clientName}
            </button>
          )
        })}
        {nowMin > 0 && nowMin < totalMin && (
          <div className="absolute -top-1 -bottom-1 w-0.5 bg-rose-500 rounded-full" style={{ left: `${nowPct}%` }}>
            <div className="absolute -top-1 -left-[3px] w-2 h-2 rounded-full bg-rose-500" />
          </div>
        )}
      </div>
      <div className="mt-3 flex flex-wrap gap-3 text-[10px] text-slate-500">
        {Object.entries(STATUS_LABELS).slice(0, 4).map(([k, label]) => (
          <span key={k} className="inline-flex items-center gap-1.5">
            <span className="w-2.5 h-2.5 rounded-sm" style={{ background: STATUS_COLORS[k] }} />
            {label}
          </span>
        ))}
        <span className="inline-flex items-center gap-1.5"><span className="w-2.5 h-2.5 rounded-sm bg-rose-500" /> Ahora · {now.toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' })}</span>
      </div>
    </div>
  )
}

function TopList({ title, sub, items, renderMeta }) {
  const max = items[0]?.count ?? 1
  return (
    <div className="bg-white rounded-2xl p-5 border border-slate-100/80 shadow-[0_2px_12px_-2px_rgba(15,23,42,0.06)]">
      <div className="mb-4">
        <div className="text-base font-bold text-[#1e3a5f]">{title}</div>
        {sub && <div className="text-xs text-slate-400 mt-0.5">{sub}</div>}
      </div>
      {items.length === 0 ? (
        <div className="text-xs text-slate-400 py-3">Sin datos este mes.</div>
      ) : (
        <div className="flex flex-col gap-3">
          {items.map((it, idx) => (
            <div key={it.name} className="flex items-center gap-3">
              <div className={`w-7 h-7 rounded-full flex items-center justify-center text-xs font-bold shrink-0 ${
                idx === 0
                  ? 'bg-gradient-to-br from-cyan-400 to-blue-500 text-white shadow-[0_4px_10px_rgba(14,165,233,0.4)]'
                  : 'bg-blue-50 text-blue-700'
              }`}>{idx + 1}</div>
              <div className="flex-1 min-w-0">
                <div className="text-sm font-semibold text-[#1e3a5f] truncate leading-tight">{it.name}</div>
                <div className="text-[11px] text-slate-500 tabular-nums mt-0.5">{renderMeta(it)}</div>
              </div>
              <div className="w-20 h-1 rounded-full bg-slate-100 overflow-hidden shrink-0">
                <div className="h-full bg-gradient-to-r from-cyan-400 to-blue-500" style={{ width: `${(it.count / max) * 100}%` }} />
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

/**
 * Página principal tras iniciar sesión: indicadores del día, lista de
 * las próximas citas y atajos visuales al resto de módulos (clientes,
 * empleados, catálogo, calendario).
 */
export default function Dashboard() {
  const { user } = useAuth()
  const toast = useToast()
  const bId = user?.businessId

  // Tick cada minuto para el countdown de "próxima cita" y la línea "ahora"
  const [now, setNow] = useState(new Date())
  useEffect(() => {
    const t = setInterval(() => setNow(new Date()), 60_000)
    return () => clearInterval(t)
  }, [])

  // Rangos
  const today      = ymd(new Date())
  const monthStart = ymd(new Date(new Date().getFullYear(), new Date().getMonth(), 1))
  const monthEnd   = ymd(new Date(new Date().getFullYear(), new Date().getMonth() + 1, 0))

  // Estado
  const [counts, setCounts]       = useState({ clients: 0, services: 0 })
  const [todayList, setTodayList] = useState([])
  const [monthList, setMonthList] = useState([])
  const [hours, setHours]         = useState([])
  const [loading, setLoading]     = useState(true)
  const [filter, setFilter]       = useState('Todas')   // estado
  const [selected, setSelected]   = useState(null)      // cita abierta en el modal

  // Carga inicial — paralelo, igual que el Dashboard original
  const fetchAll = useCallback(() => {
    if (!bId) return
    setLoading(true)
    Promise.all([
      api.get(`/api/businesses/${bId}/clients?size=1`),
      api.get(`/api/businesses/${bId}/services?size=1`),
      // size 100: el backend cappea Pageable en 100 (spring max-page-size).
      api.get(`/api/businesses/${bId}/appointments`, { params: { from: today, to: today, size: 100 } }),
      api.get(`/api/businesses/${bId}/appointments`, { params: { from: monthStart, to: monthEnd, size: 100 } }),
      api.get(`/api/businesses/${bId}/hours`),
    ])
      .then(([c, s, t, m, h]) => {
        setCounts({ clients: c.data.totalElements, services: s.data.totalElements })
        setTodayList(t.data.content)
        setMonthList(m.data.content)
        setHours(Array.isArray(h.data) ? h.data : (h.data?.content ?? []))
      })
      .catch((err) => {
        toast({ type: 'error', message: getErrorMessage(err, 'Error al cargar el dashboard.') })
      })
      .finally(() => setLoading(false))
  }, [bId, toast, today, monthStart, monthEnd])

  useEffect(() => { fetchAll() }, [fetchAll])

  /* ---------- Derivados ---------- */

  const activeToday   = useMemo(() => todayList.filter(a => ACTIVE_STATUSES.includes(a.statusName)), [todayList])
  const todayRevenue  = useMemo(
    () => activeToday.reduce((acc, a) => acc + parseFloat(totalBooked(a.bookedServices)), 0),
    [activeToday],
  )
  const pendingAttention = useMemo(() => {
    const limit = new Date(now.getTime() + 24 * 60 * 60 * 1000)
    return todayList.filter(a => a.statusName === 'PENDING' && new Date(a.startDateTime) <= limit).length
  }, [todayList, now])

  // Ocupación
  const dow = (now.getDay() === 0 ? 7 : now.getDay())
  const todayHours = hours.find(h => h.dayOfWeek === dow)
  const openMinutes = useMemo(() => {
    if (!todayHours || todayHours.isClosed || !todayHours.startTime || !todayHours.endTime) return 0
    return (new Date(`1970-01-01T${todayHours.endTime}`) - new Date(`1970-01-01T${todayHours.startTime}`)) / 60000
  }, [todayHours])
  const bookedMinutes = activeToday.reduce((acc, a) => acc + minutesBetween(a.startDateTime, a.endDateTime), 0)
  const occupancy = openMinutes > 0 ? Math.round((bookedMinutes / openMinutes) * 100) : 0
  const slotsTotal = openMinutes > 0 ? Math.round(openMinutes / 60) : null   // slots de 1 h aprox

  // Rango horario del timeline de hoy: min apertura / max cierre de
  // business_hours en toda la semana; 9-21 como fallback sin horario.
  const timelineRange = useMemo(() => {
    const open = hours.filter((h) => !h.isClosed && h.startTime && h.endTime)
    if (open.length === 0) return { start: 9, end: 21 }
    let s = 24, e = 0
    open.forEach((h) => {
      s = Math.min(s, parseInt(h.startTime.slice(0, 2), 10))
      const [eh, em] = h.endTime.slice(0, 5).split(':').map(Number)
      e = Math.max(e, em > 0 ? eh + 1 : eh)
    })
    return { start: s, end: e }
  }, [hours])

  // Próxima cita
  const upcoming = useMemo(
    () => activeToday.filter(a => new Date(a.startDateTime) > now)
                     .sort((x, y) => new Date(x.startDateTime) - new Date(y.startDateTime)),
    [activeToday, now],
  )
  const next       = upcoming[0]
  const nextInMin  = next ? Math.max(0, Math.round((new Date(next.startDateTime) - now) / 60000)) : null

  // Donut (este mes, todos los estados)
  const monthBuckets = useMemo(() => {
    const b = { PENDING: 0, CONFIRMED: 0, IN_PROGRESS: 0, COMPLETED: 0, CANCELLED: 0, NO_SHOW: 0 }
    monthList.forEach(a => { b[a.statusName] = (b[a.statusName] ?? 0) + 1 })
    return b
  }, [monthList])

  // Top servicios (este mes, excluyendo canceladas y no presentado)
  const topServices = useMemo(() => {
    const m = new Map()
    monthList
      .filter(a => a.statusName !== 'CANCELLED' && a.statusName !== 'NO_SHOW')
      .forEach(a => {
        a.bookedServices?.forEach(bs => {
          const cur = m.get(bs.serviceName) ?? { name: bs.serviceName, count: 0, revenue: 0 }
          cur.count   += 1
          cur.revenue += Number(bs.appliedPrice || 0)
          m.set(bs.serviceName, cur)
        })
      })
    return [...m.values()].sort((a, b) => b.count - a.count).slice(0, 4)
  }, [monthList])

  // Top empleados (este mes, todas las citas)
  const topEmployees = useMemo(() => {
    const m = new Map()
    monthList.forEach(a => {
      const cur = m.get(a.userFullName) ?? { name: a.userFullName, count: 0 }
      cur.count += 1
      m.set(a.userFullName, cur)
    })
    return [...m.values()].sort((a, b) => b.count - a.count).slice(0, 3)
  }, [monthList])

  // Tabla "agenda de hoy" — filtrada y ordenada por hora
  const visibleToday = useMemo(() => {
    const list = filter === 'Todas' ? todayList : todayList.filter(a => a.statusName === filter)
    return [...list].sort((a, b) => new Date(a.startDateTime) - new Date(b.startDateTime))
  }, [todayList, filter])

  /* ---------- Render ---------- */

  const firstName = user?.fullName?.split(/\s+/)[0] || ''

  return (
    <div className="px-4 sm:px-6 lg:px-8 xl:px-10 py-8">

      {/* Header */}
      <div className="flex flex-wrap items-start justify-between gap-4 mb-8">
        <div>
          <h1 className="text-3xl font-bold text-[#1e3a5f] tracking-tight">
            {greeting(now)}{firstName && `, ${firstName}`}
          </h1>
          <p className="text-sm text-slate-500 mt-1.5 font-medium">
            <span className="text-slate-700 font-semibold">{fullDate(now)}</span>
            {' · '}Tienes <strong className="text-[#1e3a5f]">{todayList.length} citas</strong> programadas hoy
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => window.dispatchEvent(new KeyboardEvent('keydown', { key: 'k', ctrlKey: true }))}
            title="Buscar (Ctrl+K)"
            className="inline-flex items-center gap-2 text-sm font-semibold text-slate-600 hover:text-[#1e3a5f] bg-white border border-slate-200 rounded-xl px-4 py-2.5 hover:bg-slate-50 transition"
          >
            <Search size={16} /> Buscar
          </button>
          <Link
            to="/citas"
            className="inline-flex items-center gap-2 text-sm font-semibold text-white bg-gradient-to-br from-cyan-400 to-blue-500 hover:from-cyan-500 hover:to-blue-600 rounded-xl px-4 py-2.5 shadow-[0_8px_20px_-6px_rgba(14,165,233,0.5)] transition"
          >
            <Plus size={16} /> Nueva cita
          </Link>
        </div>
      </div>

      {/* Onboarding: solo se muestra mientras falten pasos por configurar. */}
      <OnboardingChecklist businessId={bId} />

      {/* KPIs */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        <StatCard
          label="Citas hoy"
          icon={CalendarCheck}
          tint="cyan-blue"
          value={loading ? '…' : todayList.length}
          sub={
            slotsTotal
              ? <span><strong className="text-emerald-600">{occupancy}% ocupación</strong> · {Math.max(0, slotsTotal - activeToday.length)} huecos libres</span>
              : <span>{activeToday.length} activas</span>
          }
        />
        <StatCard
          label="Ingresos previstos"
          icon={Banknote}
          tint="blue-indigo"
          value={loading ? '…' : fmtEur(todayRevenue)}
          sub="Suma de citas activas de hoy"
        />
        <StatCard
          label="Clientes activos"
          icon={Users}
          tint="teal-cyan"
          value={loading ? '…' : counts.clients}
          sub={`${counts.services} servicios en catálogo`}
        />
        <StatCard
          label="Necesita tu atención"
          icon={AlertCircle}
          tint="amber"
          attention
          value={loading ? '…' : pendingAttention}
          sub="Citas pendientes en las próximas 24 h"
        />
      </div>

      {/* Two columns */}
      <div className="grid lg:grid-cols-10 gap-6">

        {/* LEFT — Agenda de hoy */}
        <div className="lg:col-span-7 min-w-0">
          <div className="bg-white rounded-2xl border border-slate-100/80 shadow-[0_2px_12px_-2px_rgba(15,23,42,0.06)] flex flex-col">

            {/* Card header */}
            <div className="flex flex-wrap items-center gap-3 px-6 pt-5 pb-4 border-b border-slate-100">
              <div className="flex-1">
                <div className="text-base font-bold text-[#1e3a5f] flex items-center gap-2"><CalendarDays size={16} /> Agenda de hoy</div>
                <div className="text-xs text-slate-400 mt-0.5">{visibleToday.length} citas · vista cronológica</div>
              </div>
              <div className="flex items-center gap-1 bg-slate-50 rounded-xl p-1 overflow-x-auto max-w-full">
                {STATUS_TABS.map(({ key, label }) => (
                  <button
                    key={key}
                    onClick={() => setFilter(key)}
                    className={`shrink-0 text-xs font-semibold px-3 py-1.5 rounded-lg transition ${
                      filter === key
                        ? 'bg-white text-[#1e3a5f] shadow-[0_1px_4px_rgba(15,23,42,0.08)]'
                        : 'text-slate-500 hover:text-[#1e3a5f]'
                    }`}
                  >
                    {label}
                  </button>
                ))}
              </div>
            </div>

            {/* Timeline */}
            {!loading && todayList.length > 0 && (
              <TodayTimeline
                list={todayList} now={now} onSelect={setSelected}
                dayStart={timelineRange.start} dayEnd={timelineRange.end}
              />
            )}

            {/* Table */}
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-[11px] uppercase tracking-[0.12em] text-slate-400 border-b border-slate-100 bg-slate-50/60">
                    <th className="text-left font-semibold px-6 py-3">Cliente</th>
                    <th className="text-left font-semibold px-2 py-3">Hora</th>
                    <th className="text-left font-semibold px-2 py-3 hidden md:table-cell">Servicio</th>
                    <th className="text-left font-semibold px-2 py-3 hidden lg:table-cell">Empleado · Cabina</th>
                    <th className="text-left font-semibold px-2 py-3">Estado</th>
                    <th className="text-right font-semibold px-2 py-3">Importe</th>
                    <th className="px-6 py-3"></th>
                  </tr>
                </thead>
                <tbody>
                  {loading ? (
                    [...Array(5)].map((_, i) => (
                      <tr key={i} className="border-b border-slate-50">
                        <td colSpan={7} className="px-6 py-4">
                          <div className="h-4 bg-slate-100 rounded-lg animate-pulse" />
                        </td>
                      </tr>
                    ))
                  ) : visibleToday.length === 0 ? (
                    <tr>
                      <td colSpan={7} className="px-6 py-12 text-center text-sm text-slate-400">
                        {filter === 'Todas'
                          ? 'Sin citas para hoy. Disfruta de un día tranquilo.'
                          : 'Sin citas con este estado hoy.'}
                      </td>
                    </tr>
                  ) : (
                    visibleToday.map((a) => {
                      const total = parseFloat(totalBooked(a.bookedServices))
                      return (
                        <tr
                          key={a.id}
                          onClick={() => setSelected(a)}
                          className="border-b border-slate-50 hover:bg-blue-50/40 transition cursor-pointer"
                        >
                          <td className="px-6 py-3.5">
                            <div className="flex items-center gap-3">
                              <div className={`w-9 h-9 rounded-xl bg-gradient-to-br ${avatarGradient(a.id)} flex items-center justify-center text-white text-xs font-bold shrink-0`}>
                                {initials(a.clientName)}
                              </div>
                              <div className="min-w-0">
                                <div className="font-semibold text-[#1e3a5f] truncate">{a.clientName}</div>
                                <div className="text-xs text-slate-400">{a.isPaid ? 'Pagada' : 'Pendiente de pago'}</div>
                              </div>
                            </div>
                          </td>
                          <td className="px-2 py-3.5 tabular-nums">
                            <div className="font-semibold text-[#1e3a5f]">{hhmm(a.startDateTime)} – {hhmm(a.endDateTime)}</div>
                            <div className="text-xs text-slate-400">Hoy</div>
                          </td>
                          <td className="px-2 py-3.5 text-slate-600 hidden md:table-cell">
                            {a.bookedServices?.map(b => b.serviceName).join(', ') || '—'}
                          </td>
                          <td className="px-2 py-3.5 hidden lg:table-cell">
                            <div className="font-semibold text-[#1e3a5f] text-sm leading-tight">{a.userFullName}</div>
                            {a.boothName && <div className="text-xs text-slate-400 mt-0.5">{a.boothName}</div>}
                          </td>
                          <td className="px-2 py-3.5">
                            <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-semibold ${STATUS_CHIP[a.statusName]}`}>
                              <span className="w-1.5 h-1.5 rounded-full" style={{ background: STATUS_COLORS[a.statusName] }} />
                              {STATUS_LABELS[a.statusName]}
                            </span>
                          </td>
                          <td className={`px-2 py-3.5 text-right tabular-nums font-semibold ${a.isPaid ? 'text-[#1e3a5f]' : 'text-orange-600'}`}>
                            {fmtEur(total)}
                          </td>
                          <td className="px-6 py-3.5 text-right text-slate-300">
                            <ChevronRight size={16} />
                          </td>
                        </tr>
                      )
                    })
                  )}
                </tbody>
              </table>
            </div>

            <div className="px-6 py-3 border-t border-slate-100 flex items-center justify-between">
              <span className="text-xs text-slate-400">
                Mostrando {visibleToday.length} de {todayList.length} citas de hoy
              </span>
              <Link to="/citas" className="text-xs font-semibold text-blue-600 hover:text-blue-700 inline-flex items-center gap-1">
                Ver todas las citas <ArrowRight size={12} />
              </Link>
            </div>
          </div>
        </div>

        {/* RIGHT — sidebar */}
        <div className="lg:col-span-3 space-y-5 min-w-0">

          {next && <NextAppointmentHero appt={next} mins={nextInMin} onOpen={() => setSelected(next)} />}

          <StatusDonut buckets={monthBuckets} total={monthList.length} />

          <TopList
            title="Servicios más vendidos"
            sub="Este mes"
            items={topServices}
            renderMeta={(it) => `${it.count} citas · ${fmtEur(it.revenue)}`}
          />

          <TopList
            title="Mejores empleados"
            sub="Por citas atendidas"
            items={topEmployees}
            renderMeta={(it) => `${it.count} citas`}
          />

        </div>
      </div>

      {/* Modal de detalle — reutiliza el componente que ya existe */}
      <AppointmentDetailModal
        appointment={selected}
        bId={bId}
        onClose={() => setSelected(null)}
        onChanged={() => { setSelected(null); fetchAll() }}
      />
    </div>
  )
}

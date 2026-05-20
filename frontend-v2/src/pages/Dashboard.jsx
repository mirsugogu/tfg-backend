import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Users, UserCheck, CalendarDays, Scissors, ArrowRight } from 'lucide-react'
import { StatusBadge } from '@/components/ui/Badge'
import { useAuth } from '@/context/AuthContext'
import { useToast } from '@/components/ui/Toast'
import api, { getErrorMessage } from '@/lib/api'

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
  name?.split(/\s+/).filter(Boolean).slice(0, 2).map((s) => s[0]?.toUpperCase()).join('') || '?'

const FILTER_TABS = [
  { key: 'Todas',       label: 'Todas' },
  { key: 'PENDING',     label: 'Pendiente' },
  { key: 'CONFIRMED',   label: 'Confirmada' },
  { key: 'IN_PROGRESS', label: 'En curso' },
  { key: 'COMPLETED',   label: 'Completada' },
  { key: 'CANCELLED',   label: 'Cancelada' },
  { key: 'NO_SHOW',     label: 'No presentado' },
]

function StatCard({ label, value, icon: Icon, tint = 'cyan', pct = 68 }) {
  const tints = {
    cyan:  'from-cyan-400 to-blue-500',
    blue:  'from-blue-500 to-indigo-500',
    sky:   'from-sky-400 to-blue-500',
    teal:  'from-teal-400 to-cyan-500',
  }
  return (
    <div className="bg-white rounded-2xl p-5 border border-slate-100/80 shadow-[0_2px_12px_-2px_rgba(15,23,42,0.06)] flex items-center gap-4 hover:shadow-[0_12px_40px_-12px_rgba(15,23,42,0.15)] transition-shadow">
      <div className={`shrink-0 w-14 h-14 rounded-2xl bg-gradient-to-br ${tints[tint]} flex items-center justify-center text-white shadow-[0_8px_20px_-6px_rgba(14,165,233,0.4)]`}>
        <Icon size={22} strokeWidth={1.8} />
      </div>
      <div className="flex-1 min-w-0">
        <div className="text-xs font-medium text-slate-500 tracking-wide">{label}</div>
        <div className="text-2xl font-bold text-[#1e3a5f] mt-0.5 leading-none">{value ?? '—'}</div>
        <div className="mt-2.5 h-1 bg-slate-100 rounded-full overflow-hidden">
          <div className={`h-full bg-gradient-to-r ${tints[tint]} transition-all`} style={{ width: `${pct}%` }} />
        </div>
      </div>
    </div>
  )
}

function StatusDonut({ pending, confirmed, completed, other, total }) {
  const t = total || 1
  const a = (confirmed / t) * 360
  const b = a + (pending / t) * 360
  const c = b + (completed / t) * 360
  const gradient = `conic-gradient(#3b82f6 0deg ${a}deg, #f97316 ${a}deg ${b}deg, #10b981 ${b}deg ${c}deg, #e2e8f0 ${c}deg 360deg)`
  return (
    <div className="relative w-24 h-24 shrink-0">
      <div className="w-full h-full rounded-full" style={{ background: gradient }} />
      <div className="absolute inset-3 rounded-full bg-white flex flex-col items-center justify-center">
        <div className="text-[9px] text-slate-400 uppercase tracking-wider">Total</div>
        <div className="text-lg font-bold text-[#1e3a5f] leading-none">{total}</div>
      </div>
    </div>
  )
}

const fmtDate = (dt) =>
  new Date(dt).toLocaleDateString('es-ES', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' })

export default function Dashboard() {
  const { user } = useAuth()
  const toast = useToast()
  const bId = user?.businessId

  // Contadores reales (totalElements). Antes se usaba .length de una
  // página de 100 → el número se quedaba corto con más de 100 registros.
  const [counts, setCounts] = useState({ clients: 0, employees: 0, appointments: 0, services: 0 })
  const [appointments, setAppointments] = useState([])
  const [loading, setLoading] = useState(true)
  const [filter, setFilter] = useState('Todas')

  useEffect(() => {
    if (!bId) return
    Promise.all([
      // Para los contadores basta totalElements: size=1 evita traer 100 filas.
      api.get(`/api/businesses/${bId}/clients?size=1`),
      api.get(`/api/businesses/${bId}/users?size=1`),
      api.get(`/api/businesses/${bId}/appointments?size=100`),
      api.get(`/api/businesses/${bId}/services?size=1`),
    ])
      .then(([c, e, a, s]) => {
        setCounts({
          clients:      c.data.totalElements,
          employees:    e.data.totalElements,
          appointments: a.data.totalElements,
          services:     s.data.totalElements,
        })
        setAppointments(a.data.content)
      })
      .catch((err) => {
        // El antiguo .catch(() => {}) silencioso ocultaba fallos como 403,
        // 429 o caida del backend. Ahora se notifica con el mensaje real.
        toast({ type: 'error', message: getErrorMessage(err, 'Error al cargar el dashboard.') })
      })
      .finally(() => setLoading(false))
  }, [bId, toast])

  const pending   = appointments.filter((a) => a.statusName === 'PENDING').length
  const confirmed = appointments.filter((a) => a.statusName === 'CONFIRMED').length
  const completed = appointments.filter((a) => a.statusName === 'COMPLETED').length
  const other     = appointments.length - pending - confirmed - completed

  const sorted = [...appointments].sort((a, b) => new Date(b.startDateTime) - new Date(a.startDateTime))
  const recent = (filter === 'Todas' ? sorted : sorted.filter((a) => a.statusName === filter)).slice(0, 8)

  return (
    <div className="p-8 max-w-7xl mx-auto">
      {/* Header */}
      <div className="mb-8">
        <h1 className="text-3xl font-bold text-[#1e3a5f] tracking-tight">Dashboard</h1>
        <p className="text-sm text-slate-500 mt-1.5 font-medium">Resumen general del negocio</p>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        <StatCard label="Clientes"      value={loading ? '…' : counts.clients}      icon={Users}       tint="cyan" pct={72} />
        <StatCard label="Empleados"     value={loading ? '…' : counts.employees}    icon={UserCheck}   tint="blue" pct={55} />
        <StatCard label="Citas totales" value={loading ? '…' : counts.appointments} icon={CalendarDays} tint="sky" pct={80} />
        <StatCard label="Servicios"     value={loading ? '…' : counts.services}     icon={Scissors}    tint="teal" pct={60} />
      </div>

      {/* Two columns */}
      <div className="grid lg:grid-cols-10 gap-6">

        {/* Appointments table — 7 cols */}
        <div className="lg:col-span-7">
          <div className="bg-white rounded-2xl border border-slate-100/80 shadow-[0_2px_12px_-2px_rgba(15,23,42,0.06)] h-full flex flex-col">
            {/* Header */}
            <div className="flex flex-wrap items-center gap-3 px-6 pt-5 pb-4 border-b border-slate-100">
              <div className="flex-1">
                <div className="text-base font-bold text-[#1e3a5f]">Últimas citas</div>
                <div className="text-xs text-slate-400 mt-0.5">{recent.length} resultados</div>
              </div>
              {/* Filter pills */}
              <div className="flex items-center gap-1 bg-slate-50 rounded-xl p-1">
                {FILTER_TABS.map(({ key, label }) => (
                  <button
                    key={key}
                    onClick={() => setFilter(key)}
                    className={`text-xs font-semibold px-3 py-1.5 rounded-lg transition ${
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

            {/* Table */}
            <div className="overflow-x-auto flex-1">
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-[11px] uppercase tracking-[0.12em] text-slate-400 border-b border-slate-100">
                    <th className="text-left font-semibold px-6 py-3">Cliente</th>
                    <th className="text-left font-semibold px-2 py-3 hidden md:table-cell">Fecha</th>
                    <th className="text-left font-semibold px-2 py-3">Estado</th>
                    <th className="text-right font-semibold px-6 py-3"></th>
                  </tr>
                </thead>
                <tbody>
                  {loading ? (
                    [...Array(5)].map((_, i) => (
                      <tr key={i} className="border-b border-slate-50">
                        <td colSpan={4} className="px-6 py-4">
                          <div className="h-4 bg-slate-100 rounded-lg animate-pulse" />
                        </td>
                      </tr>
                    ))
                  ) : recent.length === 0 ? (
                    <tr>
                      <td colSpan={4} className="px-6 py-12 text-center text-sm text-slate-400">
                        {filter === 'Todas' ? 'Sin citas registradas.' : 'Sin citas con este estado.'}
                      </td>
                    </tr>
                  ) : (
                    recent.map((a) => (
                      <tr key={a.id} className="border-b border-slate-50 hover:bg-slate-50/50 transition">
                        <td className="px-6 py-3.5">
                          <div className="flex items-center gap-3">
                            <div className={`w-9 h-9 rounded-xl bg-gradient-to-br ${avatarColor(a.id)} flex items-center justify-center text-white text-xs font-bold shrink-0`}>
                              {initials(a.clientName)}
                            </div>
                            <div className="min-w-0">
                              <div className="font-semibold text-[#1e3a5f] truncate">{a.clientName}</div>
                              <div className="text-xs text-slate-400 truncate">{a.userFullName}</div>
                            </div>
                          </div>
                        </td>
                        <td className="px-2 py-3.5 text-xs text-slate-500 hidden md:table-cell">{fmtDate(a.startDateTime)}</td>
                        <td className="px-2 py-3.5"><StatusBadge status={a.statusName} /></td>
                        <td className="px-6 py-3.5 text-right">
                          <span className="text-slate-300">›</span>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>

            <div className="px-6 py-3 border-t border-slate-100 flex items-center justify-between">
              <span className="text-xs text-slate-400">Mostrando {recent.length} de {appointments.length}</span>
              <Link to="/citas" className="text-xs font-semibold text-blue-600 hover:text-blue-700 flex items-center gap-1">
                Ver todas <ArrowRight size={12} />
              </Link>
            </div>
          </div>
        </div>

        {/* Right column — 3 cols */}
        <div className="lg:col-span-3 space-y-5">

          {/* Status donut */}
          <div className="bg-white rounded-2xl p-5 border border-slate-100/80 shadow-[0_2px_12px_-2px_rgba(15,23,42,0.06)]">
            <div className="flex items-center justify-between mb-4">
              <div>
                <div className="text-base font-bold text-[#1e3a5f]">Estado de citas</div>
                <div className="text-xs text-slate-400 mt-0.5">Resumen global</div>
              </div>
            </div>
            <div className="flex items-center gap-4">
              <StatusDonut pending={pending} confirmed={confirmed} completed={completed} other={other} total={appointments.length} />
              <div className="flex-1 space-y-2.5">
                {[
                  { label: 'Confirmada', value: confirmed, dot: 'bg-blue-500',    text: 'text-blue-600' },
                  { label: 'Pendiente',  value: pending,   dot: 'bg-orange-500',  text: 'text-orange-600' },
                  { label: 'Completada', value: completed, dot: 'bg-emerald-500', text: 'text-emerald-600' },
                ].map((r) => (
                  <div key={r.label}>
                    <div className="flex items-center justify-between text-sm">
                      <div className="flex items-center gap-2">
                        <span className={`w-2 h-2 rounded-full ${r.dot}`} />
                        <span className="text-slate-500 text-xs">{r.label}</span>
                      </div>
                      <span className={`font-bold text-xs ${r.text}`}>{loading ? '…' : r.value}</span>
                    </div>
                    <div className="mt-1 h-1.5 bg-slate-100 rounded-full overflow-hidden">
                      <div className={`h-full ${r.dot}`} style={{ width: appointments.length ? `${(r.value / appointments.length) * 100}%` : '0%' }} />
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>

          {/* Quick access */}
          <div className="bg-white rounded-2xl p-5 border border-slate-100/80 shadow-[0_2px_12px_-2px_rgba(15,23,42,0.06)]">
            <div className="text-base font-bold text-[#1e3a5f] mb-1">Acceso rápido</div>
            <div className="text-xs text-slate-400 mb-4">Atajos del equipo</div>
            <div className="space-y-2.5">
              {[
                { to: '/citas',    label: 'Nueva cita',       sub: 'Programar próxima visita',  Icon: CalendarDays, grad: 'from-cyan-400 to-blue-500' },
                { to: '/clientes', label: 'Nuevo cliente',    sub: 'Añadir ficha al CRM',        Icon: Users,        grad: 'from-blue-500 to-indigo-500' },
                { to: '/catalogo', label: 'Añadir servicio',  sub: 'Gestionar catálogo',         Icon: Scissors,     grad: 'from-teal-400 to-cyan-500' },
              ].map(({ to, label, sub, Icon, grad }) => (
                <Link
                  key={to}
                  to={to}
                  className="group flex items-center gap-3 p-3 rounded-xl border border-slate-100 hover:border-blue-200 hover:bg-blue-50/30 transition text-left"
                >
                  <span className={`w-10 h-10 rounded-xl bg-gradient-to-br ${grad} flex items-center justify-center text-white shadow-[0_6px_16px_-6px_rgba(14,165,233,0.45)] shrink-0`}>
                    <Icon size={17} />
                  </span>
                  <span className="flex-1 min-w-0">
                    <span className="block text-sm font-semibold text-[#1e3a5f]">{label}</span>
                    <span className="block text-[11px] text-slate-400 truncate">{sub}</span>
                  </span>
                  <ArrowRight size={14} className="text-slate-300 group-hover:text-blue-500 transition-colors shrink-0" />
                </Link>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

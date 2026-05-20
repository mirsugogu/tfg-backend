import { useEffect, useMemo, useState } from 'react'
import { Plus, Search, CalendarDays, Clock, User, Scissors, MapPin, FilterX } from 'lucide-react'
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

// Degradado del bloque de fecha de cada tarjeta, según el estado.
const TINTS = {
  PENDING:     'from-amber-400 to-orange-500',
  CONFIRMED:   'from-blue-500 to-indigo-500',
  IN_PROGRESS: 'from-cyan-400 to-blue-500',
  COMPLETED:   'from-emerald-400 to-teal-500',
  CANCELLED:   'from-slate-400 to-slate-500',
  NO_SHOW:     'from-slate-400 to-slate-500',
}

/**
 * Citas — listado de citas con paginación real y filtros. El alta de
 * citas y el detalle/cambio de estado se delegan en los componentes
 * compartidos AppointmentWizard y AppointmentDetailModal (los mismos que
 * usa el Calendario).
 */
export default function Citas() {
  const { user } = useAuth()
  const { statuses, statusLabel } = useCatalog()
  const toast = useToast()
  const bId = user?.businessId

  // ---- Filtros backend-side (van en la query del GET) ----
  const [fromDate, setFromDate] = useState('')
  const [toDate, setToDate] = useState('')
  const [employeeFilter, setEmployeeFilter] = useState('')

  // Solo se incluyen las claves con valor: el backend rechaza un ?from=
  // vacío. El sort va FIJO a startDateTime,desc (campo real de la
  // entidad); la auditoría I.010 documenta que ordenar por un campo
  // inexistente devuelve 500, por eso el sort no es configurable.
  const listParams = useMemo(() => {
    const p = { sort: 'startDateTime,desc' }
    if (fromDate) p.from = fromDate
    if (toDate) p.to = toDate
    if (employeeFilter) p.membershipId = employeeFilter
    return p
  }, [fromDate, toDate, employeeFilter])

  const {
    items: appointments, page, totalPages, totalElements, loading, setPage, refresh,
  } = usePagedFetch(bId ? `/api/businesses/${bId}/appointments` : null, { params: listParams })

  // Empleados solo para el desplegable del filtro por empleado.
  const [employees, setEmployees] = useState([])
  useEffect(() => {
    if (!bId) return
    api.get(`/api/businesses/${bId}/users?size=100`)
      .then((r) => setEmployees(r.data.content))
      .catch((err) => toast({ type: 'error', message: getErrorMessage(err, 'No se pudieron cargar los empleados.') }))
  }, [bId, toast])

  // ---- Filtros client-side sobre la página visible ----
  const [search, setSearch] = useState('')
  const [filterStatus, setFilterStatus] = useState('')

  const visible = appointments.filter((a) => {
    const matchSearch = !search ||
      a.clientName?.toLowerCase().includes(search.toLowerCase()) ||
      a.userFullName?.toLowerCase().includes(search.toLowerCase())
    const matchStatus = !filterStatus || a.statusName === filterStatus
    return matchSearch && matchStatus
  })

  // Pestañas de estado: "Todas" + una por estado del catálogo dinámico.
  const statusTabs = [
    { key: '', label: 'Todas' },
    ...statuses.map((s) => ({ key: s.name, label: statusLabel(s.name) })),
  ]

  // ---- Modales compartidos ----
  const [wizardOpen, setWizardOpen] = useState(false)
  const [detailAppt, setDetailAppt] = useState(null)

  const clearFilters = () => { setFromDate(''); setToDate(''); setEmployeeFilter('') }
  const hasServerFilters = Boolean(fromDate || toDate || employeeFilter)

  return (
    <div className="p-8 max-w-7xl mx-auto">
      {/* Header */}
      <div className="mb-8 flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-[#1e3a5f] tracking-tight">Citas</h1>
          <p className="text-sm text-slate-500 mt-1.5 font-medium flex items-center gap-1.5">
            <CalendarDays size={14} />
            {loading ? '…' : `${totalElements} cita${totalElements === 1 ? '' : 's'} registrada${totalElements === 1 ? '' : 's'}`}
          </p>
        </div>
        <Button onClick={() => setWizardOpen(true)} className="gap-2">
          <Plus size={16} /> Nueva cita
        </Button>
      </div>

      {/* Filtros */}
      <div className="mb-6 space-y-3">
        <div className="flex flex-wrap gap-3 items-center">
          <div className="relative">
            <Search size={15} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Buscar cliente o empleado en esta página…"
              className="h-11 w-72 rounded-2xl border border-slate-200 bg-slate-50 pl-10 pr-3.5 text-sm text-[#1f2c4a] placeholder:text-slate-400 focus:outline-none focus:bg-white focus:border-blue-400 focus:ring-4 focus:ring-blue-100 transition-all"
            />
          </div>
          <div className="flex items-center gap-1 bg-slate-100 rounded-2xl p-1">
            {statusTabs.map(({ key, label }) => (
              <button
                key={key}
                onClick={() => setFilterStatus(key)}
                className={`text-xs font-semibold px-3 py-2 rounded-xl transition-all ${
                  filterStatus === key
                    ? 'bg-white text-[#1e3a5f] shadow-[0_1px_4px_rgba(15,23,42,0.08)]'
                    : 'text-slate-500 hover:text-[#1e3a5f]'
                }`}
              >
                {label}
              </button>
            ))}
          </div>
        </div>

        <div className="flex flex-wrap gap-3 items-end bg-white border border-slate-100 rounded-2xl px-4 py-3 shadow-[0_2px_12px_-2px_rgba(15,23,42,0.05)]">
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
          {hasServerFilters && (
            <Button variant="ghost" size="sm" onClick={clearFilters} className="gap-1.5">
              <FilterX size={14} /> Limpiar
            </Button>
          )}
          <span className="ml-auto text-[11px] text-slate-400 font-medium pb-2.5">
            El rango de fechas y el empleado filtran todas las citas
          </span>
        </div>
      </div>

      {/* Listado */}
      <div className="space-y-3">
        {loading ? (
          [...Array(5)].map((_, i) => (
            <div key={i} className="h-24 rounded-2xl bg-white border border-slate-100 animate-pulse" />
          ))
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
            Sin resultados en esta página con ese filtro.
          </div>
        ) : (
          visible.map((a) => (
            <div
              key={a.id}
              className="bg-white rounded-2xl border border-slate-100/80 shadow-[0_2px_12px_-2px_rgba(15,23,42,0.06)] p-5 hover:shadow-[0_4px_16px_-4px_rgba(15,23,42,0.1)] hover:border-blue-200/60 transition-all cursor-pointer"
              onClick={() => setDetailAppt(a)}
            >
              <div className="flex items-center gap-5">
                <div className={`flex flex-col items-center justify-center w-16 h-16 rounded-2xl bg-gradient-to-br ${TINTS[a.statusName] || 'from-cyan-400 to-blue-500'} shrink-0 shadow-[0_8px_20px_-8px_rgba(14,165,233,0.4)]`}>
                  <span className="text-2xl font-bold text-white leading-none">
                    {new Date(a.startDateTime).getDate()}
                  </span>
                  <span className="text-[10px] text-white/80 uppercase font-semibold tracking-wide">
                    {new Date(a.startDateTime).toLocaleDateString('es-ES', { month: 'short' })}
                  </span>
                </div>

                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2.5 mb-1.5">
                    <p className="font-bold text-[#1e3a5f] text-base">{a.clientName}</p>
                    <StatusBadge status={a.statusName} />
                    {a.isPaid && <Badge variant="success">Pagado</Badge>}
                  </div>
                  <div className="flex flex-wrap gap-4 text-xs text-slate-400 font-medium">
                    <span className="flex items-center gap-1.5"><Clock size={12} />{formatDateTime(a.startDateTime)}</span>
                    <span className="flex items-center gap-1.5"><User size={12} />{a.userFullName}</span>
                    <span className="flex items-center gap-1.5"><Scissors size={12} />{a.bookedServices?.length ?? 0} servicio(s)</span>
                    {a.boothName && <span className="flex items-center gap-1.5"><MapPin size={12} />{a.boothName}</span>}
                  </div>
                </div>

                <div className="shrink-0 text-right">
                  <p className="text-xl font-bold text-[#1e3a5f]">{totalBooked(a.bookedServices)} €</p>
                  <p className="text-[11px] text-slate-400 mt-0.5">Ver detalle</p>
                </div>
              </div>
            </div>
          ))
        )}
      </div>

      {totalPages > 1 && (
        <div className="mt-6 bg-white rounded-2xl border border-slate-100 overflow-hidden shadow-[0_2px_12px_-2px_rgba(15,23,42,0.06)]">
          <Pagination page={page} totalPages={totalPages} totalElements={totalElements} onChange={setPage} />
        </div>
      )}

      {/* Modales compartidos con el Calendario */}
      <AppointmentWizard
        open={wizardOpen}
        onClose={() => setWizardOpen(false)}
        onCreated={refresh}
        bId={bId}
      />
      <AppointmentDetailModal
        appointment={detailAppt}
        bId={bId}
        onClose={() => setDetailAppt(null)}
        onChanged={refresh}
      />
    </div>
  )
}

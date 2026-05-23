import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  Plus, Search, Pencil, Trash2, Phone, Mail, Shield, User,
  Calendar, Clock, UserMinus, Users, X, RefreshCw, ChevronRight, ArchiveRestore,
} from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Input, INPUT_SANITIZE } from '@/components/ui/Input'
import { Select } from '@/components/ui/Select'
import { Modal } from '@/components/ui/Modal'
import { Pagination } from '@/components/ui/Pagination'
import { EmptyState } from '@/components/ui/EmptyState'
import { useAuth } from '@/context/AuthContext'
import { useCatalog } from '@/context/CatalogContext'
import { useToast } from '@/components/ui/Toast'
import { usePagedFetch } from '@/hooks/usePagedFetch'
import api, { getErrorMessage } from '@/lib/api'
import { totalBooked } from '@/lib/format'
import { employeeDotClass } from '@/lib/employeeColor'

/* ============================================================
   CONSTANTES Y HELPERS
   ============================================================ */

const DAYS_SHORT = ['', 'L', 'M', 'X', 'J', 'V', 'S', 'D']
const DAYS_FULL = ['', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo']
const ACTIVE_STATUSES = ['PENDING', 'CONFIRMED', 'IN_PROGRESS']

const AVATAR_COLORS = [
  'from-cyan-400 to-blue-500',
  'from-amber-400 to-orange-500',
  'from-emerald-400 to-teal-500',
  'from-indigo-400 to-violet-500',
  'from-pink-400 to-rose-500',
  'from-sky-400 to-blue-500',
]
const avatarColor = (id) => AVATAR_COLORS[(id ?? 0) % AVATAR_COLORS.length]

const empty = { fullName: '', email: '', phone: '', password: '', roleId: '', color: '' }

// Paleta fija para el color del empleado en el calendario. Los nombres
// coinciden con PALETTES de components/calendar/utils.js; varias personas
// pueden compartir color a proposito.
const COLOR_OPTIONS = [
  { name: 'cyan', cls: 'bg-cyan-500' }, { name: 'amber', cls: 'bg-amber-500' },
  { name: 'emerald', cls: 'bg-emerald-500' }, { name: 'indigo', cls: 'bg-indigo-500' },
  { name: 'pink', cls: 'bg-pink-500' }, { name: 'sky', cls: 'bg-sky-500' },
  { name: 'violet', cls: 'bg-violet-500' }, { name: 'teal', cls: 'bg-teal-500' },
]
const emptySchedule = { dayOfWeek: '1', startTime: '09:00', endTime: '18:00' }
const emptyAbsence = { startDateTime: '', endDateTime: '', reason: '' }

const fmtAbsence = (dt) =>
  new Date(dt).toLocaleDateString('es-ES', { day: '2-digit', month: 'short', year: 'numeric' })

const fmtSinceShort = (iso) =>
  iso
    ? new Date(iso).toLocaleDateString('es-ES', { month: 'short', year: 'numeric' }).replace('.', '')
    : '—'

const fmtSinceLong = (iso) =>
  iso
    ? new Date(iso).toLocaleDateString('es-ES', { day: '2-digit', month: 'long', year: 'numeric' })
    : '—'

const ymd = (d) => {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const da = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${da}`
}

const todayDow = () => {
  const d = new Date().getDay()
  return d === 0 ? 7 : d
}

const telHref = (phone) => 'tel:' + String(phone || '').replace(/\s/g, '')

/* ============================================================
   EMPLEADOS — Lista
   ============================================================ */

export default function Empleados() {
  const { user } = useAuth()
  const { roles, roleLabel } = useCatalog()
  const toast = useToast()
  const bId = user?.businessId
  const isAdmin = user?.role === 'ADMIN'

  // Persistencia ligera de preferencias
  const [pageSize, setPageSize] = useState(() => parseInt(localStorage.getItem('optima_emp_size') || '20', 10))
  useEffect(() => { localStorage.setItem('optima_emp_size', String(pageSize)) }, [pageSize])

  // El endpoint de usuarios pagina memberships: el nombre se ordena por el
  // path anidado 'user.fullName' (el campo fullName vive en la entidad User,
  // no en Membership). Se descarta el 'fullName' suelto que guardaban
  // versiones anteriores del frontend, que el backend rechaza con 400.
  const [sortKey, setSortKey] = useState(() => {
    const stored = localStorage.getItem('optima_emp_sortkey')
    return stored === 'createdAt' ? stored : 'user.fullName'
  })
  const [sortDir, setSortDir] = useState(() => localStorage.getItem('optima_emp_sortdir') || 'asc')
  useEffect(() => { localStorage.setItem('optima_emp_sortkey', sortKey) }, [sortKey])
  useEffect(() => { localStorage.setItem('optima_emp_sortdir', sortDir) }, [sortDir])

  const onSortChange = (value) => {
    // value formato "fullName,asc" — el select envía las dos partes juntas.
    const [k, d] = value.split(',')
    setSortKey(k); setSortDir(d)
  }

  // Vista activos / archivados — sin persistir (cada visita empieza en activos)
  const [view, setView] = useState('active') // 'active' | 'archived'
  const isArchived = view === 'archived'

  // Filtros que viajan al backend: ordenación + flag de soft-delete.
  const queryParams = useMemo(
    () => ({ sort: `${sortKey},${sortDir}`, active: view === 'active' }),
    [sortKey, sortDir, view],
  )

  // Listado: se cargan TODOS los empleados (size=100, tope del backend) para
  // que la búsqueda y la paginación operen sobre el conjunto completo.
  const { items: employees, totalElements, loading, refresh } =
    usePagedFetch(bId ? `/api/businesses/${bId}/users` : null, { size: 100, params: queryParams })

  const [search, setSearch] = useState('')
  const [roleFilter, setRoleFilter] = useState('ALL') // ALL | ADMIN | EMPLOYEE
  const [drawer, setDrawer] = useState(null) // empleado seleccionado para el drawer
  const [modal, setModal] = useState(null)
  const [selected, setSelected] = useState(null)
  const [form, setForm] = useState(empty)
  const [saving, setSaving] = useState(false)

  // Cierra el drawer con Esc
  useEffect(() => {
    if (!drawer) return
    const handler = (e) => { if (e.key === 'Escape') setDrawer(null) }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [drawer])

  const filtered = employees
    .filter((e) => roleFilter === 'ALL' || e.roleName === roleFilter)
    .filter((e) =>
      e.fullName?.toLowerCase().includes(search.toLowerCase()) ||
      e.email?.toLowerCase().includes(search.toLowerCase())
    )

  // Paginación en cliente sobre el resultado ya filtrado.
  const [page, setPage] = useState(0)
  const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize))
  const safePage = Math.min(page, totalPages - 1)
  const paged = filtered.slice(safePage * pageSize, safePage * pageSize + pageSize)

  const defaultRoleId = () => roles.find((r) => r.name === 'EMPLOYEE')?.id ?? roles[0]?.id ?? ''

  const openCreate = () => { setForm({ ...empty, roleId: defaultRoleId() }); setSelected(null); setModal('create') }
  const openEditRole = (e) => {
    setSelected(e)
    setForm({
      fullName: e.fullName,
      email:    e.email,
      phone:    e.phone || '',
      password: '',
      roleId:   e.roleId,
      color:    e.color || '',
    })
    setModal('edit')
  }
  // Conteo de citas proximas del empleado seleccionado para el aviso del modal
  // de desactivacion (E del audit). null mientras carga, numero cuando llega.
  // Si la consulta falla, se asume 0 para no bloquear la acción por un error
  // ortogonal (el backend tiene la red final si hay alguna inconsistencia).
  //
  // Cancelacion: si el admin cierra el modal antes de que llegue la respuesta,
  // o abre el modal de otro empleado mientras la consulta esta en vuelo, se
  // aborta para evitar (a) un warning de setState sobre estado huerfano,
  // (b) que se vea brevemente el conteo del empleado anterior.
  const [upcomingInfo, setUpcomingInfo] = useState({ loading: false, count: null })
  const upcomingAbortRef = useRef(null)

  const openDeactivate = async (e) => {
    upcomingAbortRef.current?.abort()
    const controller = new AbortController()
    upcomingAbortRef.current = controller

    setSelected(e)
    setModal('deactivate')
    setUpcomingInfo({ loading: true, count: null })

    const t = new Date()
    const ymd = `${t.getFullYear()}-${String(t.getMonth() + 1).padStart(2, '0')}-${String(t.getDate()).padStart(2, '0')}`
    try {
      const r = await api.get(`/api/businesses/${bId}/appointments`, {
        params: { membershipId: e.id, from: ymd, size: 1 },
        signal: controller.signal,
      })
      if (controller.signal.aborted) return
      setUpcomingInfo({ loading: false, count: r.data.totalElements ?? 0 })
    } catch (err) {
      // axios marca las cancelaciones como CanceledError; las ignoramos
      // porque significan "el admin cerro el modal antes que llegara".
      if (err?.name === 'CanceledError' || err?.code === 'ERR_CANCELED') return
      setUpcomingInfo({ loading: false, count: 0 })
    }
  }
  const closeModal = () => {
    // Cancela la consulta de citas proximas si quedaba alguna en vuelo y
    // resetea upcomingInfo, asi al reabrir el modal con otro empleado no se
    // ve por un instante el conteo del anterior.
    upcomingAbortRef.current?.abort()
    setUpcomingInfo({ loading: false, count: null })
    setModal(null)
    setSelected(null)
  }

  const handleChange = (e) => setForm((p) => ({ ...p, [e.target.name]: e.target.value }))

  const handleSave = async () => {
    if (modal === 'create' && (!form.fullName.trim() || !form.email.trim())) {
      toast({ type: 'error', message: 'Nombre y email son obligatorios.' }); return
    }
    if (!form.roleId) {
      toast({ type: 'error', message: 'Selecciona un rol.' }); return
    }
    if (modal === 'create' && !form.password) {
      toast({ type: 'error', message: 'La contraseña es obligatoria.' }); return
    }
    if (modal === 'create' && form.password.length < 8) {
      toast({ type: 'error', message: 'La contraseña debe tener al menos 8 caracteres.' }); return
    }
    setSaving(true)
    try {
      if (modal === 'create') {
        await api.post(`/api/businesses/${bId}/users`, {
          fullName: form.fullName,
          email:    form.email,
          phone:    form.phone || null,
          password: form.password,
          roleId:   Number(form.roleId),
        })
        toast({ type: 'success', message: 'Empleado creado correctamente.' })
      } else {
        // El PUT gobierna la "pieza local" de la membership: rol + color.
        // Nombre/email/teléfono son de la identidad: se editan en /perfil.
        await api.put(`/api/businesses/${bId}/users/${selected.id}`, {
          roleId: Number(form.roleId),
          color: form.color || null,
        })
        toast({ type: 'success', message: 'Cambios guardados.' })
      }
      closeModal()
      refresh()
      // si el drawer estaba abierto sobre este, refresca también su rol
      if (drawer?.id === selected?.id) setDrawer(null)
    } catch (err) {
      toast({ type: 'error', message: getErrorMessage(err, 'Error al guardar.') })
    } finally {
      setSaving(false)
    }
  }

  const handleDeactivate = async () => {
    setSaving(true)
    try {
      await api.delete(`/api/businesses/${bId}/users/${selected.id}`)
      toast({ type: 'success', message: 'Empleado desactivado.' })
      closeModal()
      if (drawer?.id === selected.id) setDrawer(null)
      refresh()
    } catch (err) {
      toast({ type: 'error', message: getErrorMessage(err, 'Error al desactivar el empleado.') })
    } finally {
      setSaving(false)
    }
  }

  // Reactivar es un clic directo (no destructivo): sin modal de confirmación.
  const handleReactivate = async (emp) => {
    try {
      await api.patch(`/api/businesses/${bId}/users/${emp.id}/reactivate`)
      toast({ type: 'success', message: 'Empleado reactivado.' })
      if (drawer?.id === emp.id) setDrawer(null)
      refresh()
    } catch (err) {
      toast({ type: 'error', message: getErrorMessage(err, 'Error al reactivar el empleado.') })
    }
  }

  /* ---------- Render ---------- */

  return (
    <div className="px-4 sm:px-6 lg:px-8 xl:px-10 py-8">

      {/* Header */}
      <div className="mb-7 flex items-start justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold text-[#1e3a5f] tracking-tight">Empleados</h1>
          <p className="text-sm text-slate-500 mt-1.5 font-medium flex items-center gap-1.5">
            <Users size={14} />
            {loading
              ? '…'
              : isArchived
                ? `${totalElements} usuario${totalElements === 1 ? '' : 's'} desactivado${totalElements === 1 ? '' : 's'}`
                : `${totalElements} usuario${totalElements === 1 ? '' : 's'} en plantilla`}
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
          {isAdmin && (
            <Button onClick={openCreate} className="gap-2" disabled={!roles.length}>
              <Plus size={16} /> Nuevo empleado
            </Button>
          )}
        </div>
      </div>

      {/* Toolbar */}
      <div className="mb-5 flex flex-wrap items-center gap-3">
        <div className="relative flex-1 min-w-[240px] max-w-md">
          <Search size={15} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Buscar por nombre o email…"
            className="h-11 w-full rounded-xl border border-slate-200 bg-white pl-10 pr-3.5 text-sm text-[#1f2c4a] placeholder:text-slate-400 focus:outline-none focus:border-blue-400 focus:ring-4 focus:ring-blue-100 transition-all"
          />
        </div>

        {/* Filtro por rol */}
        <div className="inline-flex items-center bg-slate-100 rounded-xl p-1">
          {[
            { key: 'ALL',      label: 'Todos' },
            { key: 'ADMIN',    label: 'Admins' },
            { key: 'EMPLOYEE', label: 'Empleados' },
          ].map(({ key, label }) => (
            <button
              key={key}
              type="button"
              onClick={() => setRoleFilter(key)}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition ${
                roleFilter === key
                  ? 'bg-white text-[#1e3a5f] shadow-[0_1px_4px_rgba(15,23,42,0.08)]'
                  : 'text-slate-500 hover:text-[#1e3a5f]'
              }`}
            >
              {label}
            </button>
          ))}
        </div>

        {/* Vista activos / archivados */}
        <div className="inline-flex items-center bg-slate-100 rounded-xl p-1">
          {[{ key: 'active', label: 'Activos' }, { key: 'archived', label: 'Desactivados' }].map(({ key, label }) => (
            <button key={key} type="button" onClick={() => setView(key)}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition ${
                view === key ? 'bg-white text-[#1e3a5f] shadow-[0_1px_4px_rgba(15,23,42,0.08)]' : 'text-slate-500 hover:text-[#1e3a5f]'
              }`}>{label}</button>
          ))}
        </div>

        {/* Ordenación */}
        <select
          value={`${sortKey},${sortDir}`}
          onChange={(e) => onSortChange(e.target.value)}
          className="h-11 rounded-xl border border-slate-200 bg-white px-3 text-sm font-semibold text-[#1e3a5f] focus:outline-none focus:border-blue-400 focus:ring-4 focus:ring-blue-100 transition"
        >
          <option value="user.fullName,asc">Nombre A → Z</option>
          <option value="user.fullName,desc">Nombre Z → A</option>
          <option value="createdAt,desc">Más recientes</option>
          <option value="createdAt,asc">Más antiguos</option>
        </select>

        {/* Tamaño de página */}
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

      {/* Cards grid */}
      <div className="card-grid">
        {loading ? (
          [...Array(4)].map((_, i) => (
            <div key={i} className="h-40 bg-white rounded-2xl border border-slate-100 animate-pulse" />
          ))
        ) : filtered.length === 0 ? (
          <div className="col-span-full">
            {search || roleFilter !== 'ALL' ? (
              <p className="py-20 text-center text-slate-400">
                Sin resultados con estos filtros.
              </p>
            ) : isArchived ? (
              <EmptyState
                icon={Users}
                title="No hay empleados desactivados"
                description="Los empleados que desactives aparecerán aquí para que puedas reactivarlos."
              />
            ) : (
              <EmptyState
                icon={Users}
                title="Aún no hay empleados"
                description="Da de alta a tu equipo para asignarles horarios y citas."
                actionLabel={isAdmin ? 'Nuevo empleado' : undefined}
                onAction={isAdmin ? openCreate : undefined}
              />
            )}
          </div>
        ) : (
          paged.map((e) => (
            <EmployeeCard
              key={e.id}
              emp={e}
              onOpen={() => setDrawer(e)}
              archived={isArchived}
              showReactivate={isArchived && isAdmin}
              onReactivate={() => handleReactivate(e)}
            />
          ))
        )}
      </div>

      {/* Paginación */}
      <div className="mt-6 rounded-2xl border border-slate-100 overflow-hidden bg-white">
        <Pagination
          page={safePage}
          totalPages={totalPages}
          totalElements={filtered.length}
          onChange={setPage}
        />
      </div>

      {/* Drawer */}
      {drawer && (
        <EmployeeDrawer
          key={drawer.id}
          emp={drawer}
          bId={bId}
          isAdmin={isAdmin}
          archived={isArchived}
          onClose={() => setDrawer(null)}
          onEditRole={() => openEditRole(drawer)}
          onDeactivate={() => openDeactivate(drawer)}
          onReactivate={() => handleReactivate(drawer)}
          onAfterChange={refresh}
        />
      )}

      {/* Modal crear / editar rol */}
      <Modal
        open={modal === 'create' || modal === 'edit'}
        onClose={closeModal}
        title={modal === 'create' ? 'Nuevo empleado' : 'Cambiar rol del empleado'}
      >
        <div className="space-y-4">
          {modal === 'edit' && (
            <div className="rounded-2xl bg-slate-50 border border-slate-100 px-4 py-3 text-xs text-slate-500">
              Para cambiar el nombre, email o teléfono el propio usuario debe editarlo en su perfil.
              Desde aquí solo puedes actualizar el rol.
            </div>
          )}
          <Input
            label="Nombre completo *"
            name="fullName"
            value={form.fullName}
            onChange={handleChange}
            placeholder="Nombre y apellidos"
            disabled={modal === 'edit'}
            maxLength={150}
          />
          <Input
            label="Email *"
            name="email"
            type="email"
            value={form.email}
            onChange={handleChange}
            placeholder="empleado@empresa.com"
            disabled={modal === 'edit'}
            maxLength={150}
          />
          <Input
            label="Teléfono"
            name="phone"
            value={form.phone}
            onChange={handleChange}
            placeholder="600 000 000"
            disabled={modal === 'edit'}
            sanitize={INPUT_SANITIZE.PHONE}
            inputMode="tel"
            maxLength={20}
          />
          {modal === 'create' && (
            <Input
              label="Contraseña *"
              name="password"
              type="password"
              value={form.password}
              onChange={handleChange}
              placeholder="Mínimo 8 caracteres"
              maxLength={100}
            />
          )}
          <Select label="Rol *" name="roleId" value={form.roleId} onChange={handleChange}>
            <option value="">Selecciona rol</option>
            {roles.map((r) => (
              <option key={r.id} value={r.id}>{roleLabel(r.name)}</option>
            ))}
          </Select>
          {modal === 'edit' && (
            <div>
              <label className="block text-sm font-semibold text-slate-700 mb-1.5">Color en el calendario</label>
              <div className="flex flex-wrap items-center gap-2">
                <button
                  type="button"
                  onClick={() => setForm((p) => ({ ...p, color: '' }))}
                  title="Sin color — el calendario usa uno automático"
                  className={`h-8 px-2 rounded-lg border-2 text-[10px] font-bold text-slate-500 transition ${form.color === '' ? 'border-[#1e3a5f]' : 'border-slate-200 hover:border-slate-300'}`}
                >
                  Auto
                </button>
                {COLOR_OPTIONS.map(({ name, cls }) => (
                  <button
                    key={name}
                    type="button"
                    onClick={() => setForm((p) => ({ ...p, color: name }))}
                    title={name}
                    aria-label={`Color ${name}`}
                    className={`h-8 w-8 rounded-lg ${cls} ring-2 ring-offset-2 transition ${form.color === name ? 'ring-[#1e3a5f]' : 'ring-transparent hover:ring-slate-300'}`}
                  />
                ))}
              </div>
              <p className="text-xs text-slate-400 mt-1.5">Para diferenciar sus citas de un vistazo. Varios empleados pueden compartir color.</p>
            </div>
          )}
          <div className="flex gap-3 pt-2">
            <Button variant="outline" onClick={closeModal} className="flex-1">Cancelar</Button>
            <Button onClick={handleSave} loading={saving} className="flex-1">
              {modal === 'create' ? 'Crear empleado' : 'Guardar cambios'}
            </Button>
          </div>
        </div>
      </Modal>

      {/* Modal desactivar */}
      <Modal open={modal === 'deactivate'} onClose={closeModal} title="Desactivar empleado" size="sm">
        <div className="space-y-5">
          {/* Aviso base con tono segun el conteo: si tiene citas proximas,
              fondo rose con explicacion del impacto; si no, amber neutro. */}
          {upcomingInfo.count != null && upcomingInfo.count > 0 ? (
            <div className="flex items-start gap-3 rounded-2xl bg-rose-50 border border-rose-200 px-4 py-4">
              <UserMinus size={20} className="text-rose-600 shrink-0 mt-0.5" />
              <div className="text-sm text-rose-800 leading-snug space-y-1.5">
                <p>
                  <strong>{selected?.fullName}</strong> tiene <strong>{upcomingInfo.count} cita{upcomingInfo.count === 1 ? '' : 's'}</strong> de hoy en adelante.
                </p>
                <p className="text-rose-700">
                  Al desactivarlo no podrá iniciar sesión ni asignarse a nuevas citas. Las existentes
                  <strong> no se cancelan automáticamente</strong>: revísalas en <em>Citas</em>
                  (filtra por este empleado) y decide si reasignarlas o cancelarlas.
                </p>
              </div>
            </div>
          ) : (
            <div className="flex items-start gap-3 rounded-2xl bg-amber-50 border border-amber-100 px-4 py-4">
              <UserMinus size={20} className="text-amber-600 shrink-0 mt-0.5" />
              <p className="text-sm text-amber-800 leading-snug">
                {upcomingInfo.loading ? (
                  <>Comprobando citas próximas de <strong>{selected?.fullName}</strong>…</>
                ) : (
                  <>¿Desactivar a <strong>{selected?.fullName}</strong>? Dejará de poder iniciar sesión y
                  no podrá asignarse a nuevas citas, pero seguirá presente en el historial.</>
                )}
              </p>
            </div>
          )}
          <div className="flex gap-3">
            <Button variant="outline" onClick={closeModal} className="flex-1">Cancelar</Button>
            <Button
              variant="danger"
              onClick={handleDeactivate}
              loading={saving}
              disabled={upcomingInfo.loading}
              className="flex-1"
            >
              {upcomingInfo.count > 0 ? 'Desactivar de todos modos' : 'Desactivar'}
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  )
}

/* ============================================================
   EMPLOYEE CARD
   ============================================================ */

function EmployeeCard({ emp, onOpen, showReactivate, onReactivate }) {
  const isAdmin = emp.roleName === 'ADMIN'
  // Color de identidad visual del empleado, coherente con el calendario:
  // si el admin le asigno un color (memberships.color), se usa solido para
  // el avatar y la barra superior; si no, se mantiene el gradient automatico
  // decorativo. Asi un mismo empleado se reconoce en Empleados, Citas,
  // DetailModal y Calendario por el mismo color.
  const dotBg = emp.color ? employeeDotClass(emp) : null
  const avatarClass = dotBg
    ? dotBg
    : `bg-gradient-to-br ${avatarColor(emp.id)}`
  const stripClass = dotBg
    ? dotBg
    : `bg-gradient-to-r ${avatarColor(emp.id)}`
  return (
    <div
      onClick={onOpen}
      className="group bg-white rounded-2xl border border-slate-100/80 shadow-[0_2px_12px_-2px_rgba(15,23,42,0.06)] overflow-hidden hover:shadow-[0_12px_40px_-12px_rgba(15,23,42,0.15)] hover:border-blue-200 transition cursor-pointer"
    >
      <div className={`h-1 w-full ${stripClass}`} />
      <div className="p-5">
        <div className="flex items-start gap-3.5">
          <div className={`flex h-14 w-14 shrink-0 items-center justify-center rounded-2xl ${avatarClass} text-white font-bold text-lg shadow-[0_6px_16px_-6px_rgba(14,165,233,0.4)]`}>
            {emp.fullName?.[0]?.toUpperCase()}
          </div>
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-2 flex-wrap">
              <p className="font-bold text-[#1e3a5f] truncate">{emp.fullName}</p>
              {isAdmin
                ? <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wider bg-[#1e3a5f] text-white shrink-0"><Shield size={10} /> Admin</span>
                : <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wider bg-cyan-50 text-cyan-700 ring-1 ring-cyan-100 shrink-0"><User size={10} /> Empleado</span>
              }
            </div>
            <p className="text-[11px] text-slate-400 mt-1 inline-flex items-center gap-1 capitalize">
              <Calendar size={11} /> Empleado desde {fmtSinceShort(emp.createdAt)}
            </p>
          </div>
          <ChevronRight size={16} className="text-slate-300 group-hover:text-blue-500 transition shrink-0" />
        </div>

        <div className="mt-3 pt-3 border-t border-slate-50 flex flex-wrap gap-2" onClick={(e) => e.stopPropagation()}>
          {emp.email && (
            <a
              href={`mailto:${emp.email}`}
              className="inline-flex items-center gap-1.5 text-xs font-medium px-2.5 py-1.5 rounded-lg bg-blue-50/50 text-blue-700 hover:bg-blue-100 transition truncate max-w-full"
            >
              <Mail size={12} /> {emp.email}
            </a>
          )}
          {emp.phone ? (
            <a
              href={telHref(emp.phone)}
              className="inline-flex items-center gap-1.5 text-xs font-medium font-mono px-2.5 py-1.5 rounded-lg bg-slate-50 text-slate-700 hover:bg-slate-100 transition"
            >
              <Phone size={12} /> {emp.phone}
            </a>
          ) : (
            <span className="text-xs text-slate-300 italic">Sin teléfono</span>
          )}
        </div>

        {showReactivate && (
          <div className="mt-3 pt-3 border-t border-slate-50" onClick={(e) => e.stopPropagation()}>
            <Button variant="success" size="sm" onClick={onReactivate} className="w-full gap-2">
              <ArchiveRestore size={14} /> Reactivar
            </Button>
          </div>
        )}
      </div>
    </div>
  )
}

/* ============================================================
   EMPLOYEE DRAWER — carga horarios + ausencias + citas del mes
   ============================================================ */

function EmployeeDrawer({ emp, bId, isAdmin, archived, onClose, onEditRole, onDeactivate, onReactivate, onAfterChange }) {
  const toast = useToast()
  const empUrl = `/api/businesses/${bId}/users/${emp.id}`

  // ---- Schedules ----
  const [schedules, setSchedules] = useState(null)
  const [schedReload, setSchedReload] = useState(0)
  useEffect(() => {
    api.get(`${empUrl}/schedules`)
      .then((r) => setSchedules(r.data))
      .catch((err) => {
        toast({ type: 'error', message: getErrorMessage(err, 'Error al cargar horarios.') })
        setSchedules([])
      })
  }, [empUrl, schedReload, toast])
  const refreshSchedules = useCallback(() => setSchedReload((v) => v + 1), [])

  // ---- Absences (paginadas, size 10) ----
  const {
    items: absences,
    page: absPage,
    totalPages: absTotalPages,
    totalElements: absTotal,
    loading: absLoading,
    setPage: setAbsPage,
    refresh: refreshAbsences,
  } = usePagedFetch(`${empUrl}/absences`, { size: 10 })

  // ---- KPI del mes: citas + ingresos + horas ----
  // Una sola llamada a /appointments filtrada por membershipId. El backend
  // cappea Pageable en 100; si un empleado superara 100 citas en un mes el
  // KPI saldria parcial (improbable para un empleado individual).
  const [kpi, setKpi] = useState(null)
  useEffect(() => {
    const from = ymd(new Date(new Date().getFullYear(), new Date().getMonth(), 1))
    const to   = ymd(new Date())
    api.get(`/api/businesses/${bId}/appointments`, {
      params: { from, to, membershipId: emp.id, size: 100 },
    })
      .then((r) => {
        const list = r.data.content ?? []
        const considered = list.filter((a) => a.statusName !== 'CANCELLED' && a.statusName !== 'NO_SHOW')
        const citas = considered.length
        const ingresos = considered.reduce((acc, a) => acc + parseFloat(totalBooked(a.bookedServices)), 0)
        const minutos = considered.reduce(
          (acc, a) => acc + (new Date(a.endDateTime) - new Date(a.startDateTime)) / 60000,
          0,
        )
        setKpi({ citas, ingresos, horas: Math.round(minutos / 60) })
      })
      .catch(() => setKpi({ citas: 0, ingresos: 0, horas: 0 }))
  }, [bId, emp.id])

  // ---- Estado del empleado HOY ----
  const dow = todayDow()
  const todaySch = (schedules ?? []).find((s) => s.dayOfWeek === dow)
  const now = new Date()
  const currentAbsence = absences.find(
    (a) => new Date(a.startDateTime) <= now && now <= new Date(a.endDateTime),
  )
  const status = currentAbsence
    ? { kind: 'absent',
        label: `Ausente hasta ${fmtAbsence(currentAbsence.endDateTime)}`,
        cls:   'bg-amber-50 text-amber-700 ring-amber-200',
        dot:   'bg-amber-500' }
    : todaySch
      ? { kind: 'available',
          label: `Disponible · ${todaySch.startTime?.slice(0,5)}–${todaySch.endTime?.slice(0,5)}`,
          cls:   'bg-emerald-50 text-emerald-700 ring-emerald-200',
          dot:   'bg-emerald-500' }
      : { kind: 'off',
          label: 'Descansa hoy',
          cls:   'bg-slate-100 text-slate-600 ring-slate-200',
          dot:   'bg-slate-400' }

  // ---- Modales de schedule/absence (mismos que el original) ----
  const [scheduleModal, setScheduleModal]   = useState(null) // create | edit | delete | copyweek
  const [absenceModal, setAbsenceModal]     = useState(null)
  const [selectedSch, setSelectedSch]       = useState(null)
  const [selectedAbs, setSelectedAbs]       = useState(null)
  const [scheduleForm, setScheduleForm]     = useState(emptySchedule)
  const [absenceForm, setAbsenceForm]       = useState(emptyAbsence)
  const [savingChild, setSavingChild]       = useState(false)

  const closeSched = () => { setScheduleModal(null); setSelectedSch(null) }
  const closeAbs   = () => { setAbsenceModal(null); setSelectedAbs(null) }

  // Schedule open helpers
  const openSchedCreate = () => { setSelectedSch(null); setScheduleForm({ ...emptySchedule, dayOfWeek: String(dow) }); setScheduleModal('create') }
  const openSchedEdit = (s) => {
    setSelectedSch(s)
    setScheduleForm({
      dayOfWeek: String(s.dayOfWeek),
      startTime: s.startTime?.slice(0, 5) || '09:00',
      endTime:   s.endTime?.slice(0, 5)   || '18:00',
    })
    setScheduleModal('edit')
  }
  const openSchedDelete = (s) => { setSelectedSch(s); setScheduleModal('delete') }

  // Quick action: clona los tramos del lunes en martes-viernes. Sobrescribe:
  // borra antes lo que cada dia tenga, porque el backend valida solapes al
  // crear (POST 409), asi que hay que vaciar el dia antes de copiar.
  const handleCopyMonToWeek = async () => {
    const all = schedules ?? []
    const mondayTramos = all.filter((s) => s.dayOfWeek === 1)
    if (mondayTramos.length === 0) {
      toast({ type: 'error', message: 'Configura primero el horario del lunes.' })
      return
    }
    setSavingChild(true)
    try {
      for (let d = 2; d <= 5; d++) {
        for (const s of all.filter((x) => x.dayOfWeek === d)) {
          await api.delete(`${empUrl}/schedules/${s.id}`)
        }
        for (const m of mondayTramos) {
          await api.post(`${empUrl}/schedules`, {
            dayOfWeek: d,
            startTime: m.startTime,
            endTime: m.endTime,
          })
        }
      }
      toast({ type: 'success', message: 'Horario del lunes copiado a martes-viernes.' })
      closeSched()
      refreshSchedules()
    } catch (err) {
      toast({ type: 'error', message: getErrorMessage(err, 'No se pudo copiar el horario.') })
      refreshSchedules()
    } finally {
      setSavingChild(false)
    }
  }

  const handleSchedSave = async () => {
    if (scheduleForm.startTime >= scheduleForm.endTime) {
      toast({ type: 'error', message: 'La hora de fin debe ser posterior a la de inicio.' }); return
    }
    setSavingChild(true)
    try {
      const payload = {
        dayOfWeek: parseInt(scheduleForm.dayOfWeek, 10),
        startTime: scheduleForm.startTime + ':00',
        endTime:   scheduleForm.endTime + ':00',
      }
      if (scheduleModal === 'create') {
        await api.post(`${empUrl}/schedules`, payload)
        toast({ type: 'success', message: 'Horario añadido.' })
      } else {
        await api.put(`${empUrl}/schedules/${selectedSch.id}`, payload)
        toast({ type: 'success', message: 'Horario actualizado.' })
      }
      closeSched()
      refreshSchedules()
    } catch (err) {
      toast({ type: 'error', message: getErrorMessage(err, 'Error al guardar.') })
    } finally { setSavingChild(false) }
  }
  const handleSchedDelete = async () => {
    setSavingChild(true)
    try {
      await api.delete(`${empUrl}/schedules/${selectedSch.id}`)
      toast({ type: 'success', message: 'Horario eliminado.' })
      closeSched()
      refreshSchedules()
    } catch (err) {
      toast({ type: 'error', message: getErrorMessage(err, 'Error al eliminar.') })
    } finally { setSavingChild(false) }
  }

  // Absence helpers
  const openAbsCreate = () => { setSelectedAbs(null); setAbsenceForm(emptyAbsence); setAbsenceModal('create') }
  const openAbsEdit = (a) => {
    setSelectedAbs(a)
    setAbsenceForm({
      startDateTime: a.startDateTime?.slice(0, 16) || '',
      endDateTime:   a.endDateTime?.slice(0, 16)   || '',
      reason:        a.reason || '',
    })
    setAbsenceModal('edit')
  }
  const openAbsDelete = (a) => { setSelectedAbs(a); setAbsenceModal('delete') }
  const handleAbsSave = async () => {
    const { startDateTime, endDateTime, reason } = absenceForm
    if (!startDateTime || !endDateTime) {
      toast({ type: 'error', message: 'Las fechas son obligatorias.' }); return
    }
    if (new Date(endDateTime) <= new Date(startDateTime)) {
      toast({ type: 'error', message: 'La fecha de fin debe ser posterior al inicio.' }); return
    }
    setSavingChild(true)
    try {
      const toLocalDt = (dt) => (dt.length === 16 ? dt + ':00' : dt)
      const payload = {
        startDateTime: toLocalDt(startDateTime),
        endDateTime:   toLocalDt(endDateTime),
        reason:        reason || null,
      }
      if (absenceModal === 'create') {
        await api.post(`${empUrl}/absences`, payload)
        toast({ type: 'success', message: 'Ausencia registrada.' })
      } else {
        await api.put(`${empUrl}/absences/${selectedAbs.id}`, payload)
        toast({ type: 'success', message: 'Ausencia actualizada.' })
      }
      closeAbs()
      refreshAbsences()
    } catch (err) {
      toast({ type: 'error', message: getErrorMessage(err, 'Error al guardar.') })
    } finally { setSavingChild(false) }
  }
  const handleAbsDelete = async () => {
    setSavingChild(true)
    try {
      await api.delete(`${empUrl}/absences/${selectedAbs.id}`)
      toast({ type: 'success', message: 'Ausencia eliminada.' })
      closeAbs()
      refreshAbsences()
    } catch (err) {
      toast({ type: 'error', message: getErrorMessage(err, 'Error al eliminar.') })
    } finally { setSavingChild(false) }
  }

  const isAdminRole = emp.roleName === 'ADMIN'

  return (
    <>
      <div
        onClick={onClose}
        className="fixed inset-0 z-40 bg-slate-900/30 backdrop-blur-sm animate-[fadeIn_200ms_ease-out]"
      />
      <aside className="fixed top-0 right-0 z-50 h-screen w-[540px] max-w-[95vw] bg-white shadow-[0_28px_56px_-16px_rgba(15,23,42,0.22)] flex flex-col animate-[slideInRight_240ms_cubic-bezier(0.2,0.7,0.2,1)]">

        {/* Head */}
        <div className="px-6 pt-6 pb-5 border-b border-slate-100">
          <div className="flex items-start justify-between gap-3">
            <div className="flex items-center gap-4 min-w-0 flex-1">
              <div className={`w-16 h-16 rounded-2xl bg-gradient-to-br ${avatarColor(emp.id)} flex items-center justify-center text-white text-xl font-bold shrink-0 shadow-[0_8px_20px_-6px_rgba(14,165,233,0.45)]`}>
                {emp.fullName?.[0]?.toUpperCase()}
              </div>
              <div className="min-w-0">
                <div className="flex items-center gap-2 flex-wrap">
                  <div className="text-lg font-bold text-[#1e3a5f]">{emp.fullName}</div>
                  {isAdminRole
                    ? <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wider bg-[#1e3a5f] text-white"><Shield size={10} /> Admin</span>
                    : <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wider bg-cyan-50 text-cyan-700 ring-1 ring-cyan-100"><User size={10} /> Empleado</span>
                  }
                </div>
                <div className="text-xs text-slate-400 mt-1 inline-flex items-center gap-1.5">
                  <Calendar size={12} /> Empleado desde {fmtSinceLong(emp.createdAt)}
                </div>
                <div className={`mt-2 inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-semibold ring-1 ${status.cls}`}>
                  <span className={`w-1.5 h-1.5 rounded-full ${status.dot}`} />
                  {status.label}
                </div>
              </div>
            </div>
            <button onClick={onClose} className="p-2 rounded-lg text-slate-400 hover:bg-slate-50 hover:text-[#1e3a5f] transition" title="Cerrar">
              <X size={16} />
            </button>
          </div>
        </div>

        <div className="flex-1 overflow-y-auto px-6 py-5 space-y-6">

          {/* KPIs del mes */}
          <div>
            <div className="text-[10px] font-bold text-slate-400 uppercase tracking-[0.16em] mb-2">Resumen del mes</div>
            <div className="grid grid-cols-3 gap-2">
              <KpiTile label="Citas"    value={kpi == null ? '…' : kpi.citas} />
              <KpiTile label="Ingresos" value={kpi == null ? '…' : `${kpi.ingresos.toLocaleString('es-ES', { minimumFractionDigits: 0, maximumFractionDigits: 0 })} €`} />
              <KpiTile label="Horas"    value={kpi == null ? '…' : `${kpi.horas} h`} />
            </div>
          </div>

          {/* Contacto */}
          <div>
            <div className="text-[10px] font-bold text-slate-400 uppercase tracking-[0.16em] mb-2">Contacto</div>
            <div className="grid grid-cols-2 gap-2">
              <a href={`mailto:${emp.email}`} className="group flex items-center gap-3 px-3 py-2.5 rounded-xl border border-slate-100 hover:border-blue-200 hover:bg-blue-50/40 transition">
                <span className="w-9 h-9 rounded-lg bg-blue-50 text-blue-600 flex items-center justify-center"><Mail size={16} /></span>
                <span className="flex-1 min-w-0">
                  <span className="block text-[10px] text-slate-400 uppercase tracking-wider font-semibold">Email</span>
                  <span className="block text-xs font-semibold text-[#1e3a5f] truncate">{emp.email}</span>
                </span>
              </a>
              {emp.phone ? (
                <a href={telHref(emp.phone)} className="group flex items-center gap-3 px-3 py-2.5 rounded-xl border border-slate-100 hover:border-blue-200 hover:bg-blue-50/40 transition">
                  <span className="w-9 h-9 rounded-lg bg-blue-50 text-blue-600 flex items-center justify-center"><Phone size={16} /></span>
                  <span className="flex-1 min-w-0">
                    <span className="block text-[10px] text-slate-400 uppercase tracking-wider font-semibold">Teléfono</span>
                    <span className="block text-xs font-semibold text-[#1e3a5f] truncate font-mono">{emp.phone}</span>
                  </span>
                </a>
              ) : (
                <div className="flex items-center gap-3 px-3 py-2.5 rounded-xl border border-slate-100 opacity-60">
                  <span className="w-9 h-9 rounded-lg bg-slate-50 text-slate-400 flex items-center justify-center"><Phone size={16} /></span>
                  <span className="flex-1 text-xs text-slate-400">Sin teléfono</span>
                </div>
              )}
            </div>
          </div>

          {/* Horario semanal — vista grid */}
          <div>
            <div className="flex items-center justify-between mb-2">
              <div className="flex items-center gap-1.5">
                <div className="flex h-5 w-5 items-center justify-center rounded-md bg-blue-50"><Clock size={11} className="text-blue-500" /></div>
                <p className="text-[10px] font-bold text-slate-400 uppercase tracking-[0.16em]">Horario semanal</p>
              </div>
              {isAdmin && (
                <div className="flex items-center gap-1.5">
                  <button onClick={() => setScheduleModal('copyweek')} className="rounded-xl bg-slate-100 px-2 py-1 text-[11px] font-semibold text-slate-600 hover:bg-slate-200 transition">
                    Copiar L→V
                  </button>
                  <button onClick={openSchedCreate} className="flex items-center gap-0.5 rounded-xl bg-blue-50 px-2 py-1 text-[11px] font-semibold text-blue-600 hover:bg-blue-100 transition">
                    <Plus size={10} /> Añadir
                  </button>
                </div>
              )}
            </div>
            <ScheduleGrid
              schedules={schedules ?? []}
              loading={schedules === null}
              isAdmin={isAdmin}
              onEdit={openSchedEdit}
              onDelete={openSchedDelete}
              onAdd={openSchedCreate}
            />
          </div>

          {/* Ausencias */}
          <div>
            <div className="flex items-center justify-between mb-2">
              <div className="flex items-center gap-1.5">
                <div className="flex h-5 w-5 items-center justify-center rounded-md bg-amber-50"><Calendar size={11} className="text-amber-500" /></div>
                <p className="text-[10px] font-bold text-slate-400 uppercase tracking-[0.16em]">
                  Ausencias{absTotal > 0 && <span className="text-slate-400 font-medium normal-case tracking-normal"> · {absTotal}</span>}
                </p>
              </div>
              {isAdmin && (
                <button onClick={openAbsCreate} className="flex items-center gap-0.5 rounded-xl bg-amber-50 px-2 py-1 text-[11px] font-semibold text-amber-600 hover:bg-amber-100 transition">
                  <Plus size={10} /> Añadir
                </button>
              )}
            </div>
            {absLoading ? (
              <div className="space-y-1.5">
                {[...Array(2)].map((_, i) => <div key={i} className="h-10 bg-amber-50/40 rounded-xl animate-pulse" />)}
              </div>
            ) : absences.length === 0 ? (
              <p className="text-xs text-slate-400 italic px-1 py-2">Sin ausencias registradas.</p>
            ) : (
              <div className="space-y-1.5">
                {[...absences]
                  .sort((a, b) => new Date(b.startDateTime) - new Date(a.startDateTime))
                  .map((a) => (
                    <div key={a.id} className="flex items-start text-xs bg-amber-50/60 rounded-xl px-3 py-2 border border-amber-100">
                      <div className="flex-1 min-w-0">
                        <p className="font-semibold text-slate-700">
                          {fmtAbsence(a.startDateTime)} – {fmtAbsence(a.endDateTime)}
                        </p>
                        {a.reason && <p className="text-slate-500 truncate mt-0.5">{a.reason}</p>}
                      </div>
                      {isAdmin && (
                        <div className="flex gap-0.5 ml-2 shrink-0">
                          <button onClick={() => openAbsEdit(a)}   className="rounded-lg p-1 text-slate-400 hover:bg-blue-50 hover:text-blue-500 transition" title="Editar"><Pencil size={11} /></button>
                          <button onClick={() => openAbsDelete(a)} className="rounded-lg p-1 text-slate-400 hover:bg-red-50 hover:text-red-500 transition" title="Eliminar"><Trash2 size={11} /></button>
                        </div>
                      )}
                    </div>
                  ))}
              </div>
            )}
            <Pagination
              page={absPage}
              totalPages={absTotalPages}
              totalElements={absTotal}
              onChange={setAbsPage}
            />
          </div>
        </div>

        {/* Foot */}
        {isAdmin && (
          <div className="px-6 py-4 border-t border-slate-100 flex gap-2">
            {archived ? (
              <Button variant="success" onClick={onReactivate} className="flex-1 gap-2">
                <ArchiveRestore size={15} /> Reactivar
              </Button>
            ) : (
              <>
                <Button variant="outline" onClick={onDeactivate} className="flex-1 gap-2">
                  <UserMinus size={15} /> Desactivar
                </Button>
                <Button onClick={onEditRole} className="flex-1 gap-2">
                  <Pencil size={15} /> Editar
                </Button>
              </>
            )}
          </div>
        )}
      </aside>

      {/* Schedule modals */}
      <Modal
        open={scheduleModal === 'create' || scheduleModal === 'edit'}
        onClose={closeSched}
        title={scheduleModal === 'create' ? 'Añadir horario' : 'Editar horario'}
        size="sm"
      >
        <div className="space-y-4">
          <Select
            label="Día *"
            value={scheduleForm.dayOfWeek}
            onChange={(e) => setScheduleForm((p) => ({ ...p, dayOfWeek: e.target.value }))}
          >
            {DAYS_FULL.slice(1).map((d, i) => (
              <option key={i + 1} value={i + 1}>{d}</option>
            ))}
          </Select>
          <div className="grid grid-cols-2 gap-3">
            <Input
              label="Hora inicio"
              type="time"
              value={scheduleForm.startTime}
              onChange={(e) => setScheduleForm((p) => ({ ...p, startTime: e.target.value }))}
            />
            <Input
              label="Hora fin"
              type="time"
              value={scheduleForm.endTime}
              onChange={(e) => setScheduleForm((p) => ({ ...p, endTime: e.target.value }))}
            />
          </div>
          <div className="flex gap-3 pt-1">
            <Button variant="outline" onClick={closeSched} className="flex-1">Cancelar</Button>
            <Button onClick={handleSchedSave} loading={savingChild} className="flex-1">
              {scheduleModal === 'create' ? 'Añadir' : 'Guardar'}
            </Button>
          </div>
        </div>
      </Modal>
      <Modal open={scheduleModal === 'delete'} onClose={closeSched} title="Eliminar tramo" size="sm">
        <div className="space-y-5">
          <div className="flex items-start gap-3 rounded-2xl bg-red-50 border border-red-100 px-4 py-4">
            <Trash2 size={18} className="text-red-500 shrink-0 mt-0.5" />
            <p className="text-sm text-red-700 leading-snug">
              ¿Eliminar el tramo del <strong>{DAYS_FULL[selectedSch?.dayOfWeek]}</strong> de{' '}
              <strong>{selectedSch?.startTime?.slice(0, 5)}</strong> a{' '}
              <strong>{selectedSch?.endTime?.slice(0, 5)}</strong>?
            </p>
          </div>
          <div className="flex gap-3">
            <Button variant="outline" onClick={closeSched} className="flex-1">Cancelar</Button>
            <Button variant="danger" onClick={handleSchedDelete} loading={savingChild} className="flex-1">Eliminar</Button>
          </div>
        </div>
      </Modal>

      <Modal open={scheduleModal === 'copyweek'} onClose={closeSched} title="Copiar horario L→V" size="sm">
        <div className="space-y-5">
          <div className="flex items-start gap-3 rounded-2xl bg-blue-50 border border-blue-100 px-4 py-4">
            <Clock size={18} className="text-blue-500 shrink-0 mt-0.5" />
            <p className="text-sm text-blue-800 leading-snug">
              Se copiarán los tramos del <strong>lunes</strong> a <strong>martes, miércoles,
              jueves y viernes</strong>. El horario que esos días tuvieran se <strong>reemplazará</strong>.
            </p>
          </div>
          <div className="flex gap-3">
            <Button variant="outline" onClick={closeSched} className="flex-1">Cancelar</Button>
            <Button onClick={handleCopyMonToWeek} loading={savingChild} className="flex-1">Copiar</Button>
          </div>
        </div>
      </Modal>

      {/* Absence modals */}
      <Modal
        open={absenceModal === 'create' || absenceModal === 'edit'}
        onClose={closeAbs}
        title={absenceModal === 'create' ? 'Registrar ausencia' : 'Editar ausencia'}
        size="sm"
      >
        <div className="space-y-4">
          {/* Sin 'min': el backend acepta ausencias con inicio en el pasado
              (caso real: a las 14:00 registras una baja que empezó a las 8:00).
              handleAbsSave ya valida que el fin sea posterior al inicio. */}
          <Input
            label="Inicio *"
            type="datetime-local"
            value={absenceForm.startDateTime}
            onChange={(e) => setAbsenceForm((p) => ({ ...p, startDateTime: e.target.value }))}
          />
          <Input
            label="Fin *"
            type="datetime-local"
            value={absenceForm.endDateTime}
            min={absenceForm.startDateTime || undefined}
            onChange={(e) => setAbsenceForm((p) => ({ ...p, endDateTime: e.target.value }))}
          />
          <Input
            label="Motivo"
            value={absenceForm.reason}
            onChange={(e) => setAbsenceForm((p) => ({ ...p, reason: e.target.value }))}
            placeholder="Vacaciones, baja médica…"
            maxLength={255}
          />
          <div className="flex gap-3 pt-1">
            <Button variant="outline" onClick={closeAbs} className="flex-1">Cancelar</Button>
            <Button onClick={handleAbsSave} loading={savingChild} className="flex-1">
              {absenceModal === 'create' ? 'Registrar' : 'Guardar'}
            </Button>
          </div>
        </div>
      </Modal>
      <Modal open={absenceModal === 'delete'} onClose={closeAbs} title="Eliminar ausencia" size="sm">
        <div className="space-y-5">
          <div className="flex items-start gap-3 rounded-2xl bg-red-50 border border-red-100 px-4 py-4">
            <Trash2 size={18} className="text-red-500 shrink-0 mt-0.5" />
            <p className="text-sm text-red-700 leading-snug">
              ¿Eliminar la ausencia del <strong>{selectedAbs ? fmtAbsence(selectedAbs.startDateTime) : ''}</strong>?
            </p>
          </div>
          <div className="flex gap-3">
            <Button variant="outline" onClick={closeAbs} className="flex-1">Cancelar</Button>
            <Button variant="danger" onClick={handleAbsDelete} loading={savingChild} className="flex-1">Eliminar</Button>
          </div>
        </div>
      </Modal>
    </>
  )
}

/* ============================================================
   KPI TILE + SCHEDULE GRID
   ============================================================ */

function KpiTile({ label, value }) {
  return (
    <div className="rounded-xl border border-slate-100 px-3 py-3 text-center">
      <div className="text-xs text-slate-500 font-medium">{label}</div>
      <div className="text-xl font-extrabold text-[#1e3a5f] mt-0.5 tabular-nums">{value}</div>
    </div>
  )
}

function ScheduleGrid({ schedules, loading, isAdmin, onEdit, onDelete, onAdd }) {
  // Rango visual adaptativo: mismo problema que la rejilla de horarios
  // del negocio (HoursTab). Con un tramo 2:00-22:00 la formula calcula
  // width > 100% sobre el rango fijo 8-22 y la barra desborda la
  // tarjeta. Calculo min/max del horario real del empleado; defaults
  // 8-22 si todavia no hay nada; cap [0, 24] defensivo.
  const { DAY_START, DAY_END } = useMemo(() => {
    let minH = 24, maxH = 0
    ;(schedules ?? []).forEach((s) => {
      if (!s.startTime || !s.endTime) return
      const [sh, sm] = s.startTime.slice(0, 5).split(':').map(Number)
      const [eh, em] = s.endTime.slice(0, 5).split(':').map(Number)
      const start = sh + sm / 60
      const end   = eh + em / 60
      if (start < minH) minH = start
      if (end > maxH)   maxH = end
    })
    if (minH === 24 || maxH === 0) return { DAY_START: 8, DAY_END: 22 }
    return {
      DAY_START: Math.max(0, Math.floor(minH)),
      DAY_END: Math.min(24, Math.ceil(maxH)),
    }
  }, [schedules])
  const total = DAY_END - DAY_START
  const dow = todayDow()

  if (loading) {
    return (
      <div className="rounded-xl border border-slate-100 bg-slate-50/50 p-3 space-y-1.5">
        {[...Array(7)].map((_, i) => <div key={i} className="h-7 bg-white rounded-md animate-pulse" />)}
      </div>
    )
  }

  /*
   * Aplanado del horario en un unico array de filas. Es clave para que
   * React reconcilie bien: con el patron anterior
   *   [1..7].map((d) => rows.map(...))
   * React veia un array de arrays sin key estable a nivel de dia y,
   * al borrar el primer tramo de un dia con turno partido, las
   * etiquetas DAYS_SHORT[d] se desplazaban a la fila siguiente porque
   * dependian del idx local del sub-map. flatMap + isFirstOfDay
   * calculado por item arregla el reciclaje y la posicion de la
   * etiqueta queda anclada al tramo correcto.
   */
  const flatRows = [1, 2, 3, 4, 5, 6, 7].flatMap((d) => {
    const daySchedules = schedules
      .filter((s) => s.dayOfWeek === d)
      .sort((a, b) => a.startTime.localeCompare(b.startTime))
    if (daySchedules.length === 0) {
      return [{ key: `empty-${d}`, sch: null, day: d, isFirstOfDay: true }]
    }
    return daySchedules.map((sch, idx) => ({
      key: `s-${sch.id}`,
      sch,
      day: d,
      isFirstOfDay: idx === 0,
    }))
  })

  return (
    <div className="rounded-xl border border-slate-100 bg-slate-50/50 p-3">
      <div className="space-y-1.5">
        {flatRows.map(({ key, sch, day, isFirstOfDay }) => {
          const isToday = day === dow
          let bar
          if (sch) {
            const [sh, sm] = sch.startTime.slice(0, 5).split(':').map(Number)
            const [eh, em] = sch.endTime.slice(0, 5).split(':').map(Number)
            const startH = sh + sm / 60 - DAY_START
            const endH   = eh + em / 60 - DAY_START
            const left   = Math.max(0, (startH / total) * 100)
            const width  = Math.max(2, ((endH - startH) / total) * 100)
            bar = (
              <div
                className="absolute inset-y-1 rounded-md flex items-center px-2 text-[10px] font-semibold text-white whitespace-nowrap overflow-hidden"
                style={{ left: `${left}%`, width: `${width}%`, background: 'linear-gradient(135deg, #22d3ee 0%, #3b82f6 100%)' }}
                title={`${sch.startTime.slice(0, 5)} – ${sch.endTime.slice(0, 5)}`}
              >
                <span className="truncate">{sch.startTime.slice(0, 5)} – {sch.endTime.slice(0, 5)}</span>
              </div>
            )
          } else {
            bar = (
              <div className="absolute inset-y-2 left-1 right-1 rounded-md border border-dashed border-slate-200 text-[10px] font-medium text-slate-400 flex items-center justify-center">
                Libre
              </div>
            )
          }
          return (
            <div key={key} className="grid grid-cols-[36px_1fr_56px] items-center gap-2">
              <div className={`text-[11px] font-bold text-center ${isToday ? 'text-blue-600' : 'text-slate-500'}`}>
                {isFirstOfDay ? DAYS_SHORT[day] : ''}
                {isFirstOfDay && isToday && <span className="block w-1 h-1 rounded-full bg-blue-500 mx-auto mt-0.5" />}
              </div>
              <div className="relative h-7 rounded-md bg-white border border-slate-100">{bar}</div>
              <div className="flex gap-0.5 justify-end">
                {sch ? (
                  isAdmin && (
                    <>
                      <button onClick={() => onEdit(sch)}   className="rounded p-1 text-slate-300 hover:bg-blue-50 hover:text-blue-500 transition"><Pencil size={12} /></button>
                      <button onClick={() => onDelete(sch)} className="rounded p-1 text-slate-300 hover:bg-red-50 hover:text-red-500 transition"><Trash2 size={12} /></button>
                    </>
                  )
                ) : (
                  isAdmin && (
                    <button onClick={onAdd} className="rounded p-1 text-slate-300 hover:bg-blue-50 hover:text-blue-500 transition" title="Añadir"><Plus size={12} /></button>
                  )
                )}
              </div>
            </div>
          )
        })}
      </div>
      <div className="grid grid-cols-[36px_1fr_56px] gap-2 mt-2">
        <div />
        <div className="flex justify-between text-[9px] text-slate-400 px-1 font-mono">
          {Array.from({ length: Math.floor(total / 2) + 1 }, (_, i) => (
            <span key={i}>{String(DAY_START + i * 2).padStart(2, '0')}</span>
          ))}
        </div>
        <div />
      </div>
    </div>
  )
}

/* ------------------------------------------------------------
   Animaciones del drawer — añade al final de src/index.css

   @keyframes fadeIn { from { opacity: 0 } to { opacity: 1 } }
   @keyframes slideInRight {
     from { transform: translateX(100%) }
     to   { transform: translateX(0) }
   }
   ------------------------------------------------------------ */

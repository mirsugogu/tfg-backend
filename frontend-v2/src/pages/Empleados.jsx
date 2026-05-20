import { useCallback, useEffect, useState } from 'react'
import {
  Plus, Search, Pencil, Trash2, Phone, Mail, Shield, User,
  ChevronDown, Calendar, Clock, UserX, Users,
} from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { Select } from '@/components/ui/Select'
import { Modal } from '@/components/ui/Modal'
import { Badge } from '@/components/ui/Badge'
import { Pagination } from '@/components/ui/Pagination'
import { EmptyState } from '@/components/ui/EmptyState'
import { useAuth } from '@/context/AuthContext'
import { useCatalog } from '@/context/CatalogContext'
import { useToast } from '@/components/ui/Toast'
import { usePagedFetch } from '@/hooks/usePagedFetch'
import api, { getErrorMessage } from '@/lib/api'

const DAYS = ['', 'Lunes', 'Martes', 'Miercoles', 'Jueves', 'Viernes', 'Sabado', 'Domingo']

const AVATAR_COLORS = [
  'from-cyan-400 to-blue-500',
  'from-amber-400 to-orange-500',
  'from-emerald-400 to-teal-500',
  'from-indigo-400 to-violet-500',
  'from-pink-400 to-rose-500',
  'from-sky-400 to-blue-500',
]
const avatarColor = (id) => AVATAR_COLORS[(id ?? 0) % AVATAR_COLORS.length]

const empty = { fullName: '', email: '', phone: '', password: '', roleId: '' }
const emptySchedule = { dayOfWeek: '1', startTime: '09:00', endTime: '18:00' }
const emptyAbsence = { startDateTime: '', endDateTime: '', reason: '' }

const fmtAbsence = (dt) =>
  new Date(dt).toLocaleDateString('es-ES', { day: '2-digit', month: 'short', year: 'numeric' })

export default function Empleados() {
  const { user } = useAuth()
  const { roles, roleLabel } = useCatalog()
  const toast = useToast()
  const bId = user?.businessId
  const isAdmin = user?.role === 'ADMIN'

  // Lista paginada real (size 20 por defecto). Se llama refresh() tras
  // crear / editar / eliminar empleados.
  const { items: employees, page, totalPages, totalElements, loading, setPage, refresh } =
    usePagedFetch(bId ? `/api/businesses/${bId}/users` : null)

  const [search, setSearch] = useState('')
  const [expandedId, setExpandedId] = useState(null)
  const [modal, setModal] = useState(null)
  const [selected, setSelected] = useState(null)
  const [form, setForm] = useState(empty)
  const [saving, setSaving] = useState(false)

  const filtered = employees.filter((e) =>
    e.fullName?.toLowerCase().includes(search.toLowerCase()) ||
    e.email?.toLowerCase().includes(search.toLowerCase())
  )

  const defaultRoleId = () =>
    roles.find((r) => r.name === 'EMPLOYEE')?.id ?? roles[0]?.id ?? ''

  const openCreate = () => {
    setForm({ ...empty, roleId: defaultRoleId() })
    setSelected(null)
    setModal('create')
  }
  const openEdit = (e) => {
    setSelected(e)
    setForm({
      fullName: e.fullName,
      email:    e.email,
      phone:    e.phone || '',
      password: '',
      roleId:   e.roleId,
    })
    setModal('edit')
  }
  const openDelete = (e) => { setSelected(e); setModal('delete') }
  const closeModal = () => { setModal(null); setSelected(null) }

  const handleChange = (e) => setForm((p) => ({ ...p, [e.target.name]: e.target.value }))

  const handleSave = async () => {
    if (modal === 'create' && (!form.fullName.trim() || !form.email.trim())) {
      toast({ type: 'error', message: 'Nombre y email son obligatorios.' }); return
    }
    if (!form.roleId) {
      toast({ type: 'error', message: 'Selecciona un rol.' }); return
    }
    if (modal === 'create' && !form.password) {
      toast({ type: 'error', message: 'La contrasena es obligatoria.' }); return
    }
    if (modal === 'create' && form.password.length < 8) {
      toast({ type: 'error', message: 'La contrasena debe tener al menos 8 caracteres.' }); return
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
        // El backend solo acepta roleId en UpdateUserRequest (audit C.10.003:
        // mass-assignment protegido — fullName/email/phone se ignorarian
        // silenciosamente). Mandamos SOLO roleId para no inducir al usuario
        // a creer que se actualiza el resto. Para cambiar nombre/email/
        // telefono cada usuario debe hacerlo en su propio /perfil.
        await api.put(`/api/businesses/${bId}/users/${selected.id}`, {
          roleId: Number(form.roleId),
        })
        toast({ type: 'success', message: 'Rol actualizado.' })
      }
      closeModal()
      refresh()
    } catch (err) {
      toast({ type: 'error', message: getErrorMessage(err, 'Error al guardar.') })
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async () => {
    setSaving(true)
    try {
      await api.delete(`/api/businesses/${bId}/users/${selected.id}`)
      toast({ type: 'success', message: 'Empleado desactivado.' })
      closeModal()
      refresh()
    } catch (err) {
      toast({ type: 'error', message: getErrorMessage(err, 'Error al eliminar el empleado.') })
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="p-8 max-w-7xl mx-auto">
      {/* Header */}
      <div className="mb-8 flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-[#1e3a5f] tracking-tight">Empleados</h1>
          <p className="text-sm text-slate-500 mt-1.5 font-medium flex items-center gap-1.5">
            <Users size={14} />
            {loading ? '…' : `${totalElements} usuario${totalElements === 1 ? '' : 's'} en plantilla`}
          </p>
        </div>
        {isAdmin && (
          <Button onClick={openCreate} className="gap-2" disabled={!roles.length}>
            <Plus size={16} /> Nuevo empleado
          </Button>
        )}
      </div>

      {/* Search */}
      <div className="mb-6 relative max-w-sm">
        <Search size={15} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400" />
        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Buscar en esta pagina por nombre o email…"
          className="h-11 w-full rounded-2xl border border-slate-200 bg-slate-50 pl-10 pr-3.5 text-sm text-[#1f2c4a] placeholder:text-slate-400 focus:outline-none focus:bg-white focus:border-blue-400 focus:ring-4 focus:ring-blue-100 transition-all"
        />
      </div>

      {/* Cards grid */}
      <div className="grid gap-5 sm:grid-cols-2 xl:grid-cols-3">
        {loading ? (
          [...Array(4)].map((_, i) => (
            <div key={i} className="h-40 bg-white rounded-2xl border border-slate-100 animate-pulse" />
          ))
        ) : filtered.length === 0 ? (
          <div className="col-span-full">
            {search ? (
              <p className="py-20 text-center text-slate-400">
                {`Sin resultados en esta página${totalPages > 1 ? ' (prueba a cambiar de página)' : ''}.`}
              </p>
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
          filtered.map((e) => (
            <div
              key={e.id}
              className="bg-white rounded-2xl border border-slate-100/80 shadow-[0_2px_12px_-2px_rgba(15,23,42,0.06)] overflow-hidden"
            >
              {/* Gradient top bar */}
              <div className={`h-1 w-full bg-gradient-to-r ${avatarColor(e.id)}`} />

              <div className="p-5">
                {/* Employee header */}
                <div className="flex items-start justify-between gap-3">
                  <div className="flex items-center gap-3.5 min-w-0">
                    <div className={`flex h-14 w-14 shrink-0 items-center justify-center rounded-2xl bg-gradient-to-br ${avatarColor(e.id)} text-white font-bold text-lg shadow-[0_6px_16px_-6px_rgba(14,165,233,0.4)]`}>
                      {e.fullName?.[0]?.toUpperCase()}
                    </div>
                    <div className="min-w-0">
                      <p className="font-bold text-[#1e3a5f] truncate">{e.fullName}</p>
                      <div className="mt-1">
                        {e.roleName === 'ADMIN'
                          ? <Badge variant="navy"><Shield size={10} className="mr-0.5" />{roleLabel('ADMIN')}</Badge>
                          : <Badge variant="cyan"><User size={10} className="mr-0.5" />{roleLabel(e.roleName)}</Badge>
                        }
                      </div>
                    </div>
                  </div>
                  {isAdmin && (
                    <div className="flex gap-1 shrink-0">
                      <button
                        onClick={() => openEdit(e)}
                        className="rounded-xl p-2 text-slate-400 hover:bg-blue-50 hover:text-blue-600 transition-colors"
                        title="Cambiar rol"
                      >
                        <Pencil size={14} />
                      </button>
                      <button
                        onClick={() => openDelete(e)}
                        className="rounded-xl p-2 text-slate-400 hover:bg-red-50 hover:text-red-600 transition-colors"
                        title="Desactivar"
                      >
                        <Trash2 size={14} />
                      </button>
                    </div>
                  )}
                </div>

                {/* Contact info */}
                <div className="mt-4 space-y-1.5">
                  <div className="flex items-center gap-2 text-xs text-slate-500">
                    <Mail size={12} className="text-slate-400 shrink-0" />
                    <span className="truncate">{e.email}</span>
                  </div>
                  {e.phone && (
                    <div className="flex items-center gap-2 text-xs text-slate-500">
                      <Phone size={12} className="text-slate-400 shrink-0" />
                      <span>{e.phone}</span>
                    </div>
                  )}
                </div>

                {/* Expand toggle */}
                <button
                  onClick={() => setExpandedId(expandedId === e.id ? null : e.id)}
                  className="mt-4 flex w-full items-center justify-center gap-1.5 rounded-2xl border border-slate-200 py-2 text-xs font-medium text-slate-500 hover:bg-slate-50 hover:text-[#1e3a5f] hover:border-blue-200 transition-all"
                >
                  <ChevronDown
                    size={13}
                    className={`transition-transform duration-200 ${expandedId === e.id ? 'rotate-180 text-blue-500' : ''}`}
                  />
                  {expandedId === e.id ? 'Ocultar horarios y ausencias' : 'Ver horarios y ausencias'}
                </button>

                {/* Expanded section — montado/desmontado con la expansion para
                    que sus hooks (paginacion de absences) se aten al ciclo
                    de vida del panel y no a la pagina completa. */}
                {expandedId === e.id && (
                  <EmployeePanel emp={e} bId={bId} isAdmin={isAdmin} />
                )}
              </div>
            </div>
          ))
        )}
      </div>

      {/* Paginacion de la lista de empleados */}
      <div className="mt-6 rounded-2xl border border-slate-100 overflow-hidden">
        <Pagination
          page={page}
          totalPages={totalPages}
          totalElements={totalElements}
          onChange={setPage}
        />
      </div>

      {/* Modal crear / editar empleado */}
      <Modal
        open={modal === 'create' || modal === 'edit'}
        onClose={closeModal}
        title={modal === 'create' ? 'Nuevo empleado' : 'Cambiar rol del empleado'}
      >
        <div className="space-y-4">
          {modal === 'edit' && (
            <div className="rounded-2xl bg-slate-50 border border-slate-100 px-4 py-3 text-xs text-slate-500">
              Para cambiar el nombre, email o telefono el propio usuario debe editarlo en su perfil.
              Desde aqui solo puedes actualizar el rol.
            </div>
          )}
          <Input
            label="Nombre completo *"
            name="fullName"
            value={form.fullName}
            onChange={handleChange}
            placeholder="Nombre y apellidos"
            disabled={modal === 'edit'}
          />
          <Input
            label="Email *"
            name="email"
            type="email"
            value={form.email}
            onChange={handleChange}
            placeholder="empleado@empresa.com"
            disabled={modal === 'edit'}
          />
          <Input
            label="Telefono"
            name="phone"
            value={form.phone}
            onChange={handleChange}
            placeholder="600 000 000"
            disabled={modal === 'edit'}
          />
          {modal === 'create' && (
            <Input
              label="Contrasena *"
              name="password"
              type="password"
              value={form.password}
              onChange={handleChange}
              placeholder="Minimo 8 caracteres"
            />
          )}
          <Select label="Rol *" name="roleId" value={form.roleId} onChange={handleChange}>
            <option value="">Selecciona rol</option>
            {roles.map((r) => (
              <option key={r.id} value={r.id}>{roleLabel(r.name)}</option>
            ))}
          </Select>
          <div className="flex gap-3 pt-2">
            <Button variant="outline" onClick={closeModal} className="flex-1">Cancelar</Button>
            <Button onClick={handleSave} loading={saving} className="flex-1">
              {modal === 'create' ? 'Crear empleado' : 'Guardar rol'}
            </Button>
          </div>
        </div>
      </Modal>

      {/* Modal eliminar empleado */}
      <Modal open={modal === 'delete'} onClose={closeModal} title="Eliminar empleado" size="sm">
        <div className="space-y-5">
          <div className="flex items-start gap-3 rounded-2xl bg-red-50 border border-red-100 px-4 py-4">
            <UserX size={20} className="text-red-500 shrink-0 mt-0.5" />
            <p className="text-sm text-red-700 leading-snug">
              ¿Desactivar a <strong>{selected?.fullName}</strong>? No podra iniciar sesion ni ser asignado a nuevas citas.
            </p>
          </div>
          <div className="flex gap-3">
            <Button variant="outline" onClick={closeModal} className="flex-1">Cancelar</Button>
            <Button variant="danger" onClick={handleDelete} loading={saving} className="flex-1">Eliminar</Button>
          </div>
        </div>
      </Modal>
    </div>
  )
}

// ---------------------------------------------------------------------------
// EmployeePanel — subpanel expandible. Carga los horarios (List<>) y las
// ausencias (Page<>) del empleado, y agrupa sus propios modales para
// crear/editar/eliminar ambos recursos.
// ---------------------------------------------------------------------------
function EmployeePanel({ emp, bId, isAdmin }) {
  const toast = useToast()
  const empUrl = `/api/businesses/${bId}/users/${emp.id}`

  // Schedules: el backend devuelve List<> plano (sin paginacion).
  const [schedules, setSchedules] = useState(null)
  const [schedLoading, setSchedLoading] = useState(true)
  const [schedReload, setSchedReload] = useState(0)
  useEffect(() => {
    setSchedLoading(true)
    api.get(`${empUrl}/schedules`)
      .then((r) => setSchedules(r.data))
      .catch((err) => {
        toast({ type: 'error', message: getErrorMessage(err, 'Error al cargar horarios.') })
        setSchedules([])
      })
      .finally(() => setSchedLoading(false))
  }, [empUrl, schedReload, toast])
  const refreshSchedules = useCallback(() => setSchedReload((v) => v + 1), [])

  // Absences: Page<> paginado. Tamano 10 para que la card no crezca demasiado.
  const {
    items: absences,
    page: absPage,
    totalPages: absTotalPages,
    totalElements: absTotal,
    loading: absLoading,
    setPage: setAbsPage,
    refresh: refreshAbsences,
  } = usePagedFetch(`${empUrl}/absences`, { size: 10 })

  // Modales locales al panel
  const [modal, setModal] = useState(null)
  const [selectedSchedule, setSelectedSchedule] = useState(null)
  const [selectedAbsence, setSelectedAbsence] = useState(null)
  const [scheduleForm, setScheduleForm] = useState(emptySchedule)
  const [absenceForm, setAbsenceForm] = useState(emptyAbsence)
  const [saving, setSaving] = useState(false)

  const closeModal = () => {
    setModal(null)
    setSelectedSchedule(null)
    setSelectedAbsence(null)
  }

  // ---- Schedules CRUD ----
  const openScheduleCreate = () => {
    setSelectedSchedule(null)
    setScheduleForm(emptySchedule)
    setModal('schedule-create')
  }
  const openScheduleEdit = (s) => {
    setSelectedSchedule(s)
    setScheduleForm({
      dayOfWeek: String(s.dayOfWeek),
      startTime: s.startTime?.slice(0, 5) || '09:00',
      endTime:   s.endTime?.slice(0, 5)   || '18:00',
    })
    setModal('schedule-edit')
  }
  const openScheduleDelete = (s) => {
    setSelectedSchedule(s)
    setModal('schedule-delete')
  }

  const handleScheduleSave = async () => {
    if (scheduleForm.startTime >= scheduleForm.endTime) {
      toast({ type: 'error', message: 'La hora de fin debe ser posterior a la de inicio.' })
      return
    }
    setSaving(true)
    try {
      const payload = {
        dayOfWeek: parseInt(scheduleForm.dayOfWeek, 10),
        startTime: scheduleForm.startTime + ':00',
        endTime:   scheduleForm.endTime   + ':00',
      }
      if (modal === 'schedule-create') {
        await api.post(`${empUrl}/schedules`, payload)
        toast({ type: 'success', message: 'Horario anadido.' })
      } else {
        await api.put(`${empUrl}/schedules/${selectedSchedule.id}`, payload)
        toast({ type: 'success', message: 'Horario actualizado.' })
      }
      closeModal()
      refreshSchedules()
    } catch (err) {
      toast({ type: 'error', message: getErrorMessage(err, 'Error al guardar.') })
    } finally {
      setSaving(false)
    }
  }

  const handleScheduleDelete = async () => {
    setSaving(true)
    try {
      await api.delete(`${empUrl}/schedules/${selectedSchedule.id}`)
      toast({ type: 'success', message: 'Horario eliminado.' })
      closeModal()
      refreshSchedules()
    } catch (err) {
      toast({ type: 'error', message: getErrorMessage(err, 'Error al eliminar.') })
    } finally {
      setSaving(false)
    }
  }

  // ---- Absences CRUD ----
  const openAbsenceCreate = () => {
    setSelectedAbsence(null)
    setAbsenceForm(emptyAbsence)
    setModal('absence-create')
  }
  const openAbsenceEdit = (a) => {
    setSelectedAbsence(a)
    // El input datetime-local pide formato yyyy-MM-ddTHH:mm (sin segundos).
    setAbsenceForm({
      startDateTime: a.startDateTime?.slice(0, 16) || '',
      endDateTime:   a.endDateTime?.slice(0, 16)   || '',
      reason:        a.reason || '',
    })
    setModal('absence-edit')
  }
  const openAbsenceDelete = (a) => {
    setSelectedAbsence(a)
    setModal('absence-delete')
  }

  const handleAbsenceSave = async () => {
    const { startDateTime, endDateTime, reason } = absenceForm
    if (!startDateTime || !endDateTime) {
      toast({ type: 'error', message: 'Las fechas son obligatorias.' })
      return
    }
    if (new Date(endDateTime) <= new Date(startDateTime)) {
      toast({ type: 'error', message: 'La fecha de fin debe ser posterior al inicio.' })
      return
    }
    setSaving(true)
    try {
      // Se anade ':00' para llegar al formato LocalDateTime esperado por el
      // backend (yyyy-MM-ddTHH:mm:ss). NO se anade 'Z': el audit H.005
      // documenta que el backend ignora silenciosamente el sufijo UTC.
      const toLocalDt = (dt) => (dt.length === 16 ? dt + ':00' : dt)
      const payload = {
        startDateTime: toLocalDt(startDateTime),
        endDateTime:   toLocalDt(endDateTime),
        reason:        reason || null,
      }
      if (modal === 'absence-create') {
        await api.post(`${empUrl}/absences`, payload)
        toast({ type: 'success', message: 'Ausencia registrada.' })
      } else {
        // PUT /absences/{id} - UpdateEmployeeAbsenceRequest acepta los
        // mismos campos pero SIN @FutureOrPresent: permite editar ausencias
        // pasadas (audit C.14.006).
        await api.put(`${empUrl}/absences/${selectedAbsence.id}`, payload)
        toast({ type: 'success', message: 'Ausencia actualizada.' })
      }
      closeModal()
      refreshAbsences()
    } catch (err) {
      toast({ type: 'error', message: getErrorMessage(err, 'Error al guardar.') })
    } finally {
      setSaving(false)
    }
  }

  const handleAbsenceDelete = async () => {
    setSaving(true)
    try {
      await api.delete(`${empUrl}/absences/${selectedAbsence.id}`)
      toast({ type: 'success', message: 'Ausencia eliminada.' })
      closeModal()
      refreshAbsences()
    } catch (err) {
      toast({ type: 'error', message: getErrorMessage(err, 'Error al eliminar.') })
    } finally {
      setSaving(false)
    }
  }

  const subLoading = schedLoading || absLoading

  return (
    <div className="mt-4 pt-4 border-t border-slate-100 space-y-5">
      {subLoading ? (
        <div className="space-y-2 py-1">
          {[...Array(3)].map((_, i) => (
            <div key={i} className="h-3 bg-slate-100 rounded-lg animate-pulse" />
          ))}
        </div>
      ) : (
        <>
          {/* Schedules */}
          <div>
            <div className="flex items-center justify-between mb-2.5">
              <div className="flex items-center gap-1.5">
                <div className="flex h-5 w-5 items-center justify-center rounded-md bg-blue-50">
                  <Clock size={11} className="text-blue-500" />
                </div>
                <p className="text-[11px] font-bold text-slate-500 uppercase tracking-wider">Horarios</p>
              </div>
              {isAdmin && (
                <button
                  onClick={openScheduleCreate}
                  className="flex items-center gap-0.5 rounded-xl bg-blue-50 px-2 py-1 text-[11px] font-semibold text-blue-600 hover:bg-blue-100 transition-colors"
                >
                  <Plus size={10} /> Anadir
                </button>
              )}
            </div>
            {(schedules ?? []).length === 0 ? (
              <p className="text-xs text-slate-400 italic pl-1">Sin horarios definidos.</p>
            ) : (
              <div className="space-y-1.5">
                {[...(schedules ?? [])]
                  .sort((a, b) => a.dayOfWeek - b.dayOfWeek)
                  .map((s) => (
                    <div key={s.id} className="flex items-center text-xs bg-slate-50 rounded-xl px-3 py-2 border border-slate-100">
                      <span className="font-semibold text-[#1e3a5f] w-20 shrink-0">{DAYS[s.dayOfWeek]}</span>
                      <span className="flex-1 text-slate-500 font-medium">
                        {s.startTime?.slice(0, 5)} – {s.endTime?.slice(0, 5)}
                      </span>
                      {isAdmin && (
                        <div className="flex gap-0.5">
                          <button onClick={() => openScheduleEdit(s)} className="rounded-lg p-1 text-slate-400 hover:bg-blue-50 hover:text-blue-500 transition-colors">
                            <Pencil size={11} />
                          </button>
                          <button onClick={() => openScheduleDelete(s)} className="rounded-lg p-1 text-slate-400 hover:bg-red-50 hover:text-red-500 transition-colors">
                            <Trash2 size={11} />
                          </button>
                        </div>
                      )}
                    </div>
                  ))}
              </div>
            )}
          </div>

          {/* Absences */}
          <div>
            <div className="flex items-center justify-between mb-2.5">
              <div className="flex items-center gap-1.5">
                <div className="flex h-5 w-5 items-center justify-center rounded-md bg-amber-50">
                  <Calendar size={11} className="text-amber-500" />
                </div>
                <p className="text-[11px] font-bold text-slate-500 uppercase tracking-wider">
                  Ausencias{absTotal > 0 && <span className="text-slate-400 font-medium normal-case tracking-normal"> · {absTotal}</span>}
                </p>
              </div>
              {isAdmin && (
                <button
                  onClick={openAbsenceCreate}
                  className="flex items-center gap-0.5 rounded-xl bg-amber-50 px-2 py-1 text-[11px] font-semibold text-amber-600 hover:bg-amber-100 transition-colors"
                >
                  <Plus size={10} /> Anadir
                </button>
              )}
            </div>
            {absences.length === 0 ? (
              <p className="text-xs text-slate-400 italic pl-1">Sin ausencias registradas.</p>
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
                          <button
                            onClick={() => openAbsenceEdit(a)}
                            className="rounded-lg p-1 text-slate-400 hover:bg-blue-50 hover:text-blue-500 transition-colors"
                            title="Editar"
                          >
                            <Pencil size={11} />
                          </button>
                          <button
                            onClick={() => openAbsenceDelete(a)}
                            className="rounded-lg p-1 text-slate-400 hover:bg-red-50 hover:text-red-500 transition-colors"
                            title="Eliminar"
                          >
                            <Trash2 size={11} />
                          </button>
                        </div>
                      )}
                    </div>
                  ))}
              </div>
            )}
            {/* Paginacion solo si el empleado tiene mas de 10 ausencias */}
            <Pagination
              page={absPage}
              totalPages={absTotalPages}
              totalElements={absTotal}
              onChange={setAbsPage}
            />
          </div>
        </>
      )}

      {/* Schedule modals */}
      <Modal
        open={modal === 'schedule-create' || modal === 'schedule-edit'}
        onClose={closeModal}
        title={modal === 'schedule-create' ? 'Anadir horario' : 'Editar horario'}
        size="sm"
      >
        <div className="space-y-4">
          <Select
            label="Dia *"
            value={scheduleForm.dayOfWeek}
            onChange={(e) => setScheduleForm((p) => ({ ...p, dayOfWeek: e.target.value }))}
          >
            {DAYS.slice(1).map((d, i) => (
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
            <Button variant="outline" onClick={closeModal} className="flex-1">Cancelar</Button>
            <Button onClick={handleScheduleSave} loading={saving} className="flex-1">
              {modal === 'schedule-create' ? 'Anadir' : 'Guardar'}
            </Button>
          </div>
        </div>
      </Modal>
      <Modal open={modal === 'schedule-delete'} onClose={closeModal} title="Eliminar horario" size="sm">
        <div className="space-y-5">
          <div className="flex items-start gap-3 rounded-2xl bg-red-50 border border-red-100 px-4 py-4">
            <Trash2 size={18} className="text-red-500 shrink-0 mt-0.5" />
            <p className="text-sm text-red-700 leading-snug">
              ¿Eliminar el horario del <strong>{DAYS[selectedSchedule?.dayOfWeek]}</strong>?
            </p>
          </div>
          <div className="flex gap-3">
            <Button variant="outline" onClick={closeModal} className="flex-1">Cancelar</Button>
            <Button variant="danger" onClick={handleScheduleDelete} loading={saving} className="flex-1">Eliminar</Button>
          </div>
        </div>
      </Modal>

      {/* Absence modals — create + edit comparten formulario */}
      <Modal
        open={modal === 'absence-create' || modal === 'absence-edit'}
        onClose={closeModal}
        title={modal === 'absence-create' ? 'Registrar ausencia' : 'Editar ausencia'}
        size="sm"
      >
        <div className="space-y-4">
          <Input
            label="Inicio *"
            type="datetime-local"
            value={absenceForm.startDateTime}
            // En CREATE el backend valida @FutureOrPresent; en EDIT NO
            // (audit C.14.006 — intencionalmente sin la anotacion).
            min={modal === 'absence-create' ? new Date().toISOString().slice(0, 16) : undefined}
            onChange={(e) => setAbsenceForm((p) => ({ ...p, startDateTime: e.target.value }))}
          />
          <Input
            label="Fin *"
            type="datetime-local"
            value={absenceForm.endDateTime}
            min={modal === 'absence-create'
              ? (absenceForm.startDateTime || new Date().toISOString().slice(0, 16))
              : absenceForm.startDateTime || undefined}
            onChange={(e) => setAbsenceForm((p) => ({ ...p, endDateTime: e.target.value }))}
          />
          <Input
            label="Motivo"
            value={absenceForm.reason}
            onChange={(e) => setAbsenceForm((p) => ({ ...p, reason: e.target.value }))}
            placeholder="Vacaciones, baja medica…"
          />
          <div className="flex gap-3 pt-1">
            <Button variant="outline" onClick={closeModal} className="flex-1">Cancelar</Button>
            <Button onClick={handleAbsenceSave} loading={saving} className="flex-1">
              {modal === 'absence-create' ? 'Registrar' : 'Guardar'}
            </Button>
          </div>
        </div>
      </Modal>
      <Modal open={modal === 'absence-delete'} onClose={closeModal} title="Eliminar ausencia" size="sm">
        <div className="space-y-5">
          <div className="flex items-start gap-3 rounded-2xl bg-red-50 border border-red-100 px-4 py-4">
            <Trash2 size={18} className="text-red-500 shrink-0 mt-0.5" />
            <p className="text-sm text-red-700 leading-snug">
              ¿Eliminar la ausencia del <strong>{selectedAbsence ? fmtAbsence(selectedAbsence.startDateTime) : ''}</strong>?
            </p>
          </div>
          <div className="flex gap-3">
            <Button variant="outline" onClick={closeModal} className="flex-1">Cancelar</Button>
            <Button variant="danger" onClick={handleAbsenceDelete} loading={saving} className="flex-1">Eliminar</Button>
          </div>
        </div>
      </Modal>
    </div>
  )
}

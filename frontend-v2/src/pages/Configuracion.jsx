import { useEffect, useState } from 'react'
import {
  Plus, Pencil, Trash2, Percent, Clock, Save, Building2, Store,
  CalendarX2, User, MapPin, Globe,
} from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { Select } from '@/components/ui/Select'
import { Modal } from '@/components/ui/Modal'
import { Badge } from '@/components/ui/Badge'
import { Pagination } from '@/components/ui/Pagination'
import { useAuth } from '@/context/AuthContext'
import { useToast } from '@/components/ui/Toast'
import { usePagedFetch } from '@/hooks/usePagedFetch'
import api, { getErrorMessage } from '@/lib/api'

const DAYS = ['', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo']

// Estilo de tarjeta blanca, idéntico al del resto de páginas del proyecto.
const CARD = 'bg-white rounded-2xl border border-slate-100/80 shadow-[0_2px_12px_-2px_rgba(15,23,42,0.06)]'
// Cabecera de tabla reutilizada por las pestañas con listado tabular.
const TH = 'px-6 py-3.5 text-left text-[11px] font-semibold text-slate-400 uppercase tracking-wider'

const emptyTax   = { name: '', percentage: '' }
const emptyHour  = { dayOfWeek: '1', startTime: '09:00', endTime: '18:00', isClosed: false }
const emptyBooth = { name: '' }
const emptyBlock = { type: 'global', membershipId: '', boothId: '', startDate: '', endDate: '', reason: '' }

const fmtDate = (d) =>
  d ? new Date(`${d}T00:00:00`).toLocaleDateString('es-ES', { day: '2-digit', month: 'short', year: 'numeric' }) : '—'

/**
 * Configuración — pantalla de ajustes del negocio con 5 pestañas. Cada
 * pestaña es un componente independiente que se monta solo cuando está
 * activa, de modo que sus hooks (incluida la paginación) se atan a su
 * propio ciclo de vida y no se cargan datos que no se están viendo.
 */
export default function Configuracion() {
  const { user } = useAuth()
  const bId = user?.businessId
  const isAdmin = user?.role === 'ADMIN'
  const [tab, setTab] = useState('business')

  const tabs = [
    { key: 'business', label: 'Negocio',   icon: Building2 },
    { key: 'taxes',    label: 'Impuestos', icon: Percent },
    { key: 'hours',    label: 'Horarios',  icon: Clock },
    { key: 'booths',   label: 'Cabinas',   icon: Store },
    { key: 'blocks',   label: 'Bloqueos',  icon: CalendarX2 },
  ]

  return (
    <div className="p-8 max-w-5xl mx-auto">
      <div className="mb-8">
        <h1 className="text-3xl font-bold text-[#1e3a5f] tracking-tight">Configuración</h1>
        <p className="text-sm text-slate-500 mt-1.5 font-medium">Gestiona los datos y parámetros de tu negocio</p>
      </div>

      <div className="mb-6 overflow-x-auto pb-1">
        <div className="flex items-center gap-1 bg-slate-100 rounded-2xl p-1.5 w-fit">
          {tabs.map(({ key, label, icon: Icon }) => (
            <button
              key={key}
              onClick={() => setTab(key)}
              className={`flex items-center gap-2 rounded-xl px-5 py-2.5 text-sm font-semibold transition-all ${
                tab === key
                  ? 'bg-white text-[#1e3a5f] shadow-[0_2px_8px_-2px_rgba(15,23,42,0.1)]'
                  : 'text-slate-500 hover:text-[#1e3a5f]'
              }`}
            >
              <Icon size={15} /> {label}
            </button>
          ))}
        </div>
      </div>

      {tab === 'business' && <BusinessTab bId={bId} isAdmin={isAdmin} />}
      {tab === 'taxes'    && <TaxesTab    bId={bId} isAdmin={isAdmin} />}
      {tab === 'hours'    && <HoursTab    bId={bId} isAdmin={isAdmin} />}
      {tab === 'booths'   && <BoothsTab   bId={bId} isAdmin={isAdmin} />}
      {tab === 'blocks'   && <BlocksTab   bId={bId} isAdmin={isAdmin} />}
    </div>
  )
}

// ───────────────────────── Negocio ─────────────────────────
function BusinessTab({ bId, isAdmin }) {
  const toast = useToast()
  const [biz, setBiz]   = useState(null)   // negocio cargado (incluye slug, coordenadas, isActive)
  const [form, setForm] = useState(null)   // solo los campos editables (UpdateBusinessRequest)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving]   = useState(false)
  const [reload, setReload]   = useState(0)              // fuerza recargar el negocio
  const [dangerOpen, setDangerOpen]     = useState(false)  // modal de confirmación de baja
  const [dangerSaving, setDangerSaving] = useState(false)

  useEffect(() => {
    if (!bId) return
    let cancelled = false
    setLoading(true)
    api.get(`/api/businesses/${bId}`)
      .then((r) => {
        if (cancelled) return
        const b = r.data
        setBiz(b)
        setForm({
          name: b.name || '', email: b.email || '', phone: b.phone || '',
          address: b.address || '', city: b.city || '', state: b.state || '',
          country: b.country || '', postalCode: b.postalCode || '',
          appointmentInterval: String(b.appointmentInterval || 30),
        })
      })
      .catch((err) => toast({ type: 'error', message: getErrorMessage(err, 'No se pudieron cargar los datos del negocio.') }))
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [bId, toast, reload])

  const set = (k, v) => setForm((p) => ({ ...p, [k]: v }))

  const handleSave = async () => {
    if (!form.name.trim())  { toast({ type: 'error', message: 'El nombre del negocio es obligatorio.' }); return }
    if (!form.email.trim()) { toast({ type: 'error', message: 'El email del negocio es obligatorio.' }); return }
    setSaving(true)
    try {
      // UpdateBusinessRequest NO incluye slug (es inmutable). El backend
      // valida que appointmentInterval sea 15/30/45/60.
      const { data } = await api.put(`/api/businesses/${bId}`, {
        ...form,
        appointmentInterval: Number(form.appointmentInterval),
      })
      setBiz(data)
      toast({ type: 'success', message: 'Datos del negocio actualizados.' })
    } catch (err) {
      toast({ type: 'error', message: getErrorMessage(err, 'No se pudo guardar.') })
    } finally {
      setSaving(false)
    }
  }

  // Baja del negocio (soft delete). Tras el DELETE se recarga: el GET
  // sigue devolviendo el negocio con isActive=false (auditoría G.002).
  const handleDeactivate = async () => {
    setDangerSaving(true)
    try {
      await api.delete(`/api/businesses/${bId}`)
      toast({ type: 'success', message: 'El negocio se ha dado de baja.' })
      setDangerOpen(false)
      setReload((v) => v + 1)
    } catch (err) {
      toast({ type: 'error', message: getErrorMessage(err, 'No se pudo dar de baja el negocio.') })
    } finally {
      setDangerSaving(false)
    }
  }

  const handleReactivate = async () => {
    setDangerSaving(true)
    try {
      const { data } = await api.patch(`/api/businesses/${bId}/reactivate`)
      setBiz(data)
      toast({ type: 'success', message: 'El negocio se ha reactivado.' })
    } catch (err) {
      toast({ type: 'error', message: getErrorMessage(err, 'No se pudo reactivar el negocio.') })
    } finally {
      setDangerSaving(false)
    }
  }

  if (loading || !form) {
    return (
      <div className={`${CARD} p-6 space-y-4`}>
        {[...Array(5)].map((_, i) => <div key={i} className="h-11 rounded-xl bg-slate-100 animate-pulse" />)}
      </div>
    )
  }

  return (
    <div className="space-y-5">
      {/* Aviso de negocio dado de baja */}
      {!biz.isActive && (
        <div className="rounded-2xl border border-amber-200 bg-amber-50 px-5 py-4 flex flex-wrap items-center gap-3">
          <p className="text-sm text-amber-800 font-medium flex-1 min-w-[220px]">
            Este negocio está <strong>dado de baja</strong>. Reactívalo para volver a operar con normalidad.
          </p>
          {isAdmin && (
            <Button onClick={handleReactivate} loading={dangerSaving} size="sm">
              Reactivar negocio
            </Button>
          )}
        </div>
      )}

      {/* Datos del negocio */}
      <div className={`${CARD} p-6 space-y-6`}>
        <div className="grid sm:grid-cols-2 gap-4">
          <Input label="Nombre *" value={form.name} onChange={(e) => set('name', e.target.value)} disabled={!isAdmin} />
          <Input label="Email *" type="email" value={form.email} onChange={(e) => set('email', e.target.value)} disabled={!isAdmin} />
          <Input label="Teléfono" value={form.phone} onChange={(e) => set('phone', e.target.value)} disabled={!isAdmin} />
          <Select
            label="Intervalo entre citas"
            value={form.appointmentInterval}
            onChange={(e) => set('appointmentInterval', e.target.value)}
            disabled={!isAdmin}
          >
            {[15, 30, 45, 60].map((v) => <option key={v} value={v}>{v} minutos</option>)}
          </Select>
        </div>

        <div>
          <Input label="Identificador (slug)" value={biz?.slug || ''} disabled />
          <p className="text-[11px] text-slate-400 mt-1.5">
            El identificador se fija al crear el negocio y no se puede cambiar.
          </p>
        </div>

        <div className="border-t border-slate-100 pt-5">
          <p className="text-xs font-bold text-slate-400 uppercase tracking-wider mb-4">Ubicación</p>
          <div className="grid sm:grid-cols-2 gap-4">
            <Input label="Dirección" value={form.address} onChange={(e) => set('address', e.target.value)} disabled={!isAdmin} />
            <Input label="Ciudad" value={form.city} onChange={(e) => set('city', e.target.value)} disabled={!isAdmin} />
            <Input label="Código postal" value={form.postalCode} onChange={(e) => set('postalCode', e.target.value)} disabled={!isAdmin} />
            <Input label="Provincia / Estado" value={form.state} onChange={(e) => set('state', e.target.value)} disabled={!isAdmin} />
            <Input label="País" value={form.country} onChange={(e) => set('country', e.target.value)} disabled={!isAdmin} />
          </div>
          {biz?.latitude != null && biz?.longitude != null && (
            <p className="text-[11px] text-slate-400 mt-3 flex items-center gap-1">
              <MapPin size={11} /> Coordenadas {biz.latitude}, {biz.longitude} — resueltas automáticamente desde la dirección.
            </p>
          )}
        </div>

        {isAdmin ? (
          <div className="pt-1">
            <Button onClick={handleSave} loading={saving} className="gap-2">
              <Save size={16} /> Guardar cambios
            </Button>
          </div>
        ) : (
          <p className="text-xs text-slate-400">Solo un administrador puede modificar estos datos.</p>
        )}
      </div>

      {/* Zona de peligro — solo ADMIN y solo con el negocio activo */}
      {isAdmin && biz.isActive && (
        <div className="rounded-2xl border border-red-200 bg-red-50/50 p-6">
          <p className="text-sm font-bold text-red-700">Zona de peligro</p>
          <p className="text-xs text-slate-500 mt-1 mb-4">
            Dar de baja el negocio lo desactiva: nadie podrá iniciar sesión ni operar con él hasta reactivarlo.
          </p>
          <Button variant="outline-danger" size="sm" onClick={() => setDangerOpen(true)} className="gap-2">
            <Trash2 size={14} /> Dar de baja el negocio
          </Button>
        </div>
      )}

      {/* Confirmación de baja */}
      <Modal open={dangerOpen} onClose={() => setDangerOpen(false)} title="Dar de baja el negocio" size="sm">
        <div className="space-y-5">
          <div className="flex items-start gap-3 rounded-2xl bg-red-50 border border-red-100 px-4 py-4">
            <Trash2 size={20} className="text-red-500 shrink-0 mt-0.5" />
            <p className="text-sm text-red-700 leading-snug">
              ¿Seguro que quieres dar de baja <strong>{biz?.name}</strong>? Podrás reactivarlo después desde esta misma pantalla.
            </p>
          </div>
          <div className="flex gap-3">
            <Button variant="outline" onClick={() => setDangerOpen(false)} className="flex-1">Cancelar</Button>
            <Button variant="danger" onClick={handleDeactivate} loading={dangerSaving} className="flex-1">Dar de baja</Button>
          </div>
        </div>
      </Modal>
    </div>
  )
}

// ───────────────────────── Impuestos ─────────────────────────
function TaxesTab({ bId, isAdmin }) {
  const toast = useToast()
  const { items: taxes, page, totalPages, totalElements, loading, setPage, refresh } =
    usePagedFetch(bId ? `/api/businesses/${bId}/taxes` : null)

  const [modal, setModal]     = useState(null)   // 'create' | 'edit' | 'delete'
  const [selected, setSelected] = useState(null)
  const [form, setForm]       = useState(emptyTax)
  const [saving, setSaving]   = useState(false)

  const closeModal = () => { setModal(null); setSelected(null) }
  const openCreate = () => { setForm(emptyTax); setSelected(null); setModal('create') }
  const openEdit   = (t) => { setSelected(t); setForm({ name: t.name, percentage: String(t.percentage) }); setModal('edit') }
  const openDelete = (t) => { setSelected(t); setModal('delete') }

  const handleSave = async () => {
    if (!form.name.trim()) { toast({ type: 'error', message: 'El nombre es obligatorio.' }); return }
    if (form.percentage === '' || isNaN(Number(form.percentage))) {
      toast({ type: 'error', message: 'El porcentaje es obligatorio.' }); return
    }
    setSaving(true)
    try {
      const payload = { name: form.name, percentage: Number(form.percentage) }
      if (modal === 'create') {
        await api.post(`/api/businesses/${bId}/taxes`, payload)
        toast({ type: 'success', message: 'Impuesto creado.' })
      } else {
        await api.put(`/api/businesses/${bId}/taxes/${selected.id}`, payload)
        toast({ type: 'success', message: 'Impuesto actualizado.' })
      }
      closeModal(); refresh()
    } catch (err) {
      toast({ type: 'error', message: getErrorMessage(err, 'No se pudo guardar el impuesto.') })
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async () => {
    setSaving(true)
    try {
      await api.delete(`/api/businesses/${bId}/taxes/${selected.id}`)
      toast({ type: 'success', message: 'Impuesto eliminado.' })
      closeModal(); refresh()
    } catch (err) {
      // El 2º DELETE devuelve 400 "ya está desactivado" (soft delete).
      toast({ type: 'error', message: getErrorMessage(err, 'No se pudo eliminar el impuesto.') })
    } finally {
      setSaving(false)
    }
  }

  return (
    <div>
      {isAdmin && (
        <div className="mb-5">
          <Button onClick={openCreate} className="gap-2"><Plus size={16} /> Nuevo impuesto</Button>
        </div>
      )}
      <div className={`${CARD} overflow-hidden`}>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-slate-50/60 border-b border-slate-100">
                <th className={TH}>Nombre</th>
                <th className={TH}>Porcentaje</th>
                {isAdmin && <th className={`${TH} text-right`}>Acciones</th>}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-50">
              {loading ? (
                [...Array(3)].map((_, i) => (
                  <tr key={i}><td colSpan={3} className="px-6 py-4"><div className="h-4 bg-slate-100 rounded-lg animate-pulse" /></td></tr>
                ))
              ) : taxes.length === 0 ? (
                <tr><td colSpan={3} className="px-6 py-12 text-center text-slate-400">No hay impuestos configurados.</td></tr>
              ) : (
                taxes.map((t) => (
                  <tr key={t.id} className="hover:bg-slate-50/60 transition-colors">
                    <td className="px-6 py-4 font-semibold text-[#1e3a5f]">{t.name}</td>
                    <td className="px-6 py-4">
                      <Badge variant="default"><Percent size={10} className="mr-0.5" />{t.percentage}%</Badge>
                    </td>
                    {isAdmin && (
                      <td className="px-6 py-4 text-right">
                        <div className="flex justify-end gap-1">
                          <button onClick={() => openEdit(t)} className="rounded-xl p-2 text-slate-400 hover:bg-blue-50 hover:text-blue-600 transition-colors" title="Editar">
                            <Pencil size={15} />
                          </button>
                          <button onClick={() => openDelete(t)} className="rounded-xl p-2 text-slate-400 hover:bg-red-50 hover:text-red-600 transition-colors" title="Eliminar">
                            <Trash2 size={15} />
                          </button>
                        </div>
                      </td>
                    )}
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
        <Pagination page={page} totalPages={totalPages} totalElements={totalElements} onChange={setPage} />
      </div>

      <Modal open={modal === 'create' || modal === 'edit'} onClose={closeModal} title={modal === 'create' ? 'Nuevo impuesto' : 'Editar impuesto'} size="sm">
        <div className="space-y-4">
          <Input label="Nombre *" value={form.name} onChange={(e) => setForm((p) => ({ ...p, name: e.target.value }))} placeholder="IVA general" />
          <Input label="Porcentaje *" type="number" min="0" max="100" step="0.01" value={form.percentage} onChange={(e) => setForm((p) => ({ ...p, percentage: e.target.value }))} placeholder="21" />
          <div className="flex gap-3">
            <Button variant="outline" onClick={closeModal} className="flex-1">Cancelar</Button>
            <Button onClick={handleSave} loading={saving} className="flex-1">{modal === 'create' ? 'Crear' : 'Guardar'}</Button>
          </div>
        </div>
      </Modal>
      <Modal open={modal === 'delete'} onClose={closeModal} title="Eliminar impuesto" size="sm">
        <p className="text-sm text-slate-600 mb-5">¿Eliminar el impuesto <strong>{selected?.name}</strong>?</p>
        <div className="flex gap-3">
          <Button variant="outline" onClick={closeModal} className="flex-1">Cancelar</Button>
          <Button variant="danger" onClick={handleDelete} loading={saving} className="flex-1">Eliminar</Button>
        </div>
      </Modal>
    </div>
  )
}

// ───────────────────────── Horarios ─────────────────────────
// El backend devuelve los horarios como List<> plano (sin paginar): hay
// como mucho 7 filas, una por día de la semana.
function HoursTab({ bId, isAdmin }) {
  const toast = useToast()
  const [hours, setHours]   = useState(null)
  const [loading, setLoading] = useState(true)
  const [reload, setReload] = useState(0)

  const [modal, setModal]     = useState(null)
  const [selected, setSelected] = useState(null)
  const [form, setForm]       = useState(emptyHour)
  const [saving, setSaving]   = useState(false)

  useEffect(() => {
    if (!bId) return
    let cancelled = false
    setLoading(true)
    api.get(`/api/businesses/${bId}/hours`)
      .then((r) => { if (!cancelled) setHours([...r.data].sort((a, b) => a.dayOfWeek - b.dayOfWeek)) })
      .catch((err) => {
        if (cancelled) return
        toast({ type: 'error', message: getErrorMessage(err, 'No se pudieron cargar los horarios.') })
        setHours([])
      })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [bId, reload, toast])

  const refresh = () => setReload((v) => v + 1)
  const closeModal = () => { setModal(null); setSelected(null) }
  const openCreate = () => { setForm(emptyHour); setSelected(null); setModal('create') }
  const openEdit = (h) => {
    setSelected(h)
    setForm({
      dayOfWeek: String(h.dayOfWeek),
      startTime: h.startTime?.slice(0, 5) || '09:00',
      endTime:   h.endTime?.slice(0, 5)   || '18:00',
      isClosed:  h.isClosed,
    })
    setModal('edit')
  }
  const openDelete = (h) => { setSelected(h); setModal('delete') }

  const handleSave = async () => {
    if (!form.isClosed && form.startTime >= form.endTime) {
      toast({ type: 'error', message: 'La hora de cierre debe ser posterior a la de apertura.' }); return
    }
    setSaving(true)
    try {
      // Si el día está cerrado, startTime/endTime van a null; si está
      // abierto, ambos son obligatorios y apertura < cierre.
      const payload = {
        dayOfWeek: Number(form.dayOfWeek),
        isClosed:  form.isClosed,
        startTime: form.isClosed ? null : `${form.startTime}:00`,
        endTime:   form.isClosed ? null : `${form.endTime}:00`,
      }
      if (modal === 'create') {
        await api.post(`/api/businesses/${bId}/hours`, payload)
        toast({ type: 'success', message: 'Tramo horario añadido.' })
      } else {
        await api.put(`/api/businesses/${bId}/hours/${selected.id}`, payload)
        toast({ type: 'success', message: 'Tramo horario actualizado.' })
      }
      closeModal(); refresh()
    } catch (err) {
      // Cada día solo admite una fila (restricción UNIQUE business+día).
      toast({ type: 'error', message: getErrorMessage(err, 'No se pudo guardar el horario.') })
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async () => {
    setSaving(true)
    try {
      await api.delete(`/api/businesses/${bId}/hours/${selected.id}`)
      toast({ type: 'success', message: 'Tramo horario eliminado.' })
      closeModal(); refresh()
    } catch (err) {
      toast({ type: 'error', message: getErrorMessage(err, 'No se pudo eliminar el horario.') })
    } finally {
      setSaving(false)
    }
  }

  return (
    <div>
      {isAdmin && (
        <div className="mb-5">
          <Button onClick={openCreate} className="gap-2"><Plus size={16} /> Añadir tramo horario</Button>
        </div>
      )}
      <div className={`${CARD} overflow-hidden`}>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-slate-50/60 border-b border-slate-100">
                <th className={TH}>Día</th>
                <th className={TH}>Apertura</th>
                <th className={TH}>Cierre</th>
                <th className={TH}>Estado</th>
                {isAdmin && <th className={`${TH} text-right`}>Acciones</th>}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-50">
              {loading ? (
                [...Array(5)].map((_, i) => (
                  <tr key={i}><td colSpan={5} className="px-6 py-4"><div className="h-4 bg-slate-100 rounded-lg animate-pulse" /></td></tr>
                ))
              ) : (hours ?? []).length === 0 ? (
                <tr><td colSpan={5} className="px-6 py-12 text-center text-slate-400">No hay horarios configurados.</td></tr>
              ) : (
                hours.map((h) => (
                  <tr key={h.id} className="hover:bg-slate-50/60 transition-colors">
                    <td className="px-6 py-4 font-semibold text-[#1e3a5f]">{DAYS[h.dayOfWeek]}</td>
                    <td className="px-6 py-4 text-slate-500 font-medium">{h.isClosed ? '—' : h.startTime?.slice(0, 5)}</td>
                    <td className="px-6 py-4 text-slate-500 font-medium">{h.isClosed ? '—' : h.endTime?.slice(0, 5)}</td>
                    <td className="px-6 py-4">
                      <Badge variant={h.isClosed ? 'danger' : 'success'}>{h.isClosed ? 'Cerrado' : 'Abierto'}</Badge>
                    </td>
                    {isAdmin && (
                      <td className="px-6 py-4 text-right">
                        <div className="flex justify-end gap-1">
                          <button onClick={() => openEdit(h)} className="rounded-xl p-2 text-slate-400 hover:bg-blue-50 hover:text-blue-600 transition-colors" title="Editar">
                            <Pencil size={15} />
                          </button>
                          <button onClick={() => openDelete(h)} className="rounded-xl p-2 text-slate-400 hover:bg-red-50 hover:text-red-600 transition-colors" title="Eliminar">
                            <Trash2 size={15} />
                          </button>
                        </div>
                      </td>
                    )}
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      <Modal open={modal === 'create' || modal === 'edit'} onClose={closeModal} title={modal === 'create' ? 'Nuevo tramo horario' : 'Editar tramo horario'} size="sm">
        <div className="space-y-4">
          <Select label="Día *" value={form.dayOfWeek} onChange={(e) => setForm((p) => ({ ...p, dayOfWeek: e.target.value }))}>
            {DAYS.slice(1).map((d, i) => <option key={i + 1} value={i + 1}>{d}</option>)}
          </Select>
          <label className="flex items-center gap-2.5 text-sm font-medium text-slate-700 cursor-pointer">
            <input
              type="checkbox"
              checked={form.isClosed}
              onChange={(e) => setForm((p) => ({ ...p, isClosed: e.target.checked }))}
              className="accent-[#1e3a5f] h-4 w-4 rounded"
            />
            El negocio cierra este día
          </label>
          {!form.isClosed && (
            <div className="grid grid-cols-2 gap-3">
              <Input label="Apertura" type="time" value={form.startTime} onChange={(e) => setForm((p) => ({ ...p, startTime: e.target.value }))} />
              <Input label="Cierre" type="time" value={form.endTime} onChange={(e) => setForm((p) => ({ ...p, endTime: e.target.value }))} />
            </div>
          )}
          <div className="flex gap-3">
            <Button variant="outline" onClick={closeModal} className="flex-1">Cancelar</Button>
            <Button onClick={handleSave} loading={saving} className="flex-1">{modal === 'create' ? 'Añadir' : 'Guardar'}</Button>
          </div>
        </div>
      </Modal>
      <Modal open={modal === 'delete'} onClose={closeModal} title="Eliminar horario" size="sm">
        <p className="text-sm text-slate-600 mb-5">¿Eliminar el horario del <strong>{DAYS[selected?.dayOfWeek]}</strong>?</p>
        <div className="flex gap-3">
          <Button variant="outline" onClick={closeModal} className="flex-1">Cancelar</Button>
          <Button variant="danger" onClick={handleDelete} loading={saving} className="flex-1">Eliminar</Button>
        </div>
      </Modal>
    </div>
  )
}

// ───────────────────────── Cabinas ─────────────────────────
function BoothsTab({ bId, isAdmin }) {
  const toast = useToast()
  const { items: booths, page, totalPages, totalElements, loading, setPage, refresh } =
    usePagedFetch(bId ? `/api/businesses/${bId}/booths` : null)

  const [modal, setModal]     = useState(null)
  const [selected, setSelected] = useState(null)
  const [form, setForm]       = useState(emptyBooth)
  const [saving, setSaving]   = useState(false)

  const closeModal = () => { setModal(null); setSelected(null) }
  const openCreate = () => { setForm(emptyBooth); setSelected(null); setModal('create') }
  const openEdit   = (b) => { setSelected(b); setForm({ name: b.name }); setModal('edit') }
  const openDelete = (b) => { setSelected(b); setModal('delete') }

  const handleSave = async () => {
    if (!form.name.trim()) { toast({ type: 'error', message: 'El nombre es obligatorio.' }); return }
    setSaving(true)
    try {
      if (modal === 'create') {
        await api.post(`/api/businesses/${bId}/booths`, { name: form.name })
        toast({ type: 'success', message: 'Cabina creada.' })
      } else {
        await api.put(`/api/businesses/${bId}/booths/${selected.id}`, { name: form.name })
        toast({ type: 'success', message: 'Cabina actualizada.' })
      }
      closeModal(); refresh()
    } catch (err) {
      toast({ type: 'error', message: getErrorMessage(err, 'No se pudo guardar la cabina.') })
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async () => {
    setSaving(true)
    try {
      await api.delete(`/api/businesses/${bId}/booths/${selected.id}`)
      toast({ type: 'success', message: 'Cabina eliminada.' })
      closeModal(); refresh()
    } catch (err) {
      toast({ type: 'error', message: getErrorMessage(err, 'No se pudo eliminar la cabina.') })
    } finally {
      setSaving(false)
    }
  }

  return (
    <div>
      {isAdmin && (
        <div className="mb-5">
          <Button onClick={openCreate} className="gap-2"><Plus size={16} /> Nueva cabina</Button>
        </div>
      )}
      {loading ? (
        <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {[...Array(3)].map((_, i) => <div key={i} className="h-24 bg-white rounded-2xl border border-slate-100 animate-pulse" />)}
        </div>
      ) : booths.length === 0 ? (
        <p className="py-16 text-center text-slate-400">No hay cabinas configuradas.</p>
      ) : (
        <>
          <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {booths.map((b) => (
              <div key={b.id} className="group bg-white rounded-2xl border border-slate-100 p-5 hover:border-blue-200 hover:shadow-[0_2px_12px_-2px_rgba(15,23,42,0.06)] transition">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-gradient-to-br from-indigo-400 to-purple-500 text-white shadow-[0_6px_16px_-6px_rgba(99,102,241,0.4)]">
                      <Store size={17} />
                    </div>
                    <p className="font-bold text-[#1e3a5f]">{b.name}</p>
                  </div>
                  {isAdmin && (
                    <div className="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                      <button onClick={() => openEdit(b)} className="rounded-xl p-1.5 text-slate-400 hover:bg-blue-50 hover:text-blue-600 transition-colors">
                        <Pencil size={14} />
                      </button>
                      <button onClick={() => openDelete(b)} className="rounded-xl p-1.5 text-slate-400 hover:bg-red-50 hover:text-red-600 transition-colors">
                        <Trash2 size={14} />
                      </button>
                    </div>
                  )}
                </div>
              </div>
            ))}
          </div>
          {totalPages > 1 && (
            <div className={`mt-6 ${CARD} overflow-hidden`}>
              <Pagination page={page} totalPages={totalPages} totalElements={totalElements} onChange={setPage} />
            </div>
          )}
        </>
      )}

      <Modal open={modal === 'create' || modal === 'edit'} onClose={closeModal} title={modal === 'create' ? 'Nueva cabina' : 'Editar cabina'} size="sm">
        <div className="space-y-4">
          <Input label="Nombre *" value={form.name} onChange={(e) => setForm({ name: e.target.value })} placeholder="Sala 1" />
          <div className="flex gap-3">
            <Button variant="outline" onClick={closeModal} className="flex-1">Cancelar</Button>
            <Button onClick={handleSave} loading={saving} className="flex-1">{modal === 'create' ? 'Crear' : 'Guardar'}</Button>
          </div>
        </div>
      </Modal>
      <Modal open={modal === 'delete'} onClose={closeModal} title="Eliminar cabina" size="sm">
        <p className="text-sm text-slate-600 mb-5">¿Eliminar la cabina <strong>{selected?.name}</strong>?</p>
        <div className="flex gap-3">
          <Button variant="outline" onClick={closeModal} className="flex-1">Cancelar</Button>
          <Button variant="danger" onClick={handleDelete} loading={saving} className="flex-1">Eliminar</Button>
        </div>
      </Modal>
    </div>
  )
}

// ───────────────────────── Bloqueos de agenda ─────────────────────────
// El backend NO expone PUT para schedule-blocks: solo POST y DELETE. Si un
// bloqueo está mal, se borra y se crea de nuevo.
function BlocksTab({ bId, isAdmin }) {
  const toast = useToast()
  const { items: blocks, page, totalPages, totalElements, loading, setPage, refresh } =
    usePagedFetch(bId ? `/api/businesses/${bId}/schedule-blocks` : null)

  // Datos para los selectores del formulario (no es una vista navegable).
  const [employees, setEmployees] = useState([])
  const [booths, setBooths]       = useState([])
  useEffect(() => {
    if (!bId) return
    Promise.allSettled([
      api.get(`/api/businesses/${bId}/users?size=100`),
      api.get(`/api/businesses/${bId}/booths?size=100`),
    ]).then(([u, b]) => {
      setEmployees(u.status === 'fulfilled' ? u.value.data.content : [])
      setBooths(b.status === 'fulfilled' ? b.value.data.content : [])
    })
  }, [bId])

  const [modal, setModal]     = useState(null)   // 'create' | 'delete'
  const [selected, setSelected] = useState(null)
  const [form, setForm]       = useState(emptyBlock)
  const [saving, setSaving]   = useState(false)

  const closeModal = () => { setModal(null); setSelected(null) }
  const openCreate = () => { setForm(emptyBlock); setModal('create') }
  const openDelete = (b) => { setSelected(b); setModal('delete') }

  const handleCreate = async () => {
    if (form.type === 'employee' && !form.membershipId) { toast({ type: 'error', message: 'Selecciona un empleado.' }); return }
    if (form.type === 'booth' && !form.boothId) { toast({ type: 'error', message: 'Selecciona una cabina.' }); return }
    if (!form.startDate || !form.endDate) { toast({ type: 'error', message: 'Las fechas son obligatorias.' }); return }
    if (form.startDate > form.endDate) { toast({ type: 'error', message: 'La fecha de fin debe ser igual o posterior al inicio.' }); return }
    setSaving(true)
    try {
      // El backend exige que membershipId y boothId NO vengan ambos a la
      // vez: global = ninguno, por empleado = solo membershipId, por
      // cabina = solo boothId. El selector "tipo" garantiza esa exclusión.
      await api.post(`/api/businesses/${bId}/schedule-blocks`, {
        membershipId: form.type === 'employee' ? Number(form.membershipId) : null,
        boothId:      form.type === 'booth' ? Number(form.boothId) : null,
        startDate: form.startDate,
        endDate: form.endDate,
        reason: form.reason || null,
      })
      toast({ type: 'success', message: 'Bloqueo creado.' })
      closeModal(); refresh()
    } catch (err) {
      toast({ type: 'error', message: getErrorMessage(err, 'No se pudo crear el bloqueo.') })
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async () => {
    setSaving(true)
    try {
      await api.delete(`/api/businesses/${bId}/schedule-blocks/${selected.id}`)
      toast({ type: 'success', message: 'Bloqueo eliminado.' })
      closeModal(); refresh()
    } catch (err) {
      toast({ type: 'error', message: getErrorMessage(err, 'No se pudo eliminar el bloqueo.') })
    } finally {
      setSaving(false)
    }
  }

  const blockBadge = (b) => {
    if (b.membershipId) return <Badge variant="info"><User size={10} className="mr-0.5" />{b.userFullName}</Badge>
    if (b.boothId)      return <Badge variant="purple"><Store size={10} className="mr-0.5" />{b.boothName}</Badge>
    return <Badge variant="default"><Globe size={10} className="mr-0.5" />Global</Badge>
  }

  return (
    <div>
      {isAdmin && (
        <div className="mb-5">
          <Button onClick={openCreate} className="gap-2"><Plus size={16} /> Nuevo bloqueo</Button>
        </div>
      )}
      <div className={`${CARD} overflow-hidden`}>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-slate-50/60 border-b border-slate-100">
                <th className={TH}>Tipo</th>
                <th className={TH}>Desde</th>
                <th className={TH}>Hasta</th>
                <th className={`${TH} hidden md:table-cell`}>Motivo</th>
                {isAdmin && <th className={`${TH} text-right`}>Acciones</th>}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-50">
              {loading ? (
                [...Array(3)].map((_, i) => (
                  <tr key={i}><td colSpan={5} className="px-6 py-4"><div className="h-4 bg-slate-100 rounded-lg animate-pulse" /></td></tr>
                ))
              ) : blocks.length === 0 ? (
                <tr><td colSpan={5} className="px-6 py-12 text-center text-slate-400">No hay bloqueos de agenda.</td></tr>
              ) : (
                blocks.map((b) => (
                  <tr key={b.id} className="hover:bg-slate-50/60 transition-colors">
                    <td className="px-6 py-4">{blockBadge(b)}</td>
                    <td className="px-6 py-4 font-semibold text-[#1e3a5f]">{fmtDate(b.startDate)}</td>
                    <td className="px-6 py-4 font-semibold text-[#1e3a5f]">{fmtDate(b.endDate)}</td>
                    <td className="px-6 py-4 text-slate-500 hidden md:table-cell">{b.reason || <span className="text-slate-300">—</span>}</td>
                    {isAdmin && (
                      <td className="px-6 py-4 text-right">
                        <button onClick={() => openDelete(b)} className="rounded-xl p-2 text-slate-400 hover:bg-red-50 hover:text-red-600 transition-colors" title="Eliminar">
                          <Trash2 size={15} />
                        </button>
                      </td>
                    )}
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
        <Pagination page={page} totalPages={totalPages} totalElements={totalElements} onChange={setPage} />
      </div>

      <Modal open={modal === 'create'} onClose={closeModal} title="Nuevo bloqueo de agenda" size="sm">
        <div className="space-y-4">
          <Select
            label="Tipo de bloqueo *"
            value={form.type}
            onChange={(e) => setForm((p) => ({ ...p, type: e.target.value, membershipId: '', boothId: '' }))}
          >
            <option value="global">Global — todo el negocio</option>
            <option value="employee">Por empleado</option>
            <option value="booth">Por cabina</option>
          </Select>
          {form.type === 'employee' && (
            <Select label="Empleado *" value={form.membershipId} onChange={(e) => setForm((p) => ({ ...p, membershipId: e.target.value }))}>
              <option value="">Selecciona empleado</option>
              {employees.map((e) => <option key={e.id} value={e.id}>{e.fullName}</option>)}
            </Select>
          )}
          {form.type === 'booth' && (
            <Select label="Cabina *" value={form.boothId} onChange={(e) => setForm((p) => ({ ...p, boothId: e.target.value }))}>
              <option value="">Selecciona cabina</option>
              {booths.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
            </Select>
          )}
          <div className="grid grid-cols-2 gap-3">
            <Input label="Desde *" type="date" value={form.startDate} onChange={(e) => setForm((p) => ({ ...p, startDate: e.target.value }))} />
            <Input label="Hasta *" type="date" value={form.endDate} onChange={(e) => setForm((p) => ({ ...p, endDate: e.target.value }))} />
          </div>
          <Input label="Motivo" value={form.reason} onChange={(e) => setForm((p) => ({ ...p, reason: e.target.value }))} placeholder="Vacaciones, festivo, mantenimiento…" />
          <div className="flex gap-3">
            <Button variant="outline" onClick={closeModal} className="flex-1">Cancelar</Button>
            <Button onClick={handleCreate} loading={saving} className="flex-1">Crear bloqueo</Button>
          </div>
        </div>
      </Modal>
      <Modal open={modal === 'delete'} onClose={closeModal} title="Eliminar bloqueo" size="sm">
        <p className="text-sm text-slate-600 mb-5">
          ¿Eliminar el bloqueo del <strong>{fmtDate(selected?.startDate)}</strong> al <strong>{fmtDate(selected?.endDate)}</strong>?
        </p>
        <div className="flex gap-3">
          <Button variant="outline" onClick={closeModal} className="flex-1">Cancelar</Button>
          <Button variant="danger" onClick={handleDelete} loading={saving} className="flex-1">Eliminar</Button>
        </div>
      </Modal>
    </div>
  )
}

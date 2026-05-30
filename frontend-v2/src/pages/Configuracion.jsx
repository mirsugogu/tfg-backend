// Configuracion del negocio: datos generales, horarios, impuestos, cabinas y bloqueos
import { useEffect, useMemo, useState } from 'react'
import {
  Plus, Pencil, Trash2, Archive, ArchiveRestore, Percent, Clock, Save, Building2, Store,
  CalendarX2, User, MapPin, Globe, Mail, Phone, RefreshCw, Search,
  ExternalLink, AlertCircle, Info,
} from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Input, INPUT_SANITIZE } from '@/components/ui/Input'
import { Select } from '@/components/ui/Select'
import { Modal } from '@/components/ui/Modal'
import { Badge } from '@/components/ui/Badge'
import { Pagination } from '@/components/ui/Pagination'
import { useAuth } from '@/context/AuthContext'
import { useToast } from '@/components/ui/Toast'
import { usePagedFetch } from '@/hooks/usePagedFetch'
import api, { getErrorMessage } from '@/lib/api'

const DAYS       = ['', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo']
const DAYS_SHORT = ['', 'L', 'M', 'X', 'J', 'V', 'S', 'D']

const CARD = 'bg-white rounded-2xl border border-slate-100/80 shadow-[0_2px_12px_-2px_rgba(15,23,42,0.06)]'
const TH   = 'px-6 py-3.5 text-left text-[11px] font-semibold text-slate-400 uppercase tracking-wider'

const emptyTax   = { name: '', percentage: '' }
const emptyHour  = { dayOfWeek: '1', startTime: '09:00', endTime: '18:00', isClosed: false }

/** Cabecera azul con descripcion breve de la pestana activa */
function SectionHint({ title, children }) {
  return (
    <div className="mb-5 flex items-start gap-3 rounded-2xl border border-blue-100 bg-blue-50/50 p-4">
      <Info size={18} className="mt-0.5 shrink-0 text-blue-600" />
      <div className="text-sm text-slate-600">
        <p className="font-semibold text-[#1e3a5f]">{title}</p>
        <p className="mt-1 leading-relaxed">{children}</p>
      </div>
    </div>
  )
}
const emptyBooth = { name: '', color: '' }

// [L] Paleta de la cabina en el calendario Mismos nombres que en
// membershipscolor y components/calendar/utilsjs Varias cabinas pueden
// compartir color a proposito
const BOOTH_COLOR_OPTIONS = [
  { name: 'cyan', cls: 'bg-cyan-500' }, { name: 'amber', cls: 'bg-amber-500' },
  { name: 'emerald', cls: 'bg-emerald-500' }, { name: 'indigo', cls: 'bg-indigo-500' },
  { name: 'pink', cls: 'bg-pink-500' }, { name: 'sky', cls: 'bg-sky-500' },
  { name: 'violet', cls: 'bg-violet-500' }, { name: 'teal', cls: 'bg-teal-500' },
]
const BOOTH_COLOR_CLS = Object.fromEntries(BOOTH_COLOR_OPTIONS.map((o) => [o.name, o.cls]))
const emptyBlock = { type: 'global', membershipId: '', boothId: '', startDate: '', endDate: '', reason: '' }

const fmtDate = (d) =>
  d ? new Date(`${d}T00:00:00`).toLocaleDateString('es-ES', { day: '2-digit', month: 'short', year: 'numeric' }) : '—'

const fmtSinceShort = (iso) =>
  iso ? new Date(iso).toLocaleDateString('es-ES', { month: 'short', year: 'numeric' }).replace('.', '') : '—'

const BOOTH_PALETTE = [
  'from-indigo-400 to-purple-500',
  'from-cyan-400 to-blue-500',
  'from-emerald-400 to-teal-500',
  'from-amber-400 to-orange-500',
  'from-pink-400 to-rose-500',
  'from-sky-400 to-blue-500',
]
const boothColor = (id) => BOOTH_PALETTE[(id ?? 0) % BOOTH_PALETTE.length]

/** Configuracion del negocio por pestanas: datos, horario, impuestos, cabinas y bloqueos */
export default function Configuracion() {
  const { user } = useAuth()
  const bId = user?.businessId
  const isAdmin = user?.role === 'ADMIN'

  const [tab, setTab] = useState(() => localStorage.getItem('optima_cfg_tab') || 'business')
  useEffect(() => { localStorage.setItem('optima_cfg_tab', tab) }, [tab])

  const tabs = [
    { key: 'business', label: 'Negocio',   icon: Building2 },
    { key: 'taxes',    label: 'Impuestos', icon: Percent },
    { key: 'hours',    label: 'Horarios',  icon: Clock },
    { key: 'booths',   label: 'Cabinas',   icon: Store },
    { key: 'blocks',   label: 'Bloqueos',  icon: CalendarX2 },
  ]

  return (
    <div className="px-4 sm:px-6 lg:px-8 xl:px-10 py-8">
      <div className="mb-8">
        <h1 className="text-3xl font-bold text-[#1e3a5f] tracking-tight">Configuración</h1>
        <p className="text-sm text-slate-500 mt-1.5 font-medium">Gestiona los datos y parámetros de tu negocio</p>
      </div>

      <div className="mb-6 overflow-x-auto pb-1">
        <div className="flex items-center gap-1 bg-slate-100 rounded-2xl p-1.5 w-fit max-w-full overflow-x-auto">
          {tabs.map(({ key, label, icon: Icon }) => (
            <button
              key={key}
              onClick={() => setTab(key)}
              className={`flex shrink-0 items-center gap-2 rounded-xl px-5 py-2.5 text-sm font-semibold transition-all ${
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


const isEmail = (v) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(String(v || '').trim())

/** Pestana de datos generales del negocio */
function BusinessTab({ bId, isAdmin }) {
  const toast = useToast()
  const [biz, setBiz]   = useState(null)
  const [form, setForm] = useState(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving]   = useState(false)
  const [reload, setReload]   = useState(0)
  const [dangerOpen, setDangerOpen]     = useState(false)
  const [dangerSaving, setDangerSaving] = useState(false)

  const refresh = () => setReload((v) => v + 1)

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
    if (!isEmail(form.email)) { toast({ type: 'error', message: 'Formato de email no válido.' }); return }
    setSaving(true)
    try {
      const { data } = await api.put(`/api/businesses/${bId}`, {
        ...form,
        appointmentInterval: Number(form.appointmentInterval),
      })
      setBiz(data)
      toast({ type: 'success', message: 'Datos del negocio actualizados.' })
    } catch (err) {
      toast({ type: 'error', message: getErrorMessage(err, 'No se pudo guardar.') })
    } finally { setSaving(false) }
  }

  const handleDeactivate = async () => {
    setDangerSaving(true)
    try {
      await api.delete(`/api/businesses/${bId}`)
      toast({ type: 'success', message: 'El negocio se ha dado de baja.' })
      setDangerOpen(false)
      refresh()
    } catch (err) {
      toast({ type: 'error', message: getErrorMessage(err, 'No se pudo dar de baja el negocio.') })
    } finally { setDangerSaving(false) }
  }

  const handleReactivate = async () => {
    setDangerSaving(true)
    try {
      const { data } = await api.patch(`/api/businesses/${bId}/reactivate`)
      setBiz(data)
      toast({ type: 'success', message: 'El negocio se ha reactivado.' })
    } catch (err) {
      toast({ type: 'error', message: getErrorMessage(err, 'No se pudo reactivar el negocio.') })
    } finally { setDangerSaving(false) }
  }

  if (loading || !form) {
    return <div className={`${CARD} p-6 space-y-4`}>{[...Array(5)].map((_, i) => <div key={i} className="h-11 rounded-xl bg-slate-100 animate-pulse" />)}</div>
  }

  const mapsHref = biz?.latitude != null && biz?.longitude != null
    ? `https://www.google.com/maps?q=${biz.latitude},${biz.longitude}`
    : `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent([biz?.address, biz?.city, biz?.country].filter(Boolean).join(', '))}`

  return (
    <div className="space-y-5 max-w-5xl">
      {!biz.isActive && (
        <div className="rounded-2xl border border-amber-200 bg-amber-50 px-5 py-4 flex flex-wrap items-center gap-3">
          <p className="text-sm text-amber-800 font-medium flex-1 min-w-[220px]">
            Este negocio está <strong>dado de baja</strong>. Reactívalo para volver a operar con normalidad.
          </p>
          {isAdmin && <Button onClick={handleReactivate} loading={dangerSaving} size="sm">Reactivar negocio</Button>}
        </div>
      )}

      <div className={`${CARD} p-4 flex flex-wrap items-center gap-3`}>
        <a href={`mailto:${biz.email}`} className="inline-flex items-center gap-1.5 text-xs font-medium px-2.5 py-1.5 rounded-lg bg-blue-50/50 text-blue-700 hover:bg-blue-100 transition truncate max-w-full">
          <Mail size={12} /> {biz.email}
        </a>
        {biz.phone && (
          <a href={`tel:${biz.phone.replace(/\s/g, '')}`} className="inline-flex items-center gap-1.5 text-xs font-medium font-mono px-2.5 py-1.5 rounded-lg bg-slate-50 text-slate-700 hover:bg-slate-100 transition">
            <Phone size={12} /> {biz.phone}
          </a>
        )}
        {(biz.address || biz.city) && (
          <a href={mapsHref} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1.5 text-xs font-medium px-2.5 py-1.5 rounded-lg bg-emerald-50/60 text-emerald-700 hover:bg-emerald-100 transition truncate">
            <MapPin size={12} /> Ver en mapa <ExternalLink size={11} />
          </a>
        )}
        <span className="ml-auto text-[11px] text-slate-400 inline-flex items-center gap-1">
          Creado {fmtSinceShort(biz.createdAt)}
        </span>
      </div>

      <div className={`${CARD} p-6 space-y-6`}>
        <div className="grid sm:grid-cols-2 gap-4">
          <Input label="Nombre *" value={form.name} onChange={(e) => set('name', e.target.value)} disabled={!isAdmin} maxLength={150} />
          <Input label="Email *" type="email" value={form.email} onChange={(e) => set('email', e.target.value)} disabled={!isAdmin} maxLength={150} />
          <Input label="Teléfono" value={form.phone} onChange={(e) => set('phone', e.target.value)} disabled={!isAdmin} sanitize={INPUT_SANITIZE.PHONE} inputMode="tel" maxLength={20} />
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
          <Input label="Identificador (slug)" value={biz?.slug || ''} disabled maxLength={150} />
          <p className="text-[11px] text-slate-400 mt-1.5">El identificador se fija al crear el negocio y no se puede cambiar.</p>
        </div>

        <div className="border-t border-slate-100 pt-5">
          <p className="text-xs font-bold text-slate-400 uppercase tracking-wider mb-4">Ubicación</p>
          <div className="grid sm:grid-cols-2 gap-4">
            <Input label="Dirección" value={form.address} onChange={(e) => set('address', e.target.value)} disabled={!isAdmin} maxLength={255} />
            <Input label="Ciudad" value={form.city} onChange={(e) => set('city', e.target.value)} disabled={!isAdmin} maxLength={100} />
            <Input label="Código postal" value={form.postalCode} onChange={(e) => set('postalCode', e.target.value)} disabled={!isAdmin} sanitize={INPUT_SANITIZE.DIGITS_ONLY} inputMode="numeric" maxLength={10} />
            <Input label="Provincia / Estado" value={form.state} onChange={(e) => set('state', e.target.value)} disabled={!isAdmin} maxLength={100} />
            <Input label="País" value={form.country} onChange={(e) => set('country', e.target.value)} disabled={!isAdmin} maxLength={100} />
          </div>
          {biz?.latitude != null && biz?.longitude != null && (
            <p className="text-[11px] text-slate-400 mt-3 flex items-center gap-1">
              <MapPin size={11} /> Coordenadas {biz.latitude}, {biz.longitude} — resueltas automáticamente desde la dirección.
            </p>
          )}
        </div>

        {isAdmin ? (
          <div className="pt-1">
            <Button onClick={handleSave} loading={saving} className="gap-2"><Save size={16} /> Guardar cambios</Button>
          </div>
        ) : (
          <p className="text-xs text-slate-400">Solo un administrador puede modificar estos datos.</p>
        )}
      </div>

      {isAdmin && biz.isActive && (
        <div className="rounded-2xl border border-red-200 bg-red-50/50 p-6">
          <p className="text-sm font-bold text-red-700">Zona de peligro</p>
          <p className="text-xs text-slate-500 mt-1 mb-4">Dar de baja el negocio lo desactiva: nadie podrá iniciar sesión ni operar con él hasta reactivarlo.</p>
          <Button variant="outline-danger" size="sm" onClick={() => setDangerOpen(true)} className="gap-2">
            <Trash2 size={14} /> Dar de baja el negocio
          </Button>
        </div>
      )}

      <Modal open={dangerOpen} onClose={() => setDangerOpen(false)} title="Dar de baja el negocio" size="sm">
        <div className="space-y-5">
          <div className="flex items-start gap-3 rounded-2xl bg-red-50 border border-red-100 px-4 py-4">
            <Trash2 size={20} className="text-red-500 shrink-0 mt-0.5" />
            <p className="text-sm text-red-700 leading-snug">¿Seguro que quieres dar de baja <strong>{biz?.name}</strong>? Podrás reactivarlo después desde esta misma pantalla.</p>
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


/** Pestana de impuestos con gestion y archivado */
function TaxesTab({ bId, isAdmin }) {
  const toast = useToast()
  const [pageSize, setPageSize] = useState(() => parseInt(localStorage.getItem('optima_cfg_taxes_size') || '20', 10))
  useEffect(() => { localStorage.setItem('optima_cfg_taxes_size', String(pageSize)) }, [pageSize])
  const [sortDir, setSortDir] = useState(() => localStorage.getItem('optima_cfg_taxes_sort') || 'asc')
  useEffect(() => { localStorage.setItem('optima_cfg_taxes_sort', sortDir) }, [sortDir])

  // Vista activos / archivados - sin persistir
  const [view, setView] = useState('active')
  const isArchived = view === 'archived'
  const queryParams = useMemo(() => ({ sort: `name,${sortDir}`, active: view === 'active' }), [sortDir, view])

  const { items: taxes, page, totalPages, totalElements, loading, setPage, refresh } =
    usePagedFetch(bId ? `/api/businesses/${bId}/taxes` : null, { size: pageSize, params: queryParams })

  // Carga del catalogo para contar uso por impuesto (sin rutas dedicado)
  const [services, setServices] = useState([])
  useEffect(() => {
    if (!bId) return
    api.get(`/api/businesses/${bId}/services?size=100`).then((r) => setServices(r.data.content ?? [])).catch(() => {})
  }, [bId])
  const usageBy = useMemo(() => {
    const m = new Map()
    services.forEach((s) => m.set(s.taxId, (m.get(s.taxId) ?? 0) + 1))
    return m
  }, [services])

  const avgPct = useMemo(() => taxes.length === 0 ? null : taxes.reduce((a, t) => a + Number(t.percentage), 0) / taxes.length, [taxes])

  const [modal, setModal]       = useState(null)
  const [selected, setSelected] = useState(null)
  const [form, setForm]         = useState(emptyTax)
  const [saving, setSaving]     = useState(false)

  const closeModal = () => { setModal(null); setSelected(null) }
  const openCreate  = () => { setForm(emptyTax); setSelected(null); setModal('create') }
  const openEdit    = (t) => { setSelected(t); setForm({ name: t.name, percentage: String(t.percentage) }); setModal('edit') }
  const openArchive = (t) => { setSelected(t); setModal('archive') }

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
    } finally { setSaving(false) }
  }

  const handleArchive = async () => {
    setSaving(true)
    try {
      await api.delete(`/api/businesses/${bId}/taxes/${selected.id}`)
      toast({ type: 'success', message: 'Impuesto archivado.' })
      closeModal(); refresh()
    } catch (err) {
      toast({ type: 'error', message: getErrorMessage(err, 'No se pudo archivar el impuesto.') })
    } finally { setSaving(false) }
  }

  // Restaurar es un clic directo (no destructivo): sin ventana de confirmacion
  const handleReactivate = async (tax) => {
    try {
      await api.patch(`/api/businesses/${bId}/taxes/${tax.id}/reactivate`)
      toast({ type: 'success', message: 'Impuesto restaurado.' })
      refresh()
    } catch (err) {
      toast({ type: 'error', message: getErrorMessage(err, 'No se pudo restaurar el impuesto.') })
    }
  }

  return (
    <div>
      <SectionHint title="¿Qué son los impuestos?">
        Cada servicio del catálogo puede llevar asociado un impuesto
        (IVA, IGIC…). El total de cada cita se calcula con el porcentaje
        vigente en el momento de la reserva. Los impuestos archivados ya
        no se pueden asignar a servicios nuevos, pero las citas que los
        usaron conservan el valor original.
      </SectionHint>
      <Toolbar
        rightCount={totalElements}
        rightLabel={isArchived
          ? `impuesto${totalElements === 1 ? '' : 's'} archivado${totalElements === 1 ? '' : 's'}`
          : `impuesto${totalElements === 1 ? '' : 's'}`}
        extraStats={!isArchived && avgPct != null ? `IVA medio ${avgPct.toFixed(1)}%` : null}
        filter={<ArchiveViewToggle view={view} onChange={setView} />}
        sortOptions={[{ value: 'asc', label: 'Nombre A → Z' }, { value: 'desc', label: 'Nombre Z → A' }]}
        sortValue={sortDir} onSortChange={setSortDir}
        pageSize={pageSize} onPageSize={(v) => { setPageSize(v); setPage(0) }}
        onRefresh={refresh} loading={loading}
        primary={isAdmin && <Button onClick={openCreate} className="gap-2"><Plus size={16} /> Nuevo impuesto</Button>}
      />

      <div className={`${CARD} overflow-hidden`}>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-slate-50/60 border-b border-slate-100">
                <th className={TH}>Nombre</th>
                <th className={TH}>Porcentaje</th>
                <th className={TH}>Servicios que lo usan</th>
                {isAdmin && <th className={`${TH} text-right`}>Acciones</th>}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-50">
              {loading ? (
                [...Array(3)].map((_, i) => (
                  <tr key={i}><td colSpan={4} className="px-6 py-4"><div className="h-4 bg-slate-100 rounded-lg animate-pulse" /></td></tr>
                ))
              ) : taxes.length === 0 ? (
                <tr><td colSpan={4} className="px-6 py-12 text-center text-slate-400">
                  {isArchived ? 'No hay impuestos archivados.' : 'No hay impuestos configurados.'}
                </td></tr>
              ) : (
                taxes.map((t) => {
                  const count = usageBy.get(t.id) ?? 0
                  return (
                    <tr key={t.id} className="hover:bg-slate-50/60 transition-colors">
                      <td className="px-6 py-4 font-semibold text-[#1e3a5f]">{t.name}</td>
                      <td className="px-6 py-4">
                        <Badge variant="default"><Percent size={10} className="mr-0.5" />{t.percentage}%</Badge>
                      </td>
                      <td className="px-6 py-4 text-slate-500 text-xs">
                        {count > 0 ? `${count} servicio${count === 1 ? '' : 's'}` : <span className="text-slate-300">— ninguno</span>}
                      </td>
                      {isAdmin && (
                        <td className="px-6 py-4 text-right">
                          {isArchived ? (
                            <div className="flex justify-end">
                              <Button variant="success" size="sm" onClick={() => handleReactivate(t)} className="gap-1.5">
                                <ArchiveRestore size={14} /> Restaurar
                              </Button>
                            </div>
                          ) : (
                            <div className="flex justify-end gap-1">
                              <button onClick={() => openEdit(t)} className="rounded-xl p-2 text-slate-400 hover:bg-blue-50 hover:text-blue-600 transition" title="Editar"><Pencil size={15} /></button>
                              <button onClick={() => openArchive(t)} className="rounded-xl p-2 text-slate-400 hover:bg-amber-50 hover:text-amber-600 transition" title="Archivar"><Archive size={15} /></button>
                            </div>
                          )}
                        </td>
                      )}
                    </tr>
                  )
                })
              )}
            </tbody>
          </table>
        </div>
        <Pagination page={page} totalPages={totalPages} totalElements={totalElements} onChange={setPage} />
      </div>

      <Modal open={modal === 'create' || modal === 'edit'} onClose={closeModal} title={modal === 'create' ? 'Nuevo impuesto' : 'Editar impuesto'} size="sm">
        <div className="space-y-4">
          <Input label="Nombre *" value={form.name} onChange={(e) => setForm((p) => ({ ...p, name: e.target.value }))} placeholder="IVA general" maxLength={50} />
          <Input label="Porcentaje *" type="number" min="0" max="100" step="0.01" value={form.percentage} onChange={(e) => setForm((p) => ({ ...p, percentage: e.target.value }))} placeholder="21" />
          <div className="flex gap-3">
            <Button variant="outline" onClick={closeModal} className="flex-1">Cancelar</Button>
            <Button onClick={handleSave} loading={saving} className="flex-1">{modal === 'create' ? 'Crear' : 'Guardar'}</Button>
          </div>
        </div>
      </Modal>
      <Modal open={modal === 'archive'} onClose={closeModal} title="Archivar impuesto" size="sm">
        <div className="space-y-5">
          <div className="flex items-start gap-3 rounded-2xl bg-amber-50 border border-amber-100 px-4 py-4">
            <Archive size={20} className="text-amber-600 shrink-0 mt-0.5" />
            <p className="text-sm text-amber-800 leading-snug">
              ¿Archivar el impuesto <strong>{selected?.name}</strong>?
              {(usageBy.get(selected?.id) ?? 0) > 0 && ` Hay ${usageBy.get(selected?.id)} servicio(s) usándolo; el backend bloqueará la acción si tiene dependencias activas.`}
            </p>
          </div>
          <div className="flex gap-3">
            <Button variant="outline" onClick={closeModal} className="flex-1">Cancelar</Button>
            <Button variant="danger" onClick={handleArchive} loading={saving} className="flex-1">Archivar</Button>
          </div>
        </div>
      </Modal>
    </div>
  )
}


/** Pestana del horario semanal de apertura del negocio */
function HoursTab({ bId, isAdmin }) {
  const toast = useToast()
  const [hours, setHours] = useState(null)
  const [loading, setLoading] = useState(true)
  const [reload, setReload] = useState(0)

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

  const [modal, setModal]       = useState(null)
  const [selected, setSelected] = useState(null)
  const [form, setForm]         = useState(emptyHour)
  const [saving, setSaving]     = useState(false)

  const closeModal = () => { setModal(null); setSelected(null) }
  const openCreate = () => { setForm(emptyHour); setSelected(null); setModal('create') }
  const openEdit = (h) => {
    setSelected(h)
    setForm({
      dayOfWeek: String(h.dayOfWeek),
      startTime: h.startTime?.slice(0, 5) || '09:00',
      endTime:   h.endTime?.slice(0, 5) || '18:00',
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
      toast({ type: 'error', message: getErrorMessage(err, 'No se pudo guardar el horario.') })
    } finally { setSaving(false) }
  }

  const handleDelete = async () => {
    setSaving(true)
    try {
      await api.delete(`/api/businesses/${bId}/hours/${selected.id}`)
      toast({ type: 'success', message: 'Tramo horario eliminado.' })
      closeModal(); refresh()
    } catch (err) {
      toast({ type: 'error', message: getErrorMessage(err, 'No se pudo eliminar el horario.') })
    } finally { setSaving(false) }
  }

  // Quick action: clona los tramos del lunes en Mar-Vie Sobrescribe: borra
  // antes los tramos de cada dia y crea los del lunes (turno partido se copia
  // completo) DELETE antes que POST porque el servidor valida solape al crear
  // (409) Mismo patron que EmpleadoshandleCopyMonToWeek
  const handleCopyMonToFriday = async () => {
    const all = hours ?? []
    const mondayTramos = all.filter((h) => h.dayOfWeek === 1 && !h.isClosed)
    if (mondayTramos.length === 0) {
      toast({ type: 'error', message: 'Configura primero el horario del lunes.' }); return
    }
    setSaving(true)
    try {
      for (let d = 2; d <= 5; d++) {
        for (const existing of all.filter((h) => h.dayOfWeek === d)) {
          await api.delete(`/api/businesses/${bId}/hours/${existing.id}`)
        }
        for (const m of mondayTramos) {
          await api.post(`/api/businesses/${bId}/hours`, {
            dayOfWeek: d,
            isClosed: false,
            startTime: m.startTime,
            endTime: m.endTime,
          })
        }
      }
      toast({ type: 'success', message: 'Horario del lunes copiado a martes–viernes.' })
      refresh()
    } catch (err) {
      toast({ type: 'error', message: getErrorMessage(err, 'No se pudo copiar el horario.') })
      refresh()
    } finally { setSaving(false) }
  }

  // Marca el fin de semana como cerrado
  const handleCloseWeekend = async () => {
    const all = hours ?? []
    setSaving(true)
    try {
      for (let d = 6; d <= 7; d++) {
        for (const existing of all.filter((h) => h.dayOfWeek === d)) {
          await api.delete(`/api/businesses/${bId}/hours/${existing.id}`)
        }
        await api.post(`/api/businesses/${bId}/hours`, {
          dayOfWeek: d, isClosed: true, startTime: null, endTime: null,
        })
      }
      toast({ type: 'success', message: 'Fin de semana marcado como cerrado.' })
      refresh()
    } catch (err) {
      toast({ type: 'error', message: getErrorMessage(err, 'No se pudo actualizar.') })
      refresh()
    } finally { setSaving(false) }
  }

  // Total weekly hours
  const totalWeekly = useMemo(() => {
    if (!hours) return 0
    return hours.reduce((acc, h) => {
      if (h.isClosed || !h.startTime || !h.endTime) return acc
      const ms = new Date(`1970-01-01T${h.endTime}`) - new Date(`1970-01-01T${h.startTime}`)
      return acc + ms / 3600000
    }, 0)
  }, [hours])

  // Rango visual de la rejilla, adaptativo al horario realmente
  // configurado Si el negocio abre 2:00-22:00 la barra ya no se
  // calcula sobre 8-22 (lo que producia width > 100% y desbordaba la
  // tarjeta) sino sobre 2-22 Defaults 8-22 si no hay nada que pintar
  // Cap defensivo [0, 24] por si alguna fila tiene una hora rara
  const { DAY_START, DAY_END } = useMemo(() => {
    let minH = 24, maxH = 0
    ;(hours ?? []).forEach((h) => {
      if (h.isClosed || !h.startTime || !h.endTime) return
      const [sh, sm] = h.startTime.slice(0, 5).split(':').map(Number)
      const [eh, em] = h.endTime.slice(0, 5).split(':').map(Number)
      const s = sh + sm / 60
      const e = eh + em / 60
      if (s < minH) minH = s
      if (e > maxH) maxH = e
    })
    if (minH === 24 || maxH === 0) return { DAY_START: 8, DAY_END: 22 }
    return {
      DAY_START: Math.max(0, Math.floor(minH)),
      DAY_END: Math.min(24, Math.ceil(maxH)),
    }
  }, [hours])
  const total = DAY_END - DAY_START

  return (
    <div>
      <SectionHint title="¿Cómo funciona el horario del negocio?">
        Define a qué hora abre y cierra cada día. El calendario respeta
        este horario en toda la aplicación: pinta gris los huecos fuera
        de horario y no permite crear citas ahí. Admite turno partido
        (mañana y tarde): añade dos tramos en el mismo día.
      </SectionHint>
      <div className="mb-4 flex flex-wrap gap-2 items-center">
        {isAdmin && (
          <>
            <button onClick={handleCopyMonToFriday} disabled={saving} className="text-xs font-semibold px-3 py-1.5 rounded-full bg-blue-50 text-blue-700 border border-blue-100 hover:bg-blue-100 transition disabled:opacity-40">Copiar L → V</button>
            <button onClick={handleCloseWeekend} disabled={saving} className="text-xs font-semibold px-3 py-1.5 rounded-full bg-slate-50 text-slate-600 border border-slate-100 hover:bg-slate-100 transition disabled:opacity-40">Fin de semana cerrado</button>
          </>
        )}
        <span className="ml-auto text-xs text-slate-500"><strong className="text-[#1e3a5f] tabular-nums">{totalWeekly.toFixed(0)} h</strong> semanales</span>
        {isAdmin && (
          <Button onClick={openCreate} className="gap-2" size="sm"><Plus size={14} /> Añadir tramo</Button>
        )}
      </div>

      {loading ? (
        <div className={`${CARD} p-5 space-y-2`}>{[...Array(7)].map((_, i) => <div key={i} className="h-8 bg-slate-100 rounded-md animate-pulse" />)}</div>
      ) : (
        <div className={`${CARD} p-5`}>
          <div className="space-y-2">
            {[1, 2, 3, 4, 5, 6, 7].flatMap((d) => {
              const dayHours = (hours ?? [])
                .filter((hh) => hh.dayOfWeek === d)
                .sort((a, b) => (a.startTime || '').localeCompare(b.startTime || ''))
              if (dayHours.length === 0) {
                return [{ key: `empty-${d}`, h: null, day: d, isFirstOfDay: true }]
              }
              return dayHours.map((hh, idx) => ({
                key: `h-${hh.id}`,
                h: hh,
                day: d,
                isFirstOfDay: idx === 0,
              }))
            }).map(({ key, h, day, isFirstOfDay }) => {
              const isToday = (new Date().getDay() === 0 ? 7 : new Date().getDay()) === day
              let bar
              if (h && !h.isClosed && h.startTime && h.endTime) {
                const [sh, sm] = h.startTime.slice(0, 5).split(':').map(Number)
                const [eh, em] = h.endTime.slice(0, 5).split(':').map(Number)
                const startH = sh + sm / 60 - DAY_START
                const endH   = eh + em / 60 - DAY_START
                const left   = Math.max(0, (startH / total) * 100)
                const width  = Math.max(2, ((endH - startH) / total) * 100)
                bar = (
                  <div
                    className="absolute inset-y-1 rounded-md flex items-center px-2 text-[10px] font-semibold text-white"
                    style={{ left: `${left}%`, width: `${width}%`, background: 'linear-gradient(135deg, #22d3ee 0%, #3b82f6 100%)' }}
                  >
                    {h.startTime.slice(0, 5)} – {h.endTime.slice(0, 5)}
                  </div>
                )
              } else if (h && h.isClosed) {
                bar = <div className="absolute inset-y-2 left-1 right-1 rounded-md bg-slate-50 text-[10px] font-medium text-slate-400 flex items-center justify-center">Cerrado</div>
              } else {
                bar = <div className="absolute inset-y-2 left-1 right-1 rounded-md border border-dashed border-slate-200 text-[10px] font-medium text-slate-400 flex items-center justify-center">Sin configurar</div>
              }
              return (
                <div key={key} className="grid grid-cols-[80px_1fr_64px] items-center gap-2">
                  <div className={`text-xs font-bold ${isToday ? 'text-blue-600' : 'text-[#1e3a5f]'}`}>
                    {isFirstOfDay ? DAYS[day] : ''}
                  </div>
                  <div className="relative h-8 rounded-md bg-slate-50 border border-slate-100">{bar}</div>
                  <div className="flex gap-0.5 justify-end">
                    {h && isAdmin && (
                      <>
                        <button onClick={() => openEdit(h)} className="rounded p-1 text-slate-300 hover:bg-blue-50 hover:text-blue-500 transition" title="Editar"><Pencil size={12} /></button>
                        <button onClick={() => openDelete(h)} className="rounded p-1 text-slate-300 hover:bg-red-50 hover:text-red-500 transition" title="Eliminar"><Trash2 size={12} /></button>
                      </>
                    )}
                    {!h && isAdmin && (
                      <button onClick={() => { setForm({ ...emptyHour, dayOfWeek: String(day) }); setModal('create') }} className="rounded p-1 text-slate-300 hover:bg-blue-50 hover:text-blue-500 transition" title="Añadir"><Plus size={12} /></button>
                    )}
                  </div>
                </div>
              )
            })}
          </div>
          <div className="grid grid-cols-[80px_1fr_64px] gap-2 mt-3">
            <div />
            <div className="flex justify-between text-[10px] text-slate-400 px-1 font-mono">
              {Array.from({ length: Math.floor(total / 2) + 1 }, (_, i) => (
                <span key={i}>{String(DAY_START + i * 2).padStart(2, '0')}</span>
              ))}
            </div>
            <div />
          </div>
        </div>
      )}

      <Modal open={modal === 'create' || modal === 'edit'} onClose={closeModal} title={modal === 'create' ? 'Nuevo tramo horario' : 'Editar tramo horario'} size="sm">
        <div className="space-y-4">
          <Select label="Día *" value={form.dayOfWeek} onChange={(e) => setForm((p) => ({ ...p, dayOfWeek: e.target.value }))}>
            {DAYS.slice(1).map((d, i) => <option key={i + 1} value={i + 1}>{d}</option>)}
          </Select>
          <label className="flex items-center gap-2.5 text-sm font-medium text-slate-700 cursor-pointer">
            <input type="checkbox" checked={form.isClosed} onChange={(e) => setForm((p) => ({ ...p, isClosed: e.target.checked }))} className="accent-[#1e3a5f] h-4 w-4 rounded" />
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


/** Pestana de cabinas fisicas con gestion y color asignado */
function BoothsTab({ bId, isAdmin }) {
  const toast = useToast()
  const [pageSize, setPageSize] = useState(() => parseInt(localStorage.getItem('optima_cfg_booths_size') || '20', 10))
  useEffect(() => { localStorage.setItem('optima_cfg_booths_size', String(pageSize)) }, [pageSize])
  const [sortDir, setSortDir] = useState(() => localStorage.getItem('optima_cfg_booths_sort') || 'asc')
  useEffect(() => { localStorage.setItem('optima_cfg_booths_sort', sortDir) }, [sortDir])

  // Vista activos / archivados - sin persistir
  const [view, setView] = useState('active')
  const isArchived = view === 'archived'
  const queryParams = useMemo(() => ({ sort: `name,${sortDir}`, active: view === 'active' }), [sortDir, view])

  const { items: booths, page, totalPages, totalElements, loading, setPage, refresh } =
    usePagedFetch(bId ? `/api/businesses/${bId}/booths` : null, { size: pageSize, params: queryParams })

  const [modal, setModal]       = useState(null)
  const [selected, setSelected] = useState(null)
  const [form, setForm]         = useState(emptyBooth)
  const [saving, setSaving]     = useState(false)

  const closeModal  = () => { setModal(null); setSelected(null) }
  const openCreate  = () => { setForm(emptyBooth); setSelected(null); setModal('create') }
  const openEdit    = (b) => { setSelected(b); setForm({ name: b.name, color: b.color || '' }); setModal('edit') }
  const openArchive = (b) => { setSelected(b); setModal('archive') }

  const handleSave = async () => {
    if (!form.name.trim()) { toast({ type: 'error', message: 'El nombre es obligatorio.' }); return }
    setSaving(true)
    try {
      const body = { name: form.name, color: form.color || null }
      if (modal === 'create') {
        await api.post(`/api/businesses/${bId}/booths`, body)
        toast({ type: 'success', message: 'Cabina creada.' })
      } else {
        await api.put(`/api/businesses/${bId}/booths/${selected.id}`, body)
        toast({ type: 'success', message: 'Cabina actualizada.' })
      }
      closeModal(); refresh()
    } catch (err) {
      toast({ type: 'error', message: getErrorMessage(err, 'No se pudo guardar la cabina.') })
    } finally { setSaving(false) }
  }

  const handleArchive = async () => {
    setSaving(true)
    try {
      await api.delete(`/api/businesses/${bId}/booths/${selected.id}`)
      toast({ type: 'success', message: 'Cabina archivada.' })
      closeModal(); refresh()
    } catch (err) {
      toast({ type: 'error', message: getErrorMessage(err, 'No se pudo archivar la cabina.') })
    } finally { setSaving(false) }
  }

  // Restaurar es un clic directo (no destructivo): sin ventana de confirmacion
  const handleReactivate = async (booth) => {
    try {
      await api.patch(`/api/businesses/${bId}/booths/${booth.id}/reactivate`)
      toast({ type: 'success', message: 'Cabina restaurada.' })
      refresh()
    } catch (err) {
      toast({ type: 'error', message: getErrorMessage(err, 'No se pudo restaurar la cabina.') })
    }
  }

  return (
    <div>
      <SectionHint title="¿Qué es una cabina?">
        Una cabina representa un espacio físico de trabajo (sala, silla,
        camilla, mesa…). Es útil cuando dos clientes pueden atenderse a
        la misma hora en sitios distintos del local, para reservar la
        cita al sitio concreto. Si tu negocio no necesita trackear esto,
        déjalo vacío: las citas funcionan igual sin cabinas.
      </SectionHint>
      <Toolbar
        rightCount={totalElements}
        rightLabel={isArchived
          ? `cabina${totalElements === 1 ? '' : 's'} archivada${totalElements === 1 ? '' : 's'}`
          : `cabina${totalElements === 1 ? '' : 's'}`}
        filter={<ArchiveViewToggle view={view} onChange={setView} />}
        sortOptions={[{ value: 'asc', label: 'Nombre A → Z' }, { value: 'desc', label: 'Nombre Z → A' }]}
        sortValue={sortDir} onSortChange={setSortDir}
        pageSize={pageSize} onPageSize={(v) => { setPageSize(v); setPage(0) }}
        onRefresh={refresh} loading={loading}
        primary={isAdmin && <Button onClick={openCreate} className="gap-2"><Plus size={16} /> Nueva cabina</Button>}
      />

      {loading ? (
        <div className="card-grid">
          {[...Array(3)].map((_, i) => <div key={i} className="h-24 bg-white rounded-2xl border border-slate-100 animate-pulse" />)}
        </div>
      ) : booths.length === 0 ? (
        <p className="py-16 text-center text-slate-400">
          {isArchived ? 'No hay cabinas archivadas.' : 'No hay cabinas configuradas.'}
        </p>
      ) : (
        <>
          <div className="card-grid">
            {booths.map((b) => (
              <div key={b.id} className="group bg-white rounded-2xl border border-slate-100 p-5 hover:border-blue-200 hover:shadow-[0_2px_12px_-2px_rgba(15,23,42,0.06)] transition">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <div
                      className={`flex h-11 w-11 items-center justify-center rounded-xl text-white shadow-[0_6px_16px_-6px_rgba(99,102,241,0.4)] ${
                        b.color && BOOTH_COLOR_CLS[b.color]
                          ? BOOTH_COLOR_CLS[b.color]
                          : `bg-gradient-to-br ${boothColor(b.id)}`
                      }`}
                    >
                      <Store size={17} />
                    </div>
                    <div>
                      <p className="font-bold text-[#1e3a5f]">{b.name}</p>
                      <p className="text-[11px] text-slate-400 mt-0.5">Creada {fmtSinceShort(b.createdAt)}</p>
                    </div>
                  </div>
                  {isAdmin && !isArchived && (
                    <div className="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                      <button onClick={() => openEdit(b)} className="rounded-xl p-1.5 text-slate-400 hover:bg-blue-50 hover:text-blue-600 transition" title="Editar"><Pencil size={14} /></button>
                      <button onClick={() => openArchive(b)} className="rounded-xl p-1.5 text-slate-400 hover:bg-amber-50 hover:text-amber-600 transition" title="Archivar"><Archive size={14} /></button>
                    </div>
                  )}
                </div>
                {isAdmin && isArchived && (
                  <div className="mt-3 pt-3 border-t border-slate-50">
                    <Button variant="success" size="sm" onClick={() => handleReactivate(b)} className="w-full gap-2">
                      <ArchiveRestore size={14} /> Restaurar
                    </Button>
                  </div>
                )}
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
          <Input
            label="Nombre *"
            value={form.name}
            onChange={(e) => setForm((p) => ({ ...p, name: e.target.value }))}
            placeholder="Sala 1"
            maxLength={80}
          />
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
              {BOOTH_COLOR_OPTIONS.map(({ name, cls }) => (
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
            <p className="text-xs text-slate-400 mt-1.5">
              Para diferenciar las citas de cada cabina de un vistazo. Varias cabinas pueden compartir color.
            </p>
          </div>
          <div className="flex gap-3">
            <Button variant="outline" onClick={closeModal} className="flex-1">Cancelar</Button>
            <Button onClick={handleSave} loading={saving} className="flex-1">{modal === 'create' ? 'Crear' : 'Guardar'}</Button>
          </div>
        </div>
      </Modal>
      <Modal open={modal === 'archive'} onClose={closeModal} title="Archivar cabina" size="sm">
        <div className="space-y-5">
          <div className="flex items-start gap-3 rounded-2xl bg-amber-50 border border-amber-100 px-4 py-4">
            <Archive size={20} className="text-amber-600 shrink-0 mt-0.5" />
            <p className="text-sm text-amber-800 leading-snug">¿Archivar la cabina <strong>{selected?.name}</strong>? Dejará de poder asignarse a citas nuevas.</p>
          </div>
          <div className="flex gap-3">
            <Button variant="outline" onClick={closeModal} className="flex-1">Cancelar</Button>
            <Button variant="danger" onClick={handleArchive} loading={saving} className="flex-1">Archivar</Button>
          </div>
        </div>
      </Modal>
    </div>
  )
}


/** Pestana de bloqueos de agenda (global, por empleado o por cabina) */
function BlocksTab({ bId, isAdmin }) {
  const toast = useToast()
  const [typeFilter, setTypeFilter] = useState('ALL')

  // Sin paginacion: esta pestana agrupa los bloqueos en secciones (En curso /
  // Proximos / Pasados) Paginar descuadraria el recuento de cada seccion
  // frente al total size=100 es el tope del servidor, de sobra para los
  // bloqueos de agenda de un negocio
  const { items: blocks, totalElements, loading, refresh } =
    usePagedFetch(bId ? `/api/businesses/${bId}/schedule-blocks` : null, { size: 100 })

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

  const [modal, setModal]       = useState(null)
  const [selected, setSelected] = useState(null)
  const [form, setForm]         = useState(emptyBlock)
  const [saving, setSaving]     = useState(false)

  const closeModal = () => { setModal(null); setSelected(null) }
  const openCreate = () => { setForm(emptyBlock); setModal('create') }
  const openDelete = (b) => { setSelected(b); setModal('delete') }

  const handleCreate = async () => {
    if (form.type === 'employee' && !form.membershipId) { toast({ type: 'error', message: 'Selecciona un empleado.' }); return }
    if (form.type === 'booth' && !form.boothId)         { toast({ type: 'error', message: 'Selecciona una cabina.' }); return }
    if (!form.startDate || !form.endDate)               { toast({ type: 'error', message: 'Las fechas son obligatorias.' }); return }
    if (form.startDate > form.endDate)                  { toast({ type: 'error', message: 'La fecha de fin debe ser igual o posterior al inicio.' }); return }
    setSaving(true)
    try {
      await api.post(`/api/businesses/${bId}/schedule-blocks`, {
        membershipId: form.type === 'employee' ? Number(form.membershipId) : null,
        boothId:      form.type === 'booth' ? Number(form.boothId) : null,
        startDate: form.startDate, endDate: form.endDate, reason: form.reason || null,
      })
      toast({ type: 'success', message: 'Bloqueo creado.' })
      closeModal(); refresh()
    } catch (err) {
      toast({ type: 'error', message: getErrorMessage(err, 'No se pudo crear el bloqueo.') })
    } finally { setSaving(false) }
  }

  const handleDelete = async () => {
    setSaving(true)
    try {
      await api.delete(`/api/businesses/${bId}/schedule-blocks/${selected.id}`)
      toast({ type: 'success', message: 'Bloqueo eliminado.' })
      closeModal(); refresh()
    } catch (err) {
      toast({ type: 'error', message: getErrorMessage(err, 'No se pudo eliminar el bloqueo.') })
    } finally { setSaving(false) }
  }

  const today = new Date(); today.setHours(0, 0, 0, 0)
  const typeOf = (b) => b.membershipId ? 'employee' : (b.boothId ? 'booth' : 'global')

  const filtered = blocks.filter((b) => typeFilter === 'ALL' || typeOf(b) === typeFilter.toLowerCase())

  const sections = useMemo(() => {
    const active = [], upcoming = [], past = []
    filtered.forEach((b) => {
      const start = new Date(`${b.startDate}T00:00:00`)
      const end   = new Date(`${b.endDate}T23:59:59`)
      if (today >= start && today <= end) active.push(b)
      else if (today < start) upcoming.push(b)
      else past.push(b)
    })
    upcoming.sort((a, b) => new Date(a.startDate) - new Date(b.startDate))
    active.sort((a, b) => new Date(a.endDate) - new Date(b.endDate))
    past.sort((a, b) => new Date(b.endDate) - new Date(a.endDate))
    return { active, upcoming, past }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filtered])

  const blockKindBadge = (b) => {
    if (b.membershipId) return <Badge variant="info"><User size={10} className="mr-0.5" />{b.userFullName}</Badge>
    if (b.boothId)      return <Badge variant="purple"><Store size={10} className="mr-0.5" />{b.boothName}</Badge>
    return <Badge variant="default"><Globe size={10} className="mr-0.5" />Global</Badge>
  }

  const BlockCard = ({ b, kind }) => {
    const isActive = kind === 'active'
    return (
      <div className={`bg-white rounded-2xl border ${isActive ? 'border-rose-200' : 'border-slate-100'} p-4 flex items-center gap-3`}>
        {isActive && <div className="px-2 py-1 rounded-full text-[10px] font-bold uppercase tracking-wider bg-rose-50 text-rose-700 ring-1 ring-rose-200">EN CURSO</div>}
        {blockKindBadge(b)}
        <div className="flex-1 min-w-0">
          <div className="text-sm font-semibold text-[#1e3a5f]">{fmtDate(b.startDate)} – {fmtDate(b.endDate)}</div>
          {b.reason && <div className="text-xs text-slate-500 truncate">{b.reason}</div>}
        </div>
        {isAdmin && (
          <button onClick={() => openDelete(b)} className="rounded-xl p-2 text-slate-400 hover:bg-red-50 hover:text-red-600 transition" title="Eliminar"><Trash2 size={14} /></button>
        )}
      </div>
    )
  }

  return (
    <div>
      <SectionHint title="¿Qué es un bloqueo de agenda?">
        Cierra días completos para que no se puedan agendar citas. Útil
        para festivos, vacaciones del negocio o el cierre puntual de una
        cabina o un empleado concreto. Puede aplicar a todo el negocio
        (Global), a un empleado o a una cabina. Para huecos cortos dentro
        del día usa el horario del empleado o sus ausencias.
      </SectionHint>
      <div className="mb-4 flex flex-wrap items-center gap-2">
        <div className="inline-flex items-center bg-slate-100 rounded-xl p-1">
          {[
            { key: 'ALL',      label: 'Todos' },
            { key: 'GLOBAL',   label: 'Global' },
            { key: 'EMPLOYEE', label: 'Empleado' },
            { key: 'BOOTH',    label: 'Cabina' },
          ].map(({ key, label }) => (
            <button
              key={key}
              onClick={() => setTypeFilter(key)}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition ${typeFilter === key ? 'bg-white text-[#1e3a5f] shadow-[0_1px_4px_rgba(15,23,42,0.08)]' : 'text-slate-500 hover:text-[#1e3a5f]'}`}
            >{label}</button>
          ))}
        </div>
        <button
          onClick={refresh}
          title="Refrescar"
          className="inline-flex items-center justify-center w-9 h-9 rounded-xl border border-slate-200 bg-white text-slate-500 hover:bg-slate-50 hover:text-[#1e3a5f] transition"
        >
          <RefreshCw size={14} className={loading ? 'animate-spin' : ''} />
        </button>
        <span className="text-xs text-slate-500">{totalElements} bloqueo{totalElements === 1 ? '' : 's'}</span>
        {isAdmin && (
          <Button onClick={openCreate} className="ml-auto gap-2" size="sm"><Plus size={14} /> Nuevo bloqueo</Button>
        )}
      </div>

      {loading ? (
        <div className="space-y-3">{[...Array(3)].map((_, i) => <div key={i} className="h-16 bg-white rounded-2xl border border-slate-100 animate-pulse" />)}</div>
      ) : filtered.length === 0 ? (
        <p className="py-16 text-center text-slate-400">No hay bloqueos de agenda.</p>
      ) : (
        <div className="space-y-6">
          {sections.active.length > 0 && (
            <div>
              <div className="flex items-center gap-2 mb-3">
                <span className="w-2 h-2 rounded-full bg-rose-500 animate-pulse" />
                <h2 className="text-xs font-bold text-[#1e3a5f] uppercase tracking-wider">Activos ahora</h2>
                <span className="text-xs text-slate-400 font-medium">· {sections.active.length}</span>
              </div>
              <div className="space-y-2">{sections.active.map((b) => <BlockCard key={b.id} b={b} kind="active" />)}</div>
            </div>
          )}
          {sections.upcoming.length > 0 && (
            <div>
              <div className="flex items-center gap-2 mb-3">
                <span className="w-2 h-2 rounded-full bg-amber-500" />
                <h2 className="text-xs font-bold text-[#1e3a5f] uppercase tracking-wider">Próximos</h2>
                <span className="text-xs text-slate-400 font-medium">· {sections.upcoming.length}</span>
              </div>
              <div className="space-y-2">{sections.upcoming.map((b) => <BlockCard key={b.id} b={b} kind="upcoming" />)}</div>
            </div>
          )}
          {sections.past.length > 0 && (
            <div>
              <div className="flex items-center gap-2 mb-3">
                <span className="w-2 h-2 rounded-full bg-slate-400" />
                <h2 className="text-xs font-bold text-[#1e3a5f] uppercase tracking-wider">Pasados</h2>
                <span className="text-xs text-slate-400 font-medium">· {sections.past.length}</span>
              </div>
              <div className="space-y-2">{sections.past.map((b) => <BlockCard key={b.id} b={b} kind="past" />)}</div>
            </div>
          )}
        </div>
      )}

      <Modal open={modal === 'create'} onClose={closeModal} title="Nuevo bloqueo de agenda" size="sm">
        <div className="space-y-4">
          <Select label="Tipo de bloqueo *" value={form.type} onChange={(e) => setForm((p) => ({ ...p, type: e.target.value, membershipId: '', boothId: '' }))}>
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
          <Input label="Motivo" value={form.reason} onChange={(e) => setForm((p) => ({ ...p, reason: e.target.value }))} placeholder="Vacaciones, festivo, mantenimiento…" maxLength={255} />
          <div className="flex gap-3">
            <Button variant="outline" onClick={closeModal} className="flex-1">Cancelar</Button>
            <Button onClick={handleCreate} loading={saving} className="flex-1">Crear bloqueo</Button>
          </div>
        </div>
      </Modal>
      <Modal open={modal === 'delete'} onClose={closeModal} title="Eliminar bloqueo" size="sm">
        <p className="text-sm text-slate-600 mb-5">¿Eliminar el bloqueo del <strong>{fmtDate(selected?.startDate)}</strong> al <strong>{fmtDate(selected?.endDate)}</strong>?</p>
        <div className="flex gap-3">
          <Button variant="outline" onClick={closeModal} className="flex-1">Cancelar</Button>
          <Button variant="danger" onClick={handleDelete} loading={saving} className="flex-1">Eliminar</Button>
        </div>
      </Modal>
    </div>
  )
}


/** Barra superior con contador, filtros, orden y accion principal */
function Toolbar({ rightCount, rightLabel, extraStats, filter, sortOptions, sortValue, onSortChange, pageSize, onPageSize, onRefresh, loading, primary }) {
  return (
    <div className="mb-4 flex flex-wrap items-center gap-3">
      <span className="text-xs text-slate-500">
        <strong className="text-[#1e3a5f]">{rightCount}</strong> {rightLabel}
        {extraStats && <span className="ml-3 text-slate-400">· {extraStats}</span>}
      </span>
      <button
        type="button"
        onClick={onRefresh}
        title="Refrescar"
        className="inline-flex items-center justify-center w-9 h-9 rounded-xl border border-slate-200 bg-white text-slate-500 hover:bg-slate-50 hover:text-[#1e3a5f] transition"
      >
        <RefreshCw size={14} className={loading ? 'animate-spin' : ''} />
      </button>
      {filter}
      <select
        value={sortValue}
        onChange={(e) => onSortChange(e.target.value)}
        className="h-9 rounded-xl border border-slate-200 bg-white px-3 text-xs font-semibold text-[#1e3a5f] focus:outline-none focus:border-blue-400 focus:ring-4 focus:ring-blue-100 transition"
      >
        {sortOptions.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
      </select>
      <div className="ml-auto flex items-center gap-2 text-xs text-slate-500">
        <span>Mostrar</span>
        <select
          value={pageSize}
          onChange={(e) => onPageSize(parseInt(e.target.value, 10))}
          className="h-9 rounded-xl border border-slate-200 bg-white px-3 text-sm font-semibold text-[#1e3a5f] focus:outline-none focus:border-blue-400 focus:ring-4 focus:ring-blue-100 transition"
        >
          <option value={10}>10</option><option value={20}>20</option><option value={50}>50</option>
        </select>
        {primary}
      </div>
    </div>
  )
}

/* Control segmentado Activos / Archivados - compartido por Impuestos y Cabinas */
/** Toggle Activos/Archivados para los listados con archivado */
function ArchiveViewToggle({ view, onChange }) {
  return (
    <div className="inline-flex items-center bg-slate-100 rounded-xl p-1">
      {[{ key: 'active', label: 'Activos' }, { key: 'archived', label: 'Archivados' }].map(({ key, label }) => (
        <button key={key} type="button" onClick={() => onChange(key)}
          className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition ${
            view === key ? 'bg-white text-[#1e3a5f] shadow-[0_1px_4px_rgba(15,23,42,0.08)]' : 'text-slate-500 hover:text-[#1e3a5f]'
          }`}>{label}</button>
      ))}
    </div>
  )
}

// CRUD de clientes con buscador, paginacion, panel lateral y archivado
import { useState, useEffect, useMemo } from 'react'
import {
  Plus, Search, Pencil, Archive, ArchiveRestore, Mail, Phone, Users,
  ChevronsUpDown, ArrowDown, ArrowUp, X, Calendar, CalendarPlus, ExternalLink,
  List, LayoutGrid, RefreshCw,
} from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Input, INPUT_SANITIZE } from '@/components/ui/Input'
import { Textarea } from '@/components/ui/Textarea'
import { Modal } from '@/components/ui/Modal'
import { Pagination } from '@/components/ui/Pagination'
import { EmptyState } from '@/components/ui/EmptyState'
import { useAuth } from '@/context/AuthContext'
import { useToast } from '@/components/ui/Toast'
import { usePagedFetch } from '@/hooks/usePagedFetch'
import api, { getErrorMessage } from '@/lib/api'
import { AppointmentWizard } from '@/components/appointments/AppointmentWizard'

const AVATAR_COLORS = [
  'from-cyan-400 to-blue-500',
  'from-amber-400 to-orange-500',
  'from-emerald-400 to-teal-500',
  'from-indigo-400 to-violet-500',
  'from-pink-400 to-rose-500',
  'from-sky-400 to-blue-500',
]
const avatarColor = (id) => AVATAR_COLORS[(id ?? 0) % AVATAR_COLORS.length]

const empty = { fullName: '', email: '', phone: '', notes: '' }

// "ago 2025" - corto, para la columna de la tabla
const fmtSince = (iso) => {
  if (!iso) return '—'
  return new Date(iso)
    .toLocaleDateString('es-ES', { month: 'short', year: 'numeric' })
    .replace('.', '')
}

// "12 de agosto de 2025 - hace 9 meses" - largo, para el panel
const fmtSinceLong = (iso) => {
  if (!iso) return '—'
  const d = new Date(iso)
  const now = new Date()
  const months = (now.getFullYear() - d.getFullYear()) * 12 + (now.getMonth() - d.getMonth())
  const rel =
    months >= 12
      ? `hace ${Math.floor(months / 12)} año${Math.floor(months / 12) === 1 ? '' : 's'}`
      : months <= 1
      ? 'recién registrado'
      : `hace ${months} meses`
  const fmt = d.toLocaleDateString('es-ES', { day: '2-digit', month: 'long', year: 'numeric' })
  return `${fmt} · ${rel}`
}

const telHref = (phone) => 'tel:' + String(phone || '').replace(/\s/g, '')

/** Listado y gestion de clientes con buscador en servidor, paginacion y archivado */
export default function Clientes() {
  const { user } = useAuth()
  const toast = useToast()
  const bId = user?.businessId

  const [view, setView] = useState(() => localStorage.getItem('optima_clients_view') || 'table')
  useEffect(() => { localStorage.setItem('optima_clients_view', view) }, [view])

  const [pageSize, setPageSize] = useState(() => parseInt(localStorage.getItem('optima_clients_size') || '20', 10))
  useEffect(() => { localStorage.setItem('optima_clients_size', String(pageSize)) }, [pageSize])

  // sortKey: 'fullName' | 'createdAt' - sortDir: 'asc' | 'desc'
  const [sortKey, setSortKey] = useState(() => localStorage.getItem('optima_clients_sortkey') || 'createdAt')
  const [sortDir, setSortDir] = useState(() => localStorage.getItem('optima_clients_sortdir') || 'desc')
  useEffect(() => { localStorage.setItem('optima_clients_sortkey', sortKey) }, [sortKey])
  useEffect(() => { localStorage.setItem('optima_clients_sortdir', sortDir) }, [sortDir])

  const toggleSort = (key) => {
    if (sortKey === key) {
      setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'))
    } else {
      setSortKey(key)
      // Por defecto: nombres asc, fechas desc
      setSortDir(key === 'fullName' ? 'asc' : 'desc')
    }
  }

  const [archiveView, setArchiveView] = useState('active') // 'active' | 'archived'
  const isArchived = archiveView === 'archived'

  // Filtros que viajan al servidor: ordenacion + flag de archivado
  const queryParams = useMemo(
    () => ({ sort: `${sortKey},${sortDir}`, active: archiveView === 'active' }),
    [sortKey, sortDir, archiveView],
  )

  // para que la busqueda y la paginacion operen sobre el conjunto
  // completo, no sobre una sola pagina Mismo patron que Bloqueos
  const { items: clients, totalElements, loading, refresh } =
    usePagedFetch(bId ? `/api/businesses/${bId}/clients` : null, { size: 100, params: queryParams })

  const [search, setSearch] = useState('')
  const filtered = clients.filter((c) =>
    c.fullName?.toLowerCase().includes(search.toLowerCase()) ||
    c.email?.toLowerCase().includes(search.toLowerCase()) ||
    c.phone?.includes(search)
  )

  const [page, setPage] = useState(0)
  const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize))
  const safePage = Math.min(page, totalPages - 1)
  const paged = filtered.slice(safePage * pageSize, safePage * pageSize + pageSize)

  const [modal, setModal] = useState(null)       // 'create' | 'edit' | 'archive' | null
  const [selected, setSelected] = useState(null) // cliente seleccionado para edit/archive
  const [drawer, setDrawer] = useState(null)     // cliente abierto en el panel lateral
  const [wizardClient, setWizardClient] = useState(null) // cliente para el que se crea una cita
  const [form, setForm] = useState(empty)
  const [saving, setSaving] = useState(false)

  const openCreate = () => { setForm(empty); setSelected(null); setModal('create') }
  const openEdit = (c) => {
    setSelected(c)
    setForm({
      fullName: c.fullName,
      email:    c.email || '',
      phone:    c.phone || '',
      notes:    c.notes || '',
    })
    setModal('edit')
  }
  const openArchive = (c) => { setSelected(c); setModal('archive') }
  const closeModal = () => setModal(null)
  const closeDrawer = () => setDrawer(null)

  // Cierra el panel con Esc
  useEffect(() => {
    if (!drawer) return
    const handler = (e) => { if (e.key === 'Escape') closeDrawer() }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [drawer])

  const handleChange = (e) => setForm((p) => ({ ...p, [e.target.name]: e.target.value }))

  const handleSave = async () => {
    if (!form.fullName.trim()) {
      toast({ type: 'error', message: 'El nombre es obligatorio.' })
      return
    }
    setSaving(true)
    try {
      const payload = {
        fullName: form.fullName,
        email:    form.email,
        phone:    form.phone,
        notes:    form.notes,
      }
      if (modal === 'create') {
        await api.post(`/api/businesses/${bId}/clients`, payload)
        toast({ type: 'success', message: 'Cliente creado correctamente.' })
      } else {
        await api.put(`/api/businesses/${bId}/clients/${selected.id}`, payload)
        toast({ type: 'success', message: 'Cliente actualizado.' })
      }
      closeModal()
      refresh()
    } catch (err) {
      toast({ type: 'error', message: getErrorMessage(err, 'Error al guardar.') })
    } finally {
      setSaving(false)
    }
  }

  const handleArchive = async () => {
    setSaving(true)
    try {
      // El borrado mantiene el cliente en archivado
      await api.delete(`/api/businesses/${bId}/clients/${selected.id}`)
      toast({ type: 'success', message: 'Cliente archivado.' })
      closeModal()
      // Si el panel esta abierto sobre este cliente, cierralo
      if (drawer?.id === selected.id) setDrawer(null)
      refresh()
    } catch (err) {
      toast({ type: 'error', message: getErrorMessage(err, 'Error al archivar el cliente.') })
    } finally {
      setSaving(false)
    }
  }

  // Restaurar es un clic directo (no destructivo): sin ventana de confirmacion
  const handleReactivate = async (client) => {
    try {
      await api.patch(`/api/businesses/${bId}/clients/${client.id}/reactivate`)
      toast({ type: 'success', message: 'Cliente restaurado.' })
      if (drawer?.id === client.id) setDrawer(null)
      refresh()
    } catch (err) {
      toast({ type: 'error', message: getErrorMessage(err, 'Error al restaurar el cliente.') })
    }
  }


  const SortIcon = ({ k }) => {
    if (sortKey !== k) return <ChevronsUpDown size={12} className="opacity-60" />
    return sortDir === 'asc'
      ? <ArrowUp size={12} className="text-blue-500" />
      : <ArrowDown size={12} className="text-blue-500" />
  }

  return (
    <div className="px-4 sm:px-6 lg:px-8 xl:px-10 py-8">

      <div className="mb-7 flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold text-[#1e3a5f] tracking-tight">Clientes</h1>
          <p className="text-sm text-slate-500 mt-1.5 font-medium flex items-center gap-1.5">
            <Users size={14} />
            {loading
              ? '…'
              : isArchived
                ? `${totalElements} cliente${totalElements === 1 ? '' : 's'} archivado${totalElements === 1 ? '' : 's'}`
                : `${totalElements} cliente${totalElements === 1 ? '' : 's'} registrado${totalElements === 1 ? '' : 's'}`}
          </p>
        </div>
        <div className="flex shrink-0 items-center gap-2">
          <button
            type="button"
            onClick={refresh}
            title="Refrescar"
            className="inline-flex items-center justify-center w-11 h-11 rounded-xl border border-slate-200 bg-white text-slate-500 hover:bg-slate-50 hover:text-[#1e3a5f] transition"
          >
            <RefreshCw size={16} className={loading ? 'animate-spin' : ''} />
          </button>
          <Button onClick={openCreate} className="gap-2 shrink-0 whitespace-nowrap">
            <Plus size={16} /> Nuevo cliente
          </Button>
        </div>
      </div>

      <div className="mb-5 flex flex-wrap items-center gap-3">
        <div className="relative flex-1 min-w-[260px] max-w-md">
          <Search size={15} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Buscar por nombre, email o teléfono…"
            className="h-11 w-full rounded-xl border border-slate-200 bg-white pl-10 pr-3.5 text-sm text-[#1f2c4a] placeholder:text-slate-400 focus:outline-none focus:border-blue-400 focus:ring-4 focus:ring-blue-100 transition-all"
          />
        </div>

        <div className="inline-flex items-center bg-slate-100 rounded-xl p-1">
          {[{ key: 'active', label: 'Activos' }, { key: 'archived', label: 'Archivados' }].map(({ key, label }) => (
            <button key={key} type="button" onClick={() => setArchiveView(key)}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition ${
                archiveView === key ? 'bg-white text-[#1e3a5f] shadow-[0_1px_4px_rgba(15,23,42,0.08)]' : 'text-slate-500 hover:text-[#1e3a5f]'
              }`}>{label}</button>
          ))}
        </div>

        <div className="flex items-center gap-2 text-xs text-slate-500">
          <span>Mostrar</span>
          <select
            value={pageSize}
            onChange={(e) => { setPageSize(parseInt(e.target.value, 10)); setPage(0) }}
            className="h-11 rounded-xl border border-slate-200 bg-white px-3 text-sm font-semibold text-[#1e3a5f] focus:outline-none focus:border-blue-400 focus:ring-4 focus:ring-blue-100 transition"
          >
            <option value={10}>10</option>
            <option value={20}>20</option>
            <option value={50}>50</option>
            <option value={100}>100</option>
          </select>
          <span>por página</span>
        </div>

        <div className="ml-auto inline-flex items-center bg-slate-100 rounded-xl p-1">
          <button
            type="button"
            onClick={() => setView('table')}
            className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold transition ${
              view === 'table'
                ? 'bg-white text-[#1e3a5f] shadow-[0_1px_4px_rgba(15,23,42,0.08)]'
                : 'text-slate-500 hover:text-[#1e3a5f]'
            }`}
          >
            <List size={14} /> Tabla
          </button>
          <button
            type="button"
            onClick={() => setView('cards')}
            className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold transition ${
              view === 'cards'
                ? 'bg-white text-[#1e3a5f] shadow-[0_1px_4px_rgba(15,23,42,0.08)]'
                : 'text-slate-500 hover:text-[#1e3a5f]'
            }`}
          >
            <LayoutGrid size={14} /> Tarjetas
          </button>
        </div>
      </div>

      {view === 'table' && (
        <div className="bg-white rounded-2xl border border-slate-100/80 shadow-[0_2px_12px_-2px_rgba(15,23,42,0.06)] overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-slate-100 bg-slate-50/60">
                  <th
                    onClick={() => toggleSort('fullName')}
                    className="cursor-pointer select-none px-6 py-3.5 text-left text-[11px] font-semibold uppercase tracking-wider hover:text-blue-600 transition text-slate-400"
                  >
                    <span className={`inline-flex items-center gap-1 ${sortKey === 'fullName' ? 'text-[#1e3a5f]' : ''}`}>
                      Nombre <SortIcon k="fullName" />
                    </span>
                  </th>
                  <th className="px-6 py-3.5 text-left text-[11px] font-semibold text-slate-400 uppercase tracking-wider hidden md:table-cell">Email</th>
                  <th className="px-6 py-3.5 text-left text-[11px] font-semibold text-slate-400 uppercase tracking-wider hidden lg:table-cell">Teléfono</th>
                  <th
                    onClick={() => toggleSort('createdAt')}
                    className="cursor-pointer select-none px-6 py-3.5 text-left text-[11px] font-semibold uppercase tracking-wider hidden md:table-cell hover:text-blue-600 transition text-slate-400"
                  >
                    <span className={`inline-flex items-center gap-1 ${sortKey === 'createdAt' ? 'text-[#1e3a5f]' : ''}`}>
                      Cliente desde <SortIcon k="createdAt" />
                    </span>
                  </th>
                  <th className="px-6 py-3.5 text-right text-[11px] font-semibold text-slate-400 uppercase tracking-wider">Acciones</th>
                </tr>
              </thead>
              <tbody>
                {loading ? (
                  [...Array(5)].map((_, i) => (
                    <tr key={i} className="border-b border-slate-50">
                      <td colSpan={5} className="px-6 py-4">
                        <div className="h-4 bg-slate-100 rounded-lg animate-pulse w-3/4" />
                      </td>
                    </tr>
                  ))
                ) : filtered.length === 0 ? (
                  <tr>
                    <td colSpan={5}>
                      {search ? (
                        <p className="px-6 py-16 text-center text-slate-400 text-sm">
                          Sin coincidencias para «{search}».
                        </p>
                      ) : isArchived ? (
                        <EmptyState
                          icon={Users}
                          title="No hay clientes archivados"
                          description="Los clientes que archives aparecerán aquí para que puedas restaurarlos."
                        />
                      ) : (
                        <EmptyState
                          icon={Users}
                          title="Aún no hay clientes"
                          description="Crea la primera ficha de cliente para empezar a gestionar sus citas."
                          actionLabel="Nuevo cliente"
                          onAction={openCreate}
                        />
                      )}
                    </td>
                  </tr>
                ) : (
                  paged.map((c) => (
                    <tr
                      key={c.id}
                      onClick={() => setDrawer(c)}
                      className="border-b border-slate-50 hover:bg-blue-50/40 transition-colors cursor-pointer"
                    >
                      <td className="px-6 py-4">
                        <div className="flex items-center gap-3">
                          <div className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br ${avatarColor(c.id)} text-white text-xs font-bold`}>
                            {c.fullName?.[0]?.toUpperCase()}
                          </div>
                          <div className="min-w-0">
                            <div className="font-semibold text-[#1e3a5f] truncate">{c.fullName}</div>
                            <div className="text-xs text-slate-400 truncate">
                              {c.notes
                                ? c.notes.length > 48 ? c.notes.slice(0, 48) + '…' : c.notes
                                : <span className="text-slate-300">Sin notas</span>}
                            </div>
                          </div>
                        </div>
                      </td>
                      <td className="px-6 py-4 hidden md:table-cell">
                        {c.email ? (
                          <a
                            href={`mailto:${c.email}`}
                            onClick={(e) => e.stopPropagation()}
                            className="inline-flex items-center gap-1.5 text-sm text-slate-600 hover:text-blue-600 transition"
                          >
                            <Mail size={13} className="text-slate-400" /> {c.email}
                          </a>
                        ) : <span className="text-slate-300 text-sm">—</span>}
                      </td>
                      <td className="px-6 py-4 hidden lg:table-cell">
                        {c.phone ? (
                          <a
                            href={telHref(c.phone)}
                            onClick={(e) => e.stopPropagation()}
                            className="inline-flex items-center gap-1.5 text-sm text-slate-600 hover:text-blue-600 transition font-mono"
                          >
                            <Phone size={13} className="text-slate-400" /> {c.phone}
                          </a>
                        ) : <span className="text-slate-300 text-sm">—</span>}
                      </td>
                      <td className="px-6 py-4 hidden md:table-cell text-sm text-slate-500 capitalize">
                        {fmtSince(c.createdAt)}
                      </td>
                      <td className="px-6 py-4 text-right" onClick={(e) => e.stopPropagation()}>
                        {isArchived ? (
                          <div className="flex justify-end">
                            <Button variant="success" size="sm" onClick={() => handleReactivate(c)} className="gap-1.5">
                              <ArchiveRestore size={14} /> Restaurar
                            </Button>
                          </div>
                        ) : (
                          <div className="flex justify-end gap-1">
                            <button
                              onClick={() => openEdit(c)}
                              className="rounded-xl p-2 text-slate-400 hover:bg-blue-50 hover:text-blue-600 transition-colors"
                              title="Editar"
                            >
                              <Pencil size={15} />
                            </button>
                            <button
                              onClick={() => openArchive(c)}
                              className="rounded-xl p-2 text-slate-400 hover:bg-amber-50 hover:text-amber-600 transition-colors"
                              title="Archivar"
                            >
                              <Archive size={15} />
                            </button>
                          </div>
                        )}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>

          <Pagination
            page={safePage}
            totalPages={totalPages}
            totalElements={filtered.length}
            onChange={setPage}
          />
        </div>
      )}

      {view === 'cards' && (
        <>
          {loading ? (
            <div className="card-grid">
              {[...Array(6)].map((_, i) => (
                <div key={i} className="h-32 bg-white rounded-2xl border border-slate-100/80 shadow-[0_2px_12px_-2px_rgba(15,23,42,0.06)] p-5 animate-pulse" />
              ))}
            </div>
          ) : filtered.length === 0 ? (
            <div className="bg-white rounded-2xl border border-slate-100/80 shadow-[0_2px_12px_-2px_rgba(15,23,42,0.06)]">
              {search ? (
                <p className="px-6 py-16 text-center text-slate-400 text-sm">Sin coincidencias para «{search}».</p>
              ) : isArchived ? (
                <EmptyState
                  icon={Users}
                  title="No hay clientes archivados"
                  description="Los clientes que archives aparecerán aquí para que puedas restaurarlos."
                />
              ) : (
                <EmptyState
                  icon={Users}
                  title="Aún no hay clientes"
                  description="Crea la primera ficha de cliente para empezar a gestionar sus citas."
                  actionLabel="Nuevo cliente"
                  onAction={openCreate}
                />
              )}
            </div>
          ) : (
            <>
              <div className="card-grid">
                {paged.map((c) => (
                  <div
                    key={c.id}
                    onClick={() => setDrawer(c)}
                    className="group bg-white rounded-2xl border border-slate-100/80 shadow-[0_2px_12px_-2px_rgba(15,23,42,0.06)] p-5 hover:shadow-[0_12px_40px_-12px_rgba(15,23,42,0.15)] hover:border-blue-200 transition cursor-pointer"
                  >
                    <div className="flex items-start gap-3">
                      <div className={`flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-gradient-to-br ${avatarColor(c.id)} text-white text-sm font-bold`}>
                        {c.fullName?.[0]?.toUpperCase()}
                      </div>
                      <div className="flex-1 min-w-0">
                        <div className="font-semibold text-[#1e3a5f] truncate">{c.fullName}</div>
                        <div className="text-[11px] text-slate-400 mt-0.5 inline-flex items-center gap-1 capitalize">
                          <Calendar size={12} /> Cliente desde {fmtSince(c.createdAt)}
                        </div>
                      </div>
                      <div className="flex gap-0.5" onClick={(e) => e.stopPropagation()}>
                        {isArchived ? (
                          <Button variant="success" size="sm" onClick={() => handleReactivate(c)} className="gap-1.5">
                            <ArchiveRestore size={14} /> Restaurar
                          </Button>
                        ) : (
                          <>
                            <button
                              onClick={() => openEdit(c)}
                              className="rounded-lg p-1.5 text-slate-300 hover:bg-blue-50 hover:text-blue-600 transition"
                              title="Editar"
                            >
                              <Pencil size={14} />
                            </button>
                            <button
                              onClick={() => openArchive(c)}
                              className="rounded-lg p-1.5 text-slate-300 hover:bg-amber-50 hover:text-amber-600 transition"
                              title="Archivar"
                            >
                              <Archive size={14} />
                            </button>
                          </>
                        )}
                      </div>
                    </div>
                    <div className="mt-3 pt-3 border-t border-slate-50 flex flex-wrap gap-2" onClick={(e) => e.stopPropagation()}>
                      {c.email && (
                        <a
                          href={`mailto:${c.email}`}
                          className="inline-flex items-center gap-1.5 text-xs font-medium px-2.5 py-1.5 rounded-lg bg-blue-50/50 text-blue-700 hover:bg-blue-100 transition truncate max-w-full"
                        >
                          <Mail size={12} /> {c.email}
                        </a>
                      )}
                      {c.phone && (
                        <a
                          href={telHref(c.phone)}
                          className="inline-flex items-center gap-1.5 text-xs font-medium font-mono px-2.5 py-1.5 rounded-lg bg-slate-50 text-slate-700 hover:bg-slate-100 transition"
                        >
                          <Phone size={12} /> {c.phone}
                        </a>
                      )}
                      {!c.email && !c.phone && (
                        <span className="text-xs text-slate-300 italic">Sin datos de contacto</span>
                      )}
                    </div>
                    {c.notes && (
                      <p className="mt-3 text-xs text-slate-500 leading-relaxed line-clamp-2">{c.notes}</p>
                    )}
                  </div>
                ))}
              </div>
              <div className="mt-5 bg-white rounded-xl border border-slate-100/80">
                <Pagination
                  page={safePage}
                  totalPages={totalPages}
                  totalElements={filtered.length}
                  onChange={setPage}
                />
              </div>
            </>
          )}
        </>
      )}

      {drawer && (
        <>
          <div
            onClick={closeDrawer}
            className="fixed inset-0 z-40 bg-slate-900/30 backdrop-blur-sm animate-[fadeIn_200ms_ease-out]"
          />
          <aside
            className="fixed top-0 right-0 z-50 h-[100dvh] w-[420px] max-w-[95vw] bg-white shadow-[0_28px_56px_-16px_rgba(15,23,42,0.22)] flex flex-col animate-[slideInRight_240ms_cubic-bezier(0.2,0.7,0.2,1)]"
          >
            <div className="px-6 pt-6 pb-4 border-b border-slate-100 flex items-start justify-between gap-4">
              <div className="flex items-center gap-4 min-w-0">
                <div className={`w-14 h-14 rounded-2xl bg-gradient-to-br ${avatarColor(drawer.id)} flex items-center justify-center text-white text-lg font-bold shrink-0`}>
                  {drawer.fullName?.[0]?.toUpperCase()}
                </div>
                <div className="min-w-0">
                  <div className="text-lg font-bold text-[#1e3a5f] truncate">{drawer.fullName}</div>
                  <div className="text-xs text-slate-400 mt-0.5 inline-flex items-center gap-1.5">
                    <Calendar size={12} /> Cliente desde {fmtSinceLong(drawer.createdAt)}
                  </div>
                </div>
              </div>
              <button
                onClick={closeDrawer}
                className="p-2 rounded-lg text-slate-400 hover:bg-slate-50 hover:text-[#1e3a5f] transition"
                title="Cerrar"
              >
                <X size={16} />
              </button>
            </div>

            <div className="flex-1 overflow-y-auto px-6 py-5 space-y-5">
              <div>
                <div className="text-[10px] font-bold text-slate-400 uppercase tracking-[0.16em] mb-2">Contacto</div>
                {drawer.email ? (
                  <a
                    href={`mailto:${drawer.email}`}
                    className="group flex items-center gap-3 px-3 py-2.5 rounded-xl border border-slate-100 hover:border-blue-200 hover:bg-blue-50/40 transition"
                  >
                    <span className="w-9 h-9 rounded-lg bg-blue-50 text-blue-600 flex items-center justify-center"><Mail size={16} /></span>
                    <span className="flex-1 min-w-0">
                      <span className="block text-[11px] text-slate-400 uppercase tracking-wider font-semibold">Email</span>
                      <span className="block text-sm font-semibold text-[#1e3a5f] truncate">{drawer.email}</span>
                    </span>
                    <ExternalLink size={14} className="text-slate-300 group-hover:text-blue-500" />
                  </a>
                ) : (
                  <div className="flex items-center gap-3 px-3 py-2.5 rounded-xl border border-slate-100 opacity-60">
                    <span className="w-9 h-9 rounded-lg bg-slate-50 text-slate-400 flex items-center justify-center"><Mail size={16} /></span>
                    <span className="flex-1 text-sm text-slate-400">Sin email</span>
                  </div>
                )}
                {drawer.phone ? (
                  <a
                    href={telHref(drawer.phone)}
                    className="group mt-2 flex items-center gap-3 px-3 py-2.5 rounded-xl border border-slate-100 hover:border-blue-200 hover:bg-blue-50/40 transition"
                  >
                    <span className="w-9 h-9 rounded-lg bg-blue-50 text-blue-600 flex items-center justify-center"><Phone size={16} /></span>
                    <span className="flex-1 min-w-0">
                      <span className="block text-[11px] text-slate-400 uppercase tracking-wider font-semibold">Teléfono</span>
                      <span className="block text-sm font-semibold text-[#1e3a5f] truncate font-mono">{drawer.phone}</span>
                    </span>
                    <ExternalLink size={14} className="text-slate-300 group-hover:text-blue-500" />
                  </a>
                ) : (
                  <div className="mt-2 flex items-center gap-3 px-3 py-2.5 rounded-xl border border-slate-100 opacity-60">
                    <span className="w-9 h-9 rounded-lg bg-slate-50 text-slate-400 flex items-center justify-center"><Phone size={16} /></span>
                    <span className="flex-1 text-sm text-slate-400">Sin teléfono</span>
                  </div>
                )}
              </div>

              <div>
                <div className="text-[10px] font-bold text-slate-400 uppercase tracking-[0.16em] mb-2">Notas</div>
                <div className="text-sm leading-relaxed text-slate-700 whitespace-pre-wrap min-h-[80px] rounded-xl border border-slate-100 bg-slate-50/50 px-4 py-3">
                  {drawer.notes || <span className="text-slate-400 italic">Sin notas registradas.</span>}
                </div>
              </div>
            </div>

            <div className="px-6 py-4 border-t border-slate-100 space-y-2">
              {!isArchived && (
                <Button
                  onClick={() => { setWizardClient(drawer); setDrawer(null) }}
                  className="w-full gap-2"
                >
                  <CalendarPlus size={15} /> Nueva cita
                </Button>
              )}
              <div className="flex gap-2">
                {isArchived ? (
                  <Button
                    variant="success"
                    onClick={() => handleReactivate(drawer)}
                    className="flex-1 gap-2"
                  >
                    <ArchiveRestore size={15} /> Restaurar
                  </Button>
                ) : (
                  <>
                    <Button
                      variant="outline"
                      onClick={() => openArchive(drawer)}
                      className="flex-1 gap-2"
                    >
                      <Archive size={15} /> Archivar
                    </Button>
                    <Button
                      onClick={() => { openEdit(drawer); setDrawer(null) }}
                      className="flex-1 gap-2"
                    >
                      <Pencil size={15} /> Editar
                    </Button>
                  </>
                )}
              </div>
            </div>
          </aside>
        </>
      )}

      <Modal
        open={modal === 'create' || modal === 'edit'}
        onClose={closeModal}
        title={modal === 'create' ? 'Nuevo cliente' : 'Editar cliente'}
      >
        <div className="space-y-4">
          <Input label="Nombre completo *" name="fullName" value={form.fullName} onChange={handleChange} placeholder="Nombre y apellidos" maxLength={150} />
          <Input label="Email" name="email" type="email" value={form.email} onChange={handleChange} placeholder="cliente@email.com" maxLength={150} />
          <Input label="Teléfono" name="phone" value={form.phone} onChange={handleChange} placeholder="600 000 000" sanitize={INPUT_SANITIZE.PHONE} inputMode="tel" maxLength={20} />
          <Textarea label="Notas" name="notes" value={form.notes} onChange={handleChange} placeholder="Observaciones, alergias, preferencias…" maxLength={1000} />
          <div className="flex gap-3 pt-2">
            <Button variant="outline" onClick={closeModal} className="flex-1">Cancelar</Button>
            <Button onClick={handleSave} loading={saving} className="flex-1">
              {modal === 'create' ? 'Crear cliente' : 'Guardar cambios'}
            </Button>
          </div>
        </div>
      </Modal>

      <Modal open={modal === 'archive'} onClose={closeModal} title="Archivar cliente" size="sm">
        <div className="space-y-5">
          <div className="flex items-start gap-3 rounded-2xl bg-amber-50 border border-amber-100 px-4 py-4">
            <Archive size={20} className="text-amber-600 shrink-0 mt-0.5" />
            <p className="text-sm text-amber-800 leading-snug">
              ¿Archivar a <strong>{selected?.fullName}</strong>?
              Dejará de aparecer en el listado pero seguirá presente en el historial de citas.
            </p>
          </div>
          <div className="flex gap-3">
            <Button variant="outline" onClick={closeModal} className="flex-1">Cancelar</Button>
            <Button variant="danger" onClick={handleArchive} loading={saving} className="flex-1">Archivar</Button>
          </div>
        </div>
      </Modal>

      <AppointmentWizard
        open={!!wizardClient}
        prefillClientId={wizardClient?.id}
        bId={bId}
        onClose={() => setWizardClient(null)}
      />
    </div>
  )
}

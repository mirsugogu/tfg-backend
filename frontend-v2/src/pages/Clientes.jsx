import { useState } from 'react'
import { Plus, Search, Pencil, Trash2, UserX, Phone, Mail, Users } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { Textarea } from '@/components/ui/Textarea'
import { Modal } from '@/components/ui/Modal'
import { Pagination } from '@/components/ui/Pagination'
import { EmptyState } from '@/components/ui/EmptyState'
import { useAuth } from '@/context/AuthContext'
import { useToast } from '@/components/ui/Toast'
import { usePagedFetch } from '@/hooks/usePagedFetch'
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

const empty = { fullName: '', email: '', phone: '', notes: '' }

export default function Clientes() {
  const { user } = useAuth()
  const toast = useToast()
  const bId = user?.businessId

  // Listado paginado real (size 20 por defecto, el backend cappea en 100).
  // Tras crear/editar/eliminar se llama a refresh() para recargar la
  // pagina actual sin perder la posicion del usuario.
  const { items: clients, page, totalPages, totalElements, loading, setPage, refresh } =
    usePagedFetch(bId ? `/api/businesses/${bId}/clients` : null)

  const [search, setSearch] = useState('')
  const [modal, setModal] = useState(null)
  const [selected, setSelected] = useState(null)
  const [form, setForm] = useState(empty)
  const [saving, setSaving] = useState(false)

  // Busqueda LOCAL sobre la pagina actual: filtra los items que el
  // backend ya nos ha enviado. Para busqueda backend-side haria falta un
  // query param ?search= que hoy no expone /api/businesses/{id}/clients.
  const filtered = clients.filter((c) =>
    c.fullName?.toLowerCase().includes(search.toLowerCase()) ||
    c.email?.toLowerCase().includes(search.toLowerCase()) ||
    c.phone?.includes(search)
  )

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
  const openDelete = (c) => { setSelected(c); setModal('delete') }
  const closeModal = () => setModal(null)

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

  const handleDelete = async () => {
    setSaving(true)
    try {
      await api.delete(`/api/businesses/${bId}/clients/${selected.id}`)
      toast({ type: 'success', message: 'Cliente desactivado.' })
      closeModal()
      refresh()
    } catch (err) {
      // El backend devuelve 400 "El cliente ya esta desactivado" en el
      // 2o DELETE (M.010 / G.019 del audit). Con getErrorMessage el
      // mensaje real llega al usuario en vez de un generico.
      toast({ type: 'error', message: getErrorMessage(err, 'Error al eliminar el cliente.') })
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="p-8 max-w-7xl mx-auto">
      {/* Header */}
      <div className="mb-8 flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-[#1e3a5f] tracking-tight">Clientes</h1>
          <p className="text-sm text-slate-500 mt-1.5 font-medium flex items-center gap-1.5">
            <Users size={14} />
            {loading ? '…' : `${totalElements} cliente${totalElements === 1 ? '' : 's'} registrado${totalElements === 1 ? '' : 's'}`}
          </p>
        </div>
        <Button onClick={openCreate} className="gap-2">
          <Plus size={16} /> Nuevo cliente
        </Button>
      </div>

      {/* Search */}
      <div className="mb-5 relative max-w-sm">
        <Search size={15} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400" />
        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Buscar en esta pagina por nombre, email o telefono…"
          className="h-11 w-full rounded-2xl border border-slate-200 bg-slate-50 pl-10 pr-3.5 text-sm text-[#1f2c4a] placeholder:text-slate-400 focus:outline-none focus:bg-white focus:border-blue-400 focus:ring-4 focus:ring-blue-100 transition-all"
        />
      </div>

      {/* Table */}
      <div className="bg-white rounded-2xl border border-slate-100/80 shadow-[0_2px_12px_-2px_rgba(15,23,42,0.06)] overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-100 bg-slate-50/60">
                <th className="px-6 py-3.5 text-left text-[11px] font-semibold text-slate-400 uppercase tracking-wider">Nombre</th>
                <th className="px-6 py-3.5 text-left text-[11px] font-semibold text-slate-400 uppercase tracking-wider hidden md:table-cell">Email</th>
                <th className="px-6 py-3.5 text-left text-[11px] font-semibold text-slate-400 uppercase tracking-wider hidden lg:table-cell">Telefono</th>
                <th className="px-6 py-3.5 text-left text-[11px] font-semibold text-slate-400 uppercase tracking-wider hidden xl:table-cell">Notas</th>
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
                        {`Sin coincidencias en esta página${totalPages > 1 ? ' (prueba a cambiar de página)' : ''}.`}
                      </p>
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
                filtered.map((c) => (
                  <tr key={c.id} className="border-b border-slate-50 hover:bg-slate-50/60 transition-colors">
                    <td className="px-6 py-4">
                      <div className="flex items-center gap-3">
                        <div className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br ${avatarColor(c.id)} text-white text-xs font-bold`}>
                          {c.fullName?.[0]?.toUpperCase()}
                        </div>
                        <span className="font-semibold text-[#1e3a5f]">{c.fullName}</span>
                      </div>
                    </td>
                    <td className="px-6 py-4 hidden md:table-cell text-slate-500">
                      {c.email ? (
                        <span className="flex items-center gap-1.5 text-sm">
                          <Mail size={13} className="text-slate-400" /> {c.email}
                        </span>
                      ) : <span className="text-slate-300">—</span>}
                    </td>
                    <td className="px-6 py-4 hidden lg:table-cell text-slate-500">
                      {c.phone ? (
                        <span className="flex items-center gap-1.5 text-sm">
                          <Phone size={13} className="text-slate-400" /> {c.phone}
                        </span>
                      ) : <span className="text-slate-300">—</span>}
                    </td>
                    <td className="px-6 py-4 hidden xl:table-cell text-slate-500 text-sm max-w-xs truncate">
                      {c.notes || <span className="text-slate-300">—</span>}
                    </td>
                    <td className="px-6 py-4 text-right">
                      <div className="flex justify-end gap-1">
                        <button
                          onClick={() => openEdit(c)}
                          className="rounded-xl p-2 text-slate-400 hover:bg-blue-50 hover:text-blue-600 transition-colors"
                          title="Editar"
                        >
                          <Pencil size={15} />
                        </button>
                        <button
                          onClick={() => openDelete(c)}
                          className="rounded-xl p-2 text-slate-400 hover:bg-red-50 hover:text-red-600 transition-colors"
                          title="Eliminar"
                        >
                          <Trash2 size={15} />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        <Pagination
          page={page}
          totalPages={totalPages}
          totalElements={totalElements}
          onChange={setPage}
        />
      </div>

      {/* Create / Edit modal */}
      <Modal open={modal === 'create' || modal === 'edit'} onClose={closeModal} title={modal === 'create' ? 'Nuevo cliente' : 'Editar cliente'}>
        <div className="space-y-4">
          <Input label="Nombre completo *" name="fullName" value={form.fullName} onChange={handleChange} placeholder="Nombre y apellidos" />
          <Input label="Email" name="email" type="email" value={form.email} onChange={handleChange} placeholder="cliente@email.com" />
          <Input label="Telefono" name="phone" value={form.phone} onChange={handleChange} placeholder="600 000 000" />
          <Textarea label="Notas" name="notes" value={form.notes} onChange={handleChange} placeholder="Observaciones, alergias, preferencias…" />
          <div className="flex gap-3 pt-2">
            <Button variant="outline" onClick={closeModal} className="flex-1">Cancelar</Button>
            <Button onClick={handleSave} loading={saving} className="flex-1">
              {modal === 'create' ? 'Crear cliente' : 'Guardar cambios'}
            </Button>
          </div>
        </div>
      </Modal>

      {/* Delete modal */}
      <Modal open={modal === 'delete'} onClose={closeModal} title="Eliminar cliente" size="sm">
        <div className="space-y-5">
          <div className="flex items-start gap-3 rounded-2xl bg-red-50 border border-red-100 px-4 py-4">
            <UserX size={20} className="text-red-500 shrink-0 mt-0.5" />
            <p className="text-sm text-red-700 leading-snug">
              ¿Desactivar a <strong>{selected?.fullName}</strong>? Seguira apareciendo en el historial de citas.
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

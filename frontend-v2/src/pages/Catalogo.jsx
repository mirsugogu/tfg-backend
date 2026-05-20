import { useCallback, useEffect, useState } from 'react'
import { Plus, Pencil, Trash2, Tag, Scissors } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { Select } from '@/components/ui/Select'
import { Textarea } from '@/components/ui/Textarea'
import { Modal } from '@/components/ui/Modal'
import { Pagination } from '@/components/ui/Pagination'
import { EmptyState } from '@/components/ui/EmptyState'
import { useAuth } from '@/context/AuthContext'
import { useToast } from '@/components/ui/Toast'
import { usePagedFetch } from '@/hooks/usePagedFetch'
import api, { getErrorMessage } from '@/lib/api'

const emptyService = { name: '', description: '', price: '', durationMinutes: '', categoryId: '', taxId: '' }
const emptyCategory = { name: '' }

export default function Catalogo() {
  const { user } = useAuth()
  const toast = useToast()
  const bId = user?.businessId
  const isAdmin = user?.role === 'ADMIN'

  // Listados paginados reales (size=20). Tras crear/editar/eliminar se
  // refresca la pagina actual y se vuelve a cargar los listados aux.
  const servicesUrl   = bId ? `/api/businesses/${bId}/services`   : null
  const categoriesUrl = bId ? `/api/businesses/${bId}/categories` : null

  const {
    items: services, page: servPage, totalPages: servTotalPages, totalElements: servTotal,
    loading: servLoading, setPage: setServPage, refresh: refreshServices,
  } = usePagedFetch(servicesUrl)

  const {
    items: categories, page: catPage, totalPages: catTotalPages, totalElements: catTotal,
    loading: catLoading, setPage: setCatPage, refresh: refreshCategories,
  } = usePagedFetch(categoriesUrl)

  // Listados auxiliares completos (size=100) para:
  //  - Dropdown de categoria en el modal de servicio.
  //  - Dropdown de impuesto en el modal de servicio.
  //  - Recuento "N servicios" por categoria en el tab categorias.
  // No es una vista navegable, sino la base para selectores y stats.
  // El backend cappea en 100; suficiente para los volumenes de un TFG.
  const [aux, setAux] = useState({ categories: [], taxes: [], services: [] })
  const [auxVersion, setAuxVersion] = useState(0)
  const bumpAux = useCallback(() => setAuxVersion((v) => v + 1), [])

  useEffect(() => {
    if (!bId) return
    Promise.allSettled([
      api.get(`/api/businesses/${bId}/categories?size=100`),
      api.get(`/api/businesses/${bId}/taxes?size=100`),
      api.get(`/api/businesses/${bId}/services?size=100`),
    ]).then((results) => {
      setAux({
        categories: results[0].status === 'fulfilled' ? results[0].value.data.content : [],
        taxes:      results[1].status === 'fulfilled' ? results[1].value.data.content : [],
        services:   results[2].status === 'fulfilled' ? results[2].value.data.content : [],
      })
      const firstError = results.find((r) => r.status === 'rejected')
      if (firstError) {
        toast({
          type: 'error',
          message: getErrorMessage(firstError.reason, 'No se pudieron cargar los datos auxiliares del catalogo.'),
        })
      }
    })
  }, [bId, auxVersion, toast])

  // Maps para mostrar nombres en los cards de servicios.
  const catMap = Object.fromEntries(aux.categories.map((c) => [c.id, c.name]))

  const [tab, setTab] = useState('services')
  const [modal, setModal] = useState(null)
  const [selected, setSelected] = useState(null)
  const [serviceForm, setServiceForm] = useState(emptyService)
  const [catForm, setCatForm] = useState(emptyCategory)
  const [saving, setSaving] = useState(false)

  const closeModal = () => { setModal(null); setSelected(null) }

  // ---- Service CRUD ----
  const openCreateService = () => {
    setSelected(null)
    setServiceForm(emptyService)
    setModal('service-create')
  }
  const openEditService = (s) => {
    setSelected(s)
    setServiceForm({
      name: s.name,
      description: s.description || '',
      price: s.price,
      durationMinutes: s.durationMinutes,
      categoryId: s.categoryId,
      taxId: s.taxId,
    })
    setModal('service-edit')
  }

  const handleServiceSave = async () => {
    const { name, price, durationMinutes, categoryId, taxId } = serviceForm
    if (!name || !price || !durationMinutes || !categoryId || !taxId) {
      toast({ type: 'error', message: 'Rellena todos los campos obligatorios.' })
      return
    }
    setSaving(true)
    try {
      const payload = {
        name,
        description:     serviceForm.description,
        price:           parseFloat(price),
        durationMinutes: parseInt(durationMinutes, 10),
        categoryId:      parseInt(categoryId, 10),
        taxId:           parseInt(taxId, 10),
      }
      if (modal === 'service-create') {
        await api.post(`/api/businesses/${bId}/services`, payload)
        toast({ type: 'success', message: 'Servicio creado.' })
      } else {
        await api.put(`/api/businesses/${bId}/services/${selected.id}`, payload)
        toast({ type: 'success', message: 'Servicio actualizado.' })
      }
      closeModal()
      refreshServices()
      bumpAux()
    } catch (err) {
      toast({ type: 'error', message: getErrorMessage(err, 'Error al guardar.') })
    } finally {
      setSaving(false)
    }
  }

  const handleDeleteService = async () => {
    setSaving(true)
    try {
      await api.delete(`/api/businesses/${bId}/services/${selected.id}`)
      toast({ type: 'success', message: 'Servicio eliminado.' })
      closeModal()
      refreshServices()
      bumpAux()
    } catch (err) {
      // 400 "El servicio ya esta desactivado" en el 2o DELETE (audit G.019).
      toast({ type: 'error', message: getErrorMessage(err, 'Error al eliminar.') })
    } finally {
      setSaving(false)
    }
  }

  // ---- Category CRUD ----
  const openCreateCat = () => {
    setSelected(null)
    setCatForm(emptyCategory)
    setModal('cat-create')
  }
  const openEditCat = (c) => {
    setSelected(c)
    setCatForm({ name: c.name })
    setModal('cat-edit')
  }

  const handleCatSave = async () => {
    if (!catForm.name.trim()) {
      toast({ type: 'error', message: 'El nombre es obligatorio.' })
      return
    }
    setSaving(true)
    try {
      if (modal === 'cat-create') {
        await api.post(`/api/businesses/${bId}/categories`, catForm)
        toast({ type: 'success', message: 'Categoria creada.' })
      } else {
        await api.put(`/api/businesses/${bId}/categories/${selected.id}`, catForm)
        toast({ type: 'success', message: 'Categoria actualizada.' })
      }
      closeModal()
      refreshCategories()
      bumpAux()
    } catch (err) {
      toast({ type: 'error', message: getErrorMessage(err, 'Error al guardar.') })
    } finally {
      setSaving(false)
    }
  }

  const handleDeleteCat = async () => {
    setSaving(true)
    try {
      await api.delete(`/api/businesses/${bId}/categories/${selected.id}`)
      toast({ type: 'success', message: 'Categoria eliminada.' })
      closeModal()
      refreshCategories()
      bumpAux()
    } catch (err) {
      toast({ type: 'error', message: getErrorMessage(err, 'Error al eliminar.') })
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="p-8 max-w-7xl mx-auto">
      <div className="mb-8">
        <h1 className="text-3xl font-bold text-[#1e3a5f] tracking-tight">Catalogo de servicios</h1>
        <p className="text-sm text-slate-500 mt-1.5 font-medium">
          {servTotal} servicios · {catTotal} categorias
        </p>
      </div>

      {/* Tabs */}
      <div className="mb-6 flex items-center gap-1 bg-slate-100 rounded-2xl p-1.5 w-fit">
        {[
          { key: 'services',   label: 'Servicios',  Icon: Scissors },
          { key: 'categories', label: 'Categorias', Icon: Tag },
        ].map(({ key, label, Icon }) => (
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

      {/* SERVICES TAB */}
      {tab === 'services' && (
        <>
          {isAdmin && (
            <div className="mb-5">
              <Button onClick={openCreateService} className="gap-2">
                <Plus size={16} /> Nuevo servicio
              </Button>
            </div>
          )}
          {servLoading ? (
            <div className="grid sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
              {[...Array(4)].map((_, i) => (
                <div key={i} className="h-36 bg-white rounded-2xl border border-slate-100 animate-pulse" />
              ))}
            </div>
          ) : services.length === 0 ? (
            <EmptyState
              icon={Scissors}
              title="El catálogo está vacío"
              description="Añade tu primer servicio para poder ofrecerlo en las citas."
              actionLabel={isAdmin ? 'Nuevo servicio' : undefined}
              onAction={isAdmin ? openCreateService : undefined}
            />
          ) : (
            <>
              <div className="grid sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
                {services.map((s) => (
                  <div
                    key={s.id}
                    className="group bg-white rounded-2xl border border-slate-100 p-5 hover:border-blue-200 hover:shadow-[0_2px_12px_-2px_rgba(15,23,42,0.06)] transition"
                  >
                    <div className="flex items-start justify-between mb-4">
                      <div className="w-11 h-11 rounded-xl bg-gradient-to-br from-teal-400 to-cyan-500 flex items-center justify-center text-white shadow-[0_6px_16px_-6px_rgba(20,184,166,0.5)]">
                        <Scissors size={18} />
                      </div>
                      {isAdmin && (
                        <div className="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                          <button
                            onClick={() => openEditService(s)}
                            className="rounded-xl p-1.5 text-slate-400 hover:bg-blue-50 hover:text-blue-600 transition-colors"
                          >
                            <Pencil size={14} />
                          </button>
                          <button
                            onClick={() => { setSelected(s); setModal('service-delete') }}
                            className="rounded-xl p-1.5 text-slate-400 hover:bg-red-50 hover:text-red-600 transition-colors"
                          >
                            <Trash2 size={14} />
                          </button>
                        </div>
                      )}
                    </div>
                    <div className="font-bold text-[#1e3a5f]">{s.name}</div>
                    {s.description && (
                      <p className="text-xs text-slate-400 mt-0.5 truncate">{s.description}</p>
                    )}
                    <div className="flex items-center justify-between mt-3">
                      <div className="text-xs text-slate-400">
                        {s.durationMinutes} min · <span className="text-slate-500">{catMap[s.categoryId] || '—'}</span>
                      </div>
                      <div className="text-base font-bold text-blue-600">{parseFloat(s.price).toFixed(2)} €</div>
                    </div>
                  </div>
                ))}
              </div>

              {servTotalPages > 1 && (
                <div className="mt-6 bg-white rounded-2xl border border-slate-100 overflow-hidden shadow-[0_2px_12px_-2px_rgba(15,23,42,0.06)]">
                  <Pagination
                    page={servPage}
                    totalPages={servTotalPages}
                    totalElements={servTotal}
                    onChange={setServPage}
                  />
                </div>
              )}
            </>
          )}
        </>
      )}

      {/* CATEGORIES TAB */}
      {tab === 'categories' && (
        <>
          {isAdmin && (
            <div className="mb-5">
              <Button onClick={openCreateCat} className="gap-2">
                <Plus size={16} /> Nueva categoria
              </Button>
            </div>
          )}
          {catLoading ? (
            <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
              {[...Array(3)].map((_, i) => (
                <div key={i} className="h-24 bg-white rounded-2xl border border-slate-100 animate-pulse" />
              ))}
            </div>
          ) : categories.length === 0 ? (
            <EmptyState
              icon={Tag}
              title="No hay categorías"
              description="Crea categorías para organizar los servicios del catálogo."
              actionLabel={isAdmin ? 'Nueva categoría' : undefined}
              onAction={isAdmin ? openCreateCat : undefined}
            />
          ) : (
            <>
              <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                {categories.map((c) => {
                  // Recuento basado en aux.services (size=100) -> suficiente
                  // para los volumenes habituales. Si un negocio tiene > 100
                  // servicios el count se quedaria corto.
                  const count = aux.services.filter((s) => s.categoryId === c.id).length
                  return (
                    <div
                      key={c.id}
                      className="group bg-white rounded-2xl border border-slate-100 p-5 hover:border-blue-200 hover:shadow-[0_2px_12px_-2px_rgba(15,23,42,0.06)] transition"
                    >
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-3">
                          <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-gradient-to-br from-blue-500 to-indigo-500 text-white shadow-[0_6px_16px_-6px_rgba(99,102,241,0.4)]">
                            <Tag size={17} />
                          </div>
                          <div>
                            <p className="font-bold text-[#1e3a5f]">{c.name}</p>
                            <p className="text-xs text-slate-400 mt-0.5">
                              {count} servicio{count !== 1 ? 's' : ''}
                            </p>
                          </div>
                        </div>
                        {isAdmin && (
                          <div className="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                            <button
                              onClick={() => openEditCat(c)}
                              className="rounded-xl p-1.5 text-slate-400 hover:bg-blue-50 hover:text-blue-600 transition-colors"
                            >
                              <Pencil size={14} />
                            </button>
                            <button
                              onClick={() => { setSelected(c); setModal('cat-delete') }}
                              className="rounded-xl p-1.5 text-slate-400 hover:bg-red-50 hover:text-red-600 transition-colors"
                            >
                              <Trash2 size={14} />
                            </button>
                          </div>
                        )}
                      </div>
                    </div>
                  )
                })}
              </div>

              {catTotalPages > 1 && (
                <div className="mt-6 bg-white rounded-2xl border border-slate-100 overflow-hidden shadow-[0_2px_12px_-2px_rgba(15,23,42,0.06)]">
                  <Pagination
                    page={catPage}
                    totalPages={catTotalPages}
                    totalElements={catTotal}
                    onChange={setCatPage}
                  />
                </div>
              )}
            </>
          )}
        </>
      )}

      {/* SERVICE MODAL */}
      <Modal
        open={modal === 'service-create' || modal === 'service-edit'}
        onClose={closeModal}
        title={modal === 'service-create' ? 'Nuevo servicio' : 'Editar servicio'}
        size="lg"
      >
        <div className="grid gap-4 sm:grid-cols-2">
          <div className="sm:col-span-2">
            <Input
              label="Nombre *"
              name="name"
              value={serviceForm.name}
              onChange={(e) => setServiceForm((p) => ({ ...p, name: e.target.value }))}
              placeholder="Nombre del servicio"
            />
          </div>
          <div className="sm:col-span-2">
            <Textarea
              label="Descripcion"
              name="description"
              value={serviceForm.description}
              onChange={(e) => setServiceForm((p) => ({ ...p, description: e.target.value }))}
              placeholder="Descripcion opcional…"
            />
          </div>
          <Input
            label="Precio (€) *"
            type="number"
            min="0"
            step="0.01"
            value={serviceForm.price}
            onChange={(e) => setServiceForm((p) => ({ ...p, price: e.target.value }))}
            placeholder="0.00"
          />
          <Input
            label="Duracion (min) *"
            type="number"
            min="1"
            value={serviceForm.durationMinutes}
            onChange={(e) => setServiceForm((p) => ({ ...p, durationMinutes: e.target.value }))}
            placeholder="30"
          />
          <Select
            label="Categoria *"
            value={serviceForm.categoryId}
            onChange={(e) => setServiceForm((p) => ({ ...p, categoryId: e.target.value }))}
          >
            <option value="">Selecciona categoria</option>
            {aux.categories.map((c) => (
              <option key={c.id} value={c.id}>{c.name}</option>
            ))}
          </Select>
          <Select
            label="Impuesto *"
            value={serviceForm.taxId}
            onChange={(e) => setServiceForm((p) => ({ ...p, taxId: e.target.value }))}
          >
            <option value="">Selecciona impuesto</option>
            {aux.taxes.map((t) => (
              <option key={t.id} value={t.id}>{t.name} ({t.percentage}%)</option>
            ))}
          </Select>
        </div>
        <div className="flex gap-3 mt-6">
          <Button variant="outline" onClick={closeModal} className="flex-1">Cancelar</Button>
          <Button onClick={handleServiceSave} loading={saving} className="flex-1">
            {modal === 'service-create' ? 'Crear servicio' : 'Guardar'}
          </Button>
        </div>
      </Modal>

      <Modal open={modal === 'service-delete'} onClose={closeModal} title="Eliminar servicio" size="sm">
        <p className="text-sm text-slate-600 mb-5">¿Eliminar el servicio <strong>{selected?.name}</strong>?</p>
        <div className="flex gap-3">
          <Button variant="outline" onClick={closeModal} className="flex-1">Cancelar</Button>
          <Button variant="danger" onClick={handleDeleteService} loading={saving} className="flex-1">Eliminar</Button>
        </div>
      </Modal>

      {/* CATEGORY MODAL */}
      <Modal
        open={modal === 'cat-create' || modal === 'cat-edit'}
        onClose={closeModal}
        title={modal === 'cat-create' ? 'Nueva categoria' : 'Editar categoria'}
        size="sm"
      >
        <div className="space-y-4">
          <Input
            label="Nombre *"
            value={catForm.name}
            onChange={(e) => setCatForm({ name: e.target.value })}
            placeholder="Nombre de la categoria"
          />
          <div className="flex gap-3">
            <Button variant="outline" onClick={closeModal} className="flex-1">Cancelar</Button>
            <Button onClick={handleCatSave} loading={saving} className="flex-1">
              {modal === 'cat-create' ? 'Crear' : 'Guardar'}
            </Button>
          </div>
        </div>
      </Modal>

      <Modal open={modal === 'cat-delete'} onClose={closeModal} title="Eliminar categoria" size="sm">
        <p className="text-sm text-slate-600 mb-5">¿Eliminar la categoria <strong>{selected?.name}</strong>?</p>
        <div className="flex gap-3">
          <Button variant="outline" onClick={closeModal} className="flex-1">Cancelar</Button>
          <Button variant="danger" onClick={handleDeleteCat} loading={saving} className="flex-1">Eliminar</Button>
        </div>
      </Modal>
    </div>
  )
}

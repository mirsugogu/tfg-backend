// Selector de negocio activo para usuarios con multiples memberships
import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Building2, ChevronDown, Check, Search } from 'lucide-react'
import { Modal } from '@/components/ui/Modal'
import { useAuth } from '@/context/AuthContext'
import { useCatalog } from '@/context/CatalogContext'
import { useToast } from '@/components/ui/Toast'
import api, { getErrorMessage } from '@/lib/api'

/** Selector de negocio activo; si el usuario tiene varios, abre ventana con buscador */
export function BusinessSwitcher() {
  const { user, switchBusiness } = useAuth()
  const { roleLabel } = useCatalog()
  const toast = useToast()
  const navigate = useNavigate()

  const [businesses, setBusinesses] = useState([])
  const [open, setOpen] = useState(false)
  const [switching, setSwitching] = useState(false)
  const [query, setQuery] = useState('')

  useEffect(() => {
    api.get('/api/me/businesses')
      .then((r) => setBusinesses(r.data))
      .catch((err) => console.warn('No se pudo cargar la lista de negocios', err))
  }, [])

  const current = businesses.find((b) => b.businessId === user?.businessId)
  const canSwitch = businesses.length > 1

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    if (!q) return businesses
    return businesses.filter((b) => (b.businessName || '').toLowerCase().includes(q))
  }, [businesses, query])

  const handleSwitch = async (businessId) => {
    if (businessId === user?.businessId) { setOpen(false); return }
    setSwitching(true)
    try {
      await switchBusiness(businessId)
      setOpen(false); setQuery('')
      navigate('/dashboard')
      toast({ type: 'success', message: 'Has cambiado de negocio.' })
    } catch (err) {
      toast({ type: 'error', message: getErrorMessage(err, 'No se pudo cambiar de negocio.') })
    } finally {
      setSwitching(false)
    }
  }

  return (
    <div className="px-3 pt-4">
      <button
        onClick={() => canSwitch && setOpen(true)}
        disabled={!canSwitch}
        title={canSwitch ? 'Cambiar de negocio' : undefined}
        className={`w-full flex items-center gap-2.5 rounded-xl bg-white/6 px-3 py-2.5 transition-colors ${
          canSwitch ? 'hover:bg-white/12 cursor-pointer' : 'cursor-default'
        }`}
      >
        <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-cyan-400 to-blue-500 flex items-center justify-center text-white shrink-0">
          <Building2 size={15} />
        </div>
        <div className="flex-1 min-w-0 text-left">
          <p className="text-xs font-semibold text-white truncate">
            {current?.businessName || 'Mi negocio'}
          </p>
          <p className="text-[10px] text-white/40 font-medium uppercase tracking-wider mt-0.5">
            {current ? roleLabel(current.role) : '—'}
          </p>
        </div>
        {canSwitch && <ChevronDown size={14} className="text-white/40 shrink-0" />}
      </button>

      <Modal open={open} onClose={() => { setOpen(false); setQuery('') }} title="Cambiar de negocio" size="sm">
        <div className="space-y-3">
          {businesses.length > 5 && (
            <div className="relative">
              <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
              <input
                autoFocus
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Filtrar por nombre…"
                className="h-10 w-full rounded-xl border border-slate-200 bg-white pl-9 pr-3 text-sm text-[#1e3a5f] focus:outline-none focus:border-blue-400 focus:ring-4 focus:ring-blue-100 transition"
              />
            </div>
          )}
          <div className="space-y-2 max-h-80 overflow-y-auto">
            {filtered.length === 0 ? (
              <p className="text-center text-xs text-slate-400 py-6">Sin resultados.</p>
            ) : (
              filtered.map((b) => {
                const active = b.businessId === user?.businessId
                return (
                  <button
                    key={b.businessId}
                    onClick={() => handleSwitch(b.businessId)}
                    disabled={switching}
                    className={`w-full flex items-center gap-3 rounded-2xl border px-4 py-3 text-left transition-all disabled:opacity-60 ${
                      active
                        ? 'border-blue-300 bg-blue-50/60'
                        : 'border-slate-200 bg-white hover:border-blue-300 hover:bg-blue-50/40'
                    }`}
                  >
                    <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-cyan-400 to-blue-500 flex items-center justify-center text-white shrink-0">
                      <Building2 size={16} />
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="font-semibold text-[#1e3a5f] text-sm truncate">
                        {b.businessName}
                      </p>
                      <p className="text-xs text-slate-400">{roleLabel(b.role)}</p>
                    </div>
                    {active && <Check size={16} className="text-blue-600 shrink-0" />}
                  </button>
                )
              })
            )}
          </div>
        </div>
      </Modal>
    </div>
  )
}

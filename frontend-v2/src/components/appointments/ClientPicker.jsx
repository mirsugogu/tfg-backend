import { useState, useEffect, useRef, useId } from 'react'
import { Search, X, UserPlus, Check, Loader2 } from 'lucide-react'
import { cn } from '@/lib/utils'
import api, { getErrorMessage } from '@/lib/api'
import { useToast } from '@/components/ui/Toast'
import { Input } from '@/components/ui/Input'
import { Button } from '@/components/ui/Button'

/**
 * ClientPicker — selector de cliente con búsqueda server-side.
 *
 * Sustituye al <select> plano de clientes: en lugar de precargar los
 * primeros 100 clientes (y dejar fuera al resto), consulta
 * GET /clients?search=... con debounce, así escala sin límite.
 *
 * Incluye crear un cliente al vuelo sin salir del asistente (P5): el botón
 * "Nuevo cliente" despliega un mini-formulario en el propio desplegable.
 *
 * Controlado por `value` (id del cliente). Si llega un `value` sin que el
 * componente tenga el objeto (caso prefill), lo resuelve con GET /clients/{id}.
 *
 * @param bId       id del negocio (multi-tenant).
 * @param value     id del cliente seleccionado, o '' .
 * @param onChange  callback(cliente|null) al seleccionar, crear o limpiar.
 * @param label     etiqueta del campo.
 * @param error     mensaje de error opcional.
 */
export function ClientPicker({ bId, value, onChange, label, error }) {
  const toast = useToast()
  const fieldId = useId()
  const boxRef = useRef(null)
  // id del cliente ya resuelto: evita re-fetchear tras una selección propia.
  const lastResolved = useRef(null)
  // onChange vía ref: el efecto de resolución del prefill notifica al padre
  // sin tener que re-suscribirse en cada render.
  const onChangeRef = useRef(onChange)
  onChangeRef.current = onChange

  const [selected, setSelected] = useState(null)
  const [query, setQuery] = useState('')
  const [results, setResults] = useState([])
  const [loading, setLoading] = useState(false)
  const [open, setOpen] = useState(false)
  const [creating, setCreating] = useState(false)
  const [newClient, setNewClient] = useState({ fullName: '', email: '', phone: '' })
  const [savingNew, setSavingNew] = useState(false)

  // Resuelve el objeto cliente cuando llega un `value` de fuera (prefill).
  useEffect(() => {
    if (!value) { setSelected(null); lastResolved.current = null; return }
    if (String(lastResolved.current) === String(value)) return
    let cancelled = false
    api.get(`/api/businesses/${bId}/clients/${value}`)
      .then((r) => {
        if (cancelled) return
        setSelected(r.data)
        lastResolved.current = String(value)
        onChangeRef.current(r.data)
      })
      .catch(() => { /* si falla, el campo queda en modo búsqueda */ })
    return () => { cancelled = true }
  }, [value, bId])

  // Búsqueda con debounce (300 ms). Mínimo 2 caracteres.
  useEffect(() => {
    if (!open || creating) return
    const q = query.trim()
    if (q.length < 2) { setResults([]); setLoading(false); return }
    setLoading(true)
    const t = setTimeout(() => {
      api.get(`/api/businesses/${bId}/clients`, { params: { search: q, size: 20 } })
        .then((r) => setResults(r.data.content ?? []))
        .catch((err) => {
          toast({ type: 'error', message: getErrorMessage(err, 'Error al buscar clientes.') })
          setResults([])
        })
        .finally(() => setLoading(false))
    }, 300)
    return () => clearTimeout(t)
  }, [query, open, creating, bId, toast])

  // Cierra el desplegable al pulsar fuera.
  useEffect(() => {
    if (!open) return
    const onClickOutside = (e) => {
      if (!boxRef.current?.contains(e.target)) { setOpen(false); setCreating(false) }
    }
    document.addEventListener('mousedown', onClickOutside)
    return () => document.removeEventListener('mousedown', onClickOutside)
  }, [open])

  const choose = (client) => {
    lastResolved.current = String(client.id)
    setSelected(client)
    onChange(client)
    setOpen(false)
    setCreating(false)
    setQuery('')
    setResults([])
  }

  const clear = () => {
    lastResolved.current = null
    setSelected(null)
    onChange(null)
    setQuery('')
    setResults([])
  }

  const submitNewClient = async () => {
    const name = newClient.fullName.trim()
    if (!name) {
      toast({ type: 'error', message: 'El nombre del cliente es obligatorio.' })
      return
    }
    setSavingNew(true)
    try {
      const r = await api.post(`/api/businesses/${bId}/clients`, {
        fullName: name,
        email: newClient.email.trim() || null,
        phone: newClient.phone.trim() || null,
      })
      toast({ type: 'success', message: 'Cliente creado.' })
      setNewClient({ fullName: '', email: '', phone: '' })
      choose(r.data)
    } catch (err) {
      toast({ type: 'error', message: getErrorMessage(err, 'No se pudo crear el cliente.') })
    } finally {
      setSavingNew(false)
    }
  }

  const q = query.trim()

  return (
    <div className="flex flex-col gap-1.5" ref={boxRef}>
      {label && (
        <label htmlFor={fieldId} className="text-[11px] font-semibold text-slate-500 uppercase tracking-wide">{label}</label>
      )}

      {selected ? (
        /* Cliente elegido — chip con opción de quitarlo */
        <div className="flex items-center justify-between h-11 rounded-2xl border border-blue-200 bg-blue-50 px-3.5">
          <span className="flex items-center gap-2 text-sm font-semibold text-[#1e3a5f] truncate">
            <Check size={15} className="text-blue-500 shrink-0" />
            <span className="truncate">{selected.fullName}</span>
          </span>
          <button type="button" onClick={clear} aria-label="Quitar cliente"
            className="text-slate-400 hover:text-red-500 transition shrink-0">
            <X size={16} />
          </button>
        </div>
      ) : (
        /* Buscador */
        <div className="relative">
          <Search size={15} className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            id={fieldId}
            value={query}
            onChange={(e) => { setQuery(e.target.value); setOpen(true); setCreating(false) }}
            onFocusCapture={() => setOpen(true)}
            onFocus={() => setOpen(true)}
            placeholder="Busca por nombre, email o teléfono…"
            autoComplete="off"
            className={cn(
              'h-11 w-full rounded-2xl border border-slate-200 bg-slate-50 pl-10 pr-3.5 text-sm text-[#1f2c4a] placeholder:text-slate-400',
              'transition-all focus:outline-none focus:bg-white focus:border-blue-400 focus:ring-4 focus:ring-blue-100',
              error && 'border-red-300',
            )}
          />

          {open && (
            <div className="absolute z-30 mt-1.5 w-full rounded-2xl border border-slate-200 bg-white shadow-[0_20px_50px_-10px_rgba(15,23,42,0.18)] overflow-hidden">
              {creating ? (
                /* Mini-formulario: crear cliente al vuelo (P5) */
                <div className="p-3 space-y-2.5">
                  <p className="text-[11px] font-bold text-slate-400 uppercase tracking-wider">Nuevo cliente</p>
                  <Input label="Nombre *" value={newClient.fullName} autoFocus
                    onChange={(e) => setNewClient((p) => ({ ...p, fullName: e.target.value }))} />
                  <Input label="Email" type="email" value={newClient.email}
                    onChange={(e) => setNewClient((p) => ({ ...p, email: e.target.value }))} />
                  <Input label="Teléfono" value={newClient.phone}
                    onChange={(e) => setNewClient((p) => ({ ...p, phone: e.target.value }))} />
                  <div className="flex gap-2 pt-1">
                    <Button variant="outline" onClick={() => setCreating(false)} className="flex-1">Cancelar</Button>
                    <Button onClick={submitNewClient} loading={savingNew} className="flex-1">Crear</Button>
                  </div>
                </div>
              ) : (
                <>
                  <div className="max-h-56 overflow-y-auto">
                    {loading && (
                      <div className="flex items-center gap-2 px-3.5 py-3 text-sm text-slate-400">
                        <Loader2 size={14} className="animate-spin" /> Buscando…
                      </div>
                    )}
                    {!loading && q.length < 2 && (
                      <div className="px-3.5 py-3 text-sm text-slate-400">Escribe al menos 2 caracteres…</div>
                    )}
                    {!loading && q.length >= 2 && results.length === 0 && (
                      <div className="px-3.5 py-3 text-sm text-slate-400">Sin coincidencias.</div>
                    )}
                    {!loading && results.map((c) => (
                      <button key={c.id} type="button" onClick={() => choose(c)}
                        className="w-full text-left px-3.5 py-2.5 hover:bg-blue-50 transition border-b border-slate-50 last:border-0">
                        <div className="text-sm font-semibold text-[#1e3a5f] truncate">{c.fullName}</div>
                        {(c.email || c.phone) && (
                          <div className="text-xs text-slate-400 truncate">
                            {[c.email, c.phone].filter(Boolean).join(' · ')}
                          </div>
                        )}
                      </button>
                    ))}
                  </div>
                  <button type="button" onClick={() => setCreating(true)}
                    className="w-full flex items-center gap-2 px-3.5 py-2.5 text-sm font-semibold text-blue-600 hover:bg-blue-50 transition border-t border-slate-100">
                    <UserPlus size={15} /> Nuevo cliente
                  </button>
                </>
              )}
            </div>
          )}
        </div>
      )}

      {error && <p className="text-xs text-red-500 font-medium">{error}</p>}
    </div>
  )
}

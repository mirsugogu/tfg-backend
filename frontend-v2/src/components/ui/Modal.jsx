// Dialogo modal accesible con cierre por Escape y gestion de foco
import { useEffect, useId, useRef } from 'react'
import { X } from 'lucide-react'
import { cn } from '@/lib/utils'

/** Ventana centrada con cierre por teclado */
export function Modal({ open, onClose, title, children, size = 'md' }) {
  const titleId = useId()
  const panelRef = useRef(null)

  // Mantiene el cierre actual sin rehacer el efecto
  const onCloseRef = useRef(onClose)
  useEffect(() => { onCloseRef.current = onClose }, [onClose])

  useEffect(() => {
    document.body.style.overflow = open ? 'hidden' : ''
    if (!open) return
    // Cierre con Escape
    const onKey = (e) => { if (e.key === 'Escape') onCloseRef.current?.() }
    window.addEventListener('keydown', onKey)
    // Al abrir mueve el foco y al cerrar lo restaura
    const prevFocus = document.activeElement
    panelRef.current?.focus()
    return () => {
      document.body.style.overflow = ''
      window.removeEventListener('keydown', onKey)
      if (prevFocus instanceof HTMLElement) prevFocus.focus()
    }
    // Efecto ligado solo al estado abierto
  }, [open])

  if (!open) return null

  const sizes = { sm: 'max-w-md', md: 'max-w-lg', lg: 'max-w-2xl', xl: 'max-w-3xl' }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div
        className="absolute inset-0 bg-[#1e3a5f]/40 backdrop-blur-sm"
        onClick={onClose}
      />
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        className={cn(
          // El panel conserva altura visible y deja scroll interno
          'relative flex max-h-[90dvh] w-full flex-col rounded-2xl bg-white shadow-[0_20px_60px_-10px_rgba(15,23,42,0.25)] animate-in fade-in zoom-in-95 duration-200 focus:outline-none',
          sizes[size]
        )}
      >
        <div className="flex shrink-0 items-center justify-between border-b border-slate-100 px-6 py-4">
          <h2 id={titleId} className="text-base font-bold text-[#1e3a5f] tracking-tight">{title}</h2>
          <button
            onClick={onClose}
            aria-label="Cerrar"
            className="w-8 h-8 rounded-xl flex items-center justify-center text-slate-400 hover:bg-slate-100 hover:text-slate-700 transition-colors"
          >
            <X size={17} />
          </button>
        </div>
        <div className="min-h-0 overflow-y-auto px-6 py-5">{children}</div>
      </div>
    </div>
  )
}

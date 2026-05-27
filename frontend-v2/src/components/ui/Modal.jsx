import { useEffect, useId, useRef } from 'react'
import { X } from 'lucide-react'
import { cn } from '@/lib/utils'

/** Modal accesible centrado con backdrop, cierre por Esc y restauración de foco. */
export function Modal({ open, onClose, title, children, size = 'md' }) {
  const titleId = useId()
  const panelRef = useRef(null)

  // El componente padre recrea `onClose` en cada render (p. ej.
  // `onClose={() => setModal(null)}`). Lo guardamos en un ref para leerlo
  // desde el efecto de abajo SIN incluirlo en sus dependencias: si
  // estuviera en las deps, el efecto se re-ejecutaría en cada render del
  // padre — y como un formulario re-renderiza en cada pulsación de tecla,
  // el `panelRef.current.focus()` robaría el foco al <input> letra a letra.
  const onCloseRef = useRef(onClose)
  useEffect(() => { onCloseRef.current = onClose }, [onClose])

  useEffect(() => {
    document.body.style.overflow = open ? 'hidden' : ''
    if (!open) return
    // Cerrar con la tecla Escape, igual que los drawers laterales.
    const onKey = (e) => { if (e.key === 'Escape') onCloseRef.current?.() }
    window.addEventListener('keydown', onKey)
    // Accesibilidad: al abrir, recuerda el elemento que tenía el foco y
    // muévelo al diálogo; al cerrar, restáuralo. Sin esto el foco se queda
    // en el botón del fondo y un lector de pantalla nunca "entra" al modal.
    const prevFocus = document.activeElement
    panelRef.current?.focus()
    return () => {
      document.body.style.overflow = ''
      window.removeEventListener('keydown', onKey)
      if (prevFocus instanceof HTMLElement) prevFocus.focus()
    }
    // Dependencia SOLO `open`: el efecto debe correr al abrir/cerrar el
    // modal, nunca en cada render del padre (ver comentario del ref arriba).
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
          // max-h + flex-col: el panel nunca supera el 90% del alto VISIBLE de
          // la ventana (dvh, no vh — descuenta la barra del navegador móvil);
          // si el contenido es más alto, el cuerpo hace scroll en lugar de
          // salirse de la pantalla (crítico en móvil y pantallas bajas).
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
        {/* min-h-0 permite que este bloque encoja dentro del flex y, con
            overflow-y-auto, el contenido largo hace scroll sin tapar la
            cabecera ni dejar los botones fuera de la pantalla. */}
        <div className="min-h-0 overflow-y-auto px-6 py-5">{children}</div>
      </div>
    </div>
  )
}

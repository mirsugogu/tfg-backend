import { ChevronLeft, ChevronRight } from 'lucide-react'

/**
 * Control de paginacion sencillo: prev/next + indicador "Pagina X de Y".
 * Se oculta automaticamente si hay 1 o 0 paginas.
 *
 * Props:
 *   page          numero de pagina actual (0-indexed)
 *   totalPages    total de paginas
 *   totalElements total de elementos (mostrado como info)
 *   onChange(n)   callback al cambiar de pagina
 */
export function Pagination({ page, totalPages, totalElements, onChange }) {
  if (!totalPages || totalPages <= 1) return null

  const prev = () => onChange(Math.max(0, page - 1))
  const next = () => onChange(Math.min(totalPages - 1, page + 1))

  return (
    <div className="flex items-center justify-between px-6 py-3 border-t border-slate-100 bg-white">
      <span className="text-xs text-slate-400">
        Pagina <strong className="text-[#1e3a5f] font-semibold">{page + 1}</strong> de {totalPages}
        {typeof totalElements === 'number' && (
          <> · {totalElements} registro{totalElements === 1 ? '' : 's'}</>
        )}
      </span>
      <div className="flex gap-1">
        <button
          type="button"
          onClick={prev}
          disabled={page <= 0}
          className="rounded-xl p-2 text-slate-400 hover:bg-blue-50 hover:text-blue-600 disabled:opacity-30 disabled:pointer-events-none transition-colors"
          aria-label="Pagina anterior"
        >
          <ChevronLeft size={16} />
        </button>
        <button
          type="button"
          onClick={next}
          disabled={page >= totalPages - 1}
          className="rounded-xl p-2 text-slate-400 hover:bg-blue-50 hover:text-blue-600 disabled:opacity-30 disabled:pointer-events-none transition-colors"
          aria-label="Pagina siguiente"
        >
          <ChevronRight size={16} />
        </button>
      </div>
    </div>
  )
}

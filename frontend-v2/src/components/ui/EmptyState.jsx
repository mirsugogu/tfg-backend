import { Plus } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { cn } from '@/lib/utils'

/**
 * EmptyState — estado vacío reutilizable: icono + título + descripción y
 * una acción opcional (botón "crear …").
 *
 * Se usa cuando un listado todavía no tiene NINGÚN elemento, para invitar
 * al usuario a crear el primero. No se usa para "la búsqueda no encontró
 * resultados" (ese caso se queda como texto simple).
 *
 * Props:
 *   icon         componente de icono (lucide-react).
 *   title        título corto.
 *   description  texto de apoyo (opcional).
 *   actionLabel  texto del botón (opcional; sin él no se muestra botón).
 *   onAction     callback del botón (opcional).
 */
export function EmptyState({ icon: Icon, title, description, actionLabel, onAction, className }) {
  return (
    <div className={cn('flex flex-col items-center justify-center text-center px-6 py-16', className)}>
      {Icon && (
        <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-slate-100 text-slate-400 mb-4">
          <Icon size={24} />
        </div>
      )}
      <p className="text-sm font-semibold text-[#1e3a5f]">{title}</p>
      {description && (
        <p className="text-xs text-slate-400 mt-1 max-w-xs leading-relaxed">{description}</p>
      )}
      {actionLabel && onAction && (
        <Button onClick={onAction} size="sm" className="mt-4 gap-1.5">
          <Plus size={14} /> {actionLabel}
        </Button>
      )}
    </div>
  )
}

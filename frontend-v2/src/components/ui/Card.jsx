import { cn } from '@/lib/utils'

/**
 * Tarjeta blanca con borde y sombra suave. Es el contenedor visual
 * estándar de las secciones de la aplicación. `CardHeader`, `CardTitle`
 * y `CardContent` son sub-componentes opcionales para estructurar el
 * interior con paddings coherentes.
 */
export function Card({ children, className, ...props }) {
  return (
    <div
      className={cn(
        'rounded-2xl border border-slate-100/80 bg-white shadow-[0_2px_12px_-2px_rgba(15,23,42,0.06)]',
        className
      )}
      {...props}
    >
      {children}
    </div>
  )
}

export function CardHeader({ children, className }) {
  return <div className={cn('px-6 pt-6 pb-3', className)}>{children}</div>
}

export function CardTitle({ children, className }) {
  return (
    <h3 className={cn('text-base font-bold text-[#1e3a5f] tracking-tight', className)}>{children}</h3>
  )
}

export function CardContent({ children, className }) {
  return <div className={cn('px-6 pb-6', className)}>{children}</div>
}

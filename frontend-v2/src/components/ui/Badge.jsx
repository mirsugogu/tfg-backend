import { cn } from '@/lib/utils'

const variants = {
  default:  'bg-slate-100 text-slate-600 ring-1 ring-slate-200',
  navy:     'bg-[#1e3a5f] text-white',
  cyan:     'bg-[#e0f7ff] text-[#0284c7] ring-1 ring-[#38bdf8]/30',
  success:  'bg-emerald-50 text-emerald-700 ring-1 ring-emerald-200',
  warning:  'bg-amber-50 text-amber-700 ring-1 ring-amber-200',
  orange:   'bg-orange-50 text-orange-600 ring-1 ring-orange-200',
  danger:   'bg-red-50 text-red-700 ring-1 ring-red-200',
  info:     'bg-blue-50 text-blue-700 ring-1 ring-blue-200',
  purple:   'bg-purple-50 text-purple-700 ring-1 ring-purple-200',
}

/** Etiqueta pequeña de color con variantes predefinidas. */
export function Badge({ children, variant = 'default', className }) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-semibold',
        variants[variant],
        className
      )}
    >
      {children}
    </span>
  )
}

const STATUS_MAP = {
  PENDING:     { label: 'Pendiente',     variant: 'orange',   dot: 'bg-orange-500' },
  CONFIRMED:   { label: 'Confirmada',    variant: 'info',     dot: 'bg-blue-500' },
  IN_PROGRESS: { label: 'En curso',      variant: 'cyan',     dot: 'bg-[#38bdf8]' },
  COMPLETED:   { label: 'Completada',    variant: 'success',  dot: 'bg-emerald-500' },
  CANCELLED:   { label: 'Cancelada',     variant: 'danger',   dot: 'bg-red-500' },
  NO_SHOW:     { label: 'No presentado', variant: 'default',  dot: 'bg-slate-400' },
}

/** Badge para el estado de una cita: traduce el nombre técnico y aplica color. */
export function StatusBadge({ status }) {
  const config = STATUS_MAP[status] ?? { label: status, variant: 'default', dot: 'bg-slate-400' }
  return (
    <Badge variant={config.variant}>
      <span className={cn('h-1.5 w-1.5 rounded-full shrink-0', config.dot)} />
      {config.label}
    </Badge>
  )
}

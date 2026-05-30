/* Celdas y eventos del calendario: rejilla horaria, slots, linea "ahora", evento posicionado y chip */
import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { Ban, Clock, User, MapPin, Scissors } from 'lucide-react'
import { pad2, minutesOf, apptDuration, apptHHMM, styleFor, labelForBlock } from './utils'
import { dotClassFromColorAndId } from '@/lib/employeeColor'
import { totalBooked } from '@/lib/format'
import { useDragAppointment } from './drag'
import { useCatalog } from '@/context/CatalogContext'

/** Funcion de hover con delay de 250ms; devuelve handlers y un tooltip listo para inyectar */
function useApptHover(appt, employeeColor) {
  const [rect, setRect] = useState(null)
  const timerRef = useRef(null)
  useEffect(() => () => clearTimeout(timerRef.current), [])
  const onMouseEnter = (e) => {
    const r = e.currentTarget.getBoundingClientRect()
    clearTimeout(timerRef.current)
    timerRef.current = setTimeout(() => setRect(r), 250)
  }
  const onMouseLeave = () => {
    clearTimeout(timerRef.current)
    setRect(null)
  }
  const tooltip = rect ? (
    <ApptHoverCard appt={appt} anchorRect={rect} employeeColor={employeeColor} />
  ) : null
  return { onMouseEnter, onMouseLeave, tooltip }
}

/** Tarjeta flotante portaleada con el resumen de la cita; se ancla a un rect dado */
function ApptHoverCard({ appt, anchorRect, employeeColor }) {
  const { statusLabel } = useCatalog()
  const CARD_W = 280
  const CARD_H_ESTIMATED = 220
  const margin = 8
  const showRight = anchorRect.right + CARD_W + margin <= window.innerWidth
  const left = showRight
    ? anchorRect.right + margin
    : Math.max(margin, anchorRect.left - CARD_W - margin)
  const top = Math.max(
    margin,
    Math.min(anchorRect.top, window.innerHeight - CARD_H_ESTIMATED - margin),
  )
  const empDot = dotClassFromColorAndId(employeeColor, appt.membershipId)
  const services = appt.bookedServices ?? []
  const total = services.length ? totalBooked(services) : null

  return createPortal(
    <div
      style={{ position: 'fixed', top, left, width: CARD_W, zIndex: 60 }}
      className="pointer-events-none rounded-2xl bg-white border border-slate-200 shadow-[0_12px_40px_-8px_rgba(15,23,42,0.25)] p-4 text-left"
    >
      <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Cliente</p>
      <p className="text-sm font-bold text-[#1e3a5f] truncate">{appt.clientName}</p>

      <div className="mt-3 grid grid-cols-[auto_1fr] gap-x-2 gap-y-1.5 text-xs text-slate-600">
        <Clock size={12} className="text-slate-400 mt-0.5" />
        <span className="tabular-nums">
          {apptHHMM(appt.startDateTime)} – {apptHHMM(appt.endDateTime)}
        </span>

        <span className="flex items-center justify-center w-3 mt-0.5">
          <span className={`w-2 h-2 rounded-full ${empDot}`} aria-hidden />
        </span>
        <span className="truncate">
          <User size={11} className="inline -mt-0.5 mr-1 text-slate-400" />
          {appt.userFullName}
        </span>

        {appt.boothName && (
          <>
            <MapPin size={12} className="text-slate-400 mt-0.5" />
            <span className="truncate">{appt.boothName}</span>
          </>
        )}

        {services.length > 0 && (
          <>
            <Scissors size={12} className="text-slate-400 mt-0.5" />
            <span className="truncate" title={services.map((s) => s.serviceName).join(', ')}>
              {services.map((s) => s.serviceName).join(' + ')}
            </span>
          </>
        )}
      </div>

      <div className="mt-3 pt-2 border-t border-slate-100 flex items-center justify-between text-xs">
        <span className="font-semibold text-slate-500 uppercase tracking-wider text-[10px]">
          {statusLabel(appt.statusName)}
        </span>
        {total != null && (
          <span className="font-bold text-[#1e3a5f] tabular-nums">{total} €</span>
        )}
      </div>
    </div>,
    document.body,
  )
}

/** Columna izquierda con las etiquetas horarias del dia */
export function HourColumn({ withHeader = true, dayStart, dayEnd, hourPx }) {
  const hours = []
  for (let h = dayStart; h < dayEnd; h++) hours.push(h)
  return (
    <div className="w-14 shrink-0 border-r-2 border-slate-300 bg-white">
      {withHeader && <div className="h-10 border-b-2 border-slate-300" />}
      {hours.map((h, idx) => (
        <div key={h} style={{ height: hourPx }} className="relative border-b border-slate-200">
          <span className={`absolute ${idx === 0 ? 'top-1' : '-top-2'} right-1.5 text-[10px] font-semibold text-slate-500 bg-white px-1`}>
            {pad2(h)}:00
          </span>
        </div>
      ))}
    </div>
  )
}

/** Slots horarios clicables del dia; pinta cerrado fuera del negocio/empleado y bloqueado en blocks */
export function HourSlots({
  dayKey, onSlotClick, dayStart, dayEnd, hourPx, closedRanges,
  workingRanges = null,
  isBlocked = false, blockedReason = null,
}) {
  const hours = []
  for (let h = dayStart; h < dayEnd; h++) hours.push(h)
  // Una hora esta abierta si cae en algun tramo; soporta 0/1/N tramos (turno partido)
  const inRange = (h, ranges) => ranges.some(([s, e]) => h >= s && h < e)
  const closedByBusiness  = (h) => !inRange(h, closedRanges)
  // Si la columna es de un empleado con horario propio, su descanso tambien cierra el slot
  const closedByEmployee  = (h) => workingRanges != null && !inRange(h, workingRanges)
  return (
    <>
      {hours.map((h, idx) => {
        const byBusiness = closedByBusiness(h)
        const byEmployee = !byBusiness && closedByEmployee(h)
        const closed = byBusiness || byEmployee
        const zebra = idx % 2 === 0 ? 'bg-slate-50/40' : 'bg-white'
        // Inactivo si cerrado por horario o si hay bloqueo aplicable; el servidor valida igual con 409
        const inactive = closed || isBlocked
        const title = byBusiness ? 'Fuera de horario'
                    : byEmployee ? 'Fuera del horario del empleado'
                    : isBlocked ? (blockedReason || 'Tramo bloqueado: no se pueden crear citas')
                    : 'Crear cita a esta hora'
        return (
          <div
            key={h}
            onClick={() => !inactive && onSlotClick(dayKey, h)}
            style={{ height: hourPx }}
            className={`border-b border-slate-300 relative ${
              closed
                ? 'bg-slate-200/60 cursor-not-allowed'
                : isBlocked
                  ? 'cursor-not-allowed'
                  : `${zebra} hover:bg-blue-50/60 cursor-pointer`
            }`}
            title={title}
          >
            <div className="absolute left-0 right-0 border-t border-dashed border-slate-300" style={{ top: hourPx / 2 }} />
          </div>
        )
      })}
    </>
  )
}

/** Franja roja con trama diagonal que cubre las horas de ausencia del empleado en la columna */
export function AbsenceOverlay({ absences, dayDate, dayStart, dayEnd, hourPx }) {
  if (!absences || absences.length === 0) return null
  // Recorta cada ausencia al rango visible (dayStart-dayEnd) para calcular top y height en px
  const dayMidnightMs = new Date(dayDate.getFullYear(), dayDate.getMonth(), dayDate.getDate()).getTime()
  const gridStartMs = dayMidnightMs + dayStart * 60 * 60 * 1000
  const gridEndMs = dayMidnightMs + dayEnd * 60 * 60 * 1000
  const gridTotalPx = (dayEnd - dayStart) * hourPx
  return (
    <>
      {absences.map((abs) => {
        const startMs = new Date(abs.startDateTime).getTime()
        const endMs = new Date(abs.endDateTime).getTime()
        const visStart = Math.max(startMs, gridStartMs)
        const visEnd = Math.min(endMs, gridEndMs)
        if (visEnd <= visStart) return null
        const minutesFromGridStart = (visStart - gridStartMs) / 60000
        const durationMinutes = (visEnd - visStart) / 60000
        const topPx = (minutesFromGridStart / 60) * hourPx
        // Clamp final: nunca exceder el contenedor
        const rawHeight = (durationMinutes / 60) * hourPx
        const heightPx = Math.max(8, Math.min(rawHeight, gridTotalPx - topPx))
        const label = abs.reason ? `Ausencia: ${abs.reason}` : 'Ausencia'
        return (
          <div
            key={abs.id}
            aria-label={label}
            title={label}
            onClick={(e) => e.stopPropagation()}
            className="absolute left-0 right-0 z-10 cursor-not-allowed flex items-start justify-center pt-1"
            style={{
              top: `${Math.max(0, topPx)}px`,
              height: `${heightPx}px`,
              backgroundColor: 'rgba(244, 63, 94, 0.15)',
              backgroundImage:
                'repeating-linear-gradient(45deg, rgba(244,63,94,0.28) 0 6px, transparent 6px 14px)',
            }}
          >
            {heightPx >= 24 && (
              <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded-md bg-rose-600/90 text-white text-[10px] font-bold uppercase tracking-wider shadow">
                <Ban size={10} />
                <span className="truncate max-w-[120px]">{label}</span>
              </span>
            )}
          </div>
        )
      })}
    </>
  )
}

/** Capa con trama diagonal e icono Ban que cubre la celda cuando hay un bloqueo aplicable */
export function BlockOverlay({ blocks }) {
  if (!blocks || blocks.length === 0) return null
  const label = labelForBlock(blocks[0])
  return (
    <div
      onClick={(e) => e.stopPropagation()}
      className="absolute inset-0 z-10 flex items-start justify-center pt-2 cursor-not-allowed"
      style={{
        backgroundColor: 'rgba(244, 63, 94, 0.10)',
        backgroundImage:
          'repeating-linear-gradient(45deg, rgba(244,63,94,0.18) 0 6px, transparent 6px 14px)',
      }}
      title={label}
    >
      <span
        className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded-md bg-rose-600/90 text-white text-[10px] font-bold uppercase tracking-wider shadow"
      >
        <Ban size={10} />
        <span className="truncate max-w-[120px]">{label}</span>
      </span>
    </div>
  )
}

/** Linea horizontal que marca la hora actual sobre la rejilla */
export function NowLine({ now, dayStart, dayEnd, hourPx }) {
  const minutes = now.getHours() * 60 + now.getMinutes() - dayStart * 60
  const total = (dayEnd - dayStart) * 60
  if (minutes < 0 || minutes > total) return null
  const top = (minutes / 60) * hourPx
  return (
    <div className="absolute left-0 right-0 z-20 pointer-events-none" style={{ top }}>
      <div className="absolute -left-1 -top-1.5 w-3 h-3 rounded-full bg-rose-500 shadow" />
      <div className="h-0.5 bg-rose-500" />
    </div>
  )
}

// Umbrales de altura para mostrar hora+nombre, solo nombre o nada
const POS_EVENT_TEXT_HEIGHT      = 28
const POS_EVENT_TWO_LINES_HEIGHT = 44

/** Capsula de cita posicionada en la rejilla Dia/Semana con arrastre y tooltip */
export function PositionedEvent({ appt, onClick, onDrop, col, cols, colorBy, dayStart, hourPx }) {
  const topPx = ((minutesOf(appt.startDateTime) - dayStart * 60) / 60) * hourPx
  const heightPx = Math.max(22, (apptDuration(appt) / 60) * hourPx)
  const widthPct = 100 / cols
  const s = styleFor(appt, colorBy)
  const isInProgress = appt.statusName === 'IN_PROGRESS'
  const isTerminal = appt.statusName === 'CANCELLED' || appt.statusName === 'NO_SHOW'
  const startHHMM = apptHHMM(appt.startDateTime)
  const label = `${appt.clientName} · ${startHHMM}–${apptHHMM(appt.endDateTime)} · ${appt.userFullName}${appt.boothName ? ' · ' + appt.boothName : ''}`

  const showText     = heightPx >= POS_EVENT_TEXT_HEIGHT
  const showTwoLines = heightPx >= POS_EVENT_TWO_LINES_HEIGHT

  const hover = useApptHover(appt, appt.employeeColor)

  // arrastre activo solo si el padre da onDrop y la cita no es terminal
  const isDraggable = Boolean(onDrop) && !isTerminal
  const drag = useDragAppointment({
    appt,
    isDraggable,
    onClick: (a) => onClick?.(a),
    onDrop,
  })

  return (
    <>
      <button
        onPointerDown={(e) => {
          // preventDefault evita el click sintetico post-pointerup; el funcion lo re-emite si no hubo arrastre
          e.preventDefault()
          e.stopPropagation()
          drag.onPointerDown(e)
        }}
        onMouseEnter={hover.onMouseEnter}
        onMouseLeave={hover.onMouseLeave}
        aria-label={label}
        style={{
          top: `${topPx}px`, height: `${heightPx}px`,
          left: `${col * widthPct}%`, width: `${widthPct}%`,
        }}
        className={`absolute z-20 ${s.dot} ring-1 ring-black/10 overflow-hidden transition hover:brightness-110 text-left ${isInProgress ? 'animate-pulse' : ''} ${isTerminal ? 'opacity-60' : ''} ${isDraggable ? 'cursor-grab active:cursor-grabbing' : ''}`}
      >
        {showText && (
          <div className="flex flex-col h-full px-1.5 py-0.5 text-white leading-tight">
            {showTwoLines && (
              <span className="text-[10px] font-bold tabular-nums opacity-90">
                {startHHMM}
              </span>
            )}
            <span className={`text-[11px] font-semibold truncate ${isTerminal ? 'line-through' : ''}`}>
              {appt.clientName}
            </span>
          </div>
        )}
      </button>
      {drag.ghostEl}
      {hover.tooltip}
    </>
  )
}

/** Chip plano de cita en variante 'grid' (Mes, solo hora) o 'list' (sidebar, nombre + hora) */
export function EventChip({ appt, onClick, colorBy, variant = 'list' }) {
  const s = styleFor(appt, colorBy)
  const isInProgress = appt.statusName === 'IN_PROGRESS'
  const isTerminal = appt.statusName === 'CANCELLED' || appt.statusName === 'NO_SHOW'
  const label = `${appt.clientName} · ${apptHHMM(appt.startDateTime)}–${apptHHMM(appt.endDateTime)} · ${appt.userFullName}${appt.boothName ? ' · ' + appt.boothName : ''}`
  const hover = useApptHover(appt, appt.employeeColor)

  if (variant === 'grid') {
    return (
      <>
        <button
          onClick={(e) => { e.stopPropagation(); onClick(appt) }}
          onMouseEnter={hover.onMouseEnter}
          onMouseLeave={hover.onMouseLeave}
          aria-label={label}
          className={`w-full ${s.bg} ${s.hover} ${s.text} px-1.5 py-1 text-[10px] font-bold tabular-nums text-left leading-tight transition ${isInProgress ? 'ring-1 ring-cyan-400 animate-pulse' : ''} ${isTerminal ? 'line-through opacity-60' : ''}`}
        >
          {apptHHMM(appt.startDateTime)}
        </button>
        {hover.tooltip}
      </>
    )
  }

  return (
    <>
      <button
        onClick={(e) => { e.stopPropagation(); onClick(appt) }}
        onMouseEnter={hover.onMouseEnter}
        onMouseLeave={hover.onMouseLeave}
        aria-label={label}
        className={`w-full flex items-center gap-1.5 ${s.bg} ${s.hover} ${s.text} text-[11px] font-medium rounded-md px-1.5 py-1 text-left transition ${isInProgress ? 'ring-1 ring-cyan-300 animate-pulse' : ''} ${isTerminal ? 'line-through opacity-60' : ''}`}
      >
        <span className={`w-0.5 self-stretch ${s.bar} rounded-full shrink-0`} />
        <span className="truncate flex-1">{appt.clientName}</span>
        <span className="text-[10px] opacity-70 hidden xl:inline">{apptHHMM(appt.startDateTime)}</span>
      </button>
      {hover.tooltip}
    </>
  )
}

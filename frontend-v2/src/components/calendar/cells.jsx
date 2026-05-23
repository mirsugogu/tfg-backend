/*
 * Celdas y eventos del calendario: rejilla horaria, slots, línea
 * "ahora", cápsula posicionada absoluta y chip plano (vista Mes /
 * sidebar). Estilo tipo hoja de cálculo: bordes slate-300, zebra sutil
 * cada hora, slots cerrados con fondo gris pleno.
 */
import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { Ban, Clock, User, MapPin, Scissors } from 'lucide-react'
import { pad2, minutesOf, apptDuration, apptHHMM, styleFor, labelForBlock } from './utils'
import { dotClassFromColorAndId } from '@/lib/employeeColor'
import { totalBooked } from '@/lib/format'
import { useDragAppointment } from './drag'
import { useCatalog } from '@/context/CatalogContext'

/*
 * useApptHover — hook que gestiona el hover de un evento del calendario.
 *
 * Devuelve los handlers para enganchar a un boton y un elemento JSX listo
 * para inyectar (el portal del tooltip si esta visible, null si no).
 *
 * - delay de 250 ms al entrar: evita parpadeos cuando el cursor recorre
 *   varias citas al scrollear.
 * - inmediato al salir: no se queda colgado.
 * - guarda el getBoundingClientRect del propio boton para anclar la
 *   tarjeta sin necesidad de seguir el cursor.
 */
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

/*
 * ApptHoverCard — tarjeta flotante con el resumen de una cita.
 *
 * Se renderiza con createPortal sobre <body> para no quedar recortada por
 * el overflow-auto del calendario. Se ancla a la derecha del boton si cabe
 * en la viewport; si no, a la izquierda. Vertical: se intenta alinear al
 * top del boton, sin pasarse de los limites de la ventana.
 *
 * Contenido: cliente, hora, empleado con su punto de color, cabina si
 * aplica, lista de servicios, estado y total. pointer-events-none para no
 * interferir con clicks fuera del propio tooltip.
 */
function ApptHoverCard({ appt, anchorRect, employeeColor }) {
  // useCatalog traduce el statusName (CONFIRMED, IN_PROGRESS...) a su
  // etiqueta en espanol; si el catalogo todavia no esta cargado, hace
  // fallback al nombre crudo y no rompe el render del hover.
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

export function HourColumn({ withHeader = true, dayStart, dayEnd, hourPx }) {
  const hours = []
  for (let h = dayStart; h < dayEnd; h++) hours.push(h)
  return (
    <div className="w-14 shrink-0 border-r-2 border-slate-300 bg-white">
      {withHeader && <div className="h-10 border-b-2 border-slate-300" />}
      {hours.map((h) => (
        <div key={h} style={{ height: hourPx }} className="relative border-b border-slate-200">
          <span className="absolute -top-2 right-1.5 text-[10px] font-semibold text-slate-500 bg-white px-1">
            {pad2(h)}:00
          </span>
        </div>
      ))}
    </div>
  )
}

export function HourSlots({
  dayKey, onSlotClick, dayStart, dayEnd, hourPx, closedRanges,
  isBlocked = false, blockedReason = null,
}) {
  const hours = []
  for (let h = dayStart; h < dayEnd; h++) hours.push(h)
  // Una hora se considera cerrada si NO cae dentro de ningun tramo
  // abierto. La invariante funciona con 0, 1 o N tramos (turno partido):
  // - 0 tramos -> some() false sobre array vacio -> todo cerrado.
  // - 1 tramo  -> equivalente a la version anterior.
  // - N tramos -> abierta si esta dentro de cualquiera de ellos.
  const isClosed = (h) => !closedRanges.some(([s, e]) => h >= s && h < e)
  return (
    <>
      {hours.map((h, idx) => {
        const closed = isClosed(h)
        // Zebra sutil para escanear filas; bordes Excel-style (slate-300).
        const zebra = idx % 2 === 0 ? 'bg-slate-50/40' : 'bg-white'
        // Una franja "fuera de horario" sigue siendo no clickable. Una franja
        // dentro de un bloqueo (festivo / vacaciones / mantenimiento) tampoco
        // debe permitir crear cita: lo mismo que el backend devuelve con
        // validateNoScheduleBlock (409), pero anticipado en la UI.
        const inactive = closed || isBlocked
        const title = closed ? 'Fuera de horario'
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

/*
 * AbsenceOverlay — franja roja que cubre las horas en las que un empleado
 * tiene una ausencia registrada (vacaciones, baja, cita medica). Se pinta
 * solo en las sub-columnas del empleado afectado en las vistas resource
 * agrupadas por empleado.
 *
 * Una ausencia puede durar varios días; este componente recibe `dayDate`
 * (la fecha de la sub-columna que se está pintando) y hace clamp del rango
 * absence.start / absence.end a las horas visibles de ese día. Asi cubre
 * solo la franja que corresponde.
 *
 * pointer-events-none: las citas (PositionedEvent z-20) y los slots
 * permanecen clicables; el overlay es solo informativo. El backend ya
 * rechaza crear citas dentro de una ausencia (validateNoEmployeeAbsence,
 * 409).
 */
export function AbsenceOverlay({ absences, dayDate, dayStart, dayEnd, hourPx }) {
  if (!absences || absences.length === 0) return null
  // Clamp al RANGO HORARIO VISIBLE de la rejilla (dayStart..dayEnd), no al
  // dia completo 00-24. Si la ausencia empieza a las 17:53 pero la rejilla
  // termina en 21:00, la franja debe cortarse en 21:00 — no salirse al
  // hueco que hay por debajo del calendario.
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
        // Clamp final del alto por si acaso: nunca exceder el contenedor.
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

/*
 * BlockOverlay — capa visual que cubre la rejilla horaria de un día (o una
 * sub-columna en las vistas resource) cuando hay un schedule_block aplicable.
 *
 * Diseño: trama diagonal + icono Ban + reason del bloqueo, sobre fondo rose
 * con baja opacidad para que el patrón de la rejilla siga visible.
 *
 * Comportamiento: captura los clicks (cursor not-allowed) y stopea su
 * propagacion para que no abran el wizard ni naveguen al día. HourSlots
 * tambien valida `isBlocked` internamente; el overlay es la red de defensa
 * superior y el backend (validateNoScheduleBlock) sigue siendo la última.
 *
 * blocks: array de schedule_blocks aplicables a esta celda; si esta vacio,
 *         no se renderiza nada. El primero manda la etiqueta visible.
 */
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

/*
 * PositionedEvent — cápsula de cita en la rejilla Día/Semana. Bloque de
 * color sólido (s.dot, tono -500) con texto adaptativo según altura:
 *   - height >= 44 px (≈30 min en densidad cómoda): 2 líneas, hora + nombre.
 *   - 28 px <= height < 44 px (≈15 min cómoda o 30 min compacta): nombre solo,
 *     truncado; la hora se infiere de la posición en la rejilla.
 *   - height < 28 px (citas muy cortas o vista densa al máximo): sin texto,
 *     el color y la posición siguen comunicando la información esencial.
 *
 * Tooltip (title) y aria-label conservan SIEMPRE la información completa
 * para el hover y los lectores de pantalla, independientemente del modo
 * visual elegido.
 */
const POS_EVENT_TEXT_HEIGHT      = 28
const POS_EVENT_TWO_LINES_HEIGHT = 44

export function PositionedEvent({ appt, onClick, onDrop, col, cols, colorBy, dayStart, hourPx }) {
  const topPx = ((minutesOf(appt.startDateTime) - dayStart * 60) / 60) * hourPx
  // Sin "- 4": el bloque ocupa el alto completo de su franja horaria.
  const heightPx = Math.max(22, (apptDuration(appt) / 60) * hourPx)
  const widthPct = 100 / cols
  const s = styleFor(appt, colorBy)
  const isInProgress = appt.statusName === 'IN_PROGRESS'
  const isTerminal = appt.statusName === 'CANCELLED' || appt.statusName === 'NO_SHOW'
  const startHHMM = apptHHMM(appt.startDateTime)
  const label = `${appt.clientName} · ${startHHMM}–${apptHHMM(appt.endDateTime)} · ${appt.userFullName}${appt.boothName ? ' · ' + appt.boothName : ''}`

  const showText     = heightPx >= POS_EVENT_TEXT_HEIGHT
  const showTwoLines = heightPx >= POS_EVENT_TWO_LINES_HEIGHT

  // Tooltip rico (G): aprovecha el employeeColor que Calendario enriquece
  // en `filtered`. El title nativo se mantiene como fallback de a11y.
  const hover = useApptHover(appt, appt.employeeColor)

  // Drag-and-drop (Fase D): solo activo si el padre proporciona onDrop y la
  // cita no esta en estado terminal. Las citas COMPLETED/CANCELLED/NO_SHOW
  // no se arrastran porque el backend ya las rechazaria con 400 al editar.
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
          // preventDefault evita el click sintetico posterior al pointerup;
          // el hook re-emite el click manualmente si no hubo drag, asi se
          // controla con precision si abrir el detalle o disparar el drop.
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

/*
 * EventChip — chip plano de cita (no posicionado). Dos variantes:
 *  - 'grid' (vista Mes): bloque de color + solo la hora, sin nombre. El
 *    color es la señal a primera vista; el nombre va en tooltip y al hacer
 *    clic. La vista Mes no tiene eje de tiempo, por eso conserva la hora.
 *  - 'list' (barra lateral "Agenda"): es una lista PARA LEER, así que
 *    mantiene la barra de color + el nombre del cliente + la hora.
 */
export function EventChip({ appt, onClick, colorBy, variant = 'list' }) {
  const s = styleFor(appt, colorBy)
  const isInProgress = appt.statusName === 'IN_PROGRESS'
  const isTerminal = appt.statusName === 'CANCELLED' || appt.statusName === 'NO_SHOW'
  const label = `${appt.clientName} · ${apptHHMM(appt.startDateTime)}–${apptHHMM(appt.endDateTime)} · ${appt.userFullName}${appt.boothName ? ' · ' + appt.boothName : ''}`
  // Tooltip rico tambien en los chips (Mes y sidebar): la informacion del
  // bloque pequeno es limitada, asi que el hover aporta especialmente.
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

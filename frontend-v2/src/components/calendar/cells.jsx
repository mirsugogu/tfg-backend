/*
 * Celdas y eventos del calendario: rejilla horaria, slots, línea
 * "ahora", cápsula posicionada absoluta y chip plano (vista Mes /
 * sidebar). Estilo tipo hoja de cálculo: bordes slate-300, zebra sutil
 * cada hora, slots cerrados con fondo gris pleno.
 */
import { pad2, minutesOf, apptDuration, apptHHMM, styleFor } from './utils'

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

export function HourSlots({ dayKey, onSlotClick, dayStart, dayEnd, hourPx, closedRanges }) {
  const hours = []
  for (let h = dayStart; h < dayEnd; h++) hours.push(h)
  const isClosed = (h) => closedRanges.some(([s, e]) => h < s || h >= e)
  return (
    <>
      {hours.map((h, idx) => {
        const closed = isClosed(h)
        // Zebra sutil para escanear filas; bordes Excel-style (slate-300).
        const zebra = idx % 2 === 0 ? 'bg-slate-50/40' : 'bg-white'
        return (
          <div
            key={h}
            onClick={() => !closed && onSlotClick(dayKey, h)}
            style={{ height: hourPx }}
            className={`border-b border-slate-300 relative ${closed ? 'bg-slate-200/60 cursor-not-allowed' : `${zebra} hover:bg-blue-50/60 cursor-pointer`}`}
            title={closed ? 'Fuera de horario' : 'Crear cita a esta hora'}
          >
            <div className="absolute left-0 right-0 border-t border-dashed border-slate-300" style={{ top: hourPx / 2 }} />
          </div>
        )
      })}
    </>
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

export function PositionedEvent({ appt, onClick, col, cols, colorBy, dayStart, hourPx }) {
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

  return (
    <button
      onClick={(e) => { e.stopPropagation(); onClick(appt) }}
      title={label}
      aria-label={label}
      style={{
        top: `${topPx}px`, height: `${heightPx}px`,
        left: `${col * widthPct}%`, width: `${widthPct}%`,
      }}
      className={`absolute ${s.dot} ring-1 ring-black/10 overflow-hidden transition hover:brightness-110 text-left ${isInProgress ? 'animate-pulse' : ''} ${isTerminal ? 'opacity-60' : ''}`}
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

  if (variant === 'grid') {
    return (
      <button
        onClick={(e) => { e.stopPropagation(); onClick(appt) }}
        title={label}
        aria-label={label}
        className={`w-full ${s.bg} ${s.hover} ${s.text} px-1.5 py-1 text-[10px] font-bold tabular-nums text-left leading-tight transition ${isInProgress ? 'ring-1 ring-cyan-400 animate-pulse' : ''} ${isTerminal ? 'line-through opacity-60' : ''}`}
      >
        {apptHHMM(appt.startDateTime)}
      </button>
    )
  }

  return (
    <button
      onClick={(e) => { e.stopPropagation(); onClick(appt) }}
      title={label}
      className={`w-full flex items-center gap-1.5 ${s.bg} ${s.hover} ${s.text} text-[11px] font-medium rounded-md px-1.5 py-1 text-left transition ${isInProgress ? 'ring-1 ring-cyan-300 animate-pulse' : ''} ${isTerminal ? 'line-through opacity-60' : ''}`}
    >
      <span className={`w-0.5 self-stretch ${s.bar} rounded-full shrink-0`} />
      <span className="truncate flex-1">{appt.clientName}</span>
      <span className="text-[10px] opacity-70 hidden xl:inline">{apptHHMM(appt.startDateTime)}</span>
    </button>
  )
}

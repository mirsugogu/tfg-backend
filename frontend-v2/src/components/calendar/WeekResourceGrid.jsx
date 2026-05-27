import { useMemo } from 'react'
import {
  PALETTES, GRAY_PALETTE, DAYS_ES_SHORT, paletteByName,
  keyOf, isSameDay, layoutEvents, openRangesFor, workingRangesFor, startOfWeek,
  blocksForCell, labelForBlock,
} from './utils'
import { HourColumn, HourSlots, NowLine, PositionedEvent, BlockOverlay, AbsenceOverlay } from './cells'

/** Vista Semana con sub-columnas de recurso (empleado o cabina) dentro de cada día. */
export function WeekResourceGrid({
  cursor, today, eventsByDay, colorBy, onSelectEvent, onSlotClick,
  dayStart, dayEnd, hourPx, businessHours, now,
  resources, // [{ id, name, short, accent }]
  resourceFor,
  unassignedShort = 'Sin',
  blocks = [],         // schedule_blocks aplicables al rango visible
  resourceType = null, // 'employee' | 'booth' | null
  absences = [],       // ausencias del rango; solo se pintan en columnas de empleado
  schedulesByMembership = null, // Map<membershipId, EmployeeSchedule[]>; opcional. Igual que en ResourceDayGrid: pinta como gris los huecos del horario semanal del empleado.
  onDropAppointment,   // drag-and-drop: callback al soltar una cita en otra sub-columna
  appointmentInterval = 30, // snap del drag al intervalo del negocio
}) {
  const ws = startOfWeek(cursor)
  const days = Array.from({ length: 7 }, (_, i) => { const d = new Date(ws); d.setDate(ws.getDate() + i); return d })

  // ¿Hay alguna cita sin recurso esta semana? Si no, se omite la columna "Sin asignar".
  const hasUnassigned = useMemo(() => {
    for (const d of days) {
      const dayEvents = eventsByDay.get(keyOf(d)) || []
      for (const a of dayEvents) {
        if (resourceFor(a) == null) return true
      }
    }
    return false
  }, [days, eventsByDay, resourceFor])

  // "Sin asignar" aparece solo si hay citas huérfanas o si no hay recursos configurados.
  const cols = [
    ...resources,
    ...((hasUnassigned || resources.length === 0)
      ? [{ id: '__none__', name: 'Sin asignar', short: unassignedShort, accent: -1 }]
      : []),
  ]
  const nCols = cols.length

  // Ancho mínimo por sub-columna; el contenedor hace scroll horizontal.
  const SUB_COL_MIN = 56
  const minTotalWidth = 56 /* hour col */ + 7 * nCols * SUB_COL_MIN

  return (
    <div className="overflow-auto" style={{ maxHeight: '72vh' }}>
      <div className="flex" style={{ minWidth: minTotalWidth }}>
        {/* HourColumn con cabecera doble (40 + 28) para alinear con cuerpo */}
        <div className="w-14 shrink-0">
          <div className="h-[68px] border-b-2 border-slate-300 border-r-2 bg-white sticky top-0 z-30" />
          <div className="-mt-px">
            <HourColumn
              withHeader={false}
              dayStart={dayStart}
              dayEnd={dayEnd}
              hourPx={hourPx}
            />
          </div>
        </div>
        <div
          className="flex-1 grid"
          style={{ gridTemplateColumns: `repeat(7, minmax(${nCols * SUB_COL_MIN}px, 1fr))` }}
        >
          {days.map((d, i) => {
            const dayKey = keyOf(d)
            const isToday = isSameDay(d, today)
            const openRanges = openRangesFor(businessHours, d)
            const dayEvents = eventsByDay.get(dayKey) || []

            // Bucketize por recurso
            const buckets = new Map()
            cols.forEach((c) => buckets.set(c.id, []))
            dayEvents.forEach((a) => {
              const rid = resourceFor(a)
              if (rid != null && buckets.has(rid)) buckets.get(rid).push(a)
              else buckets.get('__none__').push(a)
            })

            return (
              <div key={i} className="relative border-r-2 border-slate-400 last:border-r-0">
                {/* Cabecera fila 1 sticky; z-30 > eventos (z-20). */}
                <div className={`h-10 border-b-2 border-slate-300 flex items-center justify-center gap-2 sticky top-0 z-30 ${isToday ? 'bg-blue-100' : 'bg-white'}`}>
                  <span className={`text-[10px] uppercase tracking-wider font-semibold ${i >= 5 ? 'text-blue-600' : 'text-slate-500'}`}>{DAYS_ES_SHORT[i]}</span>
                  <span className={`inline-flex items-center justify-center min-w-[22px] h-6 px-1.5 rounded-full text-xs font-bold ${isToday ? 'bg-gradient-to-br from-cyan-500 to-blue-500 text-white' : 'text-[#1e3a5f]'}`}>{d.getDate()}</span>
                </div>

                {/* Cabecera fila 2 anclada en top:40, debajo de la fila 1. */}
                <div
                  className={`h-7 border-b-2 border-slate-300 grid sticky z-30 ${isToday ? 'bg-blue-50' : 'bg-slate-100'}`}
                  style={{ top: 40, gridTemplateColumns: `repeat(${nCols}, minmax(0, 1fr))` }}
                >
                  {cols.map((c) => {
                    const palette = paletteByName(c.color) || (c.accent >= 0 ? PALETTES[c.accent % PALETTES.length] : GRAY_PALETTE)
                    return (
                      <div
                        key={c.id}
                        title={c.name}
                        className="flex items-center justify-center gap-1 border-r border-slate-300 last:border-r-0"
                      >
                        <span className={`w-1.5 h-1.5 rounded-full shrink-0 ${palette.dot}`} />
                        <span className="text-[10px] font-bold text-[#1e3a5f] uppercase tracking-wider truncate">
                          {c.short}
                        </span>
                      </div>
                    )
                  })}
                </div>

                {/* Cuerpo: una sub-columna por recurso */}
                <div
                  className="relative grid"
                  style={{ gridTemplateColumns: `repeat(${nCols}, minmax(0, 1fr))` }}
                >
                  {cols.map((c) => {
                    const laidOut = layoutEvents(buckets.get(c.id) || [])
                    // "Sin asignar": solo bloqueos globales. Resto: globales + dirigidos al recurso.
                    const cellBlocks = c.id === '__none__'
                      ? blocksForCell(blocks, d)
                      : blocksForCell(blocks, d, resourceType, c.id)
                    const cellIsBlocked = cellBlocks.length > 0
                    const cellBlockLabel = cellIsBlocked ? labelForBlock(cellBlocks[0]) : null
                    // Ausencias: solo si la sub-columna es de empleado.
                    const cellAbsences = (resourceType === 'employee' && c.id !== '__none__')
                      ? absences.filter((ab) => ab.membershipId === c.id)
                      : []
                    // Rangos laborables solo en sub-columnas de empleado con horario cargado.
                    const cellWorkingRanges = (resourceType === 'employee' && c.id !== '__none__' && schedulesByMembership?.has(c.id))
                      ? workingRangesFor(schedulesByMembership.get(c.id), d)
                      : null
                    return (
                      <div
                        key={c.id}
                        data-cal-cell
                        data-day={dayKey}
                        data-resource-type={resourceType || 'none'}
                        data-resource-id={c.id}
                        data-hour-px={hourPx}
                        data-day-start={dayStart}
                        data-interval={appointmentInterval}
                        className="relative border-r border-slate-300 last:border-r-0"
                      >
                        <HourSlots
                          dayKey={dayKey}
                          onSlotClick={onSlotClick}
                          dayStart={dayStart}
                          dayEnd={dayEnd}
                          hourPx={hourPx}
                          closedRanges={openRanges}
                          workingRanges={cellWorkingRanges}
                          isBlocked={cellIsBlocked}
                          blockedReason={cellBlockLabel}
                        />
                        <AbsenceOverlay
                          absences={cellAbsences}
                          dayDate={d}
                          dayStart={dayStart}
                          dayEnd={dayEnd}
                          hourPx={hourPx}
                        />
                        {laidOut.map(({ a, col, cols: c2 }) => (
                          <PositionedEvent
                            key={a.id}
                            appt={a}
                            onClick={onSelectEvent}
                            onDrop={onDropAppointment}
                            col={col}
                            cols={c2}
                            colorBy={colorBy}
                            dayStart={dayStart}
                            hourPx={hourPx}
                          />
                        ))}
                        <BlockOverlay blocks={cellBlocks} />
                      </div>
                    )
                  })}
                  {/* Línea "ahora" abarcando todas las sub-columnas del día */}
                  {isToday && (
                    <div className="absolute inset-0 pointer-events-none">
                      <NowLine now={now} dayStart={dayStart} dayEnd={dayEnd} hourPx={hourPx} />
                    </div>
                  )}
                </div>
              </div>
            )
          })}
        </div>
      </div>
    </div>
  )
}

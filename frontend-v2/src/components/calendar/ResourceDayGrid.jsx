/*
 * ResourceDayGrid — vista Día por columnas de recurso (cabina o
 * empleado). Eje X = recursos, eje Y = tiempo. Las citas se colocan en
 * la columna de su cabina/empleado; si hay solapes dentro de la misma
 * columna, layoutEvents los reparte en sub-columnas.
 */
import { useMemo } from 'react'
import {
  PALETTES, GRAY_PALETTE, paletteByName,
  keyOf, isSameDay, layoutEvents, openRangesFor,
  blocksForCell, labelForBlock,
} from './utils'
import { HourColumn, HourSlots, NowLine, PositionedEvent, EventChip, BlockOverlay, AbsenceOverlay } from './cells'

export function ResourceDayGrid({
  cursor, eventsByDay, colorBy, onSelectEvent, onSlotClick,
  dayStart, dayEnd, hourPx, businessHours, now,
  resources, // [{ id, name, accent }] — accent: índice de paleta
  resourceFor, // (appt) => resource.id  (o null = "Sin asignar")
  unassignedLabel = 'Sin asignar',
  blocks = [],         // schedule_blocks aplicables al rango visible
  resourceType = null, // 'employee' | 'booth' | null
  absences = [],       // ausencias del rango; solo se pintan en columnas de empleado
}) {
  const dayKey = keyOf(cursor)
  const dayEvents = eventsByDay.get(dayKey) || []
  const openRanges = openRangesFor(businessHours, cursor)
  const isToday = isSameDay(cursor, new Date())

  // Bucketizar eventos por recurso. Citas sin recurso → columna "unassigned".
  const buckets = useMemo(() => {
    const map = new Map()
    resources.forEach((r) => map.set(r.id, []))
    const unassigned = []
    dayEvents.forEach((a) => {
      const rid = resourceFor(a)
      if (rid != null && map.has(rid)) map.get(rid).push(a)
      else unassigned.push(a)
    })
    return { map, unassigned }
  }, [dayEvents, resources, resourceFor])

  // Si hay citas sin recurso, añadimos una columna "Sin asignar" al principio.
  const columns = [
    ...(buckets.unassigned.length ? [{ id: '__none__', name: unassignedLabel, accent: -1, events: buckets.unassigned }] : []),
    ...resources.map((r) => ({ ...r, events: buckets.map.get(r.id) || [] })),
  ]

  return (
    <div className="grid grid-cols-1 xl:grid-cols-[1fr_300px] gap-5 p-5">
      <div className="border border-slate-100 rounded-2xl overflow-hidden">
        <div className="overflow-auto" style={{ maxHeight: '68vh' }}>
          <div className="flex" style={{ minWidth: 56 + columns.length * 160 }}>
            <HourColumn dayStart={dayStart} dayEnd={dayEnd} hourPx={hourPx} />
            <div className="relative flex-1 grid" style={{ gridTemplateColumns: `repeat(${columns.length}, minmax(140px, 1fr))` }}>
              {columns.map((col) => {
                const palette = paletteByName(col.color)
                  || (col.accent >= 0 ? PALETTES[col.accent % PALETTES.length] : GRAY_PALETTE)
                const laidOut = layoutEvents(col.events)
                return (
                  <div key={col.id} className="relative border-r-2 border-slate-300 last:border-r-0">
                    {/* Cabecera sticky con el recurso */}
                    <div className="h-10 border-b-2 border-slate-300 px-2.5 flex items-center justify-between gap-2 sticky top-0 z-10 bg-slate-50">
                      <div className="flex items-center gap-2 min-w-0">
                        <span className={`w-2 h-2 rounded-full shrink-0 ${palette.dot}`} />
                        <span className="text-[11px] font-bold uppercase tracking-wider text-[#1e3a5f] truncate">
                          {col.name}
                        </span>
                      </div>
                      <span className="text-[10px] font-bold tabular-nums text-slate-400 shrink-0">
                        {col.events.length}
                      </span>
                    </div>
                    <div className="relative">
                      {(() => {
                        const cellBlocks = col.id === '__none__'
                          ? blocksForCell(blocks, cursor)
                          : blocksForCell(blocks, cursor, resourceType, col.id)
                        const cellIsBlocked = cellBlocks.length > 0
                        const cellBlockLabel = cellIsBlocked ? labelForBlock(cellBlocks[0]) : null
                        const cellAbsences = (resourceType === 'employee' && col.id !== '__none__')
                          ? absences.filter((ab) => ab.membershipId === col.id)
                          : []
                        return (
                          <>
                            <HourSlots
                              dayKey={dayKey}
                              onSlotClick={onSlotClick}
                              dayStart={dayStart}
                              dayEnd={dayEnd}
                              hourPx={hourPx}
                              closedRanges={openRanges}
                              isBlocked={cellIsBlocked}
                              blockedReason={cellBlockLabel}
                            />
                            <AbsenceOverlay
                              absences={cellAbsences}
                              dayDate={cursor}
                              dayStart={dayStart}
                              dayEnd={dayEnd}
                              hourPx={hourPx}
                            />
                            {laidOut.map(({ a, col: c, cols }) => (
                              <PositionedEvent
                                key={a.id}
                                appt={a}
                                onClick={onSelectEvent}
                                col={c}
                                cols={cols}
                                colorBy={colorBy}
                                dayStart={dayStart}
                                hourPx={hourPx}
                              />
                            ))}
                            <BlockOverlay blocks={cellBlocks} />
                          </>
                        )
                      })()}
                    </div>
                  </div>
                )
              })}
              {/* Línea "ahora": una sola, sobre todas las columnas (top:40 = alto de la cabecera). */}
              {isToday && (
                <div className="absolute left-0 right-0 pointer-events-none" style={{ top: 40 }}>
                  <NowLine now={now} dayStart={dayStart} dayEnd={dayEnd} hourPx={hourPx} />
                </div>
              )}
            </div>
          </div>
        </div>
      </div>

      <ResourceDayAside
        columns={columns}
        dayEvents={dayEvents}
        colorBy={colorBy}
        onSelectEvent={onSelectEvent}
      />
    </div>
  )
}

function ResourceDayAside({ columns, dayEvents, colorBy, onSelectEvent }) {
  const sorted = [...dayEvents].sort((a, b) => a.startDateTime.localeCompare(b.startDateTime))
  return (
    <aside className="space-y-4 no-print">
      <div className="bg-white border border-slate-100 rounded-2xl p-5">
        <p className="text-xs uppercase tracking-[0.14em] text-slate-400 font-semibold mb-1">Resumen del día</p>
        <p className="text-3xl font-bold text-[#1e3a5f]">{dayEvents.length}</p>
        <p className="text-sm text-slate-500">
          cita{dayEvents.length === 1 ? '' : 's'} programada{dayEvents.length === 1 ? '' : 's'}
        </p>
        <div className="mt-4 space-y-2">
          {dayEvents.length === 0 ? (
            <p className="text-sm text-slate-400">Sin citas este día.</p>
          ) : columns.map((col) => {
            const palette = paletteByName(col.color) || (col.accent >= 0 ? PALETTES[col.accent % PALETTES.length] : GRAY_PALETTE)
            return (
              <div key={col.id} className="flex items-center justify-between text-sm">
                <span className="flex items-center gap-2 min-w-0">
                  <span className={`w-2 h-2 rounded-full shrink-0 ${palette.dot}`} />
                  <span className="text-slate-600 truncate">{col.name}</span>
                </span>
                <span className={`font-semibold tabular-nums ${palette.text}`}>{col.events.length}</span>
              </div>
            )
          })}
        </div>
      </div>

      <div className="bg-white border border-slate-100 rounded-2xl p-5">
        <p className="text-xs uppercase tracking-[0.14em] text-slate-400 font-semibold mb-3">Agenda</p>
        {sorted.length === 0 ? (
          <p className="text-sm text-slate-400">No hay citas este día.</p>
        ) : (
          <div className="space-y-2">
            {sorted.map((a) => <EventChip key={a.id} appt={a} onClick={onSelectEvent} colorBy={colorBy} />)}
          </div>
        )}
      </div>
    </aside>
  )
}

/*
 * WeekResourceGrid — vista Semana con sub-columnas por recurso (cabina
 * o empleado) DENTRO de cada día. 7 días × N recursos. Pensado para
 * cabinas (3-4); con muchos empleados puede crecer mucho y aparece
 * scroll horizontal.
 *
 * Cabecera en dos filas:
 *   Fila 1 — día de la semana + número
 *   Fila 2 — sub-columnas de recurso (C1, C2, ..., Sin)
 */
import {
  PALETTES, GRAY_PALETTE, DAYS_ES_SHORT,
  keyOf, isSameDay, layoutEvents, openRangesFor, startOfWeek,
} from './utils'
import { HourColumn, HourSlots, NowLine, PositionedEvent } from './cells'

export function WeekResourceGrid({
  cursor, today, eventsByDay, colorBy, onSelectEvent, onSlotClick,
  dayStart, dayEnd, hourPx, businessHours, now,
  resources, // [{ id, name, short, accent }]
  resourceFor,
  unassignedShort = 'Sin',
}) {
  const ws = startOfWeek(cursor)
  const days = Array.from({ length: 7 }, (_, i) => { const d = new Date(ws); d.setDate(ws.getDate() + i); return d })

  // Sub-columnas finales. Añadimos siempre "Sin asignar" para que todos
  // los días sean igual de anchos — más legible al escanear.
  const cols = [
    ...resources,
    { id: '__none__', name: 'Sin asignar', short: unassignedShort, accent: -1 },
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
          <div className="h-[68px] border-b-2 border-slate-300 border-r-2 bg-white sticky top-0 z-20" />
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
                {/* Cabecera de día (fila 1) */}
                <div className={`h-10 border-b-2 border-slate-300 flex items-center justify-center gap-2 sticky top-0 z-20 ${isToday ? 'bg-blue-100' : 'bg-white'}`}>
                  <span className={`text-[10px] uppercase tracking-wider font-semibold ${i >= 5 ? 'text-blue-600' : 'text-slate-500'}`}>{DAYS_ES_SHORT[i]}</span>
                  <span className={`inline-flex items-center justify-center min-w-[22px] h-6 px-1.5 rounded-full text-xs font-bold ${isToday ? 'bg-gradient-to-br from-cyan-500 to-blue-500 text-white' : 'text-[#1e3a5f]'}`}>{d.getDate()}</span>
                </div>

                {/* Cabecera de sub-columnas (fila 2) */}
                <div
                  className={`h-7 border-b-2 border-slate-300 grid sticky z-10 ${isToday ? 'bg-blue-50' : 'bg-slate-100'}`}
                  style={{ top: 40, gridTemplateColumns: `repeat(${nCols}, minmax(0, 1fr))` }}
                >
                  {cols.map((c) => {
                    const palette = c.accent >= 0 ? PALETTES[c.accent % PALETTES.length] : GRAY_PALETTE
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
                    return (
                      <div key={c.id} className="relative border-r border-slate-300 last:border-r-0">
                        <HourSlots
                          dayKey={dayKey}
                          onSlotClick={onSlotClick}
                          dayStart={dayStart}
                          dayEnd={dayEnd}
                          hourPx={hourPx}
                          closedRanges={openRanges}
                        />
                        {laidOut.map(({ a, col, cols: c2 }) => (
                          <PositionedEvent
                            key={a.id}
                            appt={a}
                            onClick={onSelectEvent}
                            col={col}
                            cols={c2}
                            colorBy={colorBy}
                            dayStart={dayStart}
                            hourPx={hourPx}
                          />
                        ))}
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

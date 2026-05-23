/*
 * useDragAppointment — hook que gestiona el drag-and-drop de una cita en
 * el calendario, sin librerias externas.
 *
 * Distingue click de drag con un umbral de 5 px:
 *   - pointerdown + pointerup sin moverse mas de 5 px = click normal
 *     (invoca onClick(appt)).
 *   - pointerdown + pointermove > 5 px = drag. Se pinta un "ghost"
 *     (rectangulo semitransparente) que sigue al cursor en un portal
 *     sobre <body>. Al pointerup, se hace hit-test con
 *     document.elementFromPoint para detectar la celda destino y se
 *     invoca onDrop con la nueva fecha/hora/recurso ya calculados.
 *
 * Snap a intervalo: la hora destino se redondea al multiplo del
 * appointmentInterval del negocio. Por defecto 30 min hasta que las
 * grids puedan exponerlo via data-attribute.
 *
 * Las celdas destino DEBEN llevar data-attributes:
 *   data-cal-cell                  marcador de celda valida.
 *   data-day="YYYY-MM-DD"          fecha de la columna.
 *   data-resource-type="employee" | "booth" | "none"
 *   data-resource-id="<id>" | "__none__"
 *   data-hour-px="<numero>"        pixeles por hora.
 *   data-day-start="<numero>"      hora visible inicial (ej. 8).
 *   data-interval="<numero>"       intervalo de snap en minutos.
 *
 * Las citas en estado terminal (COMPLETED / CANCELLED / NO_SHOW) llegan
 * con isDraggable=false: el pointerdown se ignora y el click sigue
 * funcionando como antes.
 */
import { useCallback, useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'

const DRAG_THRESHOLD_PX = 5

export function useDragAppointment({ appt, isDraggable, onClick, onDrop }) {
  const stateRef = useRef({
    startX: 0,
    startY: 0,
    isDragging: false,
    sourceRect: null,
  })
  const [ghost, setGhost] = useState(null)
  // Limpieza por si el componente se desmonta a mitad de drag.
  useEffect(() => () => setGhost(null), [])

  const handlePointerDown = useCallback((e) => {
    if (e.button !== 0) return // solo boton izquierdo

    const rect = e.currentTarget.getBoundingClientRect()
    stateRef.current = {
      startX: e.clientX,
      startY: e.clientY,
      isDragging: false,
      sourceRect: rect,
    }

    const handleMove = (ev) => {
      const dx = ev.clientX - stateRef.current.startX
      const dy = ev.clientY - stateRef.current.startY
      if (!stateRef.current.isDragging) {
        if (Math.hypot(dx, dy) < DRAG_THRESHOLD_PX) return
        // En cuanto pasa el umbral activamos drag — pero solo si es draggable.
        // Si la cita es terminal abortamos limpio.
        if (!isDraggable) {
          window.removeEventListener('pointermove', handleMove)
          window.removeEventListener('pointerup', handleUp)
          stateRef.current = { ...stateRef.current, isDragging: false }
          return
        }
        stateRef.current.isDragging = true
      }
      const r = stateRef.current.sourceRect
      setGhost({
        x: ev.clientX - r.width / 2,
        y: ev.clientY - 12,
        width: r.width,
        height: r.height,
      })
    }

    const handleUp = (ev) => {
      window.removeEventListener('pointermove', handleMove)
      window.removeEventListener('pointerup', handleUp)
      const wasDragging = stateRef.current.isDragging
      setGhost(null)
      stateRef.current.isDragging = false

      if (!wasDragging) {
        onClick?.(appt)
        return
      }

      // Hit-test del drop.
      const target = document.elementFromPoint(ev.clientX, ev.clientY)
      if (!target) return
      const cell = target.closest('[data-cal-cell]')
      if (!cell) return

      const cellRect = cell.getBoundingClientRect()
      const relativeY = Math.max(0, ev.clientY - cellRect.top)
      const hourPx = parseFloat(cell.dataset.hourPx || '64')
      const dayStartHour = parseFloat(cell.dataset.dayStart || '8')
      const intervalMin = Math.max(1, parseInt(cell.dataset.interval || '30', 10))
      const dayKey = cell.dataset.day
      const resourceType = cell.dataset.resourceType || 'none'
      const rawResourceId = cell.dataset.resourceId
      const newResourceId = (rawResourceId == null || rawResourceId === '__none__')
        ? null
        : Number(rawResourceId)

      // Hora absoluta del drop = hora visible del grid + offset del cursor.
      const totalMinutes = dayStartHour * 60 + (relativeY / hourPx) * 60
      const snapped = Math.max(0, Math.round(totalMinutes / intervalMin) * intervalMin)
      const hh = Math.floor(snapped / 60)
      const mm = snapped % 60
      const newStart = `${dayKey}T${String(hh).padStart(2, '0')}:${String(mm).padStart(2, '0')}:00`

      onDrop?.({
        appt,
        newStartDateTime: newStart,
        newResourceType: resourceType,
        newResourceId,
      })
    }

    window.addEventListener('pointermove', handleMove)
    window.addEventListener('pointerup', handleUp)
  }, [appt, isDraggable, onClick, onDrop])

  const ghostEl = ghost
    ? createPortal(
        <div
          style={{
            position: 'fixed',
            top: ghost.y,
            left: ghost.x,
            width: ghost.width,
            height: ghost.height,
            opacity: 0.7,
            pointerEvents: 'none',
            zIndex: 100,
            backgroundColor: 'rgba(30, 58, 95, 0.85)',
            borderRadius: 6,
            boxShadow: '0 8px 24px rgba(15, 23, 42, 0.35)',
          }}
          aria-hidden
        />,
        document.body,
      )
    : null

  return { onPointerDown: handlePointerDown, ghostEl }
}

/*
 * MiniCalendar y MiniCalendarPopover — selector compacto de fecha para la
 * toolbar del Calendario.
 *
 * MiniCalendar: rejilla 7x6 con los días del mes navegable; el día actual
 * del cursor del padre se marca con fondo solido, hoy con anillo cian. Al
 * pulsar un día, invoca onPick(Date).
 *
 * MiniCalendarPopover: wrapper que añade un botón a la toolbar y abre/cierra
 * el panel con click-outside y Escape. Reutiliza los helpers de utils.js
 * (buildMonthGrid, MONTHS_ES, etc.) para no duplicar logica.
 *
 * Sin dependencias externas. Renderizado relative (no portal): la toolbar
 * tiene sitio de sobra y mantener el popover en flujo evita problemas de
 * z-index con los modales (que sí usan portal).
 */
import { useEffect, useRef, useState } from 'react'
import { ChevronLeft, ChevronRight, CalendarSearch } from 'lucide-react'
import { MONTHS_ES, DAYS_ES_SHORT, buildMonthGrid, isSameDay } from './utils'

function MiniCalendar({ selected, today, onPick }) {
  // El mes visible en el popover puede diferir del seleccionado en el padre:
  // el usuario navega por el popover sin "cometer" la fecha hasta que pulsa
  // un día. Inicia en el mes del `selected`.
  const [viewDate, setViewDate] = useState(
    () => new Date(selected.getFullYear(), selected.getMonth(), 1),
  )
  const cells = buildMonthGrid(viewDate.getFullYear(), viewDate.getMonth())
  const month = viewDate.getMonth()

  const goPrev = () => setViewDate(new Date(viewDate.getFullYear(), viewDate.getMonth() - 1, 1))
  const goNext = () => setViewDate(new Date(viewDate.getFullYear(), viewDate.getMonth() + 1, 1))

  return (
    <div className="w-[260px]">
      <div className="flex items-center justify-between mb-2">
        <button
          onClick={goPrev}
          title="Mes anterior"
          aria-label="Mes anterior"
          className="w-7 h-7 rounded-lg text-slate-500 hover:bg-slate-100 hover:text-[#1e3a5f] transition flex items-center justify-center"
        >
          <ChevronLeft size={14} />
        </button>
        <span className="text-sm font-bold text-[#1e3a5f] capitalize">
          {MONTHS_ES[viewDate.getMonth()]} {viewDate.getFullYear()}
        </span>
        <button
          onClick={goNext}
          title="Mes siguiente"
          aria-label="Mes siguiente"
          className="w-7 h-7 rounded-lg text-slate-500 hover:bg-slate-100 hover:text-[#1e3a5f] transition flex items-center justify-center"
        >
          <ChevronRight size={14} />
        </button>
      </div>

      <div className="grid grid-cols-7 gap-px">
        {DAYS_ES_SHORT.map((d, i) => (
          <div
            key={d}
            className={`text-center py-1 text-[10px] font-bold uppercase tracking-wider ${
              i >= 5 ? 'text-blue-500' : 'text-slate-400'
            }`}
          >
            {d.slice(0, 1)}
          </div>
        ))}
        {cells.map((d, i) => {
          const inMonth = d.getMonth() === month
          const isToday = isSameDay(d, today)
          const isSelected = isSameDay(d, selected)
          return (
            <button
              key={i}
              onClick={() => onPick(new Date(d.getFullYear(), d.getMonth(), d.getDate()))}
              className={`h-7 rounded-lg text-xs font-semibold transition ${
                isSelected
                  ? 'bg-[#1e3a5f] text-white'
                  : isToday
                    ? 'ring-1 ring-cyan-400 text-cyan-700 hover:bg-cyan-50'
                    : inMonth
                      ? 'text-[#1e3a5f] hover:bg-blue-50'
                      : 'text-slate-300 hover:bg-slate-50'
              }`}
            >
              {d.getDate()}
            </button>
          )
        })}
      </div>
    </div>
  )
}

export function MiniCalendarPopover({ cursor, today, onPick }) {
  const [open, setOpen] = useState(false)
  const popRef = useRef(null)
  const btnRef = useRef(null)

  useEffect(() => {
    if (!open) return
    const onClick = (e) => {
      if (popRef.current?.contains(e.target)) return
      if (btnRef.current?.contains(e.target)) return
      setOpen(false)
    }
    const onKey = (e) => { if (e.key === 'Escape') setOpen(false) }
    document.addEventListener('mousedown', onClick)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onClick)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  const handlePick = (date) => {
    onPick(date)
    setOpen(false)
  }

  return (
    <div className="relative">
      <button
        ref={btnRef}
        onClick={() => setOpen((v) => !v)}
        title="Saltar a una fecha"
        aria-label="Saltar a una fecha"
        className={`w-9 h-9 rounded-xl border transition flex items-center justify-center ${
          open
            ? 'border-blue-300 bg-blue-50 text-[#1e3a5f]'
            : 'border-slate-200 text-slate-500 hover:text-[#1e3a5f] hover:border-blue-300 hover:bg-blue-50'
        }`}
      >
        <CalendarSearch size={16} />
      </button>
      {open && (
        <div
          ref={popRef}
          className="absolute left-0 top-[calc(100%+8px)] z-30 bg-white rounded-2xl border border-slate-200 shadow-[0_20px_50px_-10px_rgba(15,23,42,0.18)] p-3"
        >
          <MiniCalendar selected={cursor} today={today} onPick={handlePick} />
        </div>
      )}
    </div>
  )
}

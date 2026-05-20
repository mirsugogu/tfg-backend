const { Icons: CIcons } = window;

const VIEW_MODES = ['Mes', 'Semana', 'Día'];

const STATUS_EVENT_STYLES = {
  Confirmada: { bar: 'bg-blue-500',    bg: 'bg-blue-50',    text: 'text-blue-700',    hover: 'hover:bg-blue-100',    ring: 'ring-blue-200' },
  Pendiente:  { bar: 'bg-orange-500',  bg: 'bg-orange-50',  text: 'text-orange-700',  hover: 'hover:bg-orange-100',  ring: 'ring-orange-200' },
  Completada: { bar: 'bg-emerald-500', bg: 'bg-emerald-50', text: 'text-emerald-700', hover: 'hover:bg-emerald-100', ring: 'ring-emerald-200' },
};

const MONTHS_ES = ['Enero','Febrero','Marzo','Abril','Mayo','Junio','Julio','Agosto','Septiembre','Octubre','Noviembre','Diciembre'];
const DAYS_ES_SHORT = ['Lun','Mar','Mié','Jue','Vie','Sáb','Dom'];
const DAYS_ES_LONG  = ['Lunes','Martes','Miércoles','Jueves','Viernes','Sábado','Domingo'];

const DAY_START = 8;   // 08:00
const DAY_END   = 21;  // 21:00 (exclusive end label)
const HOUR_PX   = 80;  // height of one hour row in px

// ───── helpers ─────
function buildMonthGrid(year, month) {
  const first = new Date(year, month, 1);
  const dayOfWeek = (first.getDay() + 6) % 7;
  const start = new Date(year, month, 1 - dayOfWeek);
  const cells = [];
  for (let i = 0; i < 42; i++) {
    const d = new Date(start); d.setDate(start.getDate() + i);
    cells.push(d);
  }
  return cells;
}
function startOfWeek(d) {
  const dow = (d.getDay() + 6) % 7;
  const r = new Date(d); r.setDate(d.getDate() - dow); r.setHours(0,0,0,0);
  return r;
}
function isSameDay(a, b) {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
}
function keyOf(d) { return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`; }
function parseDuration(text) {
  const m = String(text || '').match(/(\d+)/);
  return m ? parseInt(m[1], 10) : 45;
}
function parseTime(text) {
  const [h, m] = String(text || '0:0').split(':').map(n => parseInt(n,10));
  return { h: h || 0, m: m || 0 };
}
function addMinutes(time, minutes) {
  const t = parseTime(time);
  let total = t.h * 60 + t.m + minutes;
  const h = Math.floor(total / 60) % 24;
  const m = total % 60;
  return `${String(h).padStart(2,'0')}:${String(m).padStart(2,'0')}`;
}
function eventMeta(appt, servicios) {
  const svc = servicios.find(s => s.nombre === appt.servicio);
  const duration = parseDuration(svc?.duracion || '45 min');
  return { duration, fin: addMinutes(appt.hora, duration), precio: svc?.precio || '—', servicio: svc };
}
function fmtDateLong(d) {
  return d.toLocaleDateString('es-ES', { weekday:'long', day:'2-digit', month:'long', year:'numeric' });
}

// ───── pill / event chip ─────
function CalendarEvent({ event, onClick, dense = false }) {
  const s = STATUS_EVENT_STYLES[event.estado] || STATUS_EVENT_STYLES.Pendiente;
  return (
    <button
      onClick={(e) => { e.stopPropagation(); onClick && onClick(event); }}
      className={`w-full flex items-center gap-1.5 ${s.bg} ${s.hover} ${s.text} text-[11px] font-medium rounded-md px-1.5 ${dense ? 'py-0.5' : 'py-1'} text-left transition`}
      title={`${event.cliente} · ${event.hora}`}
    >
      <span className={`w-0.5 self-stretch ${s.bar} rounded-full shrink-0`} />
      <span className="truncate flex-1">{event.cliente}</span>
      <span className="text-[10px] opacity-70 hidden xl:inline">{event.hora}</span>
    </button>
  );
}

// ───── Month view ─────
function MonthGrid({ cursor, today, events, onPickDate, onOpenDay, onSelectEvent, onAdd }) {
  const year = cursor.getFullYear();
  const month = cursor.getMonth();
  const cells = React.useMemo(() => buildMonthGrid(year, month), [year, month]);

  return (
    <>
      <div className="grid grid-cols-7 border-b border-slate-100 bg-slate-50/40">
        {DAYS_ES_SHORT.map((d, i) => (
          <div key={d} className={`px-3 py-3 text-[11px] uppercase tracking-[0.14em] font-semibold
            ${i >= 5 ? 'text-blue-600' : 'text-slate-500'}`}>
            {d}
          </div>
        ))}
      </div>

      <div className="grid grid-cols-7 grid-rows-6 bg-slate-100 gap-px">
        {cells.map((date, idx) => {
          const inMonth = date.getMonth() === month;
          const isToday = isSameDay(date, today);
          const isWeekend = idx % 7 >= 5;
          const dayEvents = events.get(keyOf(date)) || [];
          const visible = dayEvents.slice(0, 3);
          const overflow = dayEvents.length - visible.length;

          return (
            <div
              key={idx}
              onClick={() => onPickDate(date)}
              className={`group relative min-h-[110px] md:min-h-[128px] p-2 cursor-pointer transition
                ${inMonth ? 'bg-white hover:bg-blue-50/40' : 'bg-slate-50/60 hover:bg-slate-50'}`}
            >
              <div className="flex items-center justify-between">
                <div className={`inline-flex items-center justify-center min-w-[24px] h-6 px-1.5 rounded-full text-xs font-semibold
                  ${isToday
                    ? 'bg-gradient-to-br from-cyan-500 to-blue-500 text-white shadow-[0_4px_10px_-2px_rgba(14,165,233,0.55)]'
                    : inMonth
                      ? (isWeekend ? 'text-blue-600' : 'text-navy-900')
                      : 'text-slate-400'}`}>
                  {date.getDate()}
                </div>
                <button
                  onClick={(e) => { e.stopPropagation(); onAdd(keyOf(date)); }}
                  className="opacity-0 group-hover:opacity-100 w-5 h-5 rounded-md bg-blue-500 text-white flex items-center justify-center transition"
                  aria-label="Añadir cita">
                  <CIcons.Plus size={12} />
                </button>
              </div>

              <div className="mt-1.5 space-y-1">
                {visible.slice(0, overflow > 0 ? 2 : 3).map(ev => (
                  <CalendarEvent key={ev.id} event={ev} onClick={onSelectEvent} />
                ))}
                {overflow > 0 && (
                  <button
                    onClick={(e) => { e.stopPropagation(); onOpenDay(date); }}
                    className="w-full text-[11px] font-semibold text-blue-600 hover:bg-blue-50 rounded-md px-1.5 py-1 text-left transition"
                  >
                    +{overflow + 1} más
                  </button>
                )}
                {/* If exactly 3 events, show all 3 (no overflow button) */}
                {overflow === 0 && visible.length === 3 && null}
              </div>
            </div>
          );
        })}
      </div>
    </>
  );
}

// ───── Hour timeline (shared by Week & Day views) ─────
function HourColumn({ showHeader = true }) {
  const hours = [];
  for (let h = DAY_START; h < DAY_END; h++) hours.push(h);
  return (
    <div className="w-16 shrink-0 border-r border-slate-100 bg-white">
      {showHeader && <div className="h-10 border-b border-slate-100" />}
      {hours.map(h => (
        <div key={h} style={{ height: HOUR_PX }} className="relative">
          <span className="absolute -top-2 right-2 text-[10px] font-semibold text-slate-400 bg-white px-1">
            {String(h).padStart(2,'0')}:00
          </span>
        </div>
      ))}
    </div>
  );
}

function HourGrid() {
  const hours = [];
  for (let h = DAY_START; h < DAY_END; h++) hours.push(h);
  return (
    <>
      {hours.map(h => (
        <div key={h} style={{ height: HOUR_PX }} className="relative border-b border-slate-100">
          {/* half-hour dashed divider */}
          <div className="absolute left-0 right-0" style={{ top: HOUR_PX / 2 }}>
            <div className="border-t border-dashed border-slate-100" />
          </div>
        </div>
      ))}
    </>
  );
}

// Lay out events that overlap in time side-by-side.
// Returns each event annotated with { col, cols } so width = 100/cols%, left = col*width.
function layoutEvents(events, servicios) {
  const withRange = events.map(ev => {
    const t = parseTime(ev.hora);
    const startMin = t.h * 60 + t.m;
    const duration = parseDuration((servicios.find(s => s.nombre === ev.servicio) || {}).duracion || '45 min');
    return { ev, startMin, endMin: startMin + duration, duration };
  }).sort((a, b) => a.startMin - b.startMin || a.endMin - b.endMin);

  // sweep into overlap groups
  const groups = [];
  let current = [];
  let currentEnd = -1;
  withRange.forEach(item => {
    if (item.startMin < currentEnd) {
      current.push(item);
      currentEnd = Math.max(currentEnd, item.endMin);
    } else {
      if (current.length) groups.push(current);
      current = [item];
      currentEnd = item.endMin;
    }
  });
  if (current.length) groups.push(current);

  const out = [];
  groups.forEach(group => {
    // assign column via greedy: find first column whose last event ends ≤ this.startMin
    const cols = []; // each col holds last endMin
    group.forEach(item => {
      let placed = -1;
      for (let i = 0; i < cols.length; i++) {
        if (cols[i] <= item.startMin) { cols[i] = item.endMin; placed = i; break; }
      }
      if (placed === -1) { cols.push(item.endMin); placed = cols.length - 1; }
      item.col = placed;
    });
    group.forEach(item => { item.cols = cols.length; out.push(item); });
  });
  return out;
}

function PositionedEvent({ event, servicios, onClick, compact = false, col = 0, cols = 1 }) {
  const { duration, fin } = eventMeta(event, servicios);
  const t = parseTime(event.hora);
  const startMin = (t.h - DAY_START) * 60 + t.m;
  const topPx    = (startMin / 60) * HOUR_PX;
  const heightPx = Math.max(24, (duration / 60) * HOUR_PX - 4);
  const widthPct = 100 / cols;
  const leftPct  = col * widthPct;
  const s = STATUS_EVENT_STYLES[event.estado] || STATUS_EVENT_STYLES.Pendiente;

  return (
    <button
      onClick={(e) => { e.stopPropagation(); onClick && onClick(event); }}
      style={{ top: `${topPx}px`, height: `${heightPx}px`, left: `calc(${leftPct}% + 2px)`, width: `calc(${widthPct}% - 4px)` }}
      className={`absolute ${s.bg} ${s.hover} ${s.text} ring-1 ${s.ring} rounded-lg pl-2.5 pr-2 py-1 text-left overflow-hidden transition shadow-[0_2px_8px_-4px_rgba(15,23,42,0.15)]`}
    >
      <span className={`absolute left-0 top-0 bottom-0 w-1 ${s.bar} rounded-l-lg`} />
      <div className="text-[11px] font-semibold truncate leading-tight">{event.cliente}</div>
      {heightPx > 44 && (
        <div className="text-[10px] opacity-80 truncate leading-tight mt-0.5">{event.servicio}</div>
      )}
      <div className="text-[10px] opacity-70 mt-0.5 leading-tight">{event.hora} – {fin}</div>
    </button>
  );
}

// ───── Week view ─────
function WeekGrid({ cursor, today, eventsByDay, servicios, onSelectEvent, onAdd }) {
  const ws = startOfWeek(cursor);
  const days = Array.from({ length: 7 }, (_, i) => {
    const d = new Date(ws); d.setDate(ws.getDate() + i); return d;
  });

  return (
    <div className="overflow-auto" style={{ maxHeight: '70vh' }}>
      <div className="flex min-w-[760px]">
        <HourColumn />
        <div className="flex-1 grid grid-cols-7">
          {days.map((d, i) => {
            const dayKey = keyOf(d);
            const dayEvents = eventsByDay.get(dayKey) || [];
            const laidOut = layoutEvents(dayEvents, servicios);
            const isToday = isSameDay(d, today);
            const isWeekend = i >= 5;
            return (
              <div key={i} className="relative border-r border-slate-100 last:border-r-0">
                <div className={`h-10 border-b border-slate-100 flex items-center justify-center gap-2 sticky top-0 z-10
                  ${isToday ? 'bg-blue-50/80' : 'bg-white'}`}>
                  <span className={`text-[10px] uppercase tracking-wider font-semibold ${isWeekend ? 'text-blue-600' : 'text-slate-500'}`}>
                    {DAYS_ES_SHORT[i]}
                  </span>
                  <span className={`inline-flex items-center justify-center min-w-[22px] h-6 px-1.5 rounded-full text-xs font-bold
                    ${isToday ? 'bg-gradient-to-br from-cyan-500 to-blue-500 text-white' : 'text-navy-900'}`}>
                    {d.getDate()}
                  </span>
                </div>
                <div className="relative">
                  <HourGrid />
                  {laidOut.map(({ ev, col, cols }) => (
                    <PositionedEvent key={ev.id} event={ev} servicios={servicios} onClick={onSelectEvent} compact col={col} cols={cols} />
                  ))}
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}

// ───── Day view ─────
function DayGrid({ cursor, eventsByDay, servicios, onSelectEvent, onAdd }) {
  const dayKey = keyOf(cursor);
  const dayEvents = eventsByDay.get(dayKey) || [];

  return (
    <div className="grid grid-cols-1 lg:grid-cols-[1fr_320px] gap-6 p-5 md:p-6">
      <div className="bg-white border border-slate-100 rounded-2xl overflow-hidden">
        <div className="flex items-center justify-between px-4 py-3 border-b border-slate-100 bg-slate-50/40">
          <div>
            <div className="text-xs uppercase tracking-[0.14em] text-slate-400 font-semibold">Agenda</div>
            <div className="text-base font-bold text-navy-900 capitalize">{fmtDateLong(cursor)}</div>
          </div>
          <button
            onClick={() => onAdd(dayKey)}
            className="text-xs font-semibold text-white bg-gradient-to-r from-cyan-500 to-blue-500 px-3 py-1.5 rounded-lg flex items-center gap-1">
            <CIcons.Plus size={14} /> Añadir
          </button>
        </div>
        <div className="overflow-auto" style={{ maxHeight: '65vh' }}>
          <div className="flex">
            <HourColumn showHeader={false} />
            <div className="flex-1 relative">
              <HourGrid />
              {layoutEvents(dayEvents, servicios).map(({ ev, col, cols }) => (
                <PositionedEvent key={ev.id} event={ev} servicios={servicios} onClick={onSelectEvent} col={col} cols={cols} />
              ))}
            </div>
          </div>
        </div>
      </div>

      {/* Side summary */}
      <aside className="space-y-4">
        <div className="bg-white border border-slate-100 rounded-2xl p-5">
          <div className="text-xs uppercase tracking-[0.14em] text-slate-400 font-semibold mb-1">Resumen del día</div>
          <div className="text-3xl font-bold text-navy-900">{dayEvents.length}</div>
          <div className="text-sm text-slate-500">citas programadas</div>
          <div className="mt-4 space-y-2">
            {Object.entries(STATUS_EVENT_STYLES).map(([k, s]) => {
              const n = dayEvents.filter(e => e.estado === k).length;
              return (
                <div key={k} className="flex items-center justify-between text-sm">
                  <div className="flex items-center gap-2">
                    <span className={`w-2 h-2 rounded-full ${s.bar}`} />
                    <span className="text-slate-600">{k}</span>
                  </div>
                  <span className={`font-semibold ${s.text}`}>{n}</span>
                </div>
              );
            })}
          </div>
        </div>

        <div className="bg-white border border-slate-100 rounded-2xl p-5">
          <div className="text-xs uppercase tracking-[0.14em] text-slate-400 font-semibold mb-3">Próximas</div>
          {dayEvents.length === 0 && <div className="text-sm text-slate-400">No hay citas hoy.</div>}
          <div className="space-y-2">
            {dayEvents.slice(0, 5).map(ev => (
              <CalendarEvent key={ev.id} event={ev} onClick={onSelectEvent} />
            ))}
          </div>
        </div>
      </aside>
    </div>
  );
}

// ───── Main component ─────
function CalendarView({ appointments, servicios = [], onNewAppointment, onSelectEvent, onShowDayList, initialDate }) {
  const [cursor, setCursor] = React.useState(() => {
    const d = initialDate ? new Date(initialDate) : new Date();
    return new Date(d.getFullYear(), d.getMonth(), d.getDate());
  });
  const [view, setView] = React.useState('Mes');
  const today = new Date();

  const eventsByDay = React.useMemo(() => {
    const map = new Map();
    appointments.forEach(a => {
      if (!map.has(a.fecha)) map.set(a.fecha, []);
      map.get(a.fecha).push(a);
    });
    map.forEach(list => list.sort((x,y) => (x.hora||'').localeCompare(y.hora||'')));
    return map;
  }, [appointments]);

  const goPrev = () => {
    if (view === 'Mes')    setCursor(c => new Date(c.getFullYear(), c.getMonth() - 1, 1));
    if (view === 'Semana') setCursor(c => { const d = new Date(c); d.setDate(c.getDate() - 7); return d; });
    if (view === 'Día')    setCursor(c => { const d = new Date(c); d.setDate(c.getDate() - 1); return d; });
  };
  const goNext = () => {
    if (view === 'Mes')    setCursor(c => new Date(c.getFullYear(), c.getMonth() + 1, 1));
    if (view === 'Semana') setCursor(c => { const d = new Date(c); d.setDate(c.getDate() + 7); return d; });
    if (view === 'Día')    setCursor(c => { const d = new Date(c); d.setDate(c.getDate() + 1); return d; });
  };
  const goToday = () => setCursor(new Date());

  // Picking a date in month view → switch to Día view (per the brief)
  const pickDate = (date) => { setCursor(date); setView('Día'); };
  // "+X más" → open day-list modal/popover
  const openDay  = (date) => { if (onShowDayList) onShowDayList(date); else { setCursor(date); setView('Día'); } };

  // Header text adapts to view
  let headerText = `${MONTHS_ES[cursor.getMonth()]} ${cursor.getFullYear()}`;
  let prevLabel = 'anterior', nextLabel = 'siguiente';
  if (view === 'Semana') {
    const ws = startOfWeek(cursor);
    const we = new Date(ws); we.setDate(ws.getDate() + 6);
    const sameMonth = ws.getMonth() === we.getMonth();
    headerText = sameMonth
      ? `${ws.getDate()} – ${we.getDate()} ${MONTHS_ES[ws.getMonth()]} ${ws.getFullYear()}`
      : `${ws.getDate()} ${MONTHS_ES[ws.getMonth()].slice(0,3)} – ${we.getDate()} ${MONTHS_ES[we.getMonth()].slice(0,3)} ${we.getFullYear()}`;
    prevLabel = 'Semana anterior'; nextLabel = 'Semana siguiente';
  } else if (view === 'Día') {
    headerText = fmtDateLong(cursor);
    prevLabel = 'Día anterior'; nextLabel = 'Día siguiente';
  }

  return (
    <div className="bg-white rounded-2xl shadow-card border border-slate-100/80 overflow-hidden">
      {/* Toolbar */}
      <div className="flex flex-wrap items-center gap-3 px-5 md:px-6 py-4 border-b border-slate-100">
        <div className="flex items-center gap-1.5">
          <button onClick={goPrev}
            className="w-9 h-9 rounded-xl border border-slate-200 text-slate-500 hover:text-navy-900 hover:border-blue-300 hover:bg-blue-50 transition flex items-center justify-center"
            aria-label={prevLabel}>
            <CIcons.Chevron size={16} className="rotate-180" />
          </button>
          <button onClick={goNext}
            className="w-9 h-9 rounded-xl border border-slate-200 text-slate-500 hover:text-navy-900 hover:border-blue-300 hover:bg-blue-50 transition flex items-center justify-center"
            aria-label={nextLabel}>
            <CIcons.Chevron size={16} />
          </button>
          <button onClick={goToday}
            className="ml-1 px-3 h-9 rounded-xl border border-slate-200 text-xs font-semibold text-slate-600 hover:text-navy-900 hover:border-blue-300 hover:bg-blue-50 transition">
            Hoy
          </button>
        </div>

        <div className="flex-1 min-w-[180px] text-center">
          <div className="text-xl md:text-2xl font-bold text-navy-900 capitalize">
            {headerText}
          </div>
        </div>

        <div className="flex items-center gap-1 bg-slate-50 rounded-xl p-1">
          {VIEW_MODES.map(v => (
            <button key={v} onClick={() => setView(v)}
              className={`text-xs font-semibold px-3 py-1.5 rounded-lg transition
                ${view === v ? 'bg-white text-navy-900 shadow-card' : 'text-slate-500 hover:text-navy-800'}`}>
              {v}
            </button>
          ))}
        </div>

        <button onClick={() => onNewAppointment(null)}
          className="flex items-center gap-2 bg-gradient-to-r from-cyan-500 to-blue-500 text-white text-sm font-semibold px-4 h-9 rounded-xl shadow-[0_8px_20px_-8px_rgba(14,165,233,0.6)] hover:brightness-105">
          <CIcons.Plus size={16} />
          Nueva cita
        </button>
      </div>

      {view === 'Mes' && (
        <MonthGrid
          cursor={cursor} today={today}
          events={eventsByDay}
          onPickDate={pickDate}
          onOpenDay={openDay}
          onSelectEvent={onSelectEvent}
          onAdd={onNewAppointment}
        />
      )}
      {view === 'Semana' && (
        <WeekGrid
          cursor={cursor} today={today}
          eventsByDay={eventsByDay}
          servicios={servicios}
          onSelectEvent={onSelectEvent}
          onAdd={onNewAppointment}
        />
      )}
      {view === 'Día' && (
        <DayGrid
          cursor={cursor}
          eventsByDay={eventsByDay}
          servicios={servicios}
          onSelectEvent={onSelectEvent}
          onAdd={onNewAppointment}
        />
      )}

      {/* Legend / footer */}
      <div className="px-5 md:px-6 py-3 border-t border-slate-100 flex flex-wrap items-center gap-4">
        <span className="text-[11px] uppercase tracking-[0.14em] font-semibold text-slate-400">Leyenda</span>
        {Object.entries(STATUS_EVENT_STYLES).map(([k, s]) => (
          <div key={k} className="flex items-center gap-1.5 text-xs text-slate-600">
            <span className={`w-2.5 h-2.5 rounded-sm ${s.bar}`} />
            {k}
          </div>
        ))}
        <span className="ml-auto text-xs text-slate-500">
          {appointments.length} citas totales
        </span>
      </div>
    </div>
  );
}

window.CalendarView = CalendarView;
window.STATUS_EVENT_STYLES = STATUS_EVENT_STYLES;
window.eventMeta = eventMeta;
window.fmtDateLong = fmtDateLong;

const { Icons: ATIcons } = window;

const STATUS_STYLES = {
  Pendiente:  { dot: 'bg-orange-500',  pill: 'bg-orange-50 text-orange-600 ring-orange-100' },
  Confirmada: { dot: 'bg-blue-500',    pill: 'bg-blue-50 text-blue-600 ring-blue-100' },
  Completada: { dot: 'bg-emerald-500', pill: 'bg-emerald-50 text-emerald-600 ring-emerald-100' },
};

function StatusPill({ value, onClick }) {
  const s = STATUS_STYLES[value] || STATUS_STYLES.Pendiente;
  return (
    <button
      onClick={onClick}
      className={`inline-flex items-center gap-1.5 text-xs font-semibold px-2.5 py-1 rounded-full ring-1 ${s.pill} hover:brightness-95 transition`}
    >
      <span className={`w-1.5 h-1.5 rounded-full ${s.dot}`} />
      {value}
    </button>
  );
}

function AppointmentsTable({ rows, onCycleStatus }) {
  const [filter, setFilter] = React.useState('Todas');
  const filters = ['Todas', 'Pendiente', 'Confirmada', 'Completada'];

  const filtered = filter === 'Todas' ? rows : rows.filter(r => r.estado === filter);

  const fmt = (d) => {
    const date = new Date(d);
    return date.toLocaleDateString('es-ES', { day: '2-digit', month: 'short', year: 'numeric' });
  };

  return (
    <div className="bg-white rounded-2xl shadow-card border border-slate-100/80 h-full flex flex-col">
      <div className="flex flex-wrap items-center gap-3 px-6 pt-5 pb-3">
        <div>
          <div className="text-lg font-bold text-navy-900">Últimas citas</div>
          <div className="text-xs text-slate-500 mt-0.5">{filtered.length} resultados · actualizado hace 2 min</div>
        </div>
        <div className="ml-auto flex items-center gap-1 bg-slate-50 rounded-xl p-1">
          {filters.map(f => (
            <button
              key={f}
              onClick={() => setFilter(f)}
              className={`text-xs font-semibold px-3 py-1.5 rounded-lg transition
                ${filter === f
                  ? 'bg-white text-navy-900 shadow-card'
                  : 'text-slate-500 hover:text-navy-800'}`}
            >
              {f}
            </button>
          ))}
        </div>
        <button className="w-8 h-8 rounded-lg hover:bg-slate-50 flex items-center justify-center text-slate-400">
          <ATIcons.Dots size={18} />
        </button>
      </div>

      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="text-[11px] uppercase tracking-[0.12em] text-slate-400 border-y border-slate-100">
              <th className="text-left font-semibold px-6 py-3">Cliente</th>
              <th className="text-left font-semibold px-2 py-3 hidden md:table-cell">Servicio</th>
              <th className="text-left font-semibold px-2 py-3">Fecha</th>
              <th className="text-left font-semibold px-2 py-3">Estado</th>
              <th className="text-right font-semibold px-6 py-3"></th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((row, i) => (
              <tr key={row.id} className="border-b border-slate-50 hover:bg-slate-50/60 transition anim-in" style={{animationDelay: `${i*40}ms`}}>
                <td className="px-6 py-4">
                  <div className="flex items-center gap-3">
                    <div className={`w-9 h-9 rounded-xl bg-gradient-to-br ${row.color} flex items-center justify-center text-white text-xs font-bold shrink-0`}>
                      {row.avatar}
                    </div>
                    <div className="min-w-0">
                      <div className="font-semibold text-navy-900 truncate">{row.cliente}</div>
                      <div className="text-xs text-slate-500 truncate md:hidden">{row.servicio}</div>
                      <div className="text-xs text-slate-500 truncate hidden md:block">{row.empleado}</div>
                    </div>
                  </div>
                </td>
                <td className="px-2 py-4 text-slate-600 hidden md:table-cell">{row.servicio}</td>
                <td className="px-2 py-4">
                  <div className="text-navy-900 font-medium">{fmt(row.fecha)}</div>
                  <div className="text-xs text-slate-500">{row.hora}</div>
                </td>
                <td className="px-2 py-4">
                  <StatusPill value={row.estado} onClick={() => onCycleStatus(row.id)} />
                </td>
                <td className="px-6 py-4 text-right">
                  <button className="w-8 h-8 rounded-lg hover:bg-slate-100 inline-flex items-center justify-center text-slate-400">
                    <ATIcons.Chevron size={16} />
                  </button>
                </td>
              </tr>
            ))}
            {filtered.length === 0 && (
              <tr><td colSpan="5" className="text-center text-sm text-slate-400 py-10">No hay citas en este estado.</td></tr>
            )}
          </tbody>
        </table>
      </div>

      <div className="px-6 py-3 border-t border-slate-100 flex items-center justify-between">
        <span className="text-xs text-slate-500">Mostrando {filtered.length} de {rows.length}</span>
        <button className="text-xs font-semibold text-blue-600 hover:text-blue-700 flex items-center gap-1">
          Ver todas
          <ATIcons.Chevron size={14} />
        </button>
      </div>
    </div>
  );
}

window.AppointmentsTable = AppointmentsTable;

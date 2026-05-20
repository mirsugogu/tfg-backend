const { Icons: SPIcons } = window;

function StatusDonut({ counts, total }) {
  // Build a conic-gradient donut.
  const { Pendiente = 0, Confirmada = 0, Completada = 0 } = counts;
  const t = total || 1;
  const a = (Confirmada / t) * 360;
  const b = a + (Pendiente / t) * 360;
  const c = b + (Completada / t) * 360;
  const gradient = `conic-gradient(
    #3b82f6 0deg ${a}deg,
    #f97316 ${a}deg ${b}deg,
    #10b981 ${b}deg ${c}deg,
    #e2e8f0 ${c}deg 360deg
  )`;
  return (
    <div className="relative w-28 h-28 shrink-0">
      <div className="w-full h-full rounded-full" style={{ background: gradient }} />
      <div className="absolute inset-3 rounded-full bg-white flex flex-col items-center justify-center shadow-inner">
        <div className="text-[10px] text-slate-400 uppercase tracking-wider">Total</div>
        <div className="text-xl font-bold text-navy-900 leading-none">{total}</div>
      </div>
    </div>
  );
}

function StatusBreakdown({ appointments }) {
  const counts = appointments.reduce((acc, a) => {
    acc[a.estado] = (acc[a.estado] || 0) + 1;
    return acc;
  }, {});
  const total = appointments.length;
  const rows = [
    { label: 'Confirmada', value: counts.Confirmada || 0, dot: 'bg-blue-500',    text: 'text-blue-600' },
    { label: 'Pendiente',  value: counts.Pendiente  || 0, dot: 'bg-orange-500',  text: 'text-orange-600' },
    { label: 'Completada', value: counts.Completada || 0, dot: 'bg-emerald-500', text: 'text-emerald-600' },
  ];

  return (
    <div className="bg-white rounded-2xl p-5 shadow-card border border-slate-100/80">
      <div className="flex items-center justify-between mb-4">
        <div>
          <div className="text-lg font-bold text-navy-900">Estado de citas</div>
          <div className="text-xs text-slate-500 mt-0.5">Resumen semanal</div>
        </div>
        <span className="text-[11px] font-semibold text-emerald-600 bg-emerald-50 px-2 py-1 rounded-full">+18%</span>
      </div>

      <div className="flex items-center gap-5">
        <StatusDonut counts={counts} total={total} />
        <div className="flex-1 space-y-2.5">
          {rows.map(r => (
            <div key={r.label}>
              <div className="flex items-center justify-between text-sm">
                <div className="flex items-center gap-2">
                  <span className={`w-2 h-2 rounded-full ${r.dot}`} />
                  <span className="text-slate-600">{r.label}</span>
                </div>
                <span className={`font-semibold ${r.text}`}>{r.value}</span>
              </div>
              <div className="mt-1 h-1.5 bg-slate-100 rounded-full overflow-hidden">
                <div className={`h-full ${r.dot}`} style={{ width: `${total ? (r.value/total)*100 : 0}%` }} />
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

function QuickActions({ onAction }) {
  const actions = [
    { id: 'cita',       label: 'Nueva cita',      sub: 'Programar próxima visita',  icon: SPIcons.Calendar,  grad: 'from-cyan-400 to-blue-500' },
    { id: 'cliente',    label: 'Nuevo cliente',   sub: 'Añadir ficha al CRM',       icon: SPIcons.UserPlus,  grad: 'from-blue-500 to-indigo-500' },
    { id: 'servicio',   label: 'Añadir servicio', sub: 'Crear servicio al catálogo',icon: SPIcons.Scissors,  grad: 'from-teal-400 to-cyan-500' },
  ];

  return (
    <div className="bg-white rounded-2xl p-5 shadow-card border border-slate-100/80">
      <div className="flex items-center justify-between mb-4">
        <div>
          <div className="text-lg font-bold text-navy-900">Acceso rápido</div>
          <div className="text-xs text-slate-500 mt-0.5">Atajos del equipo</div>
        </div>
        <button className="w-8 h-8 rounded-lg hover:bg-slate-50 flex items-center justify-center text-slate-400">
          <SPIcons.Dots size={18} />
        </button>
      </div>

      <div className="space-y-2.5">
        {actions.map(a => {
          const A = a.icon;
          return (
            <button
              key={a.id}
              onClick={() => onAction(a.id)}
              className="w-full flex items-center gap-3 p-3 rounded-xl border border-slate-100 hover:border-blue-200 hover:bg-blue-50/40 transition text-left group"
            >
              <span className={`w-10 h-10 rounded-xl bg-gradient-to-br ${a.grad} flex items-center justify-center text-white shadow-[0_6px_16px_-6px_rgba(14,165,233,0.5)]`}>
                <A size={18} stroke="#fff" />
              </span>
              <span className="flex-1 min-w-0">
                <span className="block text-sm font-semibold text-navy-900">{a.label}</span>
                <span className="block text-[11px] text-slate-500 truncate">{a.sub}</span>
              </span>
              <span className="w-7 h-7 rounded-lg bg-slate-50 group-hover:bg-white flex items-center justify-center text-slate-400 group-hover:text-blue-500 transition">
                <SPIcons.Plus size={14} />
              </span>
            </button>
          );
        })}
      </div>
    </div>
  );
}

window.StatusBreakdown = StatusBreakdown;
window.QuickActions = QuickActions;

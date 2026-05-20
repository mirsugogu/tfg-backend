const { Icons: MCIcons } = window;

function MetricCard({ icon: Ico, label, value, delta, trend = 'up', tint = 'cyan' }) {
  const tints = {
    cyan:   'from-cyan-400 to-blue-500',
    blue:   'from-blue-500 to-indigo-500',
    teal:   'from-teal-400 to-cyan-500',
    sky:    'from-sky-400 to-blue-500',
  };
  return (
    <div className="bg-white rounded-2xl p-5 shadow-card border border-slate-100/80 flex items-center gap-4 hover:shadow-float transition">
      <div className={`shrink-0 w-14 h-14 rounded-2xl bg-gradient-to-br ${tints[tint]} flex items-center justify-center text-white shadow-[0_8px_20px_-6px_rgba(14,165,233,0.45)]`}>
        <Ico size={22} stroke="#fff" />
      </div>
      <div className="flex-1 min-w-0">
        <div className="text-[12px] font-medium text-slate-500 tracking-wide">{label}</div>
        <div className="flex items-baseline gap-2 mt-0.5">
          <div className="text-2xl font-bold text-navy-900 leading-none">{value}</div>
          {delta && (
            <span className={`text-[11px] font-semibold flex items-center gap-0.5
              ${trend === 'up' ? 'text-emerald-500' : 'text-rose-500'}`}>
              {trend === 'up' ? '▲' : '▼'} {delta}
            </span>
          )}
        </div>
        <div className="mt-2 h-1 bg-slate-100 rounded-full overflow-hidden">
          <div className={`h-full bg-gradient-to-r ${tints[tint]}`} style={{ width: '68%' }} />
        </div>
      </div>
    </div>
  );
}

function MetricCards({ counts }) {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-5">
      <MetricCard icon={MCIcons.Users}     label="Clientes"      value={counts.clientes}  delta="+12%" tint="cyan" />
      <MetricCard icon={MCIcons.Employees} label="Empleados"     value={counts.empleados} delta="+4%"  tint="blue" />
      <MetricCard icon={MCIcons.Calendar}  label="Citas Totales" value={counts.citas}     delta="+18%" tint="sky"  />
      <MetricCard icon={MCIcons.Scissors}  label="Servicios"     value={counts.servicios} delta="+2%"  tint="teal" />
    </div>
  );
}

window.MetricCards = MetricCards;

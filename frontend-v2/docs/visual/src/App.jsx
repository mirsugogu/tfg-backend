const {
  Icons: AIcons,
  Sidebar,
  MetricCards,
  AppointmentsTable,
  StatusBreakdown,
  QuickActions,
  NewAppointmentModal,
  NewClientModal,
  NewServiceModal,
  AppointmentDetailsModal,
  DayEventsModal,
  CalendarView,
  OPTIMA_DATA,
} = window;

const STATUS_CYCLE = ['Pendiente', 'Confirmada', 'Completada'];
const AVATAR_COLORS = [
  'from-cyan-400 to-blue-500',
  'from-amber-400 to-orange-500',
  'from-emerald-400 to-teal-500',
  'from-indigo-400 to-violet-500',
  'from-pink-400 to-rose-500',
  'from-sky-400 to-blue-500',
];

function initialsOf(name) {
  return name.split(/\s+/).filter(Boolean).slice(0,2).map(s => s[0]?.toUpperCase()).join('');
}

function Header({ active, onMenu, onNewAppointment }) {
  const titles = {
    dashboard: 'Dashboard',
    clientes: 'Clientes',
    empleados: 'Empleados',
    catalogo: 'Catálogo',
    citas: 'Citas',
    config: 'Configuración',
  };
  return (
    <header className="flex items-center gap-3 mb-7">
      <button className="lg:hidden w-10 h-10 rounded-xl bg-white shadow-card flex items-center justify-center text-navy-900" onClick={onMenu} aria-label="Abrir menú">
        <AIcons.Dashboard size={20} />
      </button>
      <div className="min-w-0">
        <div className="text-xs text-slate-500 mb-0.5 flex items-center gap-1">
          <span>Panel</span>
          <AIcons.Chevron size={12} />
          <span className="text-blue-600 font-semibold">{titles[active]}</span>
        </div>
        <h1 className="text-2xl md:text-3xl font-bold text-navy-900 leading-tight">{titles[active]}</h1>
      </div>

      <div className="ml-auto flex items-center gap-2 md:gap-3">
        <div className="hidden md:flex items-center gap-2 bg-white shadow-card rounded-xl px-3 py-2 w-64">
          <AIcons.Search size={16} className="text-slate-400" />
          <input placeholder="Buscar clientes, citas…" className="bg-transparent text-sm outline-none flex-1 placeholder:text-slate-400" />
          <kbd className="text-[10px] text-slate-400 bg-slate-100 px-1.5 py-0.5 rounded">⌘K</kbd>
        </div>
        <button className="relative w-10 h-10 rounded-xl bg-white shadow-card flex items-center justify-center text-slate-500 hover:text-navy-900">
          <AIcons.Bell size={18} />
          <span className="absolute top-2 right-2 w-2 h-2 rounded-full bg-orange-500 ring-2 ring-white" />
        </button>
        <button
          onClick={onNewAppointment}
          className="hidden sm:flex items-center gap-2 bg-gradient-to-r from-cyan-500 to-blue-500 text-white text-sm font-semibold px-4 h-10 rounded-xl shadow-[0_8px_20px_-8px_rgba(14,165,233,0.6)] hover:brightness-105"
        >
          <AIcons.Plus size={16} />
          Nueva cita
        </button>
        <div className="hidden md:flex items-center gap-2 bg-white shadow-card rounded-xl pl-1 pr-3 h-10">
          <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-cyan-400 to-blue-500 flex items-center justify-center text-white text-xs font-bold">AP</div>
          <div className="text-xs leading-tight">
            <div className="font-semibold text-navy-900">Alex Prieto</div>
            <div className="text-slate-400">Admin</div>
          </div>
        </div>
      </div>
    </header>
  );
}

function PageStub({ title, desc, children }) {
  return (
    <div className="bg-white rounded-2xl shadow-card border border-slate-100/80 p-6">
      <div className="text-lg font-bold text-navy-900">{title}</div>
      <div className="text-sm text-slate-500 mt-0.5">{desc}</div>
      <div className="mt-5">{children}</div>
    </div>
  );
}

function SimpleTable({ headers, rows }) {
  return (
    <div className="overflow-x-auto -mx-2">
      <table className="w-full text-sm">
        <thead>
          <tr className="text-[11px] uppercase tracking-[0.12em] text-slate-400 border-b border-slate-100">
            {headers.map(h => <th key={h} className="text-left font-semibold px-2 py-3">{h}</th>)}
          </tr>
        </thead>
        <tbody>
          {rows.map((cells, i) => (
            <tr key={i} className="border-b border-slate-50 hover:bg-slate-50/60">
              {cells.map((c, j) => <td key={j} className="px-2 py-3 text-navy-800">{c}</td>)}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function App() {
  const [active, setActive] = React.useState('dashboard');
  const [mobileOpen, setMobileOpen] = React.useState(false);

  const [appointments, setAppointments] = React.useState(OPTIMA_DATA.initialAppointments);
  const [clientes, setClientes] = React.useState(OPTIMA_DATA.clientesData);
  const [empleados] = React.useState(OPTIMA_DATA.empleadosData);
  const [servicios, setServicios] = React.useState(OPTIMA_DATA.serviciosData);

  const [modal, setModal] = React.useState(null);
  const [prefillDate, setPrefillDate] = React.useState(null);
  const [detailsApt, setDetailsApt] = React.useState(null);
  const [dayPopover, setDayPopover] = React.useState(null);

  const cycleStatus = (id) => {
    setAppointments(rows => rows.map(r => {
      if (r.id !== id) return r;
      const idx = STATUS_CYCLE.indexOf(r.estado);
      return { ...r, estado: STATUS_CYCLE[(idx + 1) % STATUS_CYCLE.length] };
    }));
  };

  const setStatus = (id, estado) => {
    setAppointments(rows => rows.map(r => r.id === id ? { ...r, estado } : r));
    setDetailsApt(d => d && d.id === id ? { ...d, estado } : d);
  };

  const deleteAppointment = (id) => {
    setAppointments(rows => rows.filter(r => r.id !== id));
    setDetailsApt(null);
  };

  const addAppointment = (form) => {
    const nextId = Math.max(0, ...appointments.map(a => a.id)) + 1;
    setAppointments([{
      id: nextId,
      cliente: form.cliente,
      servicio: form.servicio,
      empleado: form.empleado,
      fecha: form.fecha,
      hora: form.hora,
      estado: 'Pendiente',
      avatar: initialsOf(form.cliente),
      color: AVATAR_COLORS[nextId % AVATAR_COLORS.length],
    }, ...appointments]);
  };

  const addCliente = (form) => {
    const nextId = Math.max(0, ...clientes.map(c => c.id)) + 1;
    setClientes([{ id: nextId, nombre: form.nombre, email: form.email, tel: form.tel, visitas: 0 }, ...clientes]);
  };

  const addServicio = (form) => {
    const nextId = Math.max(0, ...servicios.map(s => s.id)) + 1;
    setServicios([{ id: nextId, nombre: form.nombre, precio: form.precio || '—', duracion: form.duracion }, ...servicios]);
  };

  const counts = {
    clientes: clientes.length,
    empleados: empleados.length,
    citas: appointments.length,
    servicios: servicios.length,
  };

  const onAction = (id) => {
    if (id === 'cita') { setPrefillDate(null); setModal('cita'); }
    if (id === 'cliente') setModal('cliente');
    if (id === 'servicio') setModal('servicio');
  };

  const openNewAppointment = (date) => {
    setPrefillDate(date);
    setModal('cita');
  };

  return (
    <div className="flex h-screen overflow-hidden relative">
      {/* Mobile backdrop */}
      {!mobileOpen ? null : (
        <div className="fixed inset-0 z-30 bg-navy-900/40 lg:hidden" onClick={() => setMobileOpen(false)} />
      )}

      <Sidebar
        active={active}
        onNavigate={(id) => { setActive(id); setMobileOpen(false); }}
        collapsed={!mobileOpen}
        onToggle={() => setMobileOpen(false)}
      />

      <main className="flex-1 min-w-0 h-screen overflow-y-auto px-5 md:px-8 py-7" data-screen-label={active}>
        <Header active={active} onMenu={() => setMobileOpen(true)} onNewAppointment={() => setModal('cita')} />

        {active === 'dashboard' && (
          <div className="space-y-6">
            <MetricCards counts={counts} />

            <div className="grid grid-cols-1 xl:grid-cols-10 gap-6">
              <div className="xl:col-span-7">
                <AppointmentsTable rows={appointments} onCycleStatus={cycleStatus} />
              </div>
              <div className="xl:col-span-3 space-y-6">
                <StatusBreakdown appointments={appointments} />
                <QuickActions onAction={onAction} />
              </div>
            </div>
          </div>
        )}

        {active === 'clientes' && (
          <PageStub title="Clientes" desc={`${clientes.length} clientes activos`}>
            <SimpleTable
              headers={['Cliente', 'Email', 'Teléfono', 'Visitas']}
              rows={clientes.map(c => [
                <div className="flex items-center gap-3">
                  <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-cyan-400 to-blue-500 text-white text-xs font-bold flex items-center justify-center">{initialsOf(c.nombre)}</div>
                  <span className="font-medium">{c.nombre}</span>
                </div>,
                <span className="text-slate-500">{c.email}</span>,
                <span className="text-slate-500">{c.tel}</span>,
                <span className="font-semibold text-blue-600">{c.visitas}</span>,
              ])}
            />
          </PageStub>
        )}

        {active === 'empleados' && (
          <PageStub title="Empleados" desc={`${empleados.length} en el equipo`}>
            <SimpleTable
              headers={['Empleado', 'Rol', 'Citas asignadas']}
              rows={empleados.map(e => [
                <div className="flex items-center gap-3">
                  <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-blue-500 to-indigo-500 text-white text-xs font-bold flex items-center justify-center">{initialsOf(e.nombre)}</div>
                  <span className="font-medium">{e.nombre}</span>
                </div>,
                <span className="text-slate-500">{e.rol}</span>,
                <span className="font-semibold text-blue-600">{e.citas}</span>,
              ])}
            />
          </PageStub>
        )}

        {active === 'catalogo' && (
          <PageStub title="Catálogo" desc={`${servicios.length} servicios disponibles`}>
            <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
              {servicios.map(s => (
                <div key={s.id} className="rounded-2xl border border-slate-100 p-4 hover:border-blue-200 hover:bg-blue-50/30 transition">
                  <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-teal-400 to-cyan-500 text-white flex items-center justify-center mb-3">
                    <AIcons.Scissors size={18} stroke="#fff" />
                  </div>
                  <div className="font-semibold text-navy-900">{s.nombre}</div>
                  <div className="flex items-center justify-between mt-2 text-sm">
                    <span className="text-slate-500">{s.duracion}</span>
                    <span className="font-bold text-blue-600">{s.precio}</span>
                  </div>
                </div>
              ))}
            </div>
          </PageStub>
        )}

        {active === 'citas' && (
          <CalendarView
            appointments={appointments}
            servicios={servicios}
            onNewAppointment={openNewAppointment}
            onSelectEvent={(ev) => setDetailsApt(ev)}
            onShowDayList={(date) => setDayPopover(date)}
            initialDate="2026-05-15"
          />
        )}

        {active === 'config' && (
          <PageStub title="Configuración" desc="Personaliza tu espacio de trabajo OPTIMA.">
            <div className="grid sm:grid-cols-2 gap-4">
              {['Perfil del negocio','Notificaciones','Equipo y permisos','Facturación','Integraciones','Seguridad'].map(t => (
                <div key={t} className="rounded-2xl border border-slate-100 p-4 flex items-center gap-3 hover:border-blue-200 hover:bg-blue-50/30 transition">
                  <div className="w-10 h-10 rounded-xl bg-slate-100 text-blue-500 flex items-center justify-center">
                    <AIcons.Settings size={18} />
                  </div>
                  <div className="flex-1">
                    <div className="font-semibold text-navy-900 text-sm">{t}</div>
                    <div className="text-xs text-slate-500">Pendiente de configurar</div>
                  </div>
                  <AIcons.Chevron size={16} className="text-slate-400" />
                </div>
              ))}
            </div>
          </PageStub>
        )}
      </main>

      <NewAppointmentModal
        open={modal === 'cita'}
        onClose={() => { setModal(null); setPrefillDate(null); }}
        onSave={addAppointment}
        clientes={clientes}
        servicios={servicios}
        empleados={empleados}
        prefillDate={prefillDate}
      />
      <NewClientModal
        open={modal === 'cliente'}
        onClose={() => setModal(null)}
        onSave={addCliente}
      />
      <NewServiceModal
        open={modal === 'servicio'}
        onClose={() => setModal(null)}
        onSave={addServicio}
      />

      <AppointmentDetailsModal
        open={!!detailsApt}
        appointment={detailsApt}
        servicios={servicios}
        onClose={() => setDetailsApt(null)}
        onChangeStatus={setStatus}
        onDelete={deleteAppointment}
      />

      <DayEventsModal
        open={!!dayPopover}
        date={dayPopover}
        appointments={appointments}
        onClose={() => setDayPopover(null)}
        onSelectEvent={(ev) => setDetailsApt(ev)}
        onNewAppointment={openNewAppointment}
      />
    </div>
  );
}

ReactDOM.createRoot(document.getElementById('root')).render(<App />);

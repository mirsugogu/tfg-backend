const { Icons: MIcons } = window;

function Modal({ open, onClose, title, children, footer }) {
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 anim-in">
      <div className="absolute inset-0 bg-navy-900/40 backdrop-blur-sm" onClick={onClose} />
      <div className="relative bg-white rounded-2xl shadow-float w-full max-w-md overflow-hidden">
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100">
          <div className="text-base font-bold text-navy-900">{title}</div>
          <button onClick={onClose} className="w-8 h-8 rounded-lg hover:bg-slate-50 flex items-center justify-center text-slate-400">
            <MIcons.Close size={18} />
          </button>
        </div>
        <div className="px-6 py-5">{children}</div>
        {footer && <div className="px-6 py-4 bg-slate-50/60 border-t border-slate-100 flex justify-end gap-2">{footer}</div>}
      </div>
    </div>
  );
}

function Field({ label, children }) {
  return (
    <label className="block mb-3">
      <span className="block text-[11px] font-semibold text-slate-500 uppercase tracking-wider mb-1.5">{label}</span>
      {children}
    </label>
  );
}

const inputCls = "w-full px-3 py-2.5 rounded-xl bg-slate-50 border border-slate-200 text-sm text-navy-900 focus:bg-white focus:border-blue-400 focus:ring-2 focus:ring-blue-100 outline-none transition";

function NewAppointmentModal({ open, onClose, onSave, clientes, servicios, empleados, prefillDate }) {
  const [form, setForm] = React.useState({ cliente: '', servicio: '', empleado: '', fecha: '', hora: '10:00' });
  React.useEffect(() => {
    if (open) setForm({
      cliente: clientes[0]?.nombre || '',
      servicio: servicios[0]?.nombre || '',
      empleado: empleados[0]?.nombre || '',
      fecha: prefillDate || new Date().toISOString().slice(0,10),
      hora: '10:00',
    });
  }, [open, prefillDate]);
  const save = () => {
    if (!form.cliente || !form.fecha) return;
    onSave(form);
    onClose();
  };
  return (
    <Modal open={open} onClose={onClose} title="Nueva cita"
      footer={<>
        <button onClick={onClose} className="px-4 py-2 rounded-xl text-sm font-semibold text-slate-600 hover:bg-slate-100">Cancelar</button>
        <button onClick={save} className="px-4 py-2 rounded-xl text-sm font-semibold text-white bg-gradient-to-r from-cyan-500 to-blue-500 hover:brightness-105 shadow-card">Crear cita</button>
      </>}>
      <Field label="Cliente">
        <select className={inputCls} value={form.cliente} onChange={e => setForm({...form, cliente: e.target.value})}>
          {clientes.map(c => <option key={c.id}>{c.nombre}</option>)}
        </select>
      </Field>
      <Field label="Servicio">
        <select className={inputCls} value={form.servicio} onChange={e => setForm({...form, servicio: e.target.value})}>
          {servicios.map(s => <option key={s.id}>{s.nombre}</option>)}
        </select>
      </Field>
      <Field label="Empleado">
        <select className={inputCls} value={form.empleado} onChange={e => setForm({...form, empleado: e.target.value})}>
          {empleados.map(e => <option key={e.id}>{e.nombre}</option>)}
        </select>
      </Field>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Fecha">
          <input type="date" className={inputCls} value={form.fecha} onChange={e => setForm({...form, fecha: e.target.value})} />
        </Field>
        <Field label="Hora">
          <input type="time" className={inputCls} value={form.hora} onChange={e => setForm({...form, hora: e.target.value})} />
        </Field>
      </div>
    </Modal>
  );
}

function NewClientModal({ open, onClose, onSave }) {
  const [form, setForm] = React.useState({ nombre: '', email: '', tel: '' });
  React.useEffect(() => { if (open) setForm({ nombre: '', email: '', tel: '' }); }, [open]);
  const save = () => { if (!form.nombre) return; onSave(form); onClose(); };
  return (
    <Modal open={open} onClose={onClose} title="Nuevo cliente"
      footer={<>
        <button onClick={onClose} className="px-4 py-2 rounded-xl text-sm font-semibold text-slate-600 hover:bg-slate-100">Cancelar</button>
        <button onClick={save} className="px-4 py-2 rounded-xl text-sm font-semibold text-white bg-gradient-to-r from-cyan-500 to-blue-500 hover:brightness-105 shadow-card">Guardar</button>
      </>}>
      <Field label="Nombre y apellidos"><input className={inputCls} value={form.nombre} onChange={e=>setForm({...form, nombre: e.target.value})} placeholder="Ej. María García" /></Field>
      <Field label="Email"><input className={inputCls} value={form.email} onChange={e=>setForm({...form, email: e.target.value})} placeholder="maria@mail.com" /></Field>
      <Field label="Teléfono"><input className={inputCls} value={form.tel} onChange={e=>setForm({...form, tel: e.target.value})} placeholder="+34 600 000 000" /></Field>
    </Modal>
  );
}

function NewServiceModal({ open, onClose, onSave }) {
  const [form, setForm] = React.useState({ nombre: '', precio: '', duracion: '30 min' });
  React.useEffect(() => { if (open) setForm({ nombre: '', precio: '', duracion: '30 min' }); }, [open]);
  const save = () => { if (!form.nombre) return; onSave(form); onClose(); };
  return (
    <Modal open={open} onClose={onClose} title="Añadir servicio"
      footer={<>
        <button onClick={onClose} className="px-4 py-2 rounded-xl text-sm font-semibold text-slate-600 hover:bg-slate-100">Cancelar</button>
        <button onClick={save} className="px-4 py-2 rounded-xl text-sm font-semibold text-white bg-gradient-to-r from-cyan-500 to-blue-500 hover:brightness-105 shadow-card">Añadir</button>
      </>}>
      <Field label="Nombre del servicio"><input className={inputCls} value={form.nombre} onChange={e=>setForm({...form, nombre: e.target.value})} placeholder="Ej. Tratamiento capilar" /></Field>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Precio"><input className={inputCls} value={form.precio} onChange={e=>setForm({...form, precio: e.target.value})} placeholder="€45" /></Field>
        <Field label="Duración">
          <select className={inputCls} value={form.duracion} onChange={e=>setForm({...form, duracion: e.target.value})}>
            <option>15 min</option><option>30 min</option><option>45 min</option><option>60 min</option><option>90 min</option>
          </select>
        </Field>
      </div>
    </Modal>
  );
}

window.NewAppointmentModal = NewAppointmentModal;
window.NewClientModal = NewClientModal;
window.NewServiceModal = NewServiceModal;

// ───── Appointment details modal ─────
function AppointmentDetailsModal({ open, appointment, servicios, onClose, onChangeStatus, onDelete }) {
  if (!appointment) return null;
  const styles = window.STATUS_EVENT_STYLES || {};
  const meta = window.eventMeta ? window.eventMeta(appointment, servicios) : { duration: 45, fin: appointment.hora, precio: '—' };
  const s = styles[appointment.estado] || styles.Pendiente || { bar:'bg-orange-500', bg:'bg-orange-50', text:'text-orange-700' };
  const dateLong = new Date(appointment.fecha + 'T00:00:00').toLocaleDateString('es-ES', { weekday:'long', day:'2-digit', month:'long', year:'numeric' });

  return (
    <Modal open={open} onClose={onClose} title="Detalles de la cita"
      footer={<>
        <button onClick={() => onDelete && onDelete(appointment.id)} className="px-4 py-2 rounded-xl text-sm font-semibold text-rose-600 hover:bg-rose-50">Eliminar</button>
        <button onClick={onClose} className="px-4 py-2 rounded-xl text-sm font-semibold text-white bg-navy-900 hover:bg-navy-800">Cerrar</button>
      </>}>
      {/* Hero */}
      <div className="flex items-start gap-4 pb-4 border-b border-slate-100">
        <div className={`w-14 h-14 rounded-2xl bg-gradient-to-br ${appointment.color || 'from-cyan-400 to-blue-500'} text-white font-bold flex items-center justify-center text-base shrink-0`}>
          {appointment.avatar}
        </div>
        <div className="flex-1 min-w-0">
          <div className="text-lg font-bold text-navy-900 truncate">{appointment.cliente}</div>
          <div className="text-sm text-slate-500 truncate">{appointment.servicio}</div>
          <div className="mt-2">
            <span className={`inline-flex items-center gap-1.5 text-[11px] font-semibold px-2.5 py-1 rounded-full ${s.bg} ${s.text} ring-1 ring-inset ${s.ring || 'ring-slate-200'}`}>
              <span className={`w-1.5 h-1.5 rounded-full ${s.bar}`} />
              {appointment.estado}
            </span>
          </div>
        </div>
      </div>

      {/* Info grid */}
      <div className="grid grid-cols-2 gap-x-4 gap-y-3 py-4 border-b border-slate-100">
        <div>
          <div className="text-[10px] uppercase tracking-[0.14em] font-semibold text-slate-400">Empleado</div>
          <div className="text-sm font-semibold text-navy-900 mt-1">{appointment.empleado}</div>
        </div>
        <div>
          <div className="text-[10px] uppercase tracking-[0.14em] font-semibold text-slate-400">Duración</div>
          <div className="text-sm font-semibold text-navy-900 mt-1">{meta.duration} min</div>
        </div>
        <div className="col-span-2">
          <div className="text-[10px] uppercase tracking-[0.14em] font-semibold text-slate-400">Fecha</div>
          <div className="text-sm font-semibold text-navy-900 mt-1 capitalize">{dateLong}</div>
        </div>
        <div>
          <div className="text-[10px] uppercase tracking-[0.14em] font-semibold text-slate-400">Inicio</div>
          <div className="text-sm font-semibold text-navy-900 mt-1">{appointment.hora}</div>
        </div>
        <div>
          <div className="text-[10px] uppercase tracking-[0.14em] font-semibold text-slate-400">Fin</div>
          <div className="text-sm font-semibold text-navy-900 mt-1">{meta.fin}</div>
        </div>
      </div>

      {/* Notes */}
      <div className="py-4 border-b border-slate-100">
        <div className="text-[10px] uppercase tracking-[0.14em] font-semibold text-slate-400 mb-1.5">Notas del cliente</div>
        <div className="text-sm text-slate-600 leading-relaxed bg-slate-50 rounded-xl p-3">
          {appointment.notas || 'Sin notas adicionales. El cliente ha solicitado puntualidad y avisará si necesita reprogramar.'}
        </div>
      </div>

      {/* Service breakdown */}
      <div className="py-4 border-b border-slate-100">
        <div className="text-[10px] uppercase tracking-[0.14em] font-semibold text-slate-400 mb-2">Servicios contratados</div>
        <div className="flex items-center justify-between bg-blue-50/60 rounded-xl px-3 py-2.5">
          <div className="flex items-center gap-2 min-w-0">
            <span className="w-8 h-8 rounded-lg bg-gradient-to-br from-cyan-400 to-blue-500 text-white flex items-center justify-center text-xs font-bold shrink-0">
              {appointment.servicio.split(' ').map(w=>w[0]).slice(0,2).join('').toUpperCase()}
            </span>
            <div className="min-w-0">
              <div className="text-sm font-semibold text-navy-900 truncate">{appointment.servicio}</div>
              <div className="text-[11px] text-slate-500">{meta.duration} min</div>
            </div>
          </div>
          <div className="text-sm font-bold text-blue-600">{meta.precio}</div>
        </div>
        <div className="flex items-center justify-between mt-3 px-1">
          <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Total</span>
          <span className="text-lg font-bold text-navy-900">{meta.precio}</span>
        </div>
      </div>

      {/* Status changer */}
      <div className="pt-4">
        <div className="text-[10px] uppercase tracking-[0.14em] font-semibold text-slate-400 mb-2">Cambiar estado</div>
        <div className="grid grid-cols-3 gap-2">
          {Object.entries(styles).map(([k, st]) => {
            const active = appointment.estado === k;
            return (
              <button
                key={k}
                onClick={() => onChangeStatus(appointment.id, k)}
                className={`flex items-center justify-center gap-1.5 text-xs font-semibold px-2 py-2 rounded-xl transition ring-1 ring-inset
                  ${active
                    ? `${st.bg} ${st.text} ${st.ring}`
                    : 'bg-white text-slate-500 ring-slate-200 hover:bg-slate-50'}`}
              >
                <span className={`w-1.5 h-1.5 rounded-full ${st.bar}`} />
                {k}
              </button>
            );
          })}
        </div>
      </div>
    </Modal>
  );
}

// ───── Day events modal (overflow) ─────
function DayEventsModal({ open, date, appointments, onClose, onSelectEvent, onNewAppointment }) {
  if (!date) return null;
  const dayKey = `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`;
  const events = appointments.filter(a => a.fecha === dayKey).sort((x,y) => (x.hora||'').localeCompare(y.hora||''));
  const styles = window.STATUS_EVENT_STYLES || {};
  const dateLong = date.toLocaleDateString('es-ES', { weekday:'long', day:'2-digit', month:'long', year:'numeric' });

  return (
    <Modal open={open} onClose={onClose} title={
      <span className="capitalize">{dateLong}</span>
    }
      footer={<>
        <button onClick={onClose} className="px-4 py-2 rounded-xl text-sm font-semibold text-slate-600 hover:bg-slate-100">Cerrar</button>
        <button onClick={() => { onClose(); onNewAppointment(dayKey); }} className="px-4 py-2 rounded-xl text-sm font-semibold text-white bg-gradient-to-r from-cyan-500 to-blue-500 hover:brightness-105 shadow-card flex items-center gap-1">
          <MIcons.Plus size={14} /> Nueva cita
        </button>
      </>}>
      {events.length === 0 ? (
        <div className="text-center py-8 text-sm text-slate-400">No hay citas este día.</div>
      ) : (
        <div className="space-y-2 max-h-[60vh] overflow-auto pr-1">
          {events.map(ev => {
            const s = styles[ev.estado] || styles.Pendiente;
            const meta = window.eventMeta ? window.eventMeta(ev, []) : { fin: ev.hora };
            return (
              <button
                key={ev.id}
                onClick={() => { onClose(); onSelectEvent(ev); }}
                className={`w-full flex items-center gap-3 p-3 rounded-xl ${s.bg} ${s.hover} text-left transition`}
              >
                <span className={`w-1 self-stretch ${s.bar} rounded-full`} />
                <div className="w-9 h-9 rounded-lg bg-white/70 flex items-center justify-center text-xs font-bold text-navy-900 shrink-0">
                  {ev.avatar}
                </div>
                <div className="flex-1 min-w-0">
                  <div className="font-semibold text-navy-900 text-sm truncate">{ev.cliente}</div>
                  <div className={`text-xs ${s.text} truncate`}>{ev.servicio} · {ev.empleado}</div>
                </div>
                <div className="text-right shrink-0">
                  <div className="text-xs font-semibold text-navy-900">{ev.hora}</div>
                  <div className="text-[10px] text-slate-500">{ev.estado}</div>
                </div>
              </button>
            );
          })}
        </div>
      )}
    </Modal>
  );
}

window.AppointmentDetailsModal = AppointmentDetailsModal;
window.DayEventsModal = DayEventsModal;

const { Icons: LIcons } = window;

// Extra inline icons specific to login
const Eye = ({ size = 18, className = '' }) =>
<svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" className={className}>
    <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z" />
    <circle cx="12" cy="12" r="3" />
  </svg>;

const EyeOff = ({ size = 18, className = '' }) =>
<svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" className={className}>
    <path d="M3 3l18 18" />
    <path d="M10.6 6.2A10.9 10.9 0 0 1 12 6c6.5 0 10 7 10 7a17.7 17.7 0 0 1-3.2 4.3" />
    <path d="M6.6 6.6A17.7 17.7 0 0 0 2 13s3.5 7 10 7c1.5 0 2.8-.3 4-.8" />
    <path d="M9.9 9.9a3 3 0 0 0 4.2 4.2" />
  </svg>;

const Building = ({ size = 18, className = '' }) =>
<svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" className={className}>
    <path d="M4 21V6a2 2 0 0 1 2-2h7a2 2 0 0 1 2 2v15" />
    <path d="M15 9h3a2 2 0 0 1 2 2v10" />
    <path d="M3 21h18" />
    <path d="M8 8h3M8 12h3M8 16h3" />
  </svg>;

const Mail = ({ size = 18, className = '' }) =>
<svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" className={className}>
    <rect x="3" y="5" width="18" height="14" rx="2.5" />
    <path d="m4 7 8 6 8-6" />
  </svg>;

const Lock = ({ size = 18, className = '' }) =>
<svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" className={className}>
    <rect x="4" y="10.5" width="16" height="10" rx="2.5" />
    <path d="M8 10.5V8a4 4 0 0 1 8 0v2.5" />
  </svg>;

const Sparkle = ({ size = 18, className = '' }) =>
<svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" className={className}>
    <path d="M12 3v4M12 17v4M3 12h4M17 12h4M6 6l2.5 2.5M15.5 15.5 18 18M6 18l2.5-2.5M15.5 8.5 18 6" />
  </svg>;


const DEMO = { negocio: 'optima-demo', email: 'admin@optima.com', password: 'demo1234' };

function Field({ label, icon: Ico, children }) {
  return (
    <label className="block">
      <span className="block text-xs font-semibold text-navy-900 mb-1.5">
        {label} <span className="text-blue-500">*</span>
      </span>
      <div className="relative">
        {Ico &&
        <span className="absolute inset-y-0 left-0 w-11 flex items-center justify-center text-slate-400 pointer-events-none">
            <Ico size={18} />
          </span>
        }
        {children}
      </div>
    </label>);

}

const inputCls = "w-full pl-11 pr-4 py-3 rounded-2xl bg-slate-50 border border-slate-200 text-sm text-navy-900 placeholder:text-slate-400 focus:bg-white focus:border-blue-400 focus:ring-4 focus:ring-blue-100 outline-none transition";

function Login() {
  const [form, setForm] = React.useState({ negocio: '', email: '', password: '', remember: true });
  const [showPwd, setShowPwd] = React.useState(false);
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState('');

  const fillDemo = () => setForm((f) => ({ ...f, ...DEMO }));

  const submit = (e) => {
    e.preventDefault();
    setError('');
    if (!form.negocio || !form.email || !form.password) {
      setError('Por favor, completa todos los campos.');
      return;
    }
    setLoading(true);
    setTimeout(() => {
      setLoading(false);
      window.location.href = 'index.html';
    }, 700);
  };

  return (
    <div className="min-h-screen flex flex-col lg:flex-row">
      {/* Right side on desktop becomes TOP on mobile */}
      <aside className="relative order-first lg:order-last lg:w-1/2 bg-navy-900 text-white overflow-hidden
                        min-h-[260px] lg:min-h-screen flex items-center justify-center px-8 py-10 lg:py-0">
        
        
        
        {/* Decorative gradient blobs */}
        <div className="absolute -top-32 -right-24 w-[420px] h-[420px] rounded-full bg-gradient-to-br from-cyan-400/30 to-blue-500/10 blur-3xl" />
        <div className="absolute -bottom-40 -left-20 w-[460px] h-[460px] rounded-full bg-gradient-to-tr from-blue-500/25 to-cyan-300/10 blur-3xl" />
        <div className="absolute top-1/3 left-1/3 w-40 h-40 rounded-full bg-cyan-400/10 blur-2xl" />

        <div className="relative z-10 max-w-xl w-full text-center anim-in">
          {/* Pure-code logo: cyan swirl icon + OPTIMA wordmark */}
          <div className="flex items-center justify-center gap-4 lg:gap-5">
            <svg viewBox="0 0 64 64" className="w-14 h-14 lg:w-20 lg:h-20 text-cyan-400 float-slow drop-shadow-[0_8px_20px_rgba(34,211,238,0.5)]" fill="none" stroke="currentColor" strokeWidth="5" strokeLinecap="round" strokeLinejoin="round">
              <path d="M32 8a24 24 0 1 1-16.97 7.03" />
              <path d="M32 20a12 12 0 1 0 8.49 3.51" opacity="0.85" />
              <circle cx="32" cy="32" r="3.5" fill="currentColor" stroke="none" />
            </svg>
            <span className="text-5xl lg:text-7xl font-extrabold text-white tracking-tight leading-none">OPTIMA</span>
          </div>

          <div className="mt-8 lg:mt-12 text-2xl lg:text-4xl font-bold leading-tight tracking-tight">
            Gestiona tu negocio
            <span className="block bg-gradient-to-r from-cyan-300 to-cyan-100 bg-clip-text text-transparent">
              con inteligencia
            </span>
          </div>
          <p className="mt-3 lg:mt-4 text-sm lg:text-base text-white/70 leading-relaxed">
            Clientes, citas, empleados y catálogo de servicios en una sola plataforma diseñada para negocios modernos.
          </p>

          {/* Feature pills (desktop only) */}
          <div className="hidden lg:flex flex-wrap justify-center gap-2 mt-8">
            {['Citas inteligentes', 'CRM de clientes', 'Equipo & horarios', 'Catálogo'].map((t) =>
            <span key={t} className="text-[11px] font-medium px-3 py-1.5 rounded-full bg-white/5 border border-white/10 text-white/80">
                {t}
              </span>
            )}
          </div>
        </div>

        {/* Footer line */}
        <div className="hidden lg:flex absolute bottom-6 left-0 right-0 px-10 items-center justify-between text-[11px] text-white/40">
          <span>v1.0 · Junio 2026</span>
          <span className="flex items-center gap-1.5">
            <Sparkle size={12} className="text-cyan-300" />
            v2.4 · Mayo 2026
          </span>
        </div>
      </aside>

      {/* Left (form) */}
      <main className="lg:w-1/2 flex items-center justify-center px-6 sm:px-10 py-10 lg:py-0 bg-white">
        <div className="w-full max-w-md anim-in">
          <h1 className="text-3xl lg:text-4xl font-bold text-navy-900 leading-tight">Bienvenido OPTIMA</h1>
          <p className="text-slate-500 mt-1.5">Accede a tu panel de gestión.</p>
          <p className="mt-2 text-sm text-slate-500">
            ¿Sin cuenta?{' '}
            <a href="index.html" className="font-semibold text-blue-600 hover:text-blue-700">Solicitar acceso</a>
          </p>

          <form onSubmit={submit} className="mt-7 space-y-4">
            <Field label="Negocio" icon={Building}>
              <input
                className={inputCls}
                type="text"
                autoComplete="organization"
                placeholder="slug-de-tu-negocio"
                value={form.negocio}
                onChange={(e) => setForm({ ...form, negocio: e.target.value })} />
              
            </Field>

            <Field label="Email" icon={Mail}>
              <input
                className={inputCls}
                type="email"
                autoComplete="email"
                placeholder="tu@email.com"
                value={form.email}
                onChange={(e) => setForm({ ...form, email: e.target.value })} />
              
            </Field>

            <Field label="Contraseña" icon={Lock}>
              <input
                className={`${inputCls} pr-12`}
                type={showPwd ? 'text' : 'password'}
                autoComplete="current-password"
                placeholder="Mín. 8 caracteres"
                value={form.password}
                onChange={(e) => setForm({ ...form, password: e.target.value })} />
              
              <button
                type="button"
                onClick={() => setShowPwd((s) => !s)}
                className="absolute inset-y-0 right-0 w-11 flex items-center justify-center text-slate-400 hover:text-navy-900 transition"
                aria-label={showPwd ? 'Ocultar contraseña' : 'Mostrar contraseña'}>
                
                {showPwd ? <EyeOff /> : <Eye />}
              </button>
            </Field>

            <div className="flex items-center justify-between pt-1">
              <label className="flex items-center gap-2 cursor-pointer select-none">
                <input
                  type="checkbox"
                  checked={form.remember}
                  onChange={(e) => setForm({ ...form, remember: e.target.checked })}
                  className="peer sr-only" />
                
                <span className="w-4 h-4 rounded border border-slate-300 peer-checked:bg-blue-500 peer-checked:border-blue-500 flex items-center justify-center transition">
                  {form.remember &&
                  <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
                      <path d="M5 12.5l4.5 4.5L19 7.5" />
                    </svg>
                  }
                </span>
                <span className="text-xs font-medium text-slate-600">Mantener sesión iniciada</span>
              </label>
              <a href="#" className="text-xs font-semibold text-blue-600 hover:text-blue-700">¿Olvidaste tu contraseña?</a>
            </div>

            {error &&
            <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 rounded-xl px-3 py-2">
                {error}
              </div>
            }

            <button
              type="submit"
              disabled={loading}
              className="w-full bg-navy-900 hover:bg-navy-800 disabled:opacity-70 text-white font-semibold py-3.5 rounded-2xl transition shadow-[0_10px_30px_-10px_rgba(17,26,46,0.6)] flex items-center justify-center gap-2">
              
              {loading ?
              <>
                  <span className="w-4 h-4 rounded-full border-2 border-white/40 border-t-white animate-spin" />
                  Iniciando…
                </> :

              <>Iniciar sesión</>
              }
            </button>

            {/* Demo credentials box */}
            <div className="rounded-2xl bg-blue-50 border border-blue-100 p-4 mt-2">
              <div className="flex items-center justify-between mb-2">
                <div className="flex items-center gap-2">
                  <span className="w-6 h-6 rounded-lg bg-gradient-to-br from-cyan-400 to-blue-500 text-white flex items-center justify-center">
                    <Sparkle size={12} className="text-white" />
                  </span>
                  <span className="text-xs font-bold text-blue-900 uppercase tracking-wider">Credenciales de demo</span>
                </div>
                <button type="button" onClick={fillDemo}
                className="text-[11px] font-semibold text-blue-600 hover:text-blue-700 bg-white px-2.5 py-1 rounded-lg border border-blue-100">
                  Autocompletar
                </button>
              </div>
              <div className="grid grid-cols-1 gap-1.5 text-xs">
                <div className="flex items-center justify-between">
                  <span className="text-blue-900/70">Negocio</span>
                  <code className="font-mono font-semibold text-blue-900 bg-white/70 px-2 py-0.5 rounded">{DEMO.negocio}</code>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-blue-900/70">Email</span>
                  <code className="font-mono font-semibold text-blue-900 bg-white/70 px-2 py-0.5 rounded">{DEMO.email}</code>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-blue-900/70">Contraseña</span>
                  <code className="font-mono font-semibold text-blue-900 bg-white/70 px-2 py-0.5 rounded">{DEMO.password}</code>
                </div>
              </div>
            </div>
          </form>

          <p className="mt-8 text-center text-xs text-slate-400">
            ¿Necesitas ayuda? <a href="#" className="text-blue-600 font-semibold hover:text-blue-700">Contacta con soporte</a>
          </p>
        </div>
      </main>
    </div>);

}

ReactDOM.createRoot(document.getElementById('root')).render(<Login />);
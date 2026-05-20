import { useState } from 'react'
import { useNavigate, Navigate, Link } from 'react-router-dom'
import { Eye, EyeOff, Lock, Mail, Building2, ArrowRight, CalendarDays, Users, Scissors } from 'lucide-react'
import { useAuth } from '@/context/AuthContext'
import { getErrorMessage } from '@/lib/api'
import { LogoMark } from '@/components/ui/LogoMark'

/** Caja de error en rojo, reutilizada por el formulario y el selector. */
function ErrorBox({ children }) {
  return (
    <div className="mt-4 rounded-2xl bg-red-50 border border-red-100 px-4 py-3 text-sm text-red-700 font-medium">
      {children}
    </div>
  )
}

const features = [
  { Icon: CalendarDays, text: 'Citas y calendario en un vistazo' },
  { Icon: Users,        text: 'Clientes y equipo bajo control' },
  { Icon: Scissors,     text: 'Catálogo de servicios flexible' },
]

const inputCls =
  'h-12 w-full rounded-2xl border border-slate-200 bg-slate-50 pl-11 pr-4 text-sm text-[#1f2c4a] ' +
  'placeholder:text-slate-400 transition-all focus:outline-none focus:bg-white focus:border-blue-400 focus:ring-4 focus:ring-blue-100'

/**
 * Login — acceso a la plataforma. Diseño responsive de dos zonas:
 *  - Una tarjeta de acceso centrada (visible siempre, también en móvil).
 *  - Un panel de marca lateral azul marino (solo en escritorio, lg+).
 *
 * Soporta el login multi-membership: si la cuenta pertenece a varios
 * negocios, la tarjeta muestra el selector de negocio en lugar del
 * formulario.
 */
export default function Login() {
  const { user, login, selectBusiness, loading, pendingBusinesses } = useAuth()
  const navigate = useNavigate()

  const [form, setForm] = useState({ email: '', password: '' })
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState('')

  if (user) return <Navigate to="/dashboard" replace />

  const handleChange = (e) => setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }))

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    if (!form.email || !form.password) {
      setError('Rellena email y contraseña para continuar.')
      return
    }
    try {
      const result = await login(form.email, form.password)
      if (result.type === 'tenant') navigate('/dashboard')
      // result.type === 'identity' → pendingBusinesses queda en el contexto
      // y el selector de negocio se renderiza en este mismo render.
    } catch (err) {
      // Mensaje del backend (anti-enumeration: el mismo 401 para email
      // inexistente, contraseña incorrecta o cuenta inactiva).
      setError(getErrorMessage(err, 'Error al iniciar sesión. Inténtalo de nuevo.'))
    }
  }

  const handleSelectBusiness = async (businessId) => {
    setError('')
    try {
      await selectBusiness(businessId)
      navigate('/dashboard')
    } catch (err) {
      setError(getErrorMessage(err, 'No se pudo acceder a ese negocio.'))
    }
  }

  return (
    <div className="min-h-screen flex bg-[#f4f7fe]">
      {/* ───────── Tarjeta de acceso ───────── */}
      <div className="flex flex-1 overflow-y-auto p-5 sm:p-8">
        <div className="w-full max-w-md m-auto">
          <div className="bg-white rounded-3xl border border-slate-100 shadow-[0_24px_70px_-24px_rgba(15,23,42,0.3)] p-7 sm:p-9">
            {/* Logo */}
            <div className="flex items-center gap-2.5 mb-7">
              <LogoMark size={42} />
              <div className="leading-none">
                <p className="text-lg font-extrabold text-[#1e3a5f] tracking-tight">OPTIMA</p>
                <p className="text-[10px] text-slate-400 tracking-[0.2em] font-semibold mt-1">STUDIO</p>
              </div>
            </div>

            {pendingBusinesses ? (
              /* ── Selector de negocio (login multi-membership) ── */
              <>
                <h1 className="text-2xl font-bold text-[#1e3a5f] tracking-tight">Selecciona negocio</h1>
                <p className="mt-1.5 text-sm text-slate-500">Tu cuenta tiene acceso a varios negocios.</p>
                <div className="mt-6 space-y-2.5">
                  {pendingBusinesses.map((b) => (
                    <button
                      key={b.businessId}
                      onClick={() => handleSelectBusiness(b.businessId)}
                      disabled={loading}
                      className="w-full flex items-center justify-between gap-3 rounded-2xl border border-slate-200 bg-white px-4 py-3.5 text-left hover:border-blue-300 hover:bg-blue-50/40 disabled:opacity-60 transition-all group"
                    >
                      <span className="flex items-center gap-3 min-w-0">
                        <span className="w-10 h-10 rounded-xl bg-gradient-to-br from-cyan-400 to-blue-500 flex items-center justify-center text-white shrink-0">
                          <Building2 size={17} />
                        </span>
                        <span className="min-w-0">
                          <span className="block font-semibold text-[#1e3a5f] text-sm truncate">{b.businessName}</span>
                          <span className="block text-xs text-slate-400 mt-0.5">{b.role}</span>
                        </span>
                      </span>
                      <ArrowRight size={16} className="text-slate-300 group-hover:text-blue-500 transition-colors shrink-0" />
                    </button>
                  ))}
                </div>
                {error && <ErrorBox>{error}</ErrorBox>}
              </>
            ) : (
              /* ── Formulario de acceso ── */
              <>
                <h1 className="text-2xl font-bold text-[#1e3a5f] tracking-tight">Bienvenido</h1>
                <p className="mt-1.5 text-sm text-slate-500">Accede al panel de gestión de tu negocio.</p>

                <form onSubmit={handleSubmit} className="mt-6 space-y-4">
                  <div className="flex flex-col gap-1.5">
                    <label className="text-xs font-semibold text-slate-500 uppercase tracking-wide">Email</label>
                    <div className="relative">
                      <Mail size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400" />
                      <input
                        name="email" type="email" value={form.email} onChange={handleChange}
                        placeholder="tu@email.com" className={inputCls} autoComplete="email"
                      />
                    </div>
                  </div>

                  <div className="flex flex-col gap-1.5">
                    <div className="flex items-center justify-between">
                      <label className="text-xs font-semibold text-slate-500 uppercase tracking-wide">Contraseña</label>
                      <Link to="/forgot-password" className="text-xs font-semibold text-blue-600 hover:text-blue-700 transition-colors">
                        ¿Olvidaste?
                      </Link>
                    </div>
                    <div className="relative">
                      <Lock size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400" />
                      <input
                        name="password" type={showPassword ? 'text' : 'password'} value={form.password}
                        onChange={handleChange} placeholder="••••••••" className={`${inputCls} pr-11`}
                        autoComplete="current-password"
                      />
                      <button
                        type="button" onClick={() => setShowPassword(!showPassword)}
                        className="absolute right-3.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 transition-colors"
                        aria-label={showPassword ? 'Ocultar contraseña' : 'Mostrar contraseña'}
                      >
                        {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                      </button>
                    </div>
                  </div>

                  {error && <ErrorBox>{error}</ErrorBox>}

                  <button
                    type="submit" disabled={loading}
                    className="w-full h-12 rounded-2xl bg-gradient-to-r from-cyan-500 to-blue-500 text-white font-semibold text-sm shadow-[0_12px_30px_-10px_rgba(14,165,233,0.6)] hover:brightness-105 disabled:opacity-60 disabled:pointer-events-none transition-all flex items-center justify-center gap-2"
                  >
                    {loading && (
                      <svg className="h-4 w-4 animate-spin" fill="none" viewBox="0 0 24 24">
                        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                        <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                      </svg>
                    )}
                    {loading ? 'Accediendo…' : 'Iniciar sesión'}
                  </button>
                </form>

                {/* Cuenta de demostración */}
                <div className="mt-5 rounded-2xl bg-blue-50/70 border border-blue-100 px-4 py-3.5">
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-[11px] font-bold text-blue-900 uppercase tracking-wider">Cuenta de demostración</span>
                    <button
                      type="button"
                      onClick={() => setForm({ email: 'admin@optima.com', password: '12345678' })}
                      className="text-[11px] font-semibold text-blue-600 hover:text-blue-700 bg-white px-2.5 py-1 rounded-lg border border-blue-100 transition-colors"
                    >
                      Autocompletar
                    </button>
                  </div>
                  <div className="space-y-1 text-xs">
                    <div className="flex items-center justify-between">
                      <span className="text-blue-900/55">Email</span>
                      <code className="font-mono font-semibold text-blue-900">admin@optima.com</code>
                    </div>
                    <div className="flex items-center justify-between">
                      <span className="text-blue-900/55">Contraseña</span>
                      <code className="font-mono font-semibold text-blue-900">12345678</code>
                    </div>
                  </div>
                </div>

                <p className="mt-5 text-center text-sm text-slate-500">
                  ¿No tienes cuenta?{' '}
                  <Link to="/register" className="font-semibold text-blue-600 hover:text-blue-700">
                    Crea tu negocio
                  </Link>
                </p>
              </>
            )}
          </div>

          <p className="text-center text-[11px] text-slate-400 mt-5">
            © 2026 OPTIMA · Plataforma SaaS de gestión de citas
          </p>
        </div>
      </div>

      {/* ───────── Panel de marca (solo escritorio) ───────── */}
      <div className="hidden lg:flex lg:w-[45%] relative bg-[#1e3a5f] flex-col justify-center overflow-hidden px-12 xl:px-16">
        {/* Manchas de gradiente de fondo */}
        <div className="absolute -top-32 -right-24 w-[460px] h-[460px] rounded-full bg-gradient-to-br from-cyan-400/25 to-blue-500/5 blur-3xl pointer-events-none" />
        <div className="absolute -bottom-40 -left-24 w-[420px] h-[420px] rounded-full bg-gradient-to-tr from-blue-500/20 to-cyan-300/5 blur-3xl pointer-events-none" />

        <div className="relative z-10 max-w-md">
          <div className="flex items-center gap-3 mb-12">
            <LogoMark size={46} />
            <span className="text-3xl font-extrabold text-white tracking-tight">OPTIMA</span>
          </div>
          <h2 className="text-4xl xl:text-5xl font-bold text-white leading-[1.15] tracking-tight">
            Gestiona tu negocio
            <span className="block bg-gradient-to-r from-cyan-300 to-blue-300 bg-clip-text text-transparent mt-1">
              con inteligencia
            </span>
          </h2>
          <p className="mt-5 text-base text-white/55 leading-relaxed">
            Clientes, citas, empleados y catálogo en una sola plataforma diseñada para negocios modernos.
          </p>
          <div className="mt-10 space-y-3">
            {features.map(({ Icon, text }) => (
              <div key={text} className="flex items-center gap-3">
                <div className="w-9 h-9 rounded-xl bg-white/8 border border-white/10 flex items-center justify-center text-cyan-300 shrink-0">
                  <Icon size={16} />
                </div>
                <span className="text-sm text-white/80 font-medium">{text}</span>
              </div>
            ))}
          </div>
        </div>

        <div className="absolute bottom-7 left-12 xl:left-16 text-[11px] text-white/25">
          © 2026 OPTIMA · Plataforma SaaS
        </div>
      </div>
    </div>
  )
}

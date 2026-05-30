// Pantalla de login con seleccion de negocio para usuarios multi-tenant
import { useEffect, useRef, useState } from 'react'
import { useNavigate, Navigate, Link } from 'react-router-dom'
import {
  Eye, EyeOff, Lock, Mail, Building2, ArrowRight, ArrowLeft,
  CalendarDays, Users, Scissors, AlertCircle, AlertTriangle, Shield, User,
} from 'lucide-react'
import { useAuth } from '@/context/AuthContext'
import { getErrorMessage } from '@/lib/api'
import { LogoMark } from '@/components/ui/LogoMark'

function ErrorBox({ children, icon: Icon = AlertCircle, variant = 'danger', title }) {
  const tones = {
    danger: 'bg-red-50 border-red-100 text-red-700',
    rate:   'bg-amber-50 border-amber-100 text-amber-800',
  }
  return (
    <div className={`mt-4 rounded-2xl border px-4 py-3 text-sm font-medium flex items-start gap-2 ${tones[variant]}`}>
      <Icon size={16} className="shrink-0 mt-0.5" />
      <div>
        {title && <p className="font-semibold">{title}</p>}
        <p className={title ? 'text-xs mt-0.5' : ''}>{children}</p>
      </div>
    </div>
  )
}

const features = [
  { Icon: CalendarDays, text: 'Citas y calendario en un vistazo' },
  { Icon: Users,        text: 'Clientes y equipo bajo control' },
  { Icon: Scissors,     text: 'Catálogo de servicios flexible' },
]

const ROLE_LABEL = { ADMIN: 'Administrador', EMPLOYEE: 'Empleado' }
const isEmail = (v) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(String(v || '').trim())

const inputBase =
  'h-12 w-full rounded-2xl border bg-slate-50 pl-11 pr-4 text-sm text-[#1f2c4a] placeholder:text-slate-400 ' +
  'transition-all focus:outline-none focus:bg-white focus:ring-4'
const inputOk = `${inputBase} border-slate-200 focus:border-blue-400 focus:ring-blue-100`
const inputErr = `${inputBase} border-amber-300 bg-white focus:border-amber-400 focus:ring-amber-100`

/** Pantalla de acceso con seleccion de negocio */
export default function Login() {
  const { user, login, selectBusiness, loading, pendingBusinesses } = useAuth()
  const navigate = useNavigate()
  const emailRef = useRef(null)

  // Recordar email entre sesiones (NUNCA la contrasena)
  const [form, setForm] = useState({
    email: localStorage.getItem('optima_last_email') || '',
    password: '',
  })
  const [showPassword, setShowPassword] = useState(false)
  const [capsLock, setCapsLock] = useState(false)
  const [emailBlurred, setEmailBlurred] = useState(false)
  const [error, setError] = useState('')
  const [rateLeft, setRateLeft] = useState(0) // segundos restantes si 429

  // autoFocus inteligente: email si esta vacio, password si no
  useEffect(() => {
    if (emailRef.current) {
      if (!form.email) emailRef.current.focus()
      else document.querySelector('input[name="password"]')?.focus()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // Countdown del 429
  useEffect(() => {
    if (rateLeft <= 0) return
    const t = setInterval(() => setRateLeft((s) => Math.max(0, s - 1)), 1000)
    return () => clearInterval(t)
  }, [rateLeft])

  if (user) return <Navigate to="/dashboard" replace />

  const handleChange = (e) => setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }))

  // Detecta Bloq Mayus mientras se escribe la contrasena
  const handlePwdKey = (e) => {
    if (typeof e.getModifierState === 'function') setCapsLock(e.getModifierState('CapsLock'))
  }

  const emailInvalid = emailBlurred && form.email.length > 0 && !isEmail(form.email)

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (rateLeft > 0) return
    setError('')
    if (!form.email || !form.password) {
      setError('Rellena email y contraseña para continuar.')
      return
    }
    if (!isEmail(form.email)) {
      setError('El email no tiene un formato válido.')
      return
    }
    try {
      const result = await login(form.email, form.password)
      // Guarda el email para proximas sesiones si el acceso fue correcto
      localStorage.setItem('optima_last_email', form.email)
      if (result.type === 'tenant') navigate('/dashboard')
    } catch (err) {
      // Si el servidor devuelve 429, el interceptor de apijs ya extrajo
      // retryAfter; lo usamos para mostrar countdown
      if (err?.isRateLimited && Number.isFinite(err.retryAfter) && err.retryAfter > 0) {
        setRateLeft(err.retryAfter)
        setError('')
      } else {
        setError(getErrorMessage(err, 'Error al iniciar sesión. Inténtalo de nuevo.'))
      }
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

  const handleBackToLogin = () => {
    // Limpia el acceso inicial y vuelve al formulario
    sessionStorage.removeItem('optima_identity_token')
    sessionStorage.removeItem('optima_pending_businesses')
    window.location.reload()
  }

  return (
    <div className="min-h-screen flex bg-[#f4f7fe]">
      <div className="flex flex-1 overflow-y-auto p-5 sm:p-8">
        <div className="w-full max-w-md m-auto">
          <div className="bg-white rounded-3xl border border-slate-100 shadow-[0_24px_70px_-24px_rgba(15,23,42,0.3)] p-7 sm:p-9">
            <div className="flex items-center gap-2.5 mb-7">
              <LogoMark size={42} />
              <div className="leading-none">
                <p className="text-lg font-extrabold text-[#1e3a5f] tracking-tight">OPTIMA</p>
                <p className="text-[10px] text-slate-400 tracking-[0.2em] font-semibold mt-1">STUDIO</p>
              </div>
            </div>

            {pendingBusinesses ? (
              <>
                <div className="flex items-center justify-between mb-1.5">
                  <h1 className="text-2xl font-bold text-[#1e3a5f] tracking-tight">Selecciona negocio</h1>
                  <button
                    onClick={handleBackToLogin}
                    title="Volver al inicio de sesión"
                    className="inline-flex items-center gap-1 text-xs font-semibold text-slate-500 hover:text-[#1e3a5f] transition"
                  >
                    <ArrowLeft size={12} /> Volver
                  </button>
                </div>
                <p className="text-sm text-slate-500">Tu cuenta tiene acceso a varios negocios.</p>
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
                          <span className="mt-1 inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wider bg-slate-100 text-slate-600">
                            {b.role === 'ADMIN' ? <Shield size={10} /> : <User size={10} />}
                            {ROLE_LABEL[b.role] || b.role}
                          </span>
                        </span>
                      </span>
                      <ArrowRight size={16} className="text-slate-300 group-hover:text-blue-500 transition-colors shrink-0" />
                    </button>
                  ))}
                </div>
                {error && <ErrorBox>{error}</ErrorBox>}
                <p className="mt-5 text-center text-sm text-slate-500">
                  ¿Quieres dar de alta otro negocio?{' '}
                  <Link to="/register" className="font-semibold text-blue-600 hover:text-blue-700">Registra uno nuevo</Link>
                </p>
              </>
            ) : (
              /* ── Formulario de acceso ── */
              <>
                <h1 className="text-2xl font-bold text-[#1e3a5f] tracking-tight">Bienvenido de nuevo</h1>
                <p className="mt-1.5 text-sm text-slate-500">Accede al panel de gestión de tu negocio.</p>

                <form onSubmit={handleSubmit} noValidate className="mt-6 space-y-4">
                  <div className="flex flex-col gap-1.5">
                    <label className="text-xs font-semibold text-slate-500 uppercase tracking-wide">Email</label>
                    <div className="relative">
                      <Mail size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400" />
                      <input
                        ref={emailRef}
                        name="email"
                        type="email"
                        value={form.email}
                        onChange={handleChange}
                        onBlur={() => setEmailBlurred(true)}
                        placeholder="tu@email.com"
                        maxLength={150}
                        className={emailInvalid ? inputErr : inputOk}
                        autoComplete="email"
                      />
                    </div>
                    {emailInvalid && (
                      <p className="text-[11px] mt-0.5 text-amber-600 inline-flex items-center gap-1">
                        <AlertCircle size={12} /> Formato de email no válido
                      </p>
                    )}
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
                        name="password"
                        type={showPassword ? 'text' : 'password'}
                        value={form.password}
                        onChange={handleChange}
                        onKeyDown={handlePwdKey}
                        onKeyUp={handlePwdKey}
                        onBlur={() => setCapsLock(false)}
                        placeholder="••••••••"
                        maxLength={100}
                        className={`${inputOk} pr-11`}
                        autoComplete="current-password"
                      />
                      <button
                        type="button" onClick={() => setShowPassword(!showPassword)}
                        className="absolute right-3.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 transition-colors"
                        aria-label={showPassword ? 'Ocultar contraseña' : 'Mostrar contraseña'}
                        tabIndex={-1}
                      >
                        {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                      </button>
                    </div>
                    {capsLock && (
                      <p className="text-[11px] mt-0.5 text-amber-600 inline-flex items-center gap-1">
                        <AlertTriangle size={12} /> Bloq Mayús activado
                      </p>
                    )}
                  </div>

                  {rateLeft > 0 ? (
                    <ErrorBox variant="rate" icon={AlertCircle} title="Demasiados intentos">
                      Vuelve a probar en <strong>{rateLeft}</strong> segundo{rateLeft === 1 ? '' : 's'}.
                    </ErrorBox>
                  ) : error ? (
                    <ErrorBox>{error}</ErrorBox>
                  ) : null}

                  <button
                    type="submit" disabled={loading || rateLeft > 0}
                    className="w-full h-12 rounded-2xl bg-gradient-to-r from-cyan-500 to-blue-500 text-white font-semibold text-sm shadow-[0_12px_30px_-10px_rgba(14,165,233,0.6)] hover:brightness-105 disabled:opacity-60 disabled:pointer-events-none transition-all flex items-center justify-center gap-2"
                  >
                    {loading && (
                      <svg className="h-4 w-4 animate-spin" fill="none" viewBox="0 0 24 24">
                        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                        <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                      </svg>
                    )}
                    {loading ? 'Accediendo…' : (rateLeft > 0 ? `Espera ${rateLeft}s…` : 'Iniciar sesión')}
                  </button>
                </form>

                <p className="mt-5 text-center text-sm text-slate-500">
                  ¿No tienes cuenta?{' '}
                  <Link to="/register" className="font-semibold text-blue-600 hover:text-blue-700">Crea tu negocio</Link>
                </p>
              </>
            )}
          </div>

          <p className="text-center text-[11px] text-slate-400 mt-5">
            © 2026 OPTIMA · Plataforma SaaS de gestión de citas
          </p>
        </div>
      </div>

      <div className="hidden lg:flex lg:w-[45%] relative bg-[#1e3a5f] flex-col justify-center overflow-hidden px-12 xl:px-16">
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

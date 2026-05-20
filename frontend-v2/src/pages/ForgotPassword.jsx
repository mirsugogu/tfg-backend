import { useEffect, useState } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { ArrowLeft, ArrowRight, Mail, MailCheck, AlertCircle } from 'lucide-react'
import { LogoMark } from '@/components/ui/LogoMark'
import { useAuth } from '@/context/AuthContext'
import api, { getErrorMessage } from '@/lib/api'

const isEmail = (v) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(String(v || '').trim())

const inputOk =
  'h-12 w-full rounded-2xl border border-slate-200 bg-slate-50 pl-11 pr-4 text-sm text-[#1f2c4a] placeholder:text-slate-400 ' +
  'transition-all focus:outline-none focus:bg-white focus:border-blue-400 focus:ring-4 focus:ring-blue-100'
const inputErr =
  'h-12 w-full rounded-2xl border border-amber-300 bg-white pl-11 pr-4 text-sm text-[#1f2c4a] placeholder:text-slate-400 ' +
  'transition-all focus:outline-none focus:border-amber-400 focus:ring-4 focus:ring-amber-100'

export default function ForgotPassword() {
  const { user } = useAuth()
  const [email, setEmail] = useState(() => localStorage.getItem('optima_last_email') || '')
  const [emailBlurred, setEmailBlurred] = useState(false)
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState(null) // { type: 'success' | 'error', text }
  const [rateLeft, setRateLeft] = useState(0)

  useEffect(() => {
    if (rateLeft <= 0) return
    const t = setInterval(() => setRateLeft((s) => Math.max(0, s - 1)), 1000)
    return () => clearInterval(t)
  }, [rateLeft])

  if (user) return <Navigate to="/dashboard" replace />

  const emailInvalid = emailBlurred && email.length > 0 && !isEmail(email)

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (rateLeft > 0) return
    setResult(null)
    const trimmed = email.trim()
    if (!trimmed) { setResult({ type: 'error', text: 'Introduce tu email.' }); return }
    if (!isEmail(trimmed)) { setResult({ type: 'error', text: 'Formato de email no válido.' }); return }
    setLoading(true)
    try {
      await api.post('/api/auth/forgot-password', { email: trimmed })
      // Anti-enumeration: el backend responde 204 exista o no el usuario.
      localStorage.setItem('optima_last_email', trimmed)
      setResult({
        type: 'success',
        text: 'Si existe una cuenta con ese email, te enviaremos un código para restablecer la contraseña. Revisa tu bandeja de entrada en los próximos minutos.',
      })
    } catch (err) {
      if (err?.isRateLimited && Number.isFinite(err.retryAfter) && err.retryAfter > 0) {
        setRateLeft(err.retryAfter)
        setResult(null)
      } else {
        setResult({ type: 'error', text: getErrorMessage(err, 'No se pudo enviar el email.') })
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-[#f4f7fe] flex items-center justify-center p-6">
      <div className="w-full max-w-md">
        <div className="flex items-center justify-center gap-3 mb-8">
          <LogoMark size={40} />
          <span className="text-2xl font-extrabold text-[#1e3a5f] tracking-tight">OPTIMA</span>
        </div>

        <div className="bg-white rounded-3xl border border-slate-100 shadow-[0_24px_70px_-24px_rgba(15,23,42,0.3)] p-8">
          <h2 className="text-2xl font-bold text-[#1e3a5f] tracking-tight">Recuperar contraseña</h2>
          <p className="mt-2 text-sm text-slate-500">Te enviaremos por email un código para crear una contraseña nueva.</p>

          <form onSubmit={handleSubmit} className="mt-6 space-y-4">
            <div className="flex flex-col gap-1.5">
              <label className="text-xs font-semibold text-slate-500 uppercase tracking-wide">Email</label>
              <div className="relative">
                <Mail size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400" />
                <input
                  autoFocus
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  onBlur={() => setEmailBlurred(true)}
                  placeholder="tu@email.com"
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

            {rateLeft > 0 && (
              <div className="rounded-2xl bg-amber-50 border border-amber-100 px-4 py-3 text-sm text-amber-800 font-medium flex items-start gap-2">
                <AlertCircle size={16} className="shrink-0 mt-0.5" />
                <div>
                  <p className="font-semibold">Demasiados intentos</p>
                  <p className="text-xs mt-0.5">Vuelve a probar en <strong>{rateLeft}</strong> segundo{rateLeft === 1 ? '' : 's'}.</p>
                </div>
              </div>
            )}

            {result && (
              <div
                className={
                  result.type === 'success'
                    ? 'rounded-2xl bg-emerald-50 border border-emerald-100 px-4 py-3 text-sm text-emerald-700 font-medium flex items-start gap-2'
                    : 'rounded-2xl bg-red-50 border border-red-100 px-4 py-3 text-sm text-red-700 font-medium'
                }
              >
                {result.type === 'success' && <MailCheck size={16} className="shrink-0 mt-0.5" />}
                <span>{result.text}</span>
              </div>
            )}

            {result?.type === 'success' && (
              <Link
                to="/reset-password"
                className="w-full h-12 rounded-2xl bg-gradient-to-r from-cyan-500 to-blue-500 text-white font-semibold text-sm shadow-[0_10px_30px_-10px_rgba(14,165,233,0.55)] hover:brightness-105 transition-all flex items-center justify-center gap-2"
              >
                Ya tengo el código, restablecer <ArrowRight size={16} />
              </Link>
            )}

            <button
              type="submit"
              disabled={loading || rateLeft > 0}
              className={`w-full h-12 rounded-2xl font-semibold text-sm transition-all flex items-center justify-center gap-2 ${
                result?.type === 'success'
                  ? 'bg-white border border-slate-200 text-slate-600 hover:bg-slate-50'
                  : 'bg-gradient-to-r from-cyan-500 to-blue-500 text-white shadow-[0_10px_30px_-10px_rgba(14,165,233,0.55)] hover:brightness-105'
              } disabled:opacity-60 disabled:pointer-events-none`}
            >
              {loading && (
                <svg className="h-4 w-4 animate-spin" fill="none" viewBox="0 0 24 24">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                </svg>
              )}
              {loading ? 'Enviando…' : rateLeft > 0 ? `Espera ${rateLeft}s…` : (result?.type === 'success' ? 'Enviar otro' : 'Enviar código')}
            </button>
          </form>

          <div className="mt-6 text-center">
            <Link to="/login" className="text-sm font-medium text-slate-500 hover:text-blue-600 inline-flex items-center gap-1.5 transition-colors">
              <ArrowLeft size={14} /> Volver al login
            </Link>
          </div>
        </div>

        <p className="mt-6 text-center text-[11px] text-slate-400">
          ¿Ya tienes el código?{' '}
          <Link to="/reset-password" className="font-semibold text-blue-600 hover:text-blue-700">Restablece tu contraseña</Link>
          {' '}· Límite: 10 intentos por hora.
        </p>
      </div>
    </div>
  )
}

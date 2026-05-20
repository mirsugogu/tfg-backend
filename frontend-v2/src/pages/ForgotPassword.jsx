import { useState } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { ArrowLeft, Mail, MailCheck } from 'lucide-react'
import { LogoMark } from '@/components/ui/LogoMark'
import { useAuth } from '@/context/AuthContext'
import api, { getErrorMessage } from '@/lib/api'

const inputCls =
  'h-12 w-full rounded-2xl border border-slate-200 bg-slate-50 pl-11 pr-4 text-sm text-[#1f2c4a] placeholder:text-slate-400 transition-all focus:outline-none focus:bg-white focus:border-blue-400 focus:ring-4 focus:ring-blue-100'

/**
 * ForgotPassword — solicita el restablecimiento de contraseña. El backend
 * envía por email un CÓDIGO (token de texto, válido 1 hora) que luego se
 * pega en la pantalla /reset-password.
 */
export default function ForgotPassword() {
  const { user } = useAuth()
  const [email, setEmail] = useState('')
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState(null) // { type: 'success' | 'error', text }

  if (user) return <Navigate to="/dashboard" replace />

  const handleSubmit = async (e) => {
    e.preventDefault()
    setResult(null)
    const trimmed = email.trim()
    if (!trimmed) {
      setResult({ type: 'error', text: 'Introduce tu email.' })
      return
    }
    setLoading(true)
    try {
      await api.post('/api/auth/forgot-password', { email: trimmed })
      // El backend responde 204 exista o no el usuario (anti-enumeration);
      // el mensaje es deliberadamente genérico.
      setResult({
        type: 'success',
        text: 'Si existe una cuenta con ese email, te enviaremos un código para restablecer la contraseña. Revisa tu bandeja de entrada en los próximos minutos.',
      })
      setEmail('')
    } catch (err) {
      setResult({ type: 'error', text: getErrorMessage(err, 'No se pudo enviar el email.') })
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-[#f4f7fe] flex items-center justify-center p-6">
      <div className="w-full max-w-md">
        {/* Marca */}
        <div className="flex items-center justify-center gap-3 mb-8">
          <LogoMark size={40} />
          <span className="text-2xl font-extrabold text-[#1e3a5f] tracking-tight">OPTIMA</span>
        </div>

        {/* Tarjeta */}
        <div className="bg-white rounded-3xl border border-slate-100 shadow-[0_24px_70px_-24px_rgba(15,23,42,0.3)] p-8">
          <h2 className="text-2xl font-bold text-[#1e3a5f] tracking-tight">Recuperar contraseña</h2>
          <p className="mt-2 text-sm text-slate-500">
            Te enviaremos por email un código para crear una contraseña nueva.
          </p>

          <form onSubmit={handleSubmit} className="mt-6 space-y-4">
            <div className="flex flex-col gap-1.5">
              <label className="text-xs font-semibold text-slate-500 uppercase tracking-wide">Email</label>
              <div className="relative">
                <Mail size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400" />
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="tu@email.com"
                  className={inputCls}
                  autoComplete="email"
                />
              </div>
            </div>

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

            <button
              type="submit"
              disabled={loading}
              className="w-full h-12 rounded-2xl bg-gradient-to-r from-cyan-500 to-blue-500 text-white font-semibold text-sm shadow-[0_10px_30px_-10px_rgba(14,165,233,0.55)] hover:brightness-105 disabled:opacity-60 disabled:pointer-events-none transition-all flex items-center justify-center gap-2 mt-2"
            >
              {loading && (
                <svg className="h-4 w-4 animate-spin" fill="none" viewBox="0 0 24 24">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                </svg>
              )}
              {loading ? 'Enviando…' : 'Enviar código'}
            </button>
          </form>

          <div className="mt-6 text-center">
            <Link
              to="/login"
              className="text-sm font-medium text-slate-500 hover:text-blue-600 inline-flex items-center gap-1.5 transition-colors"
            >
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

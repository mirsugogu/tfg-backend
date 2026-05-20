import { useState } from 'react'
import { Link, Navigate, useSearchParams } from 'react-router-dom'
import { ArrowLeft, CheckCircle2, Eye, EyeOff, Lock, KeyRound } from 'lucide-react'
import { LogoMark } from '@/components/ui/LogoMark'
import { useAuth } from '@/context/AuthContext'
import api, { getErrorMessage } from '@/lib/api'

const pwdInputCls =
  'h-12 w-full rounded-2xl border border-slate-200 bg-slate-50 pl-11 pr-11 text-sm text-[#1f2c4a] placeholder:text-slate-400 transition-all focus:outline-none focus:bg-white focus:border-blue-400 focus:ring-4 focus:ring-blue-100'
const tokenInputCls =
  'h-12 w-full rounded-2xl border border-slate-200 bg-slate-50 pl-11 pr-4 text-sm text-[#1f2c4a] placeholder:text-slate-400 transition-all focus:outline-none focus:bg-white focus:border-blue-400 focus:ring-4 focus:ring-blue-100'

/**
 * ResetPassword — fija una contraseña nueva usando el token de
 * recuperación.
 *
 * El email que envía el backend contiene el token como TEXTO (no un
 * enlace), así que esta pantalla acepta el token de dos formas:
 *  - pre-rellenado desde la URL (?token=...), por si en el futuro el
 *    correo incluyera un enlace;
 *  - pegado a mano en el campo "Código de recuperación".
 * Por eso ya NO se redirige fuera cuando falta el token en la URL.
 */
export default function ResetPassword() {
  const { user } = useAuth()
  const [params] = useSearchParams()

  const [token, setToken] = useState(params.get('token') ?? '')
  const [form, setForm] = useState({ newPassword: '', confirmPassword: '' })
  const [showPwd, setShowPwd] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState(false)

  if (user) return <Navigate to="/dashboard" replace />

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    if (!token.trim()) {
      setError('Introduce el código de recuperación que te llegó por email.')
      return
    }
    const { newPassword, confirmPassword } = form
    // Validación que refleja @Size(min=8, max=100) del ResetPasswordRequest.
    if (newPassword.length < 8) {
      setError('La nueva contraseña debe tener al menos 8 caracteres.')
      return
    }
    if (newPassword.length > 100) {
      setError('La nueva contraseña no puede superar los 100 caracteres.')
      return
    }
    if (newPassword !== confirmPassword) {
      setError('Las contraseñas no coinciden.')
      return
    }
    setLoading(true)
    try {
      await api.post('/api/auth/reset-password', { token: token.trim(), newPassword })
      setSuccess(true)
    } catch (err) {
      // Mensaje genérico del backend para "no válido o caducado".
      setError(getErrorMessage(err, 'No se pudo restablecer la contraseña. El código puede haber caducado.'))
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
          {success ? (
            <div className="text-center py-2">
              <div className="mx-auto w-14 h-14 rounded-2xl bg-emerald-100 flex items-center justify-center mb-4">
                <CheckCircle2 size={26} className="text-emerald-600" />
              </div>
              <h2 className="text-2xl font-bold text-[#1e3a5f] tracking-tight">Contraseña actualizada</h2>
              <p className="mt-2 text-sm text-slate-500">Ya puedes iniciar sesión con tu nueva contraseña.</p>
              <Link
                to="/login"
                className="mt-6 w-full inline-flex items-center justify-center h-12 rounded-2xl bg-gradient-to-r from-cyan-500 to-blue-500 text-white font-semibold text-sm shadow-[0_10px_30px_-10px_rgba(14,165,233,0.55)] hover:brightness-105 transition-all"
              >
                Ir al login
              </Link>
            </div>
          ) : (
            <>
              <h2 className="text-2xl font-bold text-[#1e3a5f] tracking-tight">Nueva contraseña</h2>
              <p className="mt-2 text-sm text-slate-500">
                Pega el código que te llegó por email y crea una contraseña nueva.
              </p>

              <form onSubmit={handleSubmit} className="mt-6 space-y-4">
                {/* Código de recuperación */}
                <div className="flex flex-col gap-1.5">
                  <label className="text-xs font-semibold text-slate-500 uppercase tracking-wide">Código de recuperación</label>
                  <div className="relative">
                    <KeyRound size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400" />
                    <input
                      type="text"
                      value={token}
                      onChange={(e) => setToken(e.target.value)}
                      placeholder="Pega aquí el código del email"
                      className={tokenInputCls}
                      autoComplete="off"
                    />
                  </div>
                </div>

                {/* Nueva contraseña */}
                <div className="flex flex-col gap-1.5">
                  <label className="text-xs font-semibold text-slate-500 uppercase tracking-wide">Nueva contraseña</label>
                  <div className="relative">
                    <Lock size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400" />
                    <input
                      type={showPwd ? 'text' : 'password'}
                      value={form.newPassword}
                      onChange={(e) => setForm((p) => ({ ...p, newPassword: e.target.value }))}
                      placeholder="••••••••"
                      className={pwdInputCls}
                      autoComplete="new-password"
                    />
                    <button
                      type="button"
                      onClick={() => setShowPwd(!showPwd)}
                      className="absolute right-3.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 transition-colors"
                      aria-label={showPwd ? 'Ocultar contraseña' : 'Mostrar contraseña'}
                    >
                      {showPwd ? <EyeOff size={16} /> : <Eye size={16} />}
                    </button>
                  </div>
                </div>

                {/* Confirmar contraseña */}
                <div className="flex flex-col gap-1.5">
                  <label className="text-xs font-semibold text-slate-500 uppercase tracking-wide">Confirmar contraseña</label>
                  <div className="relative">
                    <Lock size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400" />
                    <input
                      type={showPwd ? 'text' : 'password'}
                      value={form.confirmPassword}
                      onChange={(e) => setForm((p) => ({ ...p, confirmPassword: e.target.value }))}
                      placeholder="••••••••"
                      className={pwdInputCls}
                      autoComplete="new-password"
                    />
                  </div>
                </div>

                {error && (
                  <div className="rounded-2xl bg-red-50 border border-red-100 px-4 py-3 text-sm text-red-700 font-medium">
                    {error}
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
                  {loading ? 'Guardando…' : 'Guardar contraseña'}
                </button>
              </form>

              <div className="mt-6 text-center">
                <Link to="/login" className="text-sm font-medium text-slate-500 hover:text-blue-600 inline-flex items-center gap-1.5 transition-colors">
                  <ArrowLeft size={14} /> Volver al login
                </Link>
              </div>
            </>
          )}
        </div>

        {!success && (
          <p className="mt-6 text-center text-[11px] text-slate-400">
            ¿No tienes el código?{' '}
            <Link to="/forgot-password" className="font-semibold text-blue-600 hover:text-blue-700">Solicítalo de nuevo</Link>
            {' '}— caduca 1 hora después de pedirlo.
          </p>
        )}
      </div>
    </div>
  )
}

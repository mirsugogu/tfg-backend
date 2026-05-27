import { useEffect, useRef, useState } from 'react'
import { Link, Navigate, useSearchParams } from 'react-router-dom'
import {
  ArrowLeft, CheckCircle2, Eye, EyeOff, Lock, KeyRound,
  AlertCircle, AlertTriangle, Check, X, ClipboardPaste,
} from 'lucide-react'
import { LogoMark } from '@/components/ui/LogoMark'
import { useAuth } from '@/context/AuthContext'
import api, { getErrorMessage } from '@/lib/api'

const passwordStrength = (pwd) => {
  if (!pwd) return { score: 0, label: '—', cls: 'bg-slate-100', text: 'text-slate-400' }
  let score = 0
  if (pwd.length >= 8)  score++
  if (pwd.length >= 12) score++
  if (/[a-z]/.test(pwd) && /[A-Z]/.test(pwd)) score++
  if (/\d/.test(pwd)) score++
  if (/[^a-zA-Z0-9]/.test(pwd)) score++
  if (score > 4) score = 4
  const levels = [
    { label: 'Muy débil', cls: 'bg-rose-400',    text: 'text-rose-600' },
    { label: 'Débil',     cls: 'bg-orange-400',  text: 'text-orange-600' },
    { label: 'Aceptable', cls: 'bg-amber-400',   text: 'text-amber-600' },
    { label: 'Fuerte',    cls: 'bg-emerald-400', text: 'text-emerald-600' },
    { label: 'Muy fuerte',cls: 'bg-emerald-500', text: 'text-emerald-700' },
  ]
  return { score, ...levels[score] }
}

const pwdInputCls =
  'h-12 w-full rounded-2xl border border-slate-200 bg-slate-50 pl-11 pr-11 text-sm text-[#1f2c4a] placeholder:text-slate-400 ' +
  'transition-all focus:outline-none focus:bg-white focus:border-blue-400 focus:ring-4 focus:ring-blue-100'
const tokenInputCls =
  'h-12 w-full rounded-2xl border border-slate-200 bg-slate-50 pl-11 pr-12 text-sm text-[#1f2c4a] placeholder:text-slate-400 ' +
  'font-mono transition-all focus:outline-none focus:bg-white focus:border-blue-400 focus:ring-4 focus:ring-blue-100'

/**
 * Restablece la contraseña a partir del token efímero recibido por
 * email. El token caduca a la hora y queda invalidado al primer uso.
 */
export default function ResetPassword() {
  const { user } = useAuth()
  const [params] = useSearchParams()
  const tokenRef = useRef(null)

  const [token, setToken] = useState(params.get('token') ?? '')
  const [form, setForm] = useState({ newPassword: '', confirmPassword: '' })
  const [showNew, setShowNew] = useState(false)
  const [showConfirm, setShowConfirm] = useState(false)
  const [capsLock, setCapsLock] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [expired, setExpired] = useState(false)
  const [success, setSuccess] = useState(false)

  // autoFocus en el token si está vacío
  useEffect(() => {
    if (!token && tokenRef.current) tokenRef.current.focus()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  if (user) return <Navigate to="/dashboard" replace />

  const handlePwdKey = (e) => { if (typeof e.getModifierState === 'function') setCapsLock(e.getModifierState('CapsLock')) }

  const handlePaste = async () => {
    try {
      const txt = await navigator.clipboard.readText()
      if (txt) setToken(txt.trim())
    } catch {
      // El usuario tendrá que pegar a mano (Ctrl+V)
    }
  }

  const strength = passwordStrength(form.newPassword)
  const confirmMatch = form.confirmPassword.length > 0 && form.newPassword === form.confirmPassword
  const confirmMismatch = form.confirmPassword.length > 0 && form.newPassword !== form.confirmPassword
  const tokenTooShort = token.length > 0 && token.trim().length < 8

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError(''); setExpired(false)
    if (!token.trim()) { setError('Introduce el código de recuperación que te llegó por email.'); return }
    if (tokenTooShort) { setError('El código parece demasiado corto. Revisa que lo has pegado completo.'); return }
    const { newPassword, confirmPassword } = form
    if (newPassword.length < 8)   { setError('La nueva contraseña debe tener al menos 8 caracteres.'); return }
    if (newPassword.length > 100) { setError('La nueva contraseña no puede superar los 100 caracteres.'); return }
    if (newPassword !== confirmPassword) { setError('Las contraseñas no coinciden.'); return }
    setLoading(true)
    try {
      await api.post('/api/auth/reset-password', { token: token.trim(), newPassword })
      setSuccess(true)
    } catch (err) {
      const msg = getErrorMessage(err, 'No se pudo restablecer la contraseña.')
      const lower = msg.toLowerCase()
      // Detectamos mensaje genérico de "no válido o caducado" para mostrar
      // un banner más explicativo con CTA a /forgot-password.
      if (lower.includes('caduc') || lower.includes('expir') || lower.includes('inválid') || lower.includes('válid')) {
        setExpired(true); setError('')
      } else {
        setError(msg)
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
              <p className="mt-2 text-sm text-slate-500">Pega el código que te llegó por email y crea una contraseña nueva.</p>

              <form onSubmit={handleSubmit} className="mt-6 space-y-4">
                {/* Código */}
                <div className="flex flex-col gap-1.5">
                  <label className="text-xs font-semibold text-slate-500 uppercase tracking-wide">Código de recuperación</label>
                  <div className="relative">
                    <KeyRound size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400" />
                    <input
                      ref={tokenRef}
                      type="text"
                      value={token}
                      onChange={(e) => setToken(e.target.value)}
                      placeholder="Pega aquí el código del email"
                      className={tokenInputCls}
                      autoComplete="off"
                      spellCheck={false}
                    />
                    <button
                      type="button"
                      onClick={handlePaste}
                      tabIndex={-1}
                      title="Pegar del portapapeles"
                      className="absolute right-3 top-1/2 -translate-y-1/2 p-1.5 rounded-lg text-slate-400 hover:bg-slate-100 hover:text-slate-600 transition-colors"
                    >
                      <ClipboardPaste size={15} />
                    </button>
                  </div>
                  {tokenTooShort && (
                    <p className="text-[11px] mt-0.5 text-amber-600 inline-flex items-center gap-1">
                      <AlertCircle size={12} /> El código parece incompleto
                    </p>
                  )}
                </div>

                {/* Nueva contraseña */}
                <div className="flex flex-col gap-1.5">
                  <label className="text-xs font-semibold text-slate-500 uppercase tracking-wide">Nueva contraseña</label>
                  <div className="relative">
                    <Lock size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400" />
                    <input
                      type={showNew ? 'text' : 'password'}
                      value={form.newPassword}
                      onChange={(e) => setForm((p) => ({ ...p, newPassword: e.target.value }))}
                      onKeyDown={handlePwdKey}
                      onKeyUp={handlePwdKey}
                      onBlur={() => setCapsLock(false)}
                      placeholder="••••••••"
                      maxLength={100}
                      className={pwdInputCls}
                      autoComplete="new-password"
                    />
                    <button
                      type="button"
                      onClick={() => setShowNew(!showNew)}
                      tabIndex={-1}
                      className="absolute right-3.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 transition-colors"
                      aria-label={showNew ? 'Ocultar contraseña' : 'Mostrar contraseña'}
                    >
                      {showNew ? <EyeOff size={16} /> : <Eye size={16} />}
                    </button>
                  </div>
                  {capsLock && (
                    <p className="text-[11px] mt-0.5 text-amber-600 inline-flex items-center gap-1">
                      <AlertTriangle size={12} /> Bloq Mayús activado
                    </p>
                  )}
                  {form.newPassword && (
                    <div className="mt-1">
                      <div className="flex gap-1">
                        {[0, 1, 2, 3].map((i) => (
                          <div key={i} className={`h-1.5 flex-1 rounded-full transition ${i < strength.score ? strength.cls : 'bg-slate-100'}`} />
                        ))}
                      </div>
                      <p className={`text-[11px] mt-1 font-semibold ${strength.text}`}>Seguridad: {strength.label}</p>
                    </div>
                  )}
                </div>

                {/* Confirmar */}
                <div className="flex flex-col gap-1.5">
                  <label className="text-xs font-semibold text-slate-500 uppercase tracking-wide">Confirmar contraseña</label>
                  <div className="relative">
                    <Lock size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400" />
                    <input
                      type={showConfirm ? 'text' : 'password'}
                      value={form.confirmPassword}
                      onChange={(e) => setForm((p) => ({ ...p, confirmPassword: e.target.value }))}
                      placeholder="••••••••"
                      maxLength={100}
                      className={pwdInputCls}
                      autoComplete="new-password"
                    />
                    <button
                      type="button"
                      onClick={() => setShowConfirm(!showConfirm)}
                      tabIndex={-1}
                      className="absolute right-3.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 transition-colors"
                      aria-label={showConfirm ? 'Ocultar contraseña' : 'Mostrar contraseña'}
                    >
                      {showConfirm ? <EyeOff size={16} /> : <Eye size={16} />}
                    </button>
                  </div>
                  {confirmMatch && (
                    <p className="text-[11px] mt-0.5 font-semibold text-emerald-600 inline-flex items-center gap-1">
                      <Check size={12} /> Las contraseñas coinciden
                    </p>
                  )}
                  {confirmMismatch && (
                    <p className="text-[11px] mt-0.5 font-semibold text-rose-600 inline-flex items-center gap-1">
                      <X size={12} /> No coinciden
                    </p>
                  )}
                </div>

                {expired && (
                  <div className="rounded-2xl bg-amber-50 border border-amber-100 px-4 py-3 text-sm text-amber-800 font-medium">
                    <div className="flex items-start gap-2 mb-2">
                      <AlertCircle size={16} className="shrink-0 mt-0.5" />
                      <div>
                        <p className="font-semibold">Código no válido o caducado</p>
                        <p className="text-xs mt-0.5">Los códigos caducan 1 hora después de pedirlos y son de un solo uso.</p>
                      </div>
                    </div>
                    <Link
                      to="/forgot-password"
                      className="block text-center w-full h-9 rounded-xl bg-amber-500 hover:bg-amber-600 text-white text-xs font-semibold inline-flex items-center justify-center transition"
                    >
                      Solicitar un código nuevo
                    </Link>
                  </div>
                )}

                {error && (
                  <div className="rounded-2xl bg-red-50 border border-red-100 px-4 py-3 text-sm text-red-700 font-medium flex items-start gap-2">
                    <AlertCircle size={16} className="shrink-0 mt-0.5" />
                    <span>{error}</span>
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

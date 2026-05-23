import { Fragment, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, Navigate, Link } from 'react-router-dom'
import {
  Building2, UserCircle, MapPin, ArrowLeft, ArrowRight, Check,
  AlertCircle, AlertTriangle, Eye, EyeOff, Info, Lock,
} from 'lucide-react'
import { Input, INPUT_SANITIZE } from '@/components/ui/Input'
import { Button } from '@/components/ui/Button'
import { LogoMark } from '@/components/ui/LogoMark'
import { LocationMap } from '@/components/ui/LocationMap'
import { useAuth } from '@/context/AuthContext'
import { getErrorMessage } from '@/lib/api'

/* ============================================================
   HELPERS
   ============================================================ */

const slugify = (s) =>
  s.toLowerCase()
    .normalize('NFD').replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')

const SLUG_RE = /^[a-z0-9](?:[a-z0-9-]{1,58}[a-z0-9])?$/
const isEmail = (v) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(String(v || '').trim())

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

const STORAGE_KEY = 'optima_register_draft'
const EMPTY_FORM = {
  bizName: '', bizSlug: '', bizEmail: '', bizPhone: '',
  bizAddress: '', bizCity: '', bizPostalCode: '',
  adminName: '', adminEmail: '', adminPassword: '', adminPhone: '',
  terms: false,
}

/* ============================================================
   REGISTER
   ============================================================ */

export default function Register() {
  const { user, register, loading } = useAuth()
  const navigate = useNavigate()

  // Recupera draft (sin contraseña) tras una recarga accidental
  const [form, setForm] = useState(() => {
    try {
      const raw = sessionStorage.getItem(STORAGE_KEY)
      if (raw) return { ...EMPTY_FORM, ...JSON.parse(raw), adminPassword: '' }
    } catch { /* ignore */ }
    return EMPTY_FORM
  })
  useEffect(() => {
    // Persistimos todo menos la contraseña
    const { adminPassword: _omit, ...persistable } = form
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(persistable))
  }, [form])

  const [step, setStep] = useState(0) // 0 negocio · 1 ubicación · 2 cuenta
  const [slugTouched, setSlugTouched] = useState(false)
  const [showPassword, setShowPassword] = useState(false)
  const [capsLock, setCapsLock] = useState(false)
  const [error, setError] = useState('')
  const [coords, setCoords] = useState(null)
  const [geo, setGeo] = useState('idle')

  const set = (k, v) => setForm((p) => ({ ...p, [k]: v }))
  const handleName = (v) => setForm((p) => ({ ...p, bizName: v, bizSlug: slugTouched ? p.bizSlug : slugify(v) }))

  /* ---- Geocoder Nominatim (mismo de antes) ---- */
  useEffect(() => {
    if (step !== 1) return
    const query = [form.bizAddress, form.bizPostalCode, form.bizCity].map((s) => s.trim()).filter(Boolean).join(', ')
    if (query.length < 6) { setCoords(null); setGeo('idle'); return }
    setGeo('searching')
    const timer = setTimeout(async () => {
      try {
        const res = await fetch(`https://nominatim.openstreetmap.org/search?format=json&limit=1&q=${encodeURIComponent(query)}`)
        const data = await res.json()
        if (data && data[0]) { setCoords({ lat: parseFloat(data[0].lat), lon: parseFloat(data[0].lon) }); setGeo('found') }
        else { setCoords(null); setGeo('notfound') }
      } catch { setCoords(null); setGeo('notfound') }
    }, 800)
    return () => clearTimeout(timer)
  }, [step, form.bizAddress, form.bizCity, form.bizPostalCode])

  if (user) return <Navigate to="/dashboard" replace />

  /* ---- Validaciones por paso ---- */
  const slugInvalid = form.bizSlug.length > 0 && !SLUG_RE.test(form.bizSlug)
  const bizEmailInvalid = form.bizEmail.length > 0 && !isEmail(form.bizEmail)
  const adminEmailInvalid = form.adminEmail.length > 0 && !isEmail(form.adminEmail)

  const step0Valid = form.bizName.trim() && form.bizSlug.trim() && !slugInvalid && form.bizEmail.trim() && !bizEmailInvalid
  const step2Valid = form.adminName.trim() && form.adminEmail.trim() && !adminEmailInvalid && form.adminPassword.length >= 8 && form.terms

  const next = () => {
    setError('')
    if (step === 0) {
      if (!form.bizName.trim()) { setError('El nombre del negocio es obligatorio.'); return }
      if (!form.bizSlug.trim()) { setError('El identificador (slug) es obligatorio.'); return }
      if (slugInvalid)          { setError('El identificador solo puede contener minúsculas, números y guiones.'); return }
      if (!form.bizEmail.trim()) { setError('El email del negocio es obligatorio.'); return }
      if (bizEmailInvalid)      { setError('Email del negocio inválido.'); return }
    }
    setStep((s) => Math.min(2, s + 1))
  }
  const back = () => { setError(''); setStep((s) => Math.max(0, s - 1)) }

  const handlePwdKey = (e) => { if (typeof e.getModifierState === 'function') setCapsLock(e.getModifierState('CapsLock')) }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    if (!step2Valid) {
      if (!form.adminName.trim())  return setError('Tu nombre es obligatorio.')
      if (!form.adminEmail.trim()) return setError('Tu email es obligatorio.')
      if (adminEmailInvalid)       return setError('Tu email no tiene un formato válido.')
      if (form.adminPassword.length < 8) return setError('La contraseña debe tener al menos 8 caracteres.')
      if (!form.terms)             return setError('Debes aceptar los términos y condiciones.')
      return
    }
    try {
      await register({
        business: {
          name: form.bizName, slug: form.bizSlug, email: form.bizEmail,
          phone: form.bizPhone || null,
          address: form.bizAddress || null, city: form.bizCity || null, postalCode: form.bizPostalCode || null,
        },
        admin: {
          fullName: form.adminName, email: form.adminEmail,
          password: form.adminPassword, phone: form.adminPhone || null,
        },
      })
      sessionStorage.removeItem(STORAGE_KEY)
      navigate('/dashboard')
    } catch (err) {
      // El backend devuelve 409 si el slug o el email ya existen.
      const msg = getErrorMessage(err, 'No se pudo completar el registro. Inténtalo de nuevo.')
      const lower = msg.toLowerCase()
      if (lower.includes('slug')) { setStep(0); setError(`${msg} — revisa el identificador del negocio.`) }
      else if (lower.includes('email')) {
        // No sabemos seguro de cuál, pero el biz email se valida primero
        setStep(lower.includes('admin') || lower.includes('usuario') ? 2 : 0)
        setError(msg)
      } else {
        setError(msg)
      }
    }
  }

  const useBizEmail = () => set('adminEmail', form.bizEmail)
  const strength = passwordStrength(form.adminPassword)

  const stepLabels = ['Datos del negocio', 'Ubicación del negocio', 'Tu cuenta de administrador']

  return (
    <div className="min-h-screen bg-[#f4f7fe] flex overflow-y-auto p-5 sm:p-8">
      <div className="w-full max-w-lg m-auto">
        <div className="bg-white rounded-3xl border border-slate-100 shadow-[0_24px_70px_-24px_rgba(15,23,42,0.3)] p-7 sm:p-9">
          <div className="flex items-center gap-2.5 mb-5">
            <LogoMark size={42} />
            <div className="leading-none">
              <p className="text-lg font-extrabold text-[#1e3a5f] tracking-tight">OPTIMA</p>
              <p className="text-[10px] text-slate-400 tracking-[0.2em] font-semibold mt-1">STUDIO</p>
            </div>
          </div>

          <h1 className="text-2xl font-bold text-[#1e3a5f] tracking-tight">Crea tu cuenta</h1>
          <p className="mt-1.5 text-sm text-slate-500">Paso {step + 1} de 3 · {stepLabels[step]}</p>

          {/* Stepper */}
          <Stepper step={step} />

          <form onSubmit={handleSubmit} className="space-y-6">

            {/* Paso 0: Negocio */}
            {step === 0 && (
              <div>
                <SectionHeader icon={Building2}>Datos del negocio</SectionHeader>
                <div className="space-y-3">
                  <div>
                    <Input
                      label="Nombre del negocio *"
                      value={form.bizName}
                      onChange={(e) => handleName(e.target.value)}
                      placeholder="Peluquería Eva"
                      maxLength={80}
                    />
                    <CharCount value={form.bizName} max={80} />
                  </div>
                  <div>
                    <Input
                      label="Identificador (slug) *"
                      value={form.bizSlug}
                      onChange={(e) => { setSlugTouched(true); set('bizSlug', e.target.value) }}
                      placeholder="peluqueria-eva"
                      maxLength={60}
                    />
                    {slugInvalid ? (
                      <p className="text-[11px] mt-1 text-amber-600 inline-flex items-center gap-1">
                        <AlertCircle size={12} /> Solo minúsculas, números y guiones. Mínimo 1 carácter.
                      </p>
                    ) : (
                      <p className="text-[11px] text-slate-400 mt-1">
                        Tu URL pública será <code className="font-mono font-semibold text-blue-700">optima.com/b/{form.bizSlug || 'tu-negocio'}</code>
                      </p>
                    )}
                  </div>
                  <div className="grid sm:grid-cols-2 gap-3">
                    <div>
                      <Input
                        label="Email del negocio *"
                        type="email"
                        value={form.bizEmail}
                        onChange={(e) => set('bizEmail', e.target.value)}
                        placeholder="negocio@email.com"
                        maxLength={150}
                      />
                      {bizEmailInvalid && (
                        <p className="text-[11px] mt-1 text-amber-600 inline-flex items-center gap-1">
                          <AlertCircle size={12} /> Formato inválido
                        </p>
                      )}
                    </div>
                    <Input label="Teléfono del negocio" value={form.bizPhone} onChange={(e) => set('bizPhone', e.target.value)} placeholder="600 000 000" sanitize={INPUT_SANITIZE.PHONE} inputMode="tel" maxLength={20} />
                  </div>
                </div>
              </div>
            )}

            {/* Paso 1: Ubicación */}
            {step === 1 && (
              <div>
                <SectionHeader icon={MapPin}>Ubicación del negocio</SectionHeader>
                <div className="space-y-3">
                  <Input label="Dirección" value={form.bizAddress} onChange={(e) => set('bizAddress', e.target.value)} placeholder="Calle Mayor 5" maxLength={255} />
                  <div className="grid sm:grid-cols-2 gap-3">
                    <Input label="Ciudad" value={form.bizCity} onChange={(e) => set('bizCity', e.target.value)} placeholder="Madrid" maxLength={100} />
                    <Input label="Código postal" value={form.bizPostalCode} onChange={(e) => set('bizPostalCode', e.target.value)} placeholder="28013" sanitize={INPUT_SANITIZE.DIGITS_ONLY} inputMode="numeric" maxLength={10} />
                  </div>
                  {coords ? (
                    <LocationMap lat={coords.lat} lon={coords.lon} />
                  ) : (
                    <div className="h-56 w-full rounded-2xl border border-dashed border-slate-200 bg-gradient-to-br from-blue-50 to-cyan-50 flex items-center justify-center px-6 text-center">
                      <div>
                        <div className="w-12 h-12 mx-auto rounded-2xl bg-white shadow-sm flex items-center justify-center mb-2">
                          <MapPin size={20} className="text-blue-500" />
                        </div>
                        <p className="text-xs text-slate-600 font-semibold">
                          {geo === 'searching' ? 'Buscando dirección…' : geo === 'notfound' ? 'Dirección no encontrada' : 'Vista previa del mapa'}
                        </p>
                        <p className="text-[11px] text-slate-400 mt-0.5">
                          {geo === 'notfound' ? 'Revisa los datos o ajústalos.' : 'Completa la dirección para ver la ubicación.'}
                        </p>
                      </div>
                    </div>
                  )}
                  <p className="text-[11px] text-slate-400">El mapa es solo una vista previa. La dirección es opcional: puedes completarla después.</p>
                </div>

                <SummaryPreview form={form} />
              </div>
            )}

            {/* Paso 2: Cuenta admin */}
            {step === 2 && (
              <div>
                <SectionHeader icon={UserCircle}>Tu cuenta de administrador</SectionHeader>
                <div className="space-y-3">
                  <Input label="Nombre completo *" value={form.adminName} onChange={(e) => set('adminName', e.target.value)} placeholder="Eva García" maxLength={150} />
                  <div className="grid sm:grid-cols-2 gap-3">
                    <div>
                      <div className="flex items-center justify-between mb-1.5">
                        <label className="text-xs font-semibold text-slate-500">Tu email *</label>
                        {form.bizEmail && form.adminEmail !== form.bizEmail && (
                          <button type="button" onClick={useBizEmail} className="text-[11px] font-semibold text-blue-600 hover:text-blue-700">
                            Usar el mismo del negocio
                          </button>
                        )}
                      </div>
                      <input
                        type="email"
                        value={form.adminEmail}
                        onChange={(e) => set('adminEmail', e.target.value)}
                        placeholder="tu@email.com"
                        maxLength={150}
                        className={`h-11 w-full rounded-xl border bg-white px-3.5 text-sm text-[#1e3a5f] focus:outline-none focus:ring-4 ${adminEmailInvalid ? 'border-amber-300 focus:border-amber-400 focus:ring-amber-100' : 'border-slate-200 focus:border-blue-400 focus:ring-blue-100'}`}
                      />
                      {adminEmailInvalid && (
                        <p className="text-[11px] mt-1 text-amber-600 inline-flex items-center gap-1">
                          <AlertCircle size={12} /> Formato inválido
                        </p>
                      )}
                    </div>
                    <Input label="Tu teléfono" value={form.adminPhone} onChange={(e) => set('adminPhone', e.target.value)} placeholder="600 000 000" maxLength={20} />
                  </div>
                  <div>
                    <label className="text-xs font-semibold text-slate-500 block mb-1.5">Contraseña *</label>
                    <div className="relative">
                      <input
                        type={showPassword ? 'text' : 'password'}
                        value={form.adminPassword}
                        onChange={(e) => set('adminPassword', e.target.value)}
                        onKeyDown={handlePwdKey}
                        onKeyUp={handlePwdKey}
                        onBlur={() => setCapsLock(false)}
                        placeholder="Mínimo 8 caracteres"
                        autoComplete="new-password"
                        maxLength={100}
                        className="h-11 w-full rounded-xl border border-slate-200 bg-white pl-3.5 pr-10 text-sm text-[#1e3a5f] focus:outline-none focus:border-blue-400 focus:ring-4 focus:ring-blue-100"
                      />
                      <button type="button" onClick={() => setShowPassword(!showPassword)} tabIndex={-1}
                        className="absolute right-2 top-1/2 -translate-y-1/2 p-1.5 rounded-lg text-slate-400 hover:bg-slate-100">
                        {showPassword ? <EyeOff size={15} /> : <Eye size={15} />}
                      </button>
                    </div>
                    {capsLock && (
                      <p className="text-[11px] mt-1 text-amber-600 inline-flex items-center gap-1">
                        <AlertTriangle size={12} /> Bloq Mayús activado
                      </p>
                    )}
                    {form.adminPassword && (
                      <div className="mt-2">
                        <div className="flex gap-1">
                          {[0, 1, 2, 3].map((i) => (
                            <div key={i} className={`h-1.5 flex-1 rounded-full transition ${i < strength.score ? strength.cls : 'bg-slate-100'}`} />
                          ))}
                        </div>
                        <p className={`text-[11px] mt-1 font-semibold ${strength.text}`}>Seguridad: {strength.label}</p>
                      </div>
                    )}
                  </div>

                  <label className="flex items-start gap-2.5 text-xs text-slate-600 cursor-pointer pt-1">
                    <input
                      type="checkbox"
                      checked={form.terms}
                      onChange={(e) => set('terms', e.target.checked)}
                      className="accent-[#1e3a5f] h-4 w-4 rounded mt-0.5"
                    />
                    <span>
                      Acepto los <a href="#" onClick={(e) => e.preventDefault()} className="font-semibold text-blue-600 hover:text-blue-700">términos y condiciones</a> y la{' '}
                      <a href="#" onClick={(e) => e.preventDefault()} className="font-semibold text-blue-600 hover:text-blue-700">política de privacidad</a>.
                    </span>
                  </label>
                </div>

                <SummaryPreview form={form} />
              </div>
            )}

            {error && (
              <div className="rounded-2xl bg-red-50 border border-red-100 px-4 py-3 text-sm text-red-700 font-medium flex items-start gap-2">
                <AlertCircle size={16} className="shrink-0 mt-0.5" />
                <span>{error}</span>
              </div>
            )}

            {/* Navegación */}
            <div className="flex gap-2">
              {step > 0 && (
                <button type="button" onClick={back} className="flex-1 h-11 rounded-xl border border-slate-200 text-sm font-semibold text-slate-600 hover:bg-slate-50 inline-flex items-center justify-center gap-2">
                  <ArrowLeft size={16} /> Atrás
                </button>
              )}
              {step < 2 ? (
                <button
                  type="button"
                  onClick={next}
                  disabled={step === 0 && !step0Valid}
                  className="flex-[2] h-11 rounded-xl bg-gradient-to-r from-cyan-500 to-blue-500 text-white font-semibold text-sm shadow-[0_8px_20px_-6px_rgba(14,165,233,0.5)] disabled:opacity-60 disabled:pointer-events-none inline-flex items-center justify-center gap-2 hover:brightness-105 transition"
                >
                  Siguiente <ArrowRight size={16} />
                </button>
              ) : (
                <Button type="submit" loading={loading} size="lg" disabled={!step2Valid} className="flex-[2]">
                  Crear cuenta y empezar
                </Button>
              )}
            </div>
          </form>

          <p className="mt-5 text-center text-sm text-slate-500">
            ¿Ya tienes cuenta?{' '}
            <Link to="/login" className="font-semibold text-blue-600 hover:text-blue-700">Inicia sesión</Link>
          </p>
        </div>
        <p className="text-center text-[11px] text-slate-400 mt-5">© 2026 OPTIMA · Plataforma SaaS de gestión de citas</p>
      </div>
    </div>
  )
}

/* ============================================================
   SUBCOMPONENTES
   ============================================================ */

function Stepper({ step }) {
  const items = [
    { idx: 0, label: 'Negocio' },
    { idx: 1, label: 'Ubicación' },
    { idx: 2, label: 'Tu cuenta' },
  ]
  return (
    <div className="flex items-center gap-2 mt-5 mb-6">
      {items.map((it, i) => (
        <Fragment key={it.label}>
          <div className="flex-1 flex items-center gap-2 min-w-0">
            <div className={`w-7 h-7 rounded-full flex items-center justify-center text-xs font-bold shrink-0 ${
              step > it.idx ? 'bg-emerald-500 text-white' :
              step === it.idx ? 'bg-gradient-to-br from-cyan-400 to-blue-500 text-white' :
              'bg-slate-100 text-slate-400'
            }`}>
              {step > it.idx ? <Check size={14} /> : it.idx + 1}
            </div>
            <span className={`text-xs font-semibold truncate ${
              step > it.idx ? 'text-emerald-700' :
              step === it.idx ? 'text-[#1e3a5f]' :
              'text-slate-400'
            }`}>{it.label}</span>
          </div>
          {i < items.length - 1 && (
            <div className={`h-0.5 w-6 shrink-0 ${step > i ? 'bg-blue-300' : 'bg-slate-200'}`} />
          )}
        </Fragment>
      ))}
    </div>
  )
}

function SectionHeader({ icon: Icon, children }) {
  return (
    <div className="flex items-center gap-2 mb-3">
      <Icon size={15} className="text-blue-500" />
      <h2 className="text-xs font-bold text-slate-500 uppercase tracking-wide">{children}</h2>
    </div>
  )
}

function CharCount({ value, max }) {
  if (!value) return null
  const pct = Math.round((value.length / max) * 100)
  const tone = pct > 90 ? 'text-amber-600' : 'text-slate-400'
  return <p className={`text-[10px] mt-0.5 text-right tabular-nums ${tone}`}>{value.length} / {max}</p>
}

function SummaryPreview({ form }) {
  if (!form.bizName) return null
  return (
    <div className="rounded-2xl bg-blue-50/60 border border-blue-100 px-4 py-3 mt-5 text-xs text-blue-900">
      <p className="font-bold uppercase tracking-wider text-[10px] mb-1.5 inline-flex items-center gap-1">
        <Info size={11} /> Resumen
      </p>
      <p>
        <strong>{form.bizName}</strong>
        {form.bizSlug && <> · <code className="font-mono text-blue-700">{form.bizSlug}</code></>}
        {form.bizEmail && <> · {form.bizEmail}</>}
      </p>
      {form.bizSlug && (
        <p className="mt-0.5">URL pública: <code className="font-mono text-blue-700">optima.com/b/{form.bizSlug}</code></p>
      )}
      {(form.bizCity || form.bizAddress) && (
        <p className="mt-0.5">{[form.bizAddress, form.bizCity, form.bizPostalCode].filter(Boolean).join(', ')}</p>
      )}
    </div>
  )
}

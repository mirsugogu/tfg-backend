import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  UserCircle, Mail, Shield, Lock, Save, KeyRound, Calendar, Phone,
  Eye, EyeOff, Check, X, RefreshCw, Building2, AlertCircle, ArrowRight,
} from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Input, INPUT_SANITIZE } from '@/components/ui/Input'
import { Badge } from '@/components/ui/Badge'
import { Modal } from '@/components/ui/Modal'
import { useAuth } from '@/context/AuthContext'
import { useToast } from '@/components/ui/Toast'
import api, { getErrorMessage } from '@/lib/api'

const CARD = 'bg-white rounded-2xl border border-slate-100/80 shadow-[0_2px_12px_-2px_rgba(15,23,42,0.06)]'
const ROLE_LABEL = { ADMIN: 'Administrador', EMPLOYEE: 'Empleado' }

const isEmail = (v) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(String(v || '').trim())

// Fuerza de contraseña: 0-4 según longitud + variedad
const passwordStrength = (pwd) => {
  if (!pwd) return { score: 0, label: '—', cls: 'bg-slate-100', text: 'text-slate-400' }
  let score = 0
  if (pwd.length >= 8) score++
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

const ROLE_PALETTE = {
  ADMIN:    'from-cyan-400 to-blue-500',
  EMPLOYEE: 'from-emerald-400 to-teal-500',
}

export default function Perfil() {
  const { user, applyProfile, switchBusiness } = useAuth()
  const toast = useToast()

  const [profile, setProfile] = useState({
    fullName: user?.fullName || '',
    email:    user?.email || '',
    phone:    user?.phone || '',
  })
  const [me, setMe] = useState(null)
  const [reload, setReload] = useState(0)
  const [savingProfile, setSavingProfile] = useState(false)
  const refresh = () => setReload((v) => v + 1)

  const [pwd, setPwd] = useState({ currentPassword: '', newPassword: '', confirmPassword: '' })
  const [savingPwd, setSavingPwd] = useState(false)
  const [showCurrent, setShowCurrent] = useState(false)
  const [showNew, setShowNew]         = useState(false)
  const [showConfirm, setShowConfirm] = useState(false)

  const [businesses, setBusinesses] = useState(null)
  const [confirmEmail, setConfirmEmail] = useState(false)
  const [switching, setSwitching] = useState(null)

  useEffect(() => {
    api.get('/api/me')
      .then((r) => {
        setMe(r.data)
        setProfile({
          fullName: r.data.fullName || '',
          email:    r.data.email || '',
          phone:    r.data.phone || '',
        })
      })
      .catch((err) => toast({ type: 'error', message: getErrorMessage(err, 'No se pudo cargar tu perfil.') }))
  }, [toast, reload])

  useEffect(() => {
    api.get('/api/me/businesses')
      .then((r) => setBusinesses(Array.isArray(r.data) ? r.data : (r.data?.content ?? [])))
      .catch(() => setBusinesses([]))
  }, [reload])

  // ¿Hay cambios respecto a los datos cargados? Para deshabilitar "Guardar".
  const dirty = useMemo(() => {
    if (!me) return false
    return (
      profile.fullName !== (me.fullName || '') ||
      profile.email    !== (me.email || '') ||
      (profile.phone || '') !== (me.phone || '')
    )
  }, [profile, me])

  const emailChanged = me && profile.email.trim() !== (me.email || '').trim()

  const doProfileSave = async () => {
    if (!profile.fullName.trim()) { toast({ type: 'error', message: 'El nombre es obligatorio.' }); return }
    if (!profile.email.trim())    { toast({ type: 'error', message: 'El email es obligatorio.' }); return }
    if (!isEmail(profile.email))  { toast({ type: 'error', message: 'Formato de email no válido.' }); return }
    setSavingProfile(true)
    try {
      const { data } = await api.put('/api/me', {
        fullName: profile.fullName,
        email:    profile.email,
        phone:    profile.phone || null,
      })
      setMe(data)
      applyProfile(data)
      toast({ type: 'success', message: 'Perfil actualizado.' })
      setConfirmEmail(false)
    } catch (err) {
      toast({ type: 'error', message: getErrorMessage(err, 'No se pudo actualizar el perfil.') })
    } finally { setSavingProfile(false) }
  }

  const handleProfileSave = () => {
    // Si cambia el email, confirmar antes (login + recuperación dependen de él).
    if (emailChanged) setConfirmEmail(true)
    else doProfileSave()
  }

  const strength = passwordStrength(pwd.newPassword)
  const confirmMatch = pwd.confirmPassword.length > 0 && pwd.newPassword === pwd.confirmPassword
  const confirmMismatch = pwd.confirmPassword.length > 0 && pwd.newPassword !== pwd.confirmPassword

  const handlePasswordSave = async () => {
    if (!pwd.currentPassword) {
      toast({ type: 'error', message: 'Introduce tu contraseña actual.' }); return
    }
    if (pwd.newPassword.length < 8) {
      toast({ type: 'error', message: 'La nueva contraseña debe tener al menos 8 caracteres.' }); return
    }
    if (pwd.newPassword !== pwd.confirmPassword) {
      toast({ type: 'error', message: 'La confirmación de la contraseña no coincide.' }); return
    }
    setSavingPwd(true)
    try {
      await api.put('/api/me/password', {
        currentPassword: pwd.currentPassword,
        newPassword:     pwd.newPassword,
      })
      setPwd({ currentPassword: '', newPassword: '', confirmPassword: '' })
      toast({ type: 'success', message: 'Contraseña cambiada correctamente.' })
    } catch (err) {
      toast({ type: 'error', message: getErrorMessage(err, 'No se pudo cambiar la contraseña.') })
    } finally { setSavingPwd(false) }
  }

  const handleSwitch = async (membershipId, businessId) => {
    if (businessId === user?.businessId) return
    setSwitching(membershipId)
    try {
      await switchBusiness(businessId)
      toast({ type: 'success', message: 'Negocio activo cambiado.' })
    } catch (err) {
      toast({ type: 'error', message: getErrorMessage(err, 'No se pudo cambiar de negocio.') })
    } finally { setSwitching(null) }
  }

  const displayName = profile.fullName || user?.fullName || user?.email || 'Usuario'
  const initial = displayName.trim()[0]?.toUpperCase() || 'U'
  const headerGrad = ROLE_PALETTE[user?.role] || ROLE_PALETTE.ADMIN

  // ---- Strength meter visual ----
  const meterCells = [0, 1, 2, 3].map((i) => (
    <div
      key={i}
      className={`h-1.5 flex-1 rounded-full transition ${i < strength.score ? strength.cls : 'bg-slate-100'}`}
    />
  ))

  return (
    <div className="p-8 max-w-5xl mx-auto">
      <div className="mb-7 flex items-start justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold text-[#1e3a5f] tracking-tight">Mi perfil</h1>
          <p className="text-sm text-slate-500 mt-1.5 font-medium">Gestiona tus datos personales y tu contraseña</p>
        </div>
        <button
          type="button"
          onClick={refresh}
          title="Refrescar"
          className="inline-flex items-center justify-center w-11 h-11 rounded-xl border border-slate-200 bg-white text-slate-500 hover:bg-slate-50 hover:text-[#1e3a5f] transition"
        >
          <RefreshCw size={16} className={(!me || !businesses) ? 'animate-spin' : ''} />
        </button>
      </div>

      {/* Cabecera con avatar, rol y contactos clicables */}
      <div className={`${CARD} p-6 mb-6`}>
        <div className="flex items-center gap-5">
          <div className={`flex h-20 w-20 shrink-0 items-center justify-center rounded-2xl bg-gradient-to-br ${headerGrad} text-white text-3xl font-bold shadow-[0_8px_20px_-8px_rgba(14,165,233,0.5)]`}>
            {initial}
          </div>
          <div className="min-w-0 flex-1">
            <p className="text-xl font-bold text-[#1e3a5f] truncate">{displayName}</p>
            <div className="mt-1">
              <Badge variant={user?.role === 'ADMIN' ? 'navy' : 'cyan'}>
                <Shield size={10} className="mr-0.5" />{ROLE_LABEL[user?.role] || user?.role || '—'}
              </Badge>
            </div>
            <div className="mt-3 flex flex-wrap gap-2" onClick={(e) => e.stopPropagation()}>
              {profile.email && (
                <a
                  href={`mailto:${profile.email}`}
                  className="inline-flex items-center gap-1.5 text-xs font-medium px-2.5 py-1.5 rounded-lg bg-blue-50/50 text-blue-700 hover:bg-blue-100 transition truncate max-w-full"
                >
                  <Mail size={12} /> {profile.email}
                </a>
              )}
              {profile.phone && (
                <a
                  href={`tel:${profile.phone.replace(/\s/g, '')}`}
                  className="inline-flex items-center gap-1.5 text-xs font-medium font-mono px-2.5 py-1.5 rounded-lg bg-slate-50 text-slate-700 hover:bg-slate-100 transition"
                >
                  <Phone size={12} /> {profile.phone}
                </a>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* Negocio activo */}
      {user?.businessId && businesses && businesses.length > 0 && (
        <div className={`${CARD} p-4 mb-6 flex flex-wrap items-center gap-3`}>
          <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-blue-50 text-blue-600 shrink-0">
            <Building2 size={18} />
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-[10px] font-semibold text-slate-400 uppercase tracking-wider">Negocio activo</p>
            <p className="text-sm font-bold text-[#1e3a5f] truncate">
              {businesses.find((b) => b.businessId === user.businessId)?.businessName ?? '—'}
            </p>
          </div>
          <Link
            to="/configuracion"
            className="inline-flex items-center gap-1.5 text-xs font-semibold text-blue-700 hover:text-blue-800 transition"
          >
            Ir a configuración <ArrowRight size={12} />
          </Link>
        </div>
      )}

      {/* Datos personales */}
      <div className={`${CARD} p-6 mb-6`}>
        <div className="flex items-center gap-2 mb-5">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-blue-50">
            <UserCircle size={16} className="text-blue-500" />
          </div>
          <h2 className="text-base font-bold text-[#1e3a5f]">Datos personales</h2>
        </div>
        <div className="space-y-4">
          <Input
            label="Nombre completo *"
            value={profile.fullName}
            onChange={(e) => setProfile((p) => ({ ...p, fullName: e.target.value }))}
            placeholder="Nombre y apellidos"
          />
          <Input
            label="Email *"
            type="email"
            value={profile.email}
            onChange={(e) => setProfile((p) => ({ ...p, email: e.target.value }))}
            placeholder="tu@email.com"
          />
          {emailChanged && (
            <div className="rounded-xl bg-amber-50 border border-amber-100 px-3 py-2 text-[11px] text-amber-700 flex items-start gap-2">
              <AlertCircle size={13} className="shrink-0 mt-0.5" />
              <span>Estás cambiando tu email — es la credencial con la que inicias sesión y recibes recuperaciones de contraseña. Pediremos confirmación antes de guardar.</span>
            </div>
          )}
          <Input
            label="Teléfono"
            value={profile.phone}
            onChange={(e) => setProfile((p) => ({ ...p, phone: e.target.value }))}
            placeholder="600 000 000"
            sanitize={INPUT_SANITIZE.PHONE}
            inputMode="tel"
          />
          {me?.createdAt && (
            <p className="text-[11px] text-slate-400 flex items-center gap-1">
              <Calendar size={11} /> Cuenta creada el{' '}
              {new Date(me.createdAt).toLocaleDateString('es-ES', { day: '2-digit', month: 'long', year: 'numeric' })}
            </p>
          )}
          <div className="pt-1">
            <Button onClick={handleProfileSave} loading={savingProfile} disabled={!dirty || !me} className="gap-2">
              <Save size={16} /> Guardar cambios
            </Button>
            {!dirty && me && (
              <span className="ml-3 text-[11px] text-slate-400">No hay cambios pendientes.</span>
            )}
          </div>
        </div>
      </div>

      {/* Mis negocios */}
      {businesses && businesses.length > 0 && (
        <div className={`${CARD} p-6 mb-6`}>
          <div className="flex items-center gap-2 mb-5">
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-emerald-50">
              <Building2 size={15} className="text-emerald-500" />
            </div>
            <h2 className="text-base font-bold text-[#1e3a5f]">Mis negocios</h2>
            <span className="text-xs text-slate-400 ml-1">· {businesses.length}</span>
          </div>
          <div className="space-y-2">
            {businesses.map((b) => {
              const isActive = b.businessId === user?.businessId
              return (
                <div
                  key={b.membershipId ?? b.businessId}
                  className={`flex items-center gap-3 rounded-xl border px-3 py-2.5 ${isActive ? 'border-blue-200 bg-blue-50/40' : 'border-slate-100 hover:border-blue-200 hover:bg-slate-50 transition'}`}
                >
                  <div className={`flex h-9 w-9 items-center justify-center rounded-lg bg-gradient-to-br ${ROLE_PALETTE[b.role] || 'from-slate-400 to-slate-500'} text-white font-bold text-xs`}>
                    {(b.businessName || '?').trim()[0].toUpperCase()}
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-semibold text-[#1e3a5f] truncate">{b.businessName}</p>
                    <p className="text-[11px] text-slate-500">{ROLE_LABEL[b.role] || b.role}</p>
                  </div>
                  {isActive ? (
                    <span className="inline-flex items-center gap-1 px-2 py-1 rounded-full text-[10px] font-bold uppercase tracking-wider bg-emerald-50 text-emerald-700 ring-1 ring-emerald-200">
                      <Check size={10} /> Activo
                    </span>
                  ) : (
                    <Button
                      size="sm"
                      variant="outline"
                      loading={switching === (b.membershipId ?? b.businessId)}
                      onClick={() => handleSwitch(b.membershipId ?? b.businessId, b.businessId)}
                      className="gap-1.5"
                    >
                      Cambiar a este <ArrowRight size={12} />
                    </Button>
                  )}
                </div>
              )
            })}
          </div>
        </div>
      )}

      {/* Cambiar contraseña */}
      <div className={`${CARD} p-6`}>
        <div className="flex items-center gap-2 mb-5">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-amber-50">
            <Lock size={15} className="text-amber-500" />
          </div>
          <h2 className="text-base font-bold text-[#1e3a5f]">Cambiar contraseña</h2>
        </div>
        <div className="space-y-4">
          <PasswordField
            label="Contraseña actual *"
            value={pwd.currentPassword}
            onChange={(v) => setPwd((p) => ({ ...p, currentPassword: v }))}
            show={showCurrent}
            onToggle={() => setShowCurrent((s) => !s)}
            autoComplete="current-password"
          />
          <div className="grid sm:grid-cols-2 gap-4">
            <div>
              <PasswordField
                label="Nueva contraseña *"
                value={pwd.newPassword}
                onChange={(v) => setPwd((p) => ({ ...p, newPassword: v }))}
                show={showNew}
                onToggle={() => setShowNew((s) => !s)}
                placeholder="Mínimo 8 caracteres"
                autoComplete="new-password"
              />
              {pwd.newPassword && (
                <div className="mt-2">
                  <div className="flex gap-1">{meterCells}</div>
                  <p className={`text-[11px] mt-1 font-semibold ${strength.text}`}>Seguridad: {strength.label}</p>
                </div>
              )}
            </div>
            <div>
              <PasswordField
                label="Repetir nueva contraseña *"
                value={pwd.confirmPassword}
                onChange={(v) => setPwd((p) => ({ ...p, confirmPassword: v }))}
                show={showConfirm}
                onToggle={() => setShowConfirm((s) => !s)}
                autoComplete="new-password"
              />
              {confirmMatch && (
                <p className="text-[11px] mt-1.5 font-semibold text-emerald-600 inline-flex items-center gap-1">
                  <Check size={11} /> Las contraseñas coinciden
                </p>
              )}
              {confirmMismatch && (
                <p className="text-[11px] mt-1.5 font-semibold text-rose-600 inline-flex items-center gap-1">
                  <X size={11} /> No coinciden
                </p>
              )}
            </div>
          </div>
          <div className="pt-1">
            <Button onClick={handlePasswordSave} loading={savingPwd} className="gap-2">
              <KeyRound size={16} /> Cambiar contraseña
            </Button>
          </div>
        </div>
      </div>

      {/* Modal confirmar cambio de email */}
      <Modal open={confirmEmail} onClose={() => setConfirmEmail(false)} title="Confirmar cambio de email" size="sm">
        <div className="space-y-5">
          <div className="flex items-start gap-3 rounded-2xl bg-amber-50 border border-amber-100 px-4 py-4">
            <AlertCircle size={20} className="text-amber-600 shrink-0 mt-0.5" />
            <div className="text-sm text-amber-800 leading-snug">
              <p className="font-semibold">Vas a cambiar tu email de login</p>
              <p className="mt-1">De <strong className="font-mono">{me?.email}</strong> a <strong className="font-mono">{profile.email}</strong>. A partir de ahora iniciarás sesión con el nuevo email.</p>
            </div>
          </div>
          <div className="flex gap-3">
            <Button variant="outline" onClick={() => setConfirmEmail(false)} className="flex-1">Cancelar</Button>
            <Button onClick={doProfileSave} loading={savingProfile} className="flex-1">Confirmar y guardar</Button>
          </div>
        </div>
      </Modal>
    </div>
  )
}

/* ============================================================
   Campo de contraseña con toggle "mostrar / ocultar"
   ============================================================ */
function PasswordField({ label, value, onChange, show, onToggle, placeholder, autoComplete }) {
  return (
    <div>
      <label className="text-xs font-semibold text-slate-500 block mb-1.5">{label}</label>
      <div className="relative">
        <input
          type={show ? 'text' : 'password'}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          placeholder={placeholder}
          autoComplete={autoComplete}
          className="h-11 w-full rounded-xl border border-slate-200 bg-white pl-3.5 pr-10 text-sm text-[#1e3a5f] placeholder:text-slate-400 focus:outline-none focus:border-blue-400 focus:ring-4 focus:ring-blue-100 transition"
        />
        <button
          type="button"
          onClick={onToggle}
          tabIndex={-1}
          aria-label={show ? 'Ocultar contraseña' : 'Mostrar contraseña'}
          className="absolute right-2 top-1/2 -translate-y-1/2 p-1.5 rounded-lg text-slate-400 hover:bg-slate-100 hover:text-[#1e3a5f] transition"
        >
          {show ? <EyeOff size={15} /> : <Eye size={15} />}
        </button>
      </div>
    </div>
  )
}

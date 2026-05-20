import { useEffect, useState } from 'react'
import { UserCircle, Mail, Shield, Lock, Save, KeyRound, Calendar } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { Badge } from '@/components/ui/Badge'
import { useAuth } from '@/context/AuthContext'
import { useToast } from '@/components/ui/Toast'
import api, { getErrorMessage } from '@/lib/api'

const CARD = 'bg-white rounded-2xl border border-slate-100/80 shadow-[0_2px_12px_-2px_rgba(15,23,42,0.06)]'
const ROLE_LABEL = { ADMIN: 'Administrador', EMPLOYEE: 'Empleado' }

/**
 * Mi perfil — el usuario gestiona su propia identidad (datos comunes a
 * todos sus negocios) a través de los endpoints /api/me:
 *   - GET  /api/me           carga el perfil.
 *   - PUT  /api/me           actualiza nombre, email y teléfono.
 *   - PUT  /api/me/password  cambia la contraseña (exige la actual).
 *
 * El rol y el negocio NO se editan aquí: pertenecen a la membership, no
 * a la identidad. Tras un PUT correcto se llama a applyProfile() para
 * que el sidebar refleje el cambio sin volver a iniciar sesión.
 */
export default function Perfil() {
  const { user, applyProfile } = useAuth()
  const toast = useToast()

  // Se inicializa con los datos que el AuthContext ya tiene del login,
  // así no hay parpadeo; el GET /api/me los refresca al montar.
  const [profile, setProfile] = useState({
    fullName: user?.fullName || '',
    email:    user?.email || '',
    phone:    user?.phone || '',
  })
  const [me, setMe] = useState(null)
  const [savingProfile, setSavingProfile] = useState(false)

  const [pwd, setPwd] = useState({ currentPassword: '', newPassword: '', confirmPassword: '' })
  const [savingPwd, setSavingPwd] = useState(false)

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
  }, [toast])

  const handleProfileSave = async () => {
    if (!profile.fullName.trim()) { toast({ type: 'error', message: 'El nombre es obligatorio.' }); return }
    if (!profile.email.trim())    { toast({ type: 'error', message: 'El email es obligatorio.' }); return }
    setSavingProfile(true)
    try {
      const { data } = await api.put('/api/me', {
        fullName: profile.fullName,
        email:    profile.email,
        phone:    profile.phone || null,
      })
      setMe(data)
      applyProfile(data)   // refresca nombre/email en el sidebar sin re-login
      toast({ type: 'success', message: 'Perfil actualizado.' })
    } catch (err) {
      toast({ type: 'error', message: getErrorMessage(err, 'No se pudo actualizar el perfil.') })
    } finally {
      setSavingProfile(false)
    }
  }

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
      // El backend devuelve 400 si la contraseña actual no es correcta.
      toast({ type: 'error', message: getErrorMessage(err, 'No se pudo cambiar la contraseña.') })
    } finally {
      setSavingPwd(false)
    }
  }

  const displayName = profile.fullName || user?.fullName || user?.email || 'Usuario'
  const initial = displayName.trim()[0]?.toUpperCase() || 'U'

  return (
    <div className="p-8 max-w-3xl mx-auto">
      <div className="mb-8">
        <h1 className="text-3xl font-bold text-[#1e3a5f] tracking-tight">Mi perfil</h1>
        <p className="text-sm text-slate-500 mt-1.5 font-medium">Gestiona tus datos personales y tu contraseña</p>
      </div>

      {/* Cabecera con avatar y rol */}
      <div className={`${CARD} p-6 mb-6 flex items-center gap-5`}>
        <div className="flex h-20 w-20 shrink-0 items-center justify-center rounded-2xl bg-gradient-to-br from-cyan-400 to-blue-500 text-white text-3xl font-bold shadow-[0_8px_20px_-8px_rgba(14,165,233,0.5)]">
          {initial}
        </div>
        <div className="min-w-0">
          <p className="text-xl font-bold text-[#1e3a5f] truncate">{displayName}</p>
          <p className="text-sm text-slate-500 truncate flex items-center gap-1.5 mt-0.5">
            <Mail size={13} className="text-slate-400 shrink-0" />{profile.email || user?.email}
          </p>
          <div className="mt-2">
            <Badge variant={user?.role === 'ADMIN' ? 'navy' : 'cyan'}>
              <Shield size={10} className="mr-0.5" />{ROLE_LABEL[user?.role] || user?.role || '—'}
            </Badge>
          </div>
        </div>
      </div>

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
          <Input
            label="Teléfono"
            value={profile.phone}
            onChange={(e) => setProfile((p) => ({ ...p, phone: e.target.value }))}
            placeholder="600 000 000"
          />
          {me?.createdAt && (
            <p className="text-[11px] text-slate-400 flex items-center gap-1">
              <Calendar size={11} /> Cuenta creada el{' '}
              {new Date(me.createdAt).toLocaleDateString('es-ES', { day: '2-digit', month: 'long', year: 'numeric' })}
            </p>
          )}
          <div className="pt-1">
            <Button onClick={handleProfileSave} loading={savingProfile} className="gap-2">
              <Save size={16} /> Guardar cambios
            </Button>
          </div>
        </div>
      </div>

      {/* Cambiar contraseña */}
      <div className={`${CARD} p-6`}>
        <div className="flex items-center gap-2 mb-5">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-amber-50">
            <Lock size={15} className="text-amber-500" />
          </div>
          <h2 className="text-base font-bold text-[#1e3a5f]">Cambiar contraseña</h2>
        </div>
        <div className="space-y-4">
          <Input
            label="Contraseña actual *"
            type="password"
            value={pwd.currentPassword}
            onChange={(e) => setPwd((p) => ({ ...p, currentPassword: e.target.value }))}
            autoComplete="current-password"
          />
          <div className="grid sm:grid-cols-2 gap-4">
            <Input
              label="Nueva contraseña *"
              type="password"
              value={pwd.newPassword}
              onChange={(e) => setPwd((p) => ({ ...p, newPassword: e.target.value }))}
              placeholder="Mínimo 8 caracteres"
              autoComplete="new-password"
            />
            <Input
              label="Repetir nueva contraseña *"
              type="password"
              value={pwd.confirmPassword}
              onChange={(e) => setPwd((p) => ({ ...p, confirmPassword: e.target.value }))}
              autoComplete="new-password"
            />
          </div>
          <div className="pt-1">
            <Button onClick={handlePasswordSave} loading={savingPwd} className="gap-2">
              <KeyRound size={16} /> Cambiar contraseña
            </Button>
          </div>
        </div>
      </div>
    </div>
  )
}

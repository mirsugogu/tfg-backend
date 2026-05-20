import { useEffect, useState } from 'react'
import { useNavigate, Navigate, Link } from 'react-router-dom'
import { Building2, UserCircle, MapPin } from 'lucide-react'
import { Input } from '@/components/ui/Input'
import { Button } from '@/components/ui/Button'
import { LogoMark } from '@/components/ui/LogoMark'
import { LocationMap } from '@/components/ui/LocationMap'
import { useAuth } from '@/context/AuthContext'
import { getErrorMessage } from '@/lib/api'

/** Convierte un nombre en un slug: minúsculas, sin acentos, con guiones. */
const slugify = (s) =>
  s.toLowerCase()
    .normalize('NFD').replace(/[̀-ͯ]/g, '')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')

/**
 * Register — alta pública de un negocio nuevo (POST /api/auth/register).
 * El backend crea en una sola transacción la identidad del usuario, el
 * negocio y la primera membership (ADMIN), y devuelve el token: tras
 * registrarse, el usuario entra directamente al dashboard.
 *
 * La sección de ubicación geocodifica la dirección con Nominatim
 * (OpenStreetMap) y la muestra en un mapa como vista previa. La dirección
 * es opcional; el backend la vuelve a geocodificar en el servidor.
 */
export default function Register() {
  const { user, register, loading } = useAuth()
  const navigate = useNavigate()

  const [form, setForm] = useState({
    bizName: '', bizSlug: '', bizEmail: '', bizPhone: '',
    bizAddress: '', bizCity: '', bizPostalCode: '',
    adminName: '', adminEmail: '', adminPassword: '', adminPhone: '',
  })
  const [slugTouched, setSlugTouched] = useState(false)
  const [error, setError] = useState('')

  // Vista previa del mapa: coordenadas y estado de la geocodificación.
  const [coords, setCoords] = useState(null)
  const [geo, setGeo] = useState('idle')   // idle | searching | found | notfound

  // Geocodifica la dirección (con retardo, para no llamar en cada tecla)
  // y actualiza la vista previa del mapa. Nominatim es el geocodificador
  // público de OpenStreetMap.
  useEffect(() => {
    const query = [form.bizAddress, form.bizPostalCode, form.bizCity]
      .map((s) => s.trim()).filter(Boolean).join(', ')
    if (query.length < 6) { setCoords(null); setGeo('idle'); return }
    setGeo('searching')
    const timer = setTimeout(async () => {
      try {
        const res = await fetch(
          `https://nominatim.openstreetmap.org/search?format=json&limit=1&q=${encodeURIComponent(query)}`,
        )
        const data = await res.json()
        if (data && data[0]) {
          setCoords({ lat: parseFloat(data[0].lat), lon: parseFloat(data[0].lon) })
          setGeo('found')
        } else {
          setCoords(null)
          setGeo('notfound')
        }
      } catch {
        setCoords(null)
        setGeo('notfound')
      }
    }, 800)
    return () => clearTimeout(timer)
  }, [form.bizAddress, form.bizCity, form.bizPostalCode])

  if (user) return <Navigate to="/dashboard" replace />

  const set = (k, v) => setForm((p) => ({ ...p, [k]: v }))

  const handleName = (v) => {
    setForm((p) => ({ ...p, bizName: v, bizSlug: slugTouched ? p.bizSlug : slugify(v) }))
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    if (!form.bizName.trim() || !form.bizSlug.trim() || !form.bizEmail.trim()) {
      setError('Completa el nombre, el identificador y el email del negocio.')
      return
    }
    if (!form.adminName.trim() || !form.adminEmail.trim()) {
      setError('Completa tu nombre y tu email.')
      return
    }
    if (form.adminPassword.length < 8) {
      setError('La contraseña debe tener al menos 8 caracteres.')
      return
    }
    try {
      await register({
        business: {
          name: form.bizName,
          slug: form.bizSlug,
          email: form.bizEmail,
          phone: form.bizPhone || null,
          address: form.bizAddress || null,
          city: form.bizCity || null,
          postalCode: form.bizPostalCode || null,
        },
        admin: {
          fullName: form.adminName,
          email: form.adminEmail,
          password: form.adminPassword,
          phone: form.adminPhone || null,
        },
      })
      navigate('/dashboard')
    } catch (err) {
      // El backend devuelve 409 si el slug o el email ya existen.
      setError(getErrorMessage(err, 'No se pudo completar el registro. Inténtalo de nuevo.'))
    }
  }

  return (
    <div className="min-h-screen bg-[#f4f7fe] flex overflow-y-auto p-5 sm:p-8">
      <div className="w-full max-w-lg m-auto">
        <div className="bg-white rounded-3xl border border-slate-100 shadow-[0_24px_70px_-24px_rgba(15,23,42,0.3)] p-7 sm:p-9">
          {/* Logo */}
          <div className="flex items-center gap-2.5 mb-6">
            <LogoMark size={42} />
            <div className="leading-none">
              <p className="text-lg font-extrabold text-[#1e3a5f] tracking-tight">OPTIMA</p>
              <p className="text-[10px] text-slate-400 tracking-[0.2em] font-semibold mt-1">STUDIO</p>
            </div>
          </div>

          <h1 className="text-2xl font-bold text-[#1e3a5f] tracking-tight">Crea tu cuenta</h1>
          <p className="mt-1.5 text-sm text-slate-500">Registra tu negocio y empieza a gestionar tus citas.</p>

          <form onSubmit={handleSubmit} className="mt-6 space-y-6">
            {/* Datos del negocio */}
            <div>
              <div className="flex items-center gap-2 mb-3">
                <Building2 size={15} className="text-blue-500" />
                <h2 className="text-xs font-bold text-slate-500 uppercase tracking-wide">Datos del negocio</h2>
              </div>
              <div className="space-y-3">
                <Input
                  label="Nombre del negocio *"
                  value={form.bizName}
                  onChange={(e) => handleName(e.target.value)}
                  placeholder="Peluquería Eva"
                />
                <div>
                  <Input
                    label="Identificador (slug) *"
                    value={form.bizSlug}
                    onChange={(e) => { setSlugTouched(true); set('bizSlug', e.target.value) }}
                    placeholder="peluqueria-eva"
                  />
                  <p className="text-[11px] text-slate-400 mt-1">
                    Único y sin espacios. Se genera solo a partir del nombre; puedes editarlo.
                  </p>
                </div>
                <div className="grid sm:grid-cols-2 gap-3">
                  <Input label="Email del negocio *" type="email" value={form.bizEmail} onChange={(e) => set('bizEmail', e.target.value)} placeholder="negocio@email.com" />
                  <Input label="Teléfono del negocio" value={form.bizPhone} onChange={(e) => set('bizPhone', e.target.value)} placeholder="600 000 000" />
                </div>
              </div>
            </div>

            {/* Ubicación del negocio */}
            <div>
              <div className="flex items-center gap-2 mb-3">
                <MapPin size={15} className="text-blue-500" />
                <h2 className="text-xs font-bold text-slate-500 uppercase tracking-wide">Ubicación del negocio</h2>
              </div>
              <div className="space-y-3">
                <Input label="Dirección" value={form.bizAddress} onChange={(e) => set('bizAddress', e.target.value)} placeholder="Calle Mayor 5" />
                <div className="grid sm:grid-cols-2 gap-3">
                  <Input label="Ciudad" value={form.bizCity} onChange={(e) => set('bizCity', e.target.value)} placeholder="Madrid" />
                  <Input label="Código postal" value={form.bizPostalCode} onChange={(e) => set('bizPostalCode', e.target.value)} placeholder="28013" />
                </div>
                {coords ? (
                  <LocationMap lat={coords.lat} lon={coords.lon} />
                ) : (
                  <div className="h-56 w-full rounded-2xl border border-dashed border-slate-200 bg-slate-50 flex items-center justify-center px-6 text-center">
                    <p className="text-xs text-slate-400">
                      {geo === 'searching'
                        ? 'Buscando la dirección en el mapa…'
                        : geo === 'notfound'
                        ? 'No hemos encontrado esa dirección. Revísala o ajústala.'
                        : 'Escribe la dirección para ver la ubicación en el mapa.'}
                    </p>
                  </div>
                )}
                <p className="text-[11px] text-slate-400">
                  El mapa es solo una vista previa. La dirección es opcional: puedes completarla después.
                </p>
              </div>
            </div>

            {/* Cuenta de administrador */}
            <div>
              <div className="flex items-center gap-2 mb-3">
                <UserCircle size={15} className="text-blue-500" />
                <h2 className="text-xs font-bold text-slate-500 uppercase tracking-wide">Tu cuenta de administrador</h2>
              </div>
              <div className="space-y-3">
                <Input label="Nombre completo *" value={form.adminName} onChange={(e) => set('adminName', e.target.value)} placeholder="Eva García" />
                <div className="grid sm:grid-cols-2 gap-3">
                  <Input label="Tu email *" type="email" value={form.adminEmail} onChange={(e) => set('adminEmail', e.target.value)} placeholder="tu@email.com" />
                  <Input label="Tu teléfono" value={form.adminPhone} onChange={(e) => set('adminPhone', e.target.value)} placeholder="600 000 000" />
                </div>
                <Input
                  label="Contraseña *"
                  type="password"
                  value={form.adminPassword}
                  onChange={(e) => set('adminPassword', e.target.value)}
                  placeholder="Mínimo 8 caracteres"
                  autoComplete="new-password"
                />
              </div>
            </div>

            {error && (
              <div className="rounded-2xl bg-red-50 border border-red-100 px-4 py-3 text-sm text-red-700 font-medium">
                {error}
              </div>
            )}

            <Button type="submit" loading={loading} size="lg" className="w-full">
              Crear cuenta y empezar
            </Button>
          </form>

          <p className="mt-5 text-center text-sm text-slate-500">
            ¿Ya tienes cuenta?{' '}
            <Link to="/login" className="font-semibold text-blue-600 hover:text-blue-700">Inicia sesión</Link>
          </p>
        </div>
        <p className="text-center text-[11px] text-slate-400 mt-5">
          © 2026 OPTIMA · Plataforma SaaS de gestión de citas
        </p>
      </div>
    </div>
  )
}

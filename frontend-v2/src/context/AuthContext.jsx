import { createContext, useContext, useState } from 'react'
import api from '@/lib/api'

const AuthContext = createContext(null)

/**
 * Extrae los claims del JWT y devuelve el "user minimo" que se puede
 * inferir solo del token (sin tocar la red). Util como punto de partida
 * antes de enriquecer con GET /api/me.
 */
function decodeJwt(token) {
  const payload = JSON.parse(atob(token.split('.')[1]))
  return {
    token,
    userId:     payload.userId,
    businessId: payload.businessId,
    email:      payload.sub,
    role:       payload.role,
  }
}

/**
 * true si el JWT no se puede leer o si su claim `exp` ya ha pasado. Un
 * token caducado o ilegible se trata como sesion invalida.
 */
function isJwtExpired(token) {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]))
    return typeof payload.exp === 'number' && payload.exp * 1000 <= Date.now()
  } catch {
    return true
  }
}

/** JSON.parse defensivo: ante un valor corrupto devuelve null en vez de lanzar. */
function safeParse(raw) {
  try {
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

/**
 * Lee el user persistido en localStorage de forma defensiva. Si el JSON
 * esta corrupto, o el token esta caducado/malformado, limpia el storage y
 * devuelve null. Evita dos problemas: (1) que un JSON.parse roto deje la
 * app en blanco durante el render inicial del AuthProvider; (2) que un
 * token caducado se acepte como sesion valida hasta el primer 401.
 */
function loadStoredUser() {
  const u = safeParse(localStorage.getItem('optima_user'))
  if (!u?.token || isJwtExpired(u.token)) {
    localStorage.removeItem('optima_token')
    localStorage.removeItem('optima_user')
    return null
  }
  return u
}

/**
 * Persiste el user en localStorage y limpia los restos del flujo identity
 * (sessionStorage). Se llama dos veces durante un login con exito:
 *  1) inmediatamente despues de decodificar el JWT, para que el
 *     interceptor de api.js pueda usar el token en la llamada a /api/me.
 *  2) tras enriquecer con /api/me, para guardar fullName/phone/etc.
 */
function persistUser(user) {
  localStorage.setItem('optima_token', user.token)
  localStorage.setItem('optima_user', JSON.stringify(user))
  sessionStorage.removeItem('optima_identity_token')
  sessionStorage.removeItem('optima_pending_businesses')
}

/**
 * Mezcla la identidad real (GET /api/me) sobre los claims del JWT. Si la
 * llamada falla por 401 propaga el error (el interceptor de api.js ya
 * limpia la sesion); cualquier otro fallo se degrada a warn + datos
 * minimos del JWT para no bloquear el acceso a la aplicacion.
 */
async function enrichWithMe(baseUser) {
  try {
    const { data: me } = await api.get('/api/me')
    return {
      ...baseUser,
      identityId: me.id,
      fullName:   me.fullName,
      email:      me.email,
      phone:      me.phone,
    }
  } catch (err) {
    if (err?.response?.status === 401) throw err
    console.warn('No se pudo cargar /api/me; usando datos minimos del JWT', err)
    return baseUser
  }
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(loadStoredUser)
  const [loading, setLoading] = useState(false)

  // Estado del flujo identity (multi-membership). Sobrevive a recargas
  // gracias a sessionStorage; se limpia al elegir negocio o al cerrar
  // sesion.
  const [pendingBusinesses, setPendingBusinesses] = useState(
    () => safeParse(sessionStorage.getItem('optima_pending_businesses'))
  )
  const [identityToken, setIdentityToken] = useState(
    () => sessionStorage.getItem('optima_identity_token') ?? null
  )

  const login = async (email, password) => {
    setLoading(true)
    try {
      const { data } = await api.post('/api/auth/token', { email, password })

      if (data.tokenType === 'identity') {
        sessionStorage.setItem('optima_identity_token', data.token)
        sessionStorage.setItem('optima_pending_businesses', JSON.stringify(data.businesses))
        setIdentityToken(data.token)
        setPendingBusinesses(data.businesses)
        return { type: 'identity', businesses: data.businesses }
      }

      const baseUser = decodeJwt(data.token)
      persistUser(baseUser)
      const fullUser = await enrichWithMe(baseUser)
      persistUser(fullUser)
      setUser(fullUser)
      // Limpia los restos de un eventual login identity previo en este
      // mismo navegador.
      setPendingBusinesses(null)
      setIdentityToken(null)
      return { type: 'tenant' }
    } finally {
      setLoading(false)
    }
  }

  const selectBusiness = async (businessId) => {
    setLoading(true)
    try {
      const token = identityToken ?? sessionStorage.getItem('optima_identity_token')
      const { data } = await api.post(
        `/api/auth/select-business/${businessId}`,
        {},
        { headers: { Authorization: `Bearer ${token}` } }
      )
      const baseUser = decodeJwt(data.token)
      persistUser(baseUser)
      const fullUser = await enrichWithMe(baseUser)
      persistUser(fullUser)
      setUser(fullUser)
      setPendingBusinesses(null)
      setIdentityToken(null)
    } finally {
      setLoading(false)
    }
  }

  /**
   * Registra un negocio nuevo (POST /api/auth/register). El backend crea
   * identidad + negocio + primera membership (ADMIN) y devuelve un token
   * tenant, así que se inicia sesión directamente.
   */
  const register = async (payload) => {
    setLoading(true)
    try {
      const { data } = await api.post('/api/auth/register', payload)
      const baseUser = decodeJwt(data.token)
      persistUser(baseUser)
      const fullUser = await enrichWithMe(baseUser)
      persistUser(fullUser)
      setUser(fullUser)
    } finally {
      setLoading(false)
    }
  }

  /**
   * Cambia de negocio activo sin cerrar sesión. select-business acepta el
   * token tenant actual (lo añade el interceptor de api.js), de modo que
   * un usuario con varias memberships puede saltar de un negocio a otro.
   */
  const switchBusiness = async (businessId) => {
    setLoading(true)
    try {
      const { data } = await api.post(`/api/auth/select-business/${businessId}`)
      const baseUser = decodeJwt(data.token)
      persistUser(baseUser)
      const fullUser = await enrichWithMe(baseUser)
      persistUser(fullUser)
      setUser(fullUser)
    } finally {
      setLoading(false)
    }
  }

  const logout = () => {
    localStorage.removeItem('optima_token')
    localStorage.removeItem('optima_user')
    sessionStorage.removeItem('optima_identity_token')
    sessionStorage.removeItem('optima_pending_businesses')
    setUser(null)
    setPendingBusinesses(null)
    setIdentityToken(null)
  }

  /**
   * Sincroniza en el contexto los datos de identidad (fullName, email,
   * phone) tras un PUT /api/me, para que el sidebar y el resto de la app
   * reflejen el cambio sin necesidad de volver a iniciar sesion.
   */
  const applyProfile = (me) => {
    setUser((prev) => {
      if (!prev) return prev
      const next = { ...prev, identityId: me.id, fullName: me.fullName, email: me.email, phone: me.phone }
      localStorage.setItem('optima_user', JSON.stringify(next))
      return next
    })
  }

  return (
    <AuthContext.Provider value={{ user, loading, login, register, selectBusiness, switchBusiness, logout, applyProfile, pendingBusinesses }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be inside AuthProvider')
  return ctx
}

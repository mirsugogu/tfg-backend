import { createContext, useContext, useState } from 'react'
import api from '@/lib/api'

const AuthContext = createContext(null)

/** Extrae los claims del JWT y lanza un Error claro si el token está malformado. */
function decodeJwt(token) {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]))
    return {
      token,
      userId:     payload.userId,
      businessId: payload.businessId,
      email:      payload.sub,
      role:       payload.role,
    }
  } catch {
    throw new Error('El token recibido del servidor no es válido.')
  }
}

/** True si el JWT está caducado o no se puede leer. */
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

/** Lee el user persistido; limpia y devuelve null si el JSON o el JWT no son válidos. */
function loadStoredUser() {
  const u = safeParse(localStorage.getItem('optima_user'))
  if (!u?.token || isJwtExpired(u.token)) {
    localStorage.removeItem('optima_token')
    localStorage.removeItem('optima_user')
    return null
  }
  return u
}

/** Persiste el user en localStorage y limpia los restos del flujo identity. */
function persistUser(user) {
  localStorage.setItem('optima_token', user.token)
  localStorage.setItem('optima_user', JSON.stringify(user))
  sessionStorage.removeItem('optima_identity_token')
  sessionStorage.removeItem('optima_pending_businesses')
}

/** Enriquece el user del JWT con GET /api/me; propaga 401, degrada el resto a warn. */
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

/** Provider de autenticación: login 2 pasos, switch de negocio, registro y perfil. */
export function AuthProvider({ children }) {
  const [user, setUser] = useState(loadStoredUser)
  const [loading, setLoading] = useState(false)

  // Estado del flujo identity persistido en sessionStorage.
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
      // Limpia restos de un login identity previo en este navegador.
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

  /** Registra un negocio nuevo y arranca sesión con el token tenant devuelto. */
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

  /** Cambia de negocio activo sin cerrar sesión usando el token tenant actual. */
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

  /** Refresca en el contexto los datos de identidad tras un PUT /api/me. */
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

/** Hook para consumir AuthContext; lanza si se usa fuera del provider. */
export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be inside AuthProvider')
  return ctx
}

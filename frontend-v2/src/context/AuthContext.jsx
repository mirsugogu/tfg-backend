// Contexto de autenticacion: login en 2 pasos, seleccion de negocio y persistencia JWT
import { createContext, useContext, useState } from 'react'
import api from '@/lib/api'

const AuthContext = createContext(null)

/** Lee los datos basicos de la credencial de sesion */
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

/** Revisa si la credencial ya no es valida */
function isJwtExpired(token) {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]))
    return typeof payload.exp === 'number' && payload.exp * 1000 <= Date.now()
  } catch {
    return true
  }
}

/** Lectura segura de datos guardados */
function safeParse(raw) {
  try {
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

/** Lee el usuario guardado y limpia datos no validos */
function loadStoredUser() {
  const u = safeParse(localStorage.getItem('optima_user'))
  if (!u?.token || isJwtExpired(u.token)) {
    localStorage.removeItem('optima_token')
    localStorage.removeItem('optima_user')
    return null
  }
  return u
}

/** Guarda el usuario y limpia restos de sesion */
function persistUser(user) {
  localStorage.setItem('optima_token', user.token)
  localStorage.setItem('optima_user', JSON.stringify(user))
  sessionStorage.removeItem('optima_identity_token')
  sessionStorage.removeItem('optima_pending_businesses')
}

/** Completa los datos de usuario con el perfil actual */
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

/** Gestion de sesion, negocio activo y perfil */
export function AuthProvider({ children }) {
  const [user, setUser] = useState(loadStoredUser)
  const [loading, setLoading] = useState(false)

  // Negocios pendientes del acceso inicial
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
      // Limpia restos del acceso inicial
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

  /** Registra un negocio nuevo e inicia sesion */
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

  /** Cambia el negocio activo sin cerrar sesion */
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

  /** Actualiza en memoria los datos del perfil */
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

/** Funcion para consumir AuthContext; lanza si se usa fuera del proveedor */
export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be inside AuthProvider')
  return ctx
}

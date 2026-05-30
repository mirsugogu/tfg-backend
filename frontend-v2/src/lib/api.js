// Cliente HTTP centralizado con interceptores de JWT, 401 y rate-limit
import axios from 'axios'

// Paginas anonimas: un 401 aqui no debe redirigir para no perder el estado del formulario
const PUBLIC_PATHS = ['/login', '/forgot-password', '/reset-password']

function clearSession() {
  localStorage.removeItem('optima_token')
  localStorage.removeItem('optima_user')
  sessionStorage.removeItem('optima_identity_token')
  sessionStorage.removeItem('optima_pending_businesses')
}

const api = axios.create({
  // VITE_API_URL del env; alternativa al servidor local para que dev funcione sin env
  baseURL: import.meta.env.VITE_API_URL || 'http://localhost:8080',
  headers: { 'Content-Type': 'application/json' },
})

// Adjunta la credencial guardada a cada peticion
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('optima_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// Marca errores de sesion y permisos para la interfaz
api.interceptors.response.use(
  (res) => res,
  (error) => {
    const status = error?.response?.status
    if (status === 401) {
      clearSession()
      if (!PUBLIC_PATHS.includes(window.location.pathname)) {
        window.location.href = '/login'
      }
    } else if (status === 403) {
      error.isForbidden = true
    } else if (status === 429) {
      error.isRateLimited = true
      const header = error.response?.headers?.['retry-after']
      const parsed = header != null ? parseInt(header, 10) : NaN
      error.retryAfter = Number.isFinite(parsed) ? parsed : null
    }
    return Promise.reject(error)
  }
)

/** Mensaje de error en castellano a partir de un error de peticion; respeta el ErrorResponse del servidor */
export function getErrorMessage(error, fallback = 'Error inesperado. Intentalo de nuevo.') {
  const data = error?.response?.data
  if (typeof data?.message === 'string' && data.message.trim()) {
    return data.message
  }
  if (error?.isRateLimited && Number.isFinite(error.retryAfter) && error.retryAfter > 0) {
    return `Demasiadas peticiones. Reintenta en ${error.retryAfter} segundos.`
  }
  if (!error?.response) {
    return 'No se pudo conectar con el servidor.'
  }
  return fallback
}

export default api

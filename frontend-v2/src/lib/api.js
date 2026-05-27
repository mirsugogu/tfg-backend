import axios from 'axios'

// Páginas anónimas: un 401 aquí no debe redirigir para no perder el estado del formulario.
const PUBLIC_PATHS = ['/login', '/forgot-password', '/reset-password']

function clearSession() {
  localStorage.removeItem('optima_token')
  localStorage.removeItem('optima_user')
  sessionStorage.removeItem('optima_identity_token')
  sessionStorage.removeItem('optima_pending_businesses')
}

const api = axios.create({
  // VITE_API_URL del .env; fallback al backend local para que dev funcione sin .env.
  baseURL: import.meta.env.VITE_API_URL || 'http://localhost:8080',
  headers: { 'Content-Type': 'application/json' },
})

// Adjunta el JWT de localStorage a cada petición.
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('optima_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// 401 redirige a /login salvo en páginas públicas; 403 marca isForbidden; 429 marca isRateLimited + retryAfter.
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

/** Mensaje de error en castellano a partir de un error de axios; respeta el ErrorResponse del backend. */
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

import axios from 'axios'

// Paginas que renderizan formularios anonimos: no debemos forzar un reload
// a /login cuando una de ellas recibe un 401 (p. ej. credenciales invalidas
// en el propio login), porque haria perder el estado del formulario.
const PUBLIC_PATHS = ['/login', '/forgot-password', '/reset-password']

function clearSession() {
  localStorage.removeItem('optima_token')
  localStorage.removeItem('optima_user')
  sessionStorage.removeItem('optima_identity_token')
  sessionStorage.removeItem('optima_pending_businesses')
}

const api = axios.create({
  // Se prefiere la variable de entorno (frontend-v2/.env). Si falta,
  // caemos al backend local por defecto para no romper el dev sin .env.
  baseURL: import.meta.env.VITE_API_URL || 'http://localhost:8080',
  headers: { 'Content-Type': 'application/json' },
})

// Adjunta el JWT a cada peticion si esta en localStorage.
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('optima_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// Interpretacion uniforme de los errores HTTP del backend Optima:
//  - 401: sesion invalida o expirada -> limpiar storage y redirigir a
//         /login, salvo que ya estemos en una pagina publica (login,
//         forgot, reset).
//  - 403: autenticado pero sin permiso -> NO se desloguea (el usuario
//         es valido, el recurso no esta a su alcance). Se marca
//         error.isForbidden para que la vista pueda diferenciarlo.
//  - 429: rate limit -> se marca error.isRateLimited y se extrae
//         retryAfter (segundos) del header Retry-After.
// Cualquier otro estado se propaga sin modificar.
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

/**
 * Devuelve un mensaje en castellano listo para mostrar al usuario a partir
 * de un error de axios. Parsea la forma estandar ErrorResponse del backend
 * ({ status, error, message, timestamp }) y cubre los casos especiales de
 * rate limit (429) y fallo de red.
 *
 * Politica: el backend ya da mensajes claros en castellano (auditoria
 * 02_RESULTADOS_AUTH_VALIDACION.md J.001-J.015), asi que respetar
 * `message` es lo normal; el fallback solo aplica si la respuesta no
 * sigue el contrato esperado.
 */
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

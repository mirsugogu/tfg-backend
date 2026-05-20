import { createContext, useContext, useEffect, useState } from 'react'
import api from '@/lib/api'

const CatalogContext = createContext(null)

/**
 * Etiquetas en castellano de los catálogos globales. El backend guarda
 * los nombres en inglés (PENDING, ADMIN…) porque son catálogos técnicos
 * compartidos por todos los negocios; la traducción para la interfaz es
 * responsabilidad del frontend y vive centralizada aquí, en un único
 * sitio, en lugar de duplicarse en cada pantalla.
 */
const STATUS_LABELS = {
  PENDING:     'Pendiente',
  CONFIRMED:   'Confirmada',
  IN_PROGRESS: 'En curso',
  COMPLETED:   'Completada',
  CANCELLED:   'Cancelada',
  NO_SHOW:     'No presentado',
}
const ROLE_LABELS = {
  ADMIN:    'Administrador',
  EMPLOYEE: 'Empleado',
}

/**
 * Orden de presentación de los estados (el ciclo de vida de una cita).
 * El backend los devuelve ordenados alfabéticamente; aquí se reordenan
 * para que la interfaz los muestre en el orden lógico del flujo. Un
 * estado desconocido (si el backend añadiera uno) se coloca al final.
 */
const STATUS_ORDER = ['PENDING', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'NO_SHOW']
const statusRank = (name) => {
  const i = STATUS_ORDER.indexOf(name)
  return i === -1 ? STATUS_ORDER.length : i
}

/**
 * CatalogContext — carga una sola vez, al arrancar la app, los catálogos
 * globales del backend:
 *   - GET /api/roles                 (ADMIN, EMPLOYEE)
 *   - GET /api/appointment-statuses  (PENDING, CONFIRMED, …)
 *
 * Ambos endpoints son públicos (permitAll), así que se pueden pedir sin
 * token. Centralizar esto evita que cada pantalla los pida por su cuenta
 * y que la lista de estados/roles esté hardcodeada repartida por el
 * código.
 */
export function CatalogProvider({ children }) {
  const [roles, setRoles] = useState([])
  const [statuses, setStatuses] = useState([])

  useEffect(() => {
    // Si un catálogo falla la app sigue funcionando: las etiquetas hacen
    // fallback al nombre crudo y las listas quedan vacías. No se muestra
    // toast porque esto ocurre al arrancar, antes incluso del login.
    api.get('/api/roles')
      .then((r) => setRoles(r.data))
      .catch((err) => console.warn('No se pudo cargar el catálogo de roles', err))
    api.get('/api/appointment-statuses')
      .then((r) => setStatuses([...r.data].sort((a, b) => statusRank(a.name) - statusRank(b.name))))
      .catch((err) => console.warn('No se pudo cargar el catálogo de estados', err))
  }, [])

  // Traducen un nombre técnico a su etiqueta; si no se conoce, devuelven
  // el propio nombre para no romper la interfaz.
  const statusLabel = (name) => STATUS_LABELS[name] ?? name
  const roleLabel   = (name) => ROLE_LABELS[name] ?? name

  return (
    <CatalogContext.Provider value={{ roles, statuses, statusLabel, roleLabel }}>
      {children}
    </CatalogContext.Provider>
  )
}

export function useCatalog() {
  const ctx = useContext(CatalogContext)
  if (!ctx) throw new Error('useCatalog debe usarse dentro de <CatalogProvider>')
  return ctx
}

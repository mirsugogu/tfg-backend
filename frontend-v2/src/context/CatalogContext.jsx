// Contexto global de catalogos: roles y estados de cita con traduccion al castellano
import { createContext, useContext, useEffect, useState } from 'react'
import api from '@/lib/api'

const CatalogContext = createContext(null)

// Traducciones al castellano de los catalogos globales (servidor en ingles)
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

// Orden logico del ciclo de vida de la cita; desconocidos van al final
const STATUS_ORDER = ['PENDING', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'NO_SHOW']
const statusRank = (name) => {
  const i = STATUS_ORDER.indexOf(name)
  return i === -1 ? STATUS_ORDER.length : i
}

/** Proveedor que carga al arrancar los catalogos globales (roles, estados de cita) */
export function CatalogProvider({ children }) {
  const [roles, setRoles] = useState([])
  const [statuses, setStatuses] = useState([])

  useEffect(() => {
    // Fallos no rompen la app: las etiquetas caen al nombre crudo y no se muestra aviso
    api.get('/api/roles')
      .then((r) => setRoles(r.data))
      .catch((err) => console.warn('No se pudo cargar el catálogo de roles', err))
    api.get('/api/appointment-statuses')
      .then((r) => setStatuses([...r.data].sort((a, b) => statusRank(a.name) - statusRank(b.name))))
      .catch((err) => console.warn('No se pudo cargar el catálogo de estados', err))
  }, [])

  // Traductores con alternativa al nombre crudo si la etiqueta no esta mapeada
  const statusLabel = (name) => STATUS_LABELS[name] ?? name
  const roleLabel   = (name) => ROLE_LABELS[name] ?? name

  return (
    <CatalogContext.Provider value={{ roles, statuses, statusLabel, roleLabel }}>
      {children}
    </CatalogContext.Provider>
  )
}

/** Funcion para consumir CatalogContext; lanza si se usa fuera del proveedor */
export function useCatalog() {
  const ctx = useContext(CatalogContext)
  if (!ctx) throw new Error('useCatalog debe usarse dentro de <CatalogProvider>')
  return ctx
}

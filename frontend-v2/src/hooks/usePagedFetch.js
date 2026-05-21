import { useCallback, useEffect, useRef, useState } from 'react'
import api, { getErrorMessage } from '@/lib/api'
import { useToast } from '@/components/ui/Toast'

/**
 * Hook para consumir un endpoint paginado del backend (Spring Page<T>).
 *
 * El backend siempre devuelve la misma forma:
 *   { content: [...], number, size, totalElements, totalPages, ... }
 *
 * Tamano por defecto 20 (coincide con `spring.data.web.pageable.default-page-size`).
 * El backend cappea en 100 (verificado por audit I.003).
 *
 * Filtros (?from=&to=&membershipId=) se pasan via `params`. Si `params`
 * cambia se resetea la pagina a 0 automaticamente (caller controla la
 * estabilidad del objeto serializandolo el mismo si hace falta).
 *
 * Retorno:
 *   items          contenido de la pagina actual
 *   page           numero de pagina (0-indexed) devuelto por el backend
 *   size           size efectivo (puede diferir si el backend cappea)
 *   totalPages, totalElements
 *   loading        true mientras hay una peticion en vuelo
 *   setPage(n)     cambia de pagina
 *   refresh()      vuelve a pedir la pagina actual (util tras crear/editar/eliminar)
 */
export function usePagedFetch(url, { size = 20, params } = {}) {
  const [page, setPage] = useState(0)
  const [data, setData] = useState({ content: [], number: 0, size, totalElements: 0, totalPages: 0 })
  const [loading, setLoading] = useState(true)
  const [version, setVersion] = useState(0)
  const toast = useToast()

  // Serializa los filtros para usarlos en el array de dependencias; asi el
  // caller puede pasar { from: '...', to: '...' } sin preocuparse de la
  // identidad del objeto.
  const paramsKey = params ? JSON.stringify(params) : ''
  // Recuerda los filtros del render anterior para detectar cuando cambian.
  const prevParamsKey = useRef(paramsKey)

  useEffect(() => {
    if (!url) { setLoading(false); return }
    // Si los filtros cambiaron y NO estabamos en la pagina 0, basta con
    // resetear la pagina: el re-render que provoca setPage(0) reentra aqui
    // y hace el fetch. Asi se evita la peticion desechada (page viejo +
    // params nuevos) que la version anterior lanzaba siempre.
    if (prevParamsKey.current !== paramsKey) {
      prevParamsKey.current = paramsKey
      if (page !== 0) { setPage(0); return }
    }
    let cancelled = false
    setLoading(true)
    api.get(url, { params: { page, size, ...(params ?? {}) } })
      .then((r) => { if (!cancelled) setData(r.data) })
      .catch((err) => {
        if (cancelled) return
        toast({ type: 'error', message: getErrorMessage(err, 'Error al cargar los datos.') })
      })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [url, page, size, paramsKey, version, toast])

  const refresh = useCallback(() => setVersion((v) => v + 1), [])

  return {
    items:         data.content ?? [],
    page:          data.number ?? page,
    size:          data.size ?? size,
    totalPages:    data.totalPages ?? 0,
    totalElements: data.totalElements ?? 0,
    loading,
    setPage,
    refresh,
  }
}

import { useCallback, useEffect, useRef, useState } from 'react'
import api, { getErrorMessage } from '@/lib/api'
import { useToast } from '@/components/ui/Toast'

/** Hook para endpoints paginados Spring Page<T>; resetea a página 0 si cambian los params. */
export function usePagedFetch(url, { size = 20, params } = {}) {
  const [page, setPage] = useState(0)
  const [data, setData] = useState({ content: [], number: 0, size, totalElements: 0, totalPages: 0 })
  const [loading, setLoading] = useState(true)
  const [version, setVersion] = useState(0)
  const toast = useToast()

  // Serializa los filtros para usarlos en deps sin depender de la identidad del objeto.
  const paramsKey = params ? JSON.stringify(params) : ''
  const prevParamsKey = useRef(paramsKey)

  useEffect(() => {
    if (!url) { setLoading(false); return }
    // Cambio de filtros y no estamos en página 0: resetea y deja que el re-render dispare el fetch.
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

import { createContext, useContext, useState, useCallback } from 'react'
import { CheckCircle, XCircle, AlertCircle, X } from 'lucide-react'
import { cn } from '@/lib/utils'

const ToastContext = createContext(null)

// Contador monotónico para el id de cada toast (evita colisiones por timestamp).
let _toastSeq = 0

const config = {
  success: {
    icon: <CheckCircle size={17} className="text-emerald-500 shrink-0 mt-px" />,
    border: 'border-l-emerald-500',
    bg: 'bg-emerald-50/50',
  },
  error: {
    icon: <XCircle size={17} className="text-red-500 shrink-0 mt-px" />,
    border: 'border-l-red-500',
    bg: 'bg-red-50/50',
  },
  warning: {
    icon: <AlertCircle size={17} className="text-amber-500 shrink-0 mt-px" />,
    border: 'border-l-amber-500',
    bg: 'bg-amber-50/50',
  },
}

/** Proveedor de notificaciones toast con auto-cierre a los 4s. */
export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([])

  const toast = useCallback(({ type = 'success', message }) => {
    const id = ++_toastSeq
    setToasts((prev) => [...prev, { id, type, message }])
    setTimeout(() => setToasts((prev) => prev.filter((t) => t.id !== id)), 4000)
  }, [])

  const remove = (id) => setToasts((prev) => prev.filter((t) => t.id !== id))

  return (
    <ToastContext.Provider value={toast}>
      {children}
      <div className="fixed bottom-6 right-6 z-[100] flex flex-col gap-2.5 w-80">
        {toasts.map((t) => {
          const c = config[t.type] ?? config.success
          return (
            <div
              key={t.id}
              className={cn(
                'flex items-start gap-3 rounded-2xl border border-slate-200 border-l-4 bg-white px-4 py-3.5 shadow-lg shadow-black/5',
                'animate-in slide-in-from-right-4 fade-in duration-200',
                c.border,
                c.bg
              )}
            >
              {c.icon}
              <p className="flex-1 text-sm text-slate-700 font-medium leading-snug">{t.message}</p>
              <button
                onClick={() => remove(t.id)}
                className="text-slate-400 hover:text-slate-600 transition-colors mt-px"
              >
                <X size={14} />
              </button>
            </div>
          )
        })}
      </div>
    </ToastContext.Provider>
  )
}

/** Hook para emitir toasts; lanza si se usa fuera del provider. */
export function useToast() {
  const ctx = useContext(ToastContext)
  if (!ctx) throw new Error('useToast must be inside ToastProvider')
  return ctx
}

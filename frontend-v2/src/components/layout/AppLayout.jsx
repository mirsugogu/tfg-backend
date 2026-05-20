import { useCallback, useState } from 'react'
import { Navigate, Outlet } from 'react-router-dom'
import { Menu } from 'lucide-react'
import { useAuth } from '@/context/AuthContext'
import { Sidebar } from './Sidebar'

/**
 * Layout de las páginas protegidas: sidebar + contenido.
 *
 * - Escritorio (lg+): el Sidebar va en el flujo normal, a la izquierda.
 * - Móvil/tablet (<lg): el Sidebar es un drawer deslizante fuera de
 *   pantalla; esta barra superior aporta el botón de menú (hamburguesa)
 *   para abrirlo. El estado `mobileOpen` vive aquí porque lo comparten la
 *   hamburguesa (abre) y el Sidebar (se cierra solo al navegar, con Esc o
 *   al tocar el backdrop).
 */
export function AppLayout() {
  const { user } = useAuth()
  const [mobileOpen, setMobileOpen] = useState(false)
  const closeMobile = useCallback(() => setMobileOpen(false), [])

  if (!user) return <Navigate to="/login" replace />

  return (
    <div className="flex h-screen overflow-hidden bg-[#f4f7fe]">
      <Sidebar mobileOpen={mobileOpen} onMobileClose={closeMobile} />

      <div className="flex flex-1 flex-col overflow-hidden">
        {/* Barra superior — solo móvil/tablet */}
        <header className="flex items-center gap-3 border-b border-slate-200 bg-white px-4 py-2.5 lg:hidden">
          <button
            onClick={() => setMobileOpen(true)}
            aria-label="Abrir menú"
            className="rounded-lg p-2 text-slate-600 transition hover:bg-slate-100"
          >
            <Menu size={20} />
          </button>
          <img src="/logo-horizontal.svg" alt="OPTIMA" className="h-7 w-auto" />
        </header>

        <main className="flex-1 overflow-y-auto">
          <Outlet />
        </main>
      </div>
    </div>
  )
}

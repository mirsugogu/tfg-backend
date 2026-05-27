import { useCallback, useState } from 'react'
import { Navigate, Outlet } from 'react-router-dom'
import { Menu } from 'lucide-react'
import { useAuth } from '@/context/AuthContext'
import { Sidebar } from './Sidebar'

/** Layout de páginas protegidas: sidebar fijo en escritorio, drawer en móvil. */
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

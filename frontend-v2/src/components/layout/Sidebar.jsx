import { NavLink, useNavigate } from 'react-router-dom'
import {
  LayoutDashboard, Users, UserCheck, Scissors,
  CalendarDays, CalendarRange, Settings, LogOut,
} from 'lucide-react'
import { useAuth } from '@/context/AuthContext'
import { cn } from '@/lib/utils'
import { BusinessSwitcher } from './BusinessSwitcher'

const navItems = [
  { to: '/dashboard',     icon: LayoutDashboard, label: 'Dashboard' },
  { to: '/clientes',      icon: Users,            label: 'Clientes' },
  { to: '/empleados',     icon: UserCheck,        label: 'Empleados' },
  { to: '/catalogo',      icon: Scissors,         label: 'Catálogo' },
  { to: '/citas',         icon: CalendarDays,     label: 'Citas' },
  { to: '/calendario',    icon: CalendarRange,    label: 'Calendario' },
  { to: '/configuracion', icon: Settings,         label: 'Configuración' },
]

export function Sidebar() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const handleLogout = () => { logout(); navigate('/login') }
  const displayName = user?.fullName || user?.email || 'Usuario'
  const initials = displayName.trim()[0]?.toUpperCase() ?? 'U'

  return (
    <aside className="flex h-screen w-64 flex-col bg-[#1e3a5f] shrink-0">
      {/* Logo */}
      <div className="px-5 pt-7 pb-5">
        <div className="flex items-center gap-3">
          <div className="rounded-xl bg-white p-1.5 shadow-[0_4px_14px_rgba(0,0,0,0.18)]">
            <img src="/logo.svg" alt="OPTIMA" className="h-7 w-auto" />
          </div>
          <div>
            <div className="text-lg font-bold text-white tracking-tight leading-none">OPTIMA</div>
            <div className="text-[10px] text-cyan-300/65 tracking-[0.2em] mt-1 font-medium">STUDIO · v1.0</div>
          </div>
        </div>
      </div>

      <div className="mx-5 h-px bg-white/10" />

      {/* Negocio activo + cambio de negocio */}
      <BusinessSwitcher />

      {/* Section label */}
      <div className="px-5 pt-5 pb-2">
        <p className="text-[10px] font-bold uppercase tracking-[0.22em] text-white/30">Área de trabajo</p>
      </div>

      {/* Nav items */}
      <nav className="flex-1 overflow-y-auto px-3 space-y-0.5">
        {navItems.map(({ to, icon: Icon, label }) => (
          <NavLink
            key={to}
            to={to}
            className={({ isActive }) =>
              cn(
                'group flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition-all duration-150',
                isActive
                  ? 'bg-white text-[#1e3a5f] shadow-[0_2px_12px_-2px_rgba(15,23,42,0.15)]'
                  : 'text-white/60 hover:text-white hover:bg-white/8'
              )
            }
          >
            {({ isActive }) => (
              <>
                <span
                  className={cn(
                    'w-8 h-8 rounded-lg flex items-center justify-center transition shrink-0',
                    isActive
                      ? 'bg-gradient-to-br from-cyan-400 to-blue-500 text-white shadow-[0_4px_10px_rgba(14,165,233,0.4)]'
                      : 'bg-white/8 text-cyan-300/75 group-hover:bg-white/15 group-hover:text-cyan-200'
                  )}
                >
                  <Icon size={16} />
                </span>
                <span className="flex-1">{label}</span>
                {isActive && <span className="w-1.5 h-1.5 rounded-full bg-[#38bdf8] shrink-0" />}
              </>
            )}
          </NavLink>
        ))}
      </nav>

      {/* User footer — el bloque de usuario enlaza a /perfil */}
      <div className="border-t border-white/10 px-3 py-4">
        <div className="flex items-center gap-1.5">
          <NavLink
            to="/perfil"
            title="Mi perfil"
            className={({ isActive }) =>
              cn(
                'flex flex-1 items-center gap-3 px-2 py-2.5 rounded-xl transition-colors min-w-0',
                isActive ? 'bg-white/15' : 'bg-white/6 hover:bg-white/12'
              )
            }
          >
            <div className="w-9 h-9 rounded-full bg-gradient-to-br from-cyan-400 to-blue-500 flex items-center justify-center text-xs font-bold text-white shrink-0">
              {initials}
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-xs font-semibold text-white truncate leading-tight">{displayName}</p>
              <p className="text-[10px] text-white/35 uppercase tracking-wider font-medium mt-0.5">{user?.role}</p>
            </div>
          </NavLink>
          <button
            onClick={handleLogout}
            className="text-white/30 hover:text-white hover:bg-white/8 rounded-xl transition-colors p-2.5 shrink-0"
            aria-label="Cerrar sesión"
            title="Cerrar sesión"
          >
            <LogOut size={15} />
          </button>
        </div>
      </div>
    </aside>
  )
}

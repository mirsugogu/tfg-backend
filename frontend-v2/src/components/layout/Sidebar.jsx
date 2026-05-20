import { useCallback, useEffect, useMemo, useState } from 'react'
import { NavLink, useNavigate, useLocation } from 'react-router-dom'
import {
  LayoutDashboard, Users, UserCheck, Scissors,
  CalendarDays, CalendarRange, Settings, LogOut,
  UserCircle, PanelLeftClose, PanelLeft, Search, Keyboard, X,
} from 'lucide-react'
import { useAuth } from '@/context/AuthContext'
import { cn } from '@/lib/utils'
import { BusinessSwitcher } from './BusinessSwitcher'

const navItems = [
  { to: '/dashboard',     icon: LayoutDashboard, label: 'Dashboard',     key: '1' },
  { to: '/clientes',      icon: Users,            label: 'Clientes',      key: '2' },
  { to: '/empleados',     icon: UserCheck,        label: 'Empleados',     key: '3' },
  { to: '/catalogo',      icon: Scissors,         label: 'Catálogo',      key: '4' },
  { to: '/citas',         icon: CalendarDays,     label: 'Citas',         key: '5' },
  { to: '/calendario',    icon: CalendarRange,    label: 'Calendario',    key: '6' },
  { to: '/configuracion', icon: Settings,         label: 'Configuración', key: '7' },
]

export function Sidebar({ mobileOpen = false, onMobileClose }) {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  /* Preferencia de colapso (solo escritorio), persistida en localStorage.
     En movil el sidebar es un drawer a pantalla completa: mientras el
     drawer esta abierto se ignora el colapso (`collapsed` es derivado). */
  const [collapsedPref, setCollapsedPref] = useState(() => localStorage.getItem('optima_sidebar_collapsed') === 'true')
  useEffect(() => { localStorage.setItem('optima_sidebar_collapsed', String(collapsedPref)) }, [collapsedPref])
  const collapsed = collapsedPref && !mobileOpen

  /* Al navegar (clic en un item o desde el command palette) se cierra el
     drawer movil. En escritorio onMobileClose es un no-op inofensivo. */
  useEffect(() => { onMobileClose?.() }, [location.pathname, onMobileClose])

  const [paletteOpen, setPaletteOpen] = useState(false)
  const [helpOpen, setHelpOpen] = useState(false)

  const handleLogout = () => { logout(); navigate('/login') }
  const displayName = user?.fullName || user?.email || 'Usuario'
  const initials = displayName.trim()[0]?.toUpperCase() ?? 'U'

  /* ---- Atajos de teclado ----
     - 1..7 → ir a la página correspondiente
     - Ctrl/Cmd + K → palette
     - Ctrl/Cmd + B → colapsar/expandir
     - ? → ayuda
     Se ignoran si el foco está en un input/textarea/select. */
  useEffect(() => {
    const handler = (e) => {
      const tag = (e.target?.tagName || '').toLowerCase()
      const inField = tag === 'input' || tag === 'textarea' || tag === 'select' || e.target?.isContentEditable
      const cmdK = (e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k'
      const cmdB = (e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'b'

      if (cmdK) { e.preventDefault(); setPaletteOpen((v) => !v); return }
      if (cmdB) { e.preventDefault(); setCollapsedPref((v) => !v); return }
      if (inField) return

      if (e.key === '?') { e.preventDefault(); setHelpOpen(true); return }
      if (e.key === 'Escape') { setPaletteOpen(false); setHelpOpen(false); onMobileClose?.(); return }
      // Atajos numéricos solo cuando el palette/help están cerrados
      if (!paletteOpen && !helpOpen) {
        const item = navItems.find((n) => n.key === e.key)
        if (item) { e.preventDefault(); navigate(item.to) }
      }
    }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [navigate, paletteOpen, helpOpen, onMobileClose])

  return (
    <>
      <aside
        className={cn(
          'flex h-screen flex-col bg-[#1e3a5f] transition-all duration-200',
          // Movil: drawer fijo, fuera de pantalla; entra deslizando
          'fixed inset-y-0 left-0 z-40 w-64',
          mobileOpen ? 'translate-x-0 shadow-2xl' : '-translate-x-full',
          // Escritorio: en el flujo normal, con soporte de modo colapsado
          'lg:static lg:z-auto lg:translate-x-0 lg:shadow-none lg:shrink-0',
          collapsed ? 'lg:w-[72px]' : 'lg:w-64',
        )}
      >
        {/* Logo + toggle */}
        <div className={cn('flex items-center pt-6 pb-5', collapsed ? 'px-3 justify-center' : 'px-5 justify-between')}>
          <NavLink to="/dashboard" className="flex items-center gap-3" title="Ir al dashboard">
            <div className="rounded-xl bg-white p-1.5 shadow-[0_4px_14px_rgba(0,0,0,0.18)]">
              <img src="/logo.svg" alt="OPTIMA" className="h-7 w-auto" />
            </div>
            {!collapsed && (
              <div>
                <div className="text-lg font-bold text-white tracking-tight leading-none">OPTIMA</div>
                <div className="text-[10px] text-cyan-300/65 tracking-[0.2em] mt-1 font-medium">STUDIO · v1.0</div>
              </div>
            )}
          </NavLink>
          {!collapsed && (
            <div className="flex items-center gap-1">
              {/* Cerrar el drawer — solo movil/tablet */}
              <button
                onClick={onMobileClose}
                title="Cerrar menú"
                aria-label="Cerrar menú"
                className="lg:hidden p-1.5 rounded-lg text-white/50 hover:bg-white/10 hover:text-white transition"
              >
                <X size={18} />
              </button>
              {/* Colapsar — solo escritorio */}
              <button
                onClick={() => setCollapsedPref(true)}
                title="Colapsar (Ctrl+B)"
                className="hidden lg:block p-1.5 rounded-lg text-white/40 hover:bg-white/10 hover:text-white transition"
              >
                <PanelLeftClose size={16} />
              </button>
            </div>
          )}
        </div>

        {!collapsed && <div className="mx-5 h-px bg-white/10" />}

        {/* BusinessSwitcher */}
        {!collapsed && <BusinessSwitcher />}

        {/* Cmd+K trigger (solo expandido) */}
        {!collapsed && (
          <div className="px-3 pt-3">
            <button
              onClick={() => setPaletteOpen(true)}
              className="w-full flex items-center gap-2 rounded-xl bg-white/6 px-3 py-2 hover:bg-white/12 transition"
            >
              <Search size={14} className="text-white/40" />
              <span className="text-[11px] text-white/40 flex-1 text-left">Buscar…</span>
              <kbd className="text-[10px] font-mono font-bold text-white/40 bg-white/8 rounded px-1.5 py-0.5">⌘K</kbd>
            </button>
          </div>
        )}

        {/* Section label */}
        {!collapsed && (
          <div className="px-5 pt-4 pb-2">
            <p className="text-[10px] font-bold uppercase tracking-[0.22em] text-white/30">Área de trabajo</p>
          </div>
        )}

        {/* Nav items */}
        <nav className={cn('flex-1 overflow-y-auto space-y-0.5', collapsed ? 'px-2 pt-3' : 'px-3')}>
          {navItems.map(({ to, icon: Icon, label, key }) => (
            <NavLink
              key={to}
              to={to}
              title={collapsed ? label : undefined}
              className={({ isActive }) =>
                cn(
                  'group flex items-center gap-3 rounded-xl text-sm font-medium transition-all duration-150',
                  collapsed ? 'justify-center px-2 py-2.5' : 'px-3 py-2.5',
                  isActive
                    ? 'bg-white text-[#1e3a5f] shadow-[0_2px_12px_-2px_rgba(15,23,42,0.15)]'
                    : 'text-white/60 hover:text-white hover:bg-white/8',
                )
              }
            >
              {({ isActive }) => (
                <>
                  <span className={cn(
                    'w-8 h-8 rounded-lg flex items-center justify-center transition shrink-0',
                    isActive
                      ? 'bg-gradient-to-br from-cyan-400 to-blue-500 text-white shadow-[0_4px_10px_rgba(14,165,233,0.4)]'
                      : 'bg-white/8 text-cyan-300/75 group-hover:bg-white/15 group-hover:text-cyan-200',
                  )}>
                    <Icon size={16} />
                  </span>
                  {!collapsed && (
                    <>
                      <span className="flex-1">{label}</span>
                      <kbd className={cn(
                        'text-[9px] font-mono font-bold rounded px-1.5 py-0.5',
                        isActive ? 'text-slate-400 bg-slate-100' : 'text-white/30 bg-white/8',
                      )}>{key}</kbd>
                    </>
                  )}
                </>
              )}
            </NavLink>
          ))}

          {/* Sección "Cuenta" */}
          {!collapsed && (
            <div className="px-2 pt-5 pb-2">
              <p className="text-[10px] font-bold uppercase tracking-[0.22em] text-white/30">Cuenta</p>
            </div>
          )}
          <NavLink
            to="/perfil"
            title={collapsed ? 'Mi perfil' : undefined}
            className={({ isActive }) =>
              cn(
                'group flex items-center gap-3 rounded-xl text-sm font-medium transition-all duration-150',
                collapsed ? 'justify-center px-2 py-2.5 mt-3' : 'px-3 py-2.5',
                isActive
                  ? 'bg-white text-[#1e3a5f] shadow-[0_2px_12px_-2px_rgba(15,23,42,0.15)]'
                  : 'text-white/60 hover:text-white hover:bg-white/8',
              )
            }
          >
            {({ isActive }) => (
              <>
                <span className={cn(
                  'w-8 h-8 rounded-lg flex items-center justify-center transition shrink-0',
                  isActive
                    ? 'bg-gradient-to-br from-cyan-400 to-blue-500 text-white'
                    : 'bg-white/8 text-cyan-300/75 group-hover:bg-white/15 group-hover:text-cyan-200',
                )}>
                  <UserCircle size={16} />
                </span>
                {!collapsed && <span className="flex-1">Mi perfil</span>}
              </>
            )}
          </NavLink>
        </nav>

        {/* User footer */}
        <div className={cn('border-t border-white/10', collapsed ? 'px-2 py-3' : 'px-3 py-4')}>
          {collapsed ? (
            <div className="flex flex-col items-center gap-2">
              <button
                onClick={() => setCollapsedPref(false)}
                title="Expandir (Ctrl+B)"
                className="p-2 rounded-lg text-white/40 hover:bg-white/8 hover:text-white transition"
              >
                <PanelLeft size={16} />
              </button>
              <NavLink
                to="/perfil"
                title="Mi perfil"
                className="w-9 h-9 rounded-full bg-gradient-to-br from-cyan-400 to-blue-500 flex items-center justify-center text-xs font-bold text-white"
              >
                {initials}
              </NavLink>
              <button onClick={handleLogout} title="Cerrar sesión" className="text-white/30 hover:text-white hover:bg-white/8 rounded-lg transition-colors p-2">
                <LogOut size={15} />
              </button>
            </div>
          ) : (
            <div className="flex items-center gap-1.5">
              <NavLink
                to="/perfil"
                title="Mi perfil"
                className={({ isActive }) =>
                  cn(
                    'flex flex-1 items-center gap-3 px-2 py-2.5 rounded-xl transition-colors min-w-0',
                    isActive ? 'bg-white/15' : 'bg-white/6 hover:bg-white/12',
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
          )}
        </div>
      </aside>

      {/* Backdrop del drawer movil: cubre el contenido y lo cierra al tocar */}
      {mobileOpen && (
        <div
          onClick={onMobileClose}
          aria-hidden="true"
          className="fixed inset-0 z-30 bg-slate-900/50 lg:hidden"
        />
      )}

      {paletteOpen && <CommandPalette onClose={() => setPaletteOpen(false)} />}
      {helpOpen && <KeyboardHelp onClose={() => setHelpOpen(false)} />}
    </>
  )
}

/* ============================================================
   COMMAND PALETTE (Cmd+K)
   ============================================================ */

function CommandPalette({ onClose }) {
  const navigate = useNavigate()
  const [query, setQuery] = useState('')
  const [selected, setSelected] = useState(0)

  const items = useMemo(() => {
    const q = query.trim().toLowerCase()
    const all = [
      ...navItems,
      { to: '/perfil', icon: UserCircle, label: 'Mi perfil', key: '' },
    ]
    if (!q) return all
    return all.filter((it) => it.label.toLowerCase().includes(q))
  }, [query])

  useEffect(() => { setSelected(0) }, [query])

  const choose = useCallback((it) => {
    if (!it) return
    navigate(it.to)
    onClose()
  }, [navigate, onClose])

  const onKey = (e) => {
    if (e.key === 'ArrowDown') { e.preventDefault(); setSelected((s) => Math.min(items.length - 1, s + 1)) }
    else if (e.key === 'ArrowUp')   { e.preventDefault(); setSelected((s) => Math.max(0, s - 1)) }
    else if (e.key === 'Enter')     { e.preventDefault(); choose(items[selected]) }
    else if (e.key === 'Escape')    { e.preventDefault(); onClose() }
  }

  return (
    <div onClick={onClose} className="fixed inset-0 z-50 bg-slate-900/30 backdrop-blur-sm flex items-start justify-center pt-32 px-4">
      <div onClick={(e) => e.stopPropagation()} className="bg-white rounded-2xl border border-slate-200 shadow-[0_28px_56px_-16px_rgba(15,23,42,0.22)] w-full max-w-[480px] overflow-hidden">
        <div className="flex items-center gap-2 px-4 py-3 border-b border-slate-100">
          <Search size={16} className="text-slate-400" />
          <input
            autoFocus
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={onKey}
            placeholder="Saltar a…"
            className="flex-1 text-sm text-[#1e3a5f] focus:outline-none placeholder:text-slate-400 bg-transparent"
          />
          <kbd className="text-[10px] font-mono font-bold text-slate-400 bg-slate-100 rounded px-1.5 py-0.5">esc</kbd>
        </div>
        <div className="p-2 space-y-0.5 max-h-72 overflow-y-auto">
          {items.length === 0 ? (
            <p className="text-center text-xs text-slate-400 py-6">Sin resultados.</p>
          ) : (
            items.map((it, idx) => {
              const Icon = it.icon
              const active = idx === selected
              return (
                <button
                  key={it.to}
                  onMouseEnter={() => setSelected(idx)}
                  onClick={() => choose(it)}
                  className={cn(
                    'w-full flex items-center gap-3 rounded-xl px-3 py-2.5 transition',
                    active ? 'bg-blue-50' : 'hover:bg-slate-50',
                  )}
                >
                  <span className={cn('w-8 h-8 rounded-lg flex items-center justify-center shrink-0',
                    active ? 'bg-gradient-to-br from-cyan-400 to-blue-500 text-white' : 'bg-slate-100 text-slate-500',
                  )}>
                    <Icon size={16} />
                  </span>
                  <span className={cn('flex-1 text-sm font-semibold text-left', active ? 'text-[#1e3a5f]' : 'text-slate-700')}>
                    {it.label}
                  </span>
                  {it.key && (
                    <kbd className="text-[9px] font-mono font-bold text-slate-400 bg-white rounded px-1.5 py-0.5 border border-slate-200">{it.key}</kbd>
                  )}
                </button>
              )
            })
          )}
        </div>
        <div className="px-4 py-2 border-t border-slate-100 bg-slate-50/60 flex items-center justify-between text-[10px] text-slate-400">
          <span>
            <kbd className="font-mono font-bold text-slate-500 bg-white rounded px-1.5 py-0.5 border border-slate-200">↑↓</kbd> navegar ·{' '}
            <kbd className="font-mono font-bold text-slate-500 bg-white rounded px-1.5 py-0.5 border border-slate-200">↵</kbd> abrir
          </span>
          <span>
            Pulsa <kbd className="font-mono font-bold text-slate-500 bg-white rounded px-1.5 py-0.5 border border-slate-200">?</kbd> para ver atajos
          </span>
        </div>
      </div>
    </div>
  )
}

/* ============================================================
   KEYBOARD HELP OVERLAY (?)
   ============================================================ */

function KeyboardHelp({ onClose }) {
  const rows = [
    { keys: ['1'], label: 'Ir a Dashboard' },
    { keys: ['2'], label: 'Ir a Clientes' },
    { keys: ['3'], label: 'Ir a Empleados' },
    { keys: ['4'], label: 'Ir a Catálogo' },
    { keys: ['5'], label: 'Ir a Citas' },
    { keys: ['6'], label: 'Ir a Calendario' },
    { keys: ['7'], label: 'Ir a Configuración' },
    { keys: ['⌘', 'K'], label: 'Búsqueda rápida' },
    { keys: ['⌘', 'B'], label: 'Colapsar / expandir sidebar' },
    { keys: ['?'], label: 'Mostrar atajos' },
    { keys: ['Esc'], label: 'Cerrar diálogos' },
  ]
  return (
    <div onClick={onClose} className="fixed inset-0 z-50 bg-slate-900/30 backdrop-blur-sm flex items-center justify-center px-4">
      <div onClick={(e) => e.stopPropagation()} className="bg-white rounded-2xl border border-slate-200 shadow-[0_28px_56px_-16px_rgba(15,23,42,0.22)] w-full max-w-md p-6">
        <div className="flex items-center gap-2 mb-5">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-blue-50"><Keyboard size={16} className="text-blue-500" /></div>
          <h2 className="text-base font-bold text-[#1e3a5f]">Atajos de teclado</h2>
        </div>
        <div className="space-y-2.5">
          {rows.map(({ keys, label }) => (
            <div key={label} className="flex items-center justify-between text-sm">
              <span className="text-slate-600">{label}</span>
              <div className="flex gap-1">
                {keys.map((k) => (
                  <kbd key={k} className="text-[11px] font-mono font-bold text-slate-600 bg-slate-100 rounded px-2 py-1">{k}</kbd>
                ))}
              </div>
            </div>
          ))}
        </div>
        <button onClick={onClose} className="mt-5 w-full h-10 rounded-xl bg-slate-100 hover:bg-slate-200 text-sm font-semibold text-slate-700 transition">Entendido</button>
      </div>
    </div>
  )
}

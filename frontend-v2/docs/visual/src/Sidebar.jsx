const { Icons } = window;

function Sidebar({ active, onNavigate, collapsed, onToggle }) {
  const items = [
  { id: 'dashboard', label: 'Dashboard', icon: Icons.Dashboard },
  { id: 'clientes', label: 'Clientes', icon: Icons.Users },
  { id: 'empleados', label: 'Empleados', icon: Icons.Employees },
  { id: 'catalogo', label: 'Catálogo', icon: Icons.Catalog },
  { id: 'citas', label: 'Citas', icon: Icons.Calendar },
  { id: 'config', label: 'Configuración', icon: Icons.Settings }];


  return (
    <aside
      className={`fixed lg:sticky z-40 top-0 left-0 h-screen bg-navy-900 text-white
                  transition-transform duration-300 flex flex-col
                  ${collapsed ? '-translate-x-full lg:translate-x-0' : 'translate-x-0'}
                  w-72 lg:w-64 shrink-0`}>
      
      {/* Logo */}
      <div className="px-7 pt-8 pb-6 flex items-center gap-3">
        <svg viewBox="0 0 64 64" className="w-9 h-9 text-cyan-400 shrink-0 drop-shadow-[0_4px_10px_rgba(34,211,238,0.35)]" fill="none" stroke="currentColor" strokeWidth="5" strokeLinecap="round" strokeLinejoin="round">
          <path d="M32 8a24 24 0 1 1-16.97 7.03" />
          <path d="M32 20a12 12 0 1 0 8.49 3.51" opacity="0.85" />
          <circle cx="32" cy="32" r="3.5" fill="currentColor" stroke="none" />
        </svg>
        <div className="min-w-0">
          <div className="text-2xl font-bold text-white tracking-tight leading-none">OPTIMA</div>
          <div className="text-[10px] text-cyan-300/70 tracking-[0.2em] mt-1.5 font-medium">STUDIO · v1.0</div>
        </div>
        <button
          className="ml-auto lg:hidden text-white/60 hover:text-white"
          onClick={onToggle}
          aria-label="Cerrar menú">
          
          <Icons.Close size={20} />
        </button>
      </div>

      <div className="mx-7 h-px bg-white/10" />

      {/* Section label */}
      <div className="px-7 pt-6 pb-2 text-[10px] tracking-[0.22em] text-white/40 font-semibold">
        ÁREA DE TRABAJO
      </div>

      {/* Nav */}
      <nav className="flex-1 px-4 overflow-y-auto nice-scroll">
        <ul className="space-y-1">
          {items.map((item) => {
            const isActive = active === item.id;
            const Ico = item.icon;
            return (
              <li key={item.id}>
                <button
                  onClick={() => onNavigate(item.id)}
                  className={`group relative w-full flex items-center gap-3 px-3 py-3 rounded-xl text-sm font-medium transition
                    ${isActive ?
                  'bg-white text-navy-900 shadow-card' :
                  'text-white/70 hover:text-white hover:bg-white/5'}`}>
                  
                  <span className={`w-9 h-9 rounded-lg flex items-center justify-center transition
                    ${isActive ? 'bg-gradient-to-br from-cyan-400 to-blue-500 text-white' : 'bg-white/5 text-cyan-300 group-hover:bg-white/10'}`}>
                    <Ico size={18} />
                  </span>
                  <span className="flex-1 text-left">{item.label}</span>
                  {isActive && <span className="w-1.5 h-1.5 rounded-full bg-cyan-400" />}
                </button>
              </li>);

          })}
        </ul>
      </nav>

      {/* User */}
      <div className="px-5 pb-6">
        <div className="flex items-center gap-3 px-2 py-3 rounded-xl bg-white/5">
          <div className="w-9 h-9 rounded-full bg-gradient-to-br from-cyan-400 to-blue-500 flex items-center justify-center text-xs font-bold">
            AP
          </div>
          <div className="flex-1 min-w-0">
            <div className="text-sm font-medium truncate">Alex Prieto</div>
            <div className="text-[11px] text-white/50 truncate">Administrador</div>
          </div>
          <a href="Login.html" className="text-white/40 hover:text-white" aria-label="Salir">
            <Icons.Logout size={18} />
          </a>
        </div>
      </div>
    </aside>);

}

window.Sidebar = Sidebar;
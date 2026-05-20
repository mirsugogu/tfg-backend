import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '@/context/AuthContext'
import { Sidebar } from './Sidebar'

export function AppLayout() {
  const { user } = useAuth()
  if (!user) return <Navigate to="/login" replace />

  return (
    <div className="flex h-screen overflow-hidden bg-[#f4f7fe]">
      <Sidebar />
      <main className="flex-1 overflow-y-auto">
        <Outlet />
      </main>
    </div>
  )
}

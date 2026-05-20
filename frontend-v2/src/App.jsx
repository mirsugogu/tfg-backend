import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider } from '@/context/AuthContext'
import { CatalogProvider } from '@/context/CatalogContext'
import { ToastProvider } from '@/components/ui/Toast'
import { AppLayout } from '@/components/layout/AppLayout'
import Login from '@/pages/Login'
import Register from '@/pages/Register'
import ForgotPassword from '@/pages/ForgotPassword'
import ResetPassword from '@/pages/ResetPassword'
import Dashboard from '@/pages/Dashboard'
import Clientes from '@/pages/Clientes'
import Empleados from '@/pages/Empleados'
import Catalogo from '@/pages/Catalogo'
import Citas from '@/pages/Citas'
import Calendario from '@/pages/Calendario'
import Configuracion from '@/pages/Configuracion'
import Perfil from '@/pages/Perfil'

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <ToastProvider>
          <CatalogProvider>
            <Routes>
              <Route path="/login" element={<Login />} />
              <Route path="/register" element={<Register />} />
              <Route path="/forgot-password" element={<ForgotPassword />} />
              <Route path="/reset-password" element={<ResetPassword />} />
              <Route element={<AppLayout />}>
                <Route path="/dashboard" element={<Dashboard />} />
                <Route path="/clientes" element={<Clientes />} />
                <Route path="/empleados" element={<Empleados />} />
                <Route path="/catalogo" element={<Catalogo />} />
                <Route path="/citas" element={<Citas />} />
                <Route path="/calendario" element={<Calendario />} />
                <Route path="/configuracion" element={<Configuracion />} />
                <Route path="/perfil" element={<Perfil />} />
              </Route>
              <Route path="*" element={<Navigate to="/dashboard" replace />} />
            </Routes>
          </CatalogProvider>
        </ToastProvider>
      </AuthProvider>
    </BrowserRouter>
  )
}

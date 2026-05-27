import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { CheckCircle2, Circle, ArrowRight, Sparkles } from 'lucide-react'
import api from '@/lib/api'

/** Checklist de onboarding (horario, empleados, servicios); se oculta al completar los tres. */
export function OnboardingChecklist({ businessId }) {
  // Estado por paso: null = cargando/fallo (se trata como hecho para no engañar), true/false directos.
  const [state, setState] = useState({
    hasOpenHours: null,
    hasEmployees: null,
    hasServices:  null,
  })

  useEffect(() => {
    if (!businessId) return
    let cancelled = false
    Promise.allSettled([
      api.get(`/api/businesses/${businessId}/hours`),
      api.get(`/api/businesses/${businessId}/users?size=1`),
      api.get(`/api/businesses/${businessId}/services?size=1`),
    ]).then(([h, emp, srv]) => {
      if (cancelled) return
      const hoursData = h.status === 'fulfilled'
        ? (Array.isArray(h.value.data) ? h.value.data : (h.value.data?.content ?? []))
        : null
      setState({
        hasOpenHours: hoursData == null
          ? null
          : hoursData.some((x) => !x.isClosed && x.startTime && x.endTime),
        // El propio admin cuenta como 1 membership; se considera "con empleados" a partir de 2.
        hasEmployees: emp.status === 'fulfilled' ? emp.value.data.totalElements > 1 : null,
        hasServices:  srv.status === 'fulfilled' ? srv.value.data.totalElements > 0 : null,
      })
    })
    return () => { cancelled = true }
  }, [businessId])

  // null se trata como hecho para no enseñar el checklist con datos sospechosos.
  const hasOpenHours = state.hasOpenHours !== false
  const hasEmployees = state.hasEmployees !== false
  const hasServices  = state.hasServices  !== false

  const steps = [
    {
      key: 'hours',
      label: 'Define el horario de apertura',
      done: hasOpenHours,
      to: '/configuracion',
      hint: 'En Configuración → Horario',
    },
    {
      key: 'employees',
      label: 'Añade empleados',
      done: hasEmployees,
      to: '/empleados',
      hint: 'Crea los empleados que tomarán citas',
    },
    {
      key: 'services',
      label: 'Crea servicios',
      done: hasServices,
      to: '/catalogo',
      hint: 'Catálogo de lo que ofreces',
    },
  ]

  const total = steps.length
  const done  = steps.filter((s) => s.done).length
  if (done === total) return null

  return (
    <div className="mb-6 rounded-2xl border border-cyan-100 bg-gradient-to-br from-cyan-50 to-blue-50 p-5 shadow-[0_2px_12px_-2px_rgba(15,23,42,0.06)]">
      <div className="mb-4 flex items-center justify-between">
        <div className="flex items-center gap-2.5">
          <Sparkles size={18} className="text-cyan-600" />
          <h2 className="text-sm font-bold tracking-tight text-[#1e3a5f]">Configura tu negocio para empezar</h2>
        </div>
        <span className="text-xs font-semibold tabular-nums text-slate-500">{done}/{total}</span>
      </div>
      <ul className="space-y-2">
        {steps.map((s) => (
          <li key={s.key}>
            {s.done ? (
              <div className="flex items-center gap-3 text-sm text-slate-600">
                <CheckCircle2 size={16} className="shrink-0 text-emerald-500" />
                <span className="line-through opacity-70">{s.label}</span>
              </div>
            ) : (
              <Link
                to={s.to}
                className="group flex items-center gap-3 rounded-lg px-2 py-1 -mx-2 text-sm font-medium text-[#1e3a5f] transition hover:bg-white/60"
              >
                <Circle size={16} className="shrink-0 text-slate-300 transition group-hover:text-blue-400" />
                <span className="flex-1">
                  {s.label}
                  <span className="ml-2 text-xs font-normal text-slate-500">— {s.hint}</span>
                </span>
                <ArrowRight size={14} className="text-slate-400 transition group-hover:text-blue-500" />
              </Link>
            )}
          </li>
        ))}
      </ul>
    </div>
  )
}

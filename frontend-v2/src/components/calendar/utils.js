/* Constantes compartidas por el calendario */

export const MONTHS_ES = ['Enero','Febrero','Marzo','Abril','Mayo','Junio','Julio','Agosto','Septiembre','Octubre','Noviembre','Diciembre']
export const DAYS_ES_SHORT = ['Lun','Mar','Mié','Jue','Vie','Sáb','Dom']
export const VIEW_MODES = ['Mes','Semana','Día']

// Horario base antes de cargar datos del negocio
export const DEFAULT_DAY_START = 8
export const DEFAULT_DAY_END   = 21

// Altura base de una hora en la agenda
export const HOUR_PX = 64

/** Colores estables por empleado o cabina */
export const PALETTES = [
  { name: 'cyan',    bg: 'bg-cyan-100',    text: 'text-cyan-800',    bar: 'bg-cyan-600',    ring: 'ring-cyan-400',    hover: 'hover:bg-cyan-200',    dot: 'bg-cyan-500' },
  { name: 'amber',   bg: 'bg-amber-100',   text: 'text-amber-800',   bar: 'bg-amber-600',   ring: 'ring-amber-400',   hover: 'hover:bg-amber-200',   dot: 'bg-amber-500' },
  { name: 'emerald', bg: 'bg-emerald-100', text: 'text-emerald-800', bar: 'bg-emerald-600', ring: 'ring-emerald-400', hover: 'hover:bg-emerald-200', dot: 'bg-emerald-500' },
  { name: 'indigo',  bg: 'bg-indigo-100',  text: 'text-indigo-800',  bar: 'bg-indigo-600',  ring: 'ring-indigo-400',  hover: 'hover:bg-indigo-200',  dot: 'bg-indigo-500' },
  { name: 'pink',    bg: 'bg-pink-100',    text: 'text-pink-800',    bar: 'bg-pink-600',    ring: 'ring-pink-400',    hover: 'hover:bg-pink-200',    dot: 'bg-pink-500' },
  { name: 'sky',     bg: 'bg-sky-100',     text: 'text-sky-800',     bar: 'bg-sky-600',     ring: 'ring-sky-400',     hover: 'hover:bg-sky-200',     dot: 'bg-sky-500' },
  { name: 'violet',  bg: 'bg-violet-100',  text: 'text-violet-800',  bar: 'bg-violet-600',  ring: 'ring-violet-400',  hover: 'hover:bg-violet-200',  dot: 'bg-violet-500' },
  { name: 'teal',    bg: 'bg-teal-100',    text: 'text-teal-800',    bar: 'bg-teal-600',    ring: 'ring-teal-400',    hover: 'hover:bg-teal-200',    dot: 'bg-teal-500' },
]

/** Colores de estado de cita */
export const STATUS_STYLES = {
  PENDING:     { bg:'bg-amber-200',   text:'text-amber-900',   bar:'bg-amber-600',   ring:'ring-amber-500',   hover:'hover:bg-amber-300',   dot:'bg-amber-500' },
  CONFIRMED:   { bg:'bg-blue-200',    text:'text-blue-900',    bar:'bg-blue-600',    ring:'ring-blue-500',    hover:'hover:bg-blue-300',    dot:'bg-blue-500' },
  IN_PROGRESS: { bg:'bg-cyan-200',    text:'text-cyan-900',    bar:'bg-cyan-600',    ring:'ring-cyan-500',    hover:'hover:bg-cyan-300',    dot:'bg-cyan-500' },
  COMPLETED:   { bg:'bg-emerald-200', text:'text-emerald-900', bar:'bg-emerald-600', ring:'ring-emerald-500', hover:'hover:bg-emerald-300', dot:'bg-emerald-500' },
  CANCELLED:   { bg:'bg-slate-200',   text:'text-slate-600',   bar:'bg-slate-500',   ring:'ring-slate-400',   hover:'hover:bg-slate-300',   dot:'bg-slate-500' },
  NO_SHOW:     { bg:'bg-rose-200',    text:'text-rose-900',    bar:'bg-rose-600',    ring:'ring-rose-500',    hover:'hover:bg-rose-300',    dot:'bg-rose-500' },
}
export const GRAY_PALETTE = { bg:'bg-slate-200', text:'text-slate-600', bar:'bg-slate-500', ring:'ring-slate-400', hover:'hover:bg-slate-300', dot:'bg-slate-500' }

const PALETTE_BY_NAME = Object.fromEntries(PALETTES.map((p) => [p.name, p]))
/** Devuelve la paleta asociada al nombre o null */
export const paletteByName = (name) => PALETTE_BY_NAME[name] || null

/** Devuelve los estilos del evento segun el modo "Color por"; terminales siempre apagadas */
export const styleFor = (appt, colorBy) => {
  if (appt.statusName === 'CANCELLED' || appt.statusName === 'NO_SHOW')
    return STATUS_STYLES[appt.statusName]
  if (colorBy === 'status')   return STATUS_STYLES[appt.statusName] || STATUS_STYLES.PENDING
  if (colorBy === 'employee') return paletteByName(appt.employeeColor) || PALETTES[(appt.membershipId ?? 0) % PALETTES.length]
  if (colorBy === 'booth') {
    if (!appt.boothId) return GRAY_PALETTE
    return paletteByName(appt.boothColor) || PALETTES[appt.boothId % PALETTES.length]
  }
  return STATUS_STYLES[appt.statusName] || STATUS_STYLES.PENDING
}

export const pad2 = (n) => String(n).padStart(2, '0')
export const keyOf = (d) => `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`
export const isSameDay = (a, b) =>
  a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate()
export const startOfWeek = (d) => {
  const dow = (d.getDay() + 6) % 7
  const r = new Date(d); r.setDate(d.getDate() - dow); r.setHours(0, 0, 0, 0); return r
}
export const buildMonthGrid = (year, month) => {
  const first = new Date(year, month, 1)
  const dow = (first.getDay() + 6) % 7
  const start = new Date(year, month, 1 - dow)
  return Array.from({ length: 42 }, (_, i) => { const d = new Date(start); d.setDate(start.getDate() + i); return d })
}
export const rangeFor = (view, cursor) => {
  if (view === 'Mes') {
    const cells = buildMonthGrid(cursor.getFullYear(), cursor.getMonth())
    return { from: keyOf(cells[0]), to: keyOf(cells[41]) }
  }
  if (view === 'Semana') {
    const ws = startOfWeek(cursor); const we = new Date(ws); we.setDate(ws.getDate() + 6)
    return { from: keyOf(ws), to: keyOf(we) }
  }
  return { from: keyOf(cursor), to: keyOf(cursor) }
}

export const apptDate = (a) => a.startDateTime.slice(0, 10)
export const apptHHMM = (iso) => iso.slice(11, 16)
export const minutesOf = (iso) => { const [h, m] = iso.slice(11, 16).split(':'); return Number(h) * 60 + Number(m) }
export const apptDuration = (a) => Math.max(15, minutesOf(a.endDateTime) - minutesOf(a.startDateTime))

/** Ordena citas solapadas en columnas (algoritmo greedy de packing) */
export const layoutEvents = (events) => {
  const items = events
    .map((a) => { const startMin = minutesOf(a.startDateTime); return { a, startMin, endMin: startMin + apptDuration(a) } })
    .sort((x, y) => x.startMin - y.startMin || x.endMin - y.endMin)
  // Agrupa citas que se solapan en el tiempo
  const groups = []
  let current = [], currentEnd = -1
  items.forEach((it) => {
    if (it.startMin < currentEnd) { current.push(it); currentEnd = Math.max(currentEnd, it.endMin) }
    else { if (current.length) groups.push(current); current = [it]; currentEnd = it.endMin }
  })
  if (current.length) groups.push(current)
  // Dentro de cada grupo, asigna columna visual (col) y total de columnas (cols)
  const out = []
  groups.forEach((group) => {
    const cols = []
    group.forEach((it) => {
      let placed = -1
      for (let i = 0; i < cols.length; i++) if (cols[i] <= it.startMin) { cols[i] = it.endMin; placed = i; break }
      if (placed === -1) { cols.push(it.endMin); placed = cols.length - 1 }
      it.col = placed
    })
    group.forEach((it) => { it.cols = cols.length; out.push(it) })
  })
  return out
}

/** Rangos abiertos del dia con turno partido */
export const openRangesFor = (businessHours, date) => {
  const dow = date.getDay() === 0 ? 7 : date.getDay()
  return businessHours
    .filter((x) => x.dayOfWeek === dow && !x.isClosed && x.startTime && x.endTime)
    .map((h) => {
      const [sh, sm] = h.startTime.slice(0, 5).split(':').map(Number)
      const [eh, em] = h.endTime.slice(0, 5).split(':').map(Number)
      return [sh + sm / 60, eh + em / 60]
    })
}

/** Rangos laborables del empleado en la fecha; soporta turno partido */
export const workingRangesFor = (employeeSchedules, date) => {
  const dow = date.getDay() === 0 ? 7 : date.getDay()
  return employeeSchedules
    .filter((s) => s.dayOfWeek === dow && s.startTime && s.endTime)
    .map((s) => {
      const [sh, sm] = s.startTime.slice(0, 5).split(':').map(Number)
      const [eh, em] = s.endTime.slice(0, 5).split(':').map(Number)
      return [sh + sm / 60, eh + em / 60]
    })
}


// Comprueba si la fecha entra en el bloqueo
const isDateInBlockRange = (date, block) => {
  const ymd = keyOf(date)
  return ymd >= block.startDate && ymd <= block.endDate
}

/** Bloqueos visibles para una celda */
export const blocksForCell = (blocks, date, resourceType = null, resourceId = null) => {
  if (!blocks || blocks.length === 0) return []
  return blocks.filter((b) => {
    if (!isDateInBlockRange(date, b)) return false
    const isGlobal = b.membershipId == null && b.boothId == null
    if (isGlobal) return true
    if (resourceType === 'employee' && b.membershipId === resourceId) return true
    if (resourceType === 'booth' && b.boothId === resourceId) return true
    return false
  })
}

/** Etiqueta corta del bloqueo: usa `reason` o un generico por tipo */
export const labelForBlock = (b) => {
  if (b.reason && b.reason.trim()) return b.reason
  if (b.membershipId != null) return 'Empleado bloqueado'
  if (b.boothId != null) return 'Cabina bloqueada'
  return 'Día bloqueado'
}

/** Primer bloqueo que aplica a la cita (global / por empleado / por cabina), o null */
export const blockForAppointment = (blocks, appt) => {
  if (!appt || !blocks || blocks.length === 0) return null
  const date = new Date(appt.startDateTime)
  for (const b of blocks) {
    if (!isDateInBlockRange(date, b)) continue
    const isGlobal = b.membershipId == null && b.boothId == null
    if (isGlobal) return b
    if (b.membershipId != null && b.membershipId === appt.membershipId) return b
    if (b.boothId != null && appt.boothId != null && b.boothId === appt.boothId) return b
  }
  return null
}

/** Primera ausencia que solapa con la cita en el mismo empleado, o null */
export const absenceForAppointment = (absences, appt) => {
  if (!appt || !absences || absences.length === 0) return null
  const apptStart = new Date(appt.startDateTime).getTime()
  const apptEnd = new Date(appt.endDateTime).getTime()
  for (const abs of absences) {
    if (abs.membershipId !== appt.membershipId) continue
    const absStart = new Date(abs.startDateTime).getTime()
    const absEnd = new Date(abs.endDateTime).getTime()
    if (apptStart < absEnd && apptEnd > absStart) return abs
  }
  return null
}

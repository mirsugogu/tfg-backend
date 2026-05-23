/*
 * Helpers y constantes compartidos por el Calendario y sus sub-rejillas
 * (DayGrid, WeekGrid, MonthGrid, ResourceDayGrid, WeekResourceGrid).
 * Sin JSX — se importa desde varios archivos .jsx.
 */

export const MONTHS_ES = ['Enero','Febrero','Marzo','Abril','Mayo','Junio','Julio','Agosto','Septiembre','Octubre','Noviembre','Diciembre']
export const DAYS_ES_SHORT = ['Lun','Mar','Mié','Jue','Vie','Sáb','Dom']
export const VIEW_MODES = ['Mes','Semana','Día']

// Defaults — luego se ajustan dinámicamente desde business_hours.
export const DEFAULT_DAY_START = 8
export const DEFAULT_DAY_END   = 21

// Pixeles por hora en las rejillas Dia/Semana/resource. Valor unico tras
// retirar el modo compacto (audit I): la vista Mes no respetaba la
// densidad y mantener dos modos sin coherencia entre vistas confundia.
export const HOUR_PX = 64

/*
 * Paleta determinista para "Color por" Empleado / Cabina. Backgrounds
 * a -100 con texto -800: lo bastante fuerte para diferenciar de un
 * vistazo, pero sin tapar el texto del cliente. ring a -400, hover a
 * -200, bar (barrita lateral) a -600. Coincide con la paleta extendida
 * de los chips de estado.
 */
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

/*
 * Estados: fondos saturados (-200) + texto oscuro para máxima
 * legibilidad y para que los 6 estados se distingan sin recurrir al
 * texto. CANCELLED y NO_SHOW se mantienen más apagados a propósito
 * — son finales y no deben competir visualmente.
 */
export const STATUS_STYLES = {
  PENDING:     { bg:'bg-amber-200',   text:'text-amber-900',   bar:'bg-amber-600',   ring:'ring-amber-500',   hover:'hover:bg-amber-300',   dot:'bg-amber-500' },
  CONFIRMED:   { bg:'bg-blue-200',    text:'text-blue-900',    bar:'bg-blue-600',    ring:'ring-blue-500',    hover:'hover:bg-blue-300',    dot:'bg-blue-500' },
  IN_PROGRESS: { bg:'bg-cyan-200',    text:'text-cyan-900',    bar:'bg-cyan-600',    ring:'ring-cyan-500',    hover:'hover:bg-cyan-300',    dot:'bg-cyan-500' },
  COMPLETED:   { bg:'bg-emerald-200', text:'text-emerald-900', bar:'bg-emerald-600', ring:'ring-emerald-500', hover:'hover:bg-emerald-300', dot:'bg-emerald-500' },
  CANCELLED:   { bg:'bg-slate-200',   text:'text-slate-600',   bar:'bg-slate-500',   ring:'ring-slate-400',   hover:'hover:bg-slate-300',   dot:'bg-slate-500' },
  NO_SHOW:     { bg:'bg-rose-200',    text:'text-rose-900',    bar:'bg-rose-600',    ring:'ring-rose-500',    hover:'hover:bg-rose-300',    dot:'bg-rose-500' },
}
export const GRAY_PALETTE = { bg:'bg-slate-200', text:'text-slate-600', bar:'bg-slate-500', ring:'ring-slate-400', hover:'hover:bg-slate-300', dot:'bg-slate-500' }

/* Paleta por nombre ('cyan', 'amber'…); null si el nombre no existe — el
 * llamador decide el fallback. */
const PALETTE_BY_NAME = Object.fromEntries(PALETTES.map((p) => [p.name, p]))
export const paletteByName = (name) => PALETTE_BY_NAME[name] || null

/*
 * Devuelve los estilos del evento según el modo "Color por". Excepción:
 * las citas terminales (CANCELLED / NO_SHOW) ignoran el modo y van
 * siempre apagadas, para no camuflarse entre las activas. En modo
 * 'employee', si el empleado tiene color asignado a mano (appt.employeeColor,
 * de memberships.color) se usa ese; si no, cae al color automatico por id.
 */
export const styleFor = (appt, colorBy) => {
  // Terminales: apagadas siempre, sea cual sea "Color por".
  if (appt.statusName === 'CANCELLED' || appt.statusName === 'NO_SHOW')
    return STATUS_STYLES[appt.statusName]
  if (colorBy === 'status')   return STATUS_STYLES[appt.statusName] || STATUS_STYLES.PENDING
  if (colorBy === 'employee') return paletteByName(appt.employeeColor) || PALETTES[(appt.membershipId ?? 0) % PALETTES.length]
  if (colorBy === 'booth') {
    if (!appt.boothId) return GRAY_PALETTE
    // [L] Si la cabina tiene color asignado, usamos esa paleta; si no, el
    // automatico por id. Simetria con el modo 'employee'.
    return paletteByName(appt.boothColor) || PALETTES[appt.boothId % PALETTES.length]
  }
  return STATUS_STYLES[appt.statusName] || STATUS_STYLES.PENDING
}

/* ----- fecha ----- */
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

/* ----- cita ----- */
export const apptDate = (a) => a.startDateTime.slice(0, 10)
export const apptHHMM = (iso) => iso.slice(11, 16)
export const minutesOf = (iso) => { const [h, m] = iso.slice(11, 16).split(':'); return Number(h) * 60 + Number(m) }
export const apptDuration = (a) => Math.max(15, minutesOf(a.endDateTime) - minutesOf(a.startDateTime))

/*
 * Reparte eventos solapados en sub-columnas (algoritmo greedy clásico).
 * Devuelve cada evento con { a, col, cols } para que PositionedEvent
 * calcule left/width.
 */
export const layoutEvents = (events) => {
  const items = events
    .map((a) => { const startMin = minutesOf(a.startDateTime); return { a, startMin, endMin: startMin + apptDuration(a) } })
    .sort((x, y) => x.startMin - y.startMin || x.endMin - y.endMin)
  const groups = []
  let current = [], currentEnd = -1
  items.forEach((it) => {
    if (it.startMin < currentEnd) { current.push(it); currentEnd = Math.max(currentEnd, it.endMin) }
    else { if (current.length) groups.push(current); current = [it]; currentEnd = it.endMin }
  })
  if (current.length) groups.push(current)
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

/* Devuelve los rangos abiertos del día desde la lista business_hours. */
export const openRangesFor = (businessHours, date) => {
  const dow = date.getDay() === 0 ? 7 : date.getDay()
  const h = businessHours.find((x) => x.dayOfWeek === dow)
  if (!h || h.isClosed || !h.startTime || !h.endTime) return []
  const [sh, sm] = h.startTime.slice(0, 5).split(':').map(Number)
  const [eh, em] = h.endTime.slice(0, 5).split(':').map(Number)
  return [[sh + sm / 60, eh + em / 60]]
}

/* ----- bloqueos de agenda (schedule_blocks) ----- */

/**
 * Un block aplica a la fecha si esta cae dentro de [startDate, endDate]
 * (inclusive en ambos extremos). Las fechas vienen del backend como YYYY-MM-DD.
 */
const isDateInBlockRange = (date, block) => {
  const ymd = keyOf(date)
  return ymd >= block.startDate && ymd <= block.endDate
}

/**
 * Filtra los `blocks` quedándose solo con los que aplican a una "celda" del
 * calendario, identificada por (date, resourceType, resourceId):
 *
 *  - block GLOBAL  (membershipId=null y boothId=null): aplica a TODA celda
 *    en su rango de fechas, sea cual sea el recurso.
 *  - block POR EMPLEADO (membershipId set): solo aplica si la celda es del
 *    mismo empleado (resourceType='employee' y resourceId coincide).
 *  - block POR CABINA   (boothId set):       analogo con 'booth'.
 *
 * Si se llama con resourceType=null (vista cronologica sin sub-columnas),
 * solo se devuelven los blocks globales: los parciales no son representables
 * en una columna unica del dia y el caller los ignora.
 */
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

/**
 * Etiqueta corta para mostrar en el overlay del bloqueo. Si el block trae
 * `reason`, lo usa; si no, cae a un texto generico segun el tipo.
 */
export const labelForBlock = (b) => {
  if (b.reason && b.reason.trim()) return b.reason
  if (b.membershipId != null) return 'Empleado bloqueado'
  if (b.boothId != null) return 'Cabina bloqueada'
  return 'Día bloqueado'
}

/**
 * Devuelve el primer schedule_block que aplica a una cita concreta, o null.
 * Considera los 3 tipos (global, por empleado de la cita, por cabina de la
 * cita). Lo usan los detalles del calendario para avisar al admin de que
 * la cita ha quedado dentro de un bloqueo y debe reagendarse.
 */
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

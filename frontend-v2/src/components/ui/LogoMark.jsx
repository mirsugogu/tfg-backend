/** Marca de OPTIMA dibujada inline en SVG. */
export function LogoMark({ size = 44 }) {
  const inner = Math.round(size * 0.52)
  return (
    <div
      className="rounded-xl bg-gradient-to-br from-cyan-400 to-blue-600 flex items-center justify-center shrink-0 shadow-[0_8px_22px_-8px_rgba(37,99,235,0.6)]"
      style={{ width: size, height: size }}
    >
      <svg width={inner} height={inner} viewBox="0 0 24 24" fill="none">
        <circle cx="12" cy="12" r="7" stroke="white" strokeWidth="3.2" />
        <circle cx="12" cy="12" r="2.6" fill="white" />
      </svg>
    </div>
  )
}

import { clsx } from 'clsx'
import { twMerge } from 'tailwind-merge'

/** Combina clases con clsx y resuelve conflictos de estilos con twMerge */
export function cn(...inputs) {
  return twMerge(clsx(inputs))
}

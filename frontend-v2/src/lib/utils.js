import { clsx } from 'clsx'
import { twMerge } from 'tailwind-merge'

/** Combina clases con clsx y resuelve conflictos de Tailwind con twMerge. */
export function cn(...inputs) {
  return twMerge(clsx(inputs))
}

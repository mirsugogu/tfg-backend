// Mapa interactivo Leaflet con marcador para la ubicacion del negocio
import { useEffect, useRef } from 'react'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'

/** Mapa mapa externo con un pin en las coordenadas dadas */
export function LocationMap({ lat, lon }) {
  const containerRef = useRef(null)
  const mapRef = useRef(null)
  const markerRef = useRef(null)

  useEffect(() => {
    if (!containerRef.current || mapRef.current) return
    const map = L.map(containerRef.current).setView([40.4168, -3.7038], 5)
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; OpenStreetMap',
      maxZoom: 19,
    }).addTo(map)
    mapRef.current = map
    // El contenedor puede no tener su tamano definitivo justo al montar
    setTimeout(() => map.invalidateSize(), 120)
    return () => {
      map.remove()
      mapRef.current = null
      markerRef.current = null
    }
  }, [])

  useEffect(() => {
    const map = mapRef.current
    if (!map || lat == null || lon == null) return
    map.setView([lat, lon], 16)
    const icon = L.divIcon({
      className: '',
      html: '<div style="width:22px;height:22px;border-radius:50% 50% 50% 0;background:linear-gradient(135deg,#22d3ee,#2563eb);transform:rotate(-45deg);box-shadow:0 4px 10px -2px rgba(37,99,235,.5);border:2px solid #fff"></div>',
      iconSize: [22, 22],
      iconAnchor: [11, 22],
    })
    if (markerRef.current) markerRef.current.setLatLng([lat, lon])
    else markerRef.current = L.marker([lat, lon], { icon }).addTo(map)
  }, [lat, lon])

  return (
    <div
      ref={containerRef}
      className="h-56 w-full rounded-2xl overflow-hidden border border-slate-200 bg-slate-100"
    />
  )
}

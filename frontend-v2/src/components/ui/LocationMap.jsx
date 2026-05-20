import { useEffect, useRef } from 'react'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'

/**
 * LocationMap — mapa de OpenStreetMap (con la librería Leaflet) que
 * muestra un pin en unas coordenadas. Se usa como vista previa de la
 * ubicación del negocio en el registro.
 *
 * Leaflet es una librería de mapas gratuita y sin API key; los mosaicos
 * los sirve OpenStreetMap — el mismo proyecto que el geocodificador
 * Nominatim que usa el backend para resolver direcciones.
 *
 * Props: lat, lon (números). Mientras no haya coordenadas el mapa se
 * queda centrado en una vista general.
 */
export function LocationMap({ lat, lon }) {
  const containerRef = useRef(null)
  const mapRef = useRef(null)
  const markerRef = useRef(null)

  // Crea el mapa una sola vez, cuando se monta el componente.
  useEffect(() => {
    if (!containerRef.current || mapRef.current) return
    const map = L.map(containerRef.current).setView([40.4168, -3.7038], 5)
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; OpenStreetMap',
      maxZoom: 19,
    }).addTo(map)
    mapRef.current = map
    // El contenedor puede no tener su tamaño definitivo justo al montar.
    setTimeout(() => map.invalidateSize(), 120)
    return () => {
      map.remove()
      mapRef.current = null
      markerRef.current = null
    }
  }, [])

  // Centra el mapa y coloca / mueve el pin al cambiar las coordenadas.
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

import { useEffect, useState } from 'react';
import { api } from '../api/cliente';
import { useActualizacionPeriodicaVisible } from '../api/useActualizacionPeriodicaVisible';
import { useSesionHuesped } from '../api/useSesionHuesped';

/**
 * Aviso de mensajes sin leer junto a "Mis reservas". Solo para huésped con sesión: sin
 * sesión no hay nada que contar y con cero no se muestra nada en vez de un cero gris.
 */
export function BadgeMensajes() {
  const sesion = useSesionHuesped();
  const [nuevos, setNuevos] = useState(0);

  async function actualizarMensajesNuevos() {
    if (sesion.haySesion !== true) {
      setNuevos(0);
      return;
    }
    try {
      const datos = await api.get<{ nuevos: number }>('/api/mis-reservas/mensajes/nuevos');
      setNuevos(datos.nuevos);
    } catch {
      // Una falla transitoria no oculta el último conteo válido.
    }
  }

  useEffect(() => {
    void actualizarMensajesNuevos();
  }, [sesion.haySesion]);
  useActualizacionPeriodicaVisible(actualizarMensajesNuevos, sesion.haySesion === true);

  if (nuevos <= 0) return null;
  return (
    <span className="insignia" role="status" aria-label={`${nuevos} mensajes sin leer`}>
      {nuevos} sin leer
    </span>
  );
}

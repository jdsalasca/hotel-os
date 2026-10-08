import { useEffect, useState } from 'react';
import { api } from '../api/cliente';
import { useSesionHuesped } from '../api/useSesionHuesped';

/**
 * Aviso de mensajes sin leer junto a "Mis reservas". Solo para huésped con sesión: sin
 * sesión no hay nada que contar y con cero no se muestra nada en vez de un cero gris.
 */
export function BadgeMensajes() {
  const sesion = useSesionHuesped();
  const [nuevos, setNuevos] = useState(0);

  useEffect(() => {
    if (sesion.haySesion !== true) {
      setNuevos(0);
      return;
    }
    void api
      .get<{ nuevos: number }>('/api/mis-reservas/mensajes/nuevos')
      .then((datos) => setNuevos(datos.nuevos))
      .catch(() => setNuevos(0));
  }, [sesion.haySesion]);

  if (nuevos <= 0) return null;
  return (
    <span className="insignia" aria-label={`${nuevos} mensajes sin leer`}>
      {nuevos} sin leer
    </span>
  );
}

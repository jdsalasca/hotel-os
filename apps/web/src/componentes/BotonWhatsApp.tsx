import { useEffect, useState } from 'react';
import { api } from '../api/cliente';
import { enlaceWhatsApp } from '../api/formato';

/**
 * Hablar con el hotel por WhatsApp con la reserva ya escrita. El teléfono sale de los datos
 * del hotel; sin teléfono no hay botón en vez de un enlace roto. Solo el momento de la
 * confirmación lo usa: ahí es donde nacen las dudas ("¿a qué hora puedo llegar?").
 */
export function BotonWhatsApp({ codigo }: { codigo: string }) {
  const [enlace, setEnlace] = useState<string | null>(null);

  useEffect(() => {
    void api
      .get<Record<string, string>>('/api/hotel')
      .then((datos) => {
        setEnlace(enlaceWhatsApp(datos.contacto_telefono ?? '', `Hola, mi reserva es ${codigo}`));
      })
      .catch(() => setEnlace(null));
  }, [codigo]);

  if (!enlace) return null;
  return (
    <p className="sin-margen">
      <a
        className="boton boton--secundario"
        href={enlace}
        target="_blank"
        rel="noopener noreferrer"
      >
        ¿Dudas? Escríbenos por WhatsApp
      </a>
    </p>
  );
}

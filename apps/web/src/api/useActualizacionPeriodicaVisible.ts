import { useEffect, useRef } from 'react';

const INTERVALO_ACTUALIZACION_MS = 30_000;
type Actualizador = () => void | Promise<unknown>;

/** Refresca información secundaria solo si la pestaña está a la vista. */
export function useActualizacionPeriodicaVisible(
  actualizar: Actualizador,
  habilitada = true,
) {
  const actualizarRef = useRef(actualizar);

  useEffect(() => {
    actualizarRef.current = actualizar;
  }, [actualizar]);

  useEffect(() => {
    if (!habilitada) return;

    const ejecutar = () => {
      if (document.visibilityState !== 'visible') return;
      void Promise.resolve(actualizarRef.current()).catch(() => undefined);
    };
    const alCambiarVisibilidad = () => {
      if (document.visibilityState === 'visible') ejecutar();
    };

    const temporizador = window.setInterval(ejecutar, INTERVALO_ACTUALIZACION_MS);
    document.addEventListener('visibilitychange', alCambiarVisibilidad);

    return () => {
      window.clearInterval(temporizador);
      document.removeEventListener('visibilitychange', alCambiarVisibilidad);
    };
  }, [habilitada]);
}

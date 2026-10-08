import { useEffect, useRef, useState } from 'react';
import { urlApi } from './cliente';
import { crearEstadoSesion } from './sesion';

type Yo = { email: string; nombre: string; tieneReservas: boolean };

async function pedirYo(): Promise<Yo | null> {
  const r = await fetch(urlApi('/api/yo'), { credentials: 'same-origin' });
  if (!r.ok) return null;
  return (await r.json()) as Yo;
}

/** Una sola pregunta para todos los montados: ver `sesion.ts`. */
const sesionHuesped = crearEstadoSesion(pedirYo);

/**
 * Sesión del huésped (Google). El backend usa sesión con cookie HttpOnly, igual que el panel: aquí
 * solo se sabe quién es y si hay sesión. Nunca se guarda el token en localStorage.
 *
 * Es un hook aparte del del panel a propósito: uno pregunta "soy admin" y este "soy huésped", y el
 * backend devuelve 401 en cada caso si la sesión no vale para esa puerta.
 */
export function useSesionHuesped() {
  const [yo, setYo] = useState<Yo | null>(null);
  const [haySesion, setHaySesion] = useState<boolean | null>(null);
  const vigente = useRef(true);

  async function comprobar(): Promise<void> {
    sesionHuesped.olvidar();
    const e = await sesionHuesped.leer();
    if (!vigente.current) return;
    setYo(e.datos);
    setHaySesion(e.haySesion);
  }

  useEffect(() => {
    vigente.current = true;
    void sesionHuesped.leer().then((e) => {
      if (!vigente.current) return;
      setYo(e.datos);
      setHaySesion(e.haySesion);
    });
    return () => {
      vigente.current = false;
    };
  }, []);

  return {
    haySesion,
    email: yo?.email ?? '',
    nombre: yo?.nombre ?? '',
    tieneReservas: yo?.tieneReservas ?? false,
    comprobar,
    /**
     * Igual que en el panel: true solo con confirmación del servidor; en caso
     * contrario la sesión se conserva y se avisa false para mostrar el motivo.
     */
    async salir(): Promise<boolean> {
      let r: Response;
      try {
        r = await fetch(urlApi('/api/huesped/logout'), {
          method: 'POST',
          headers: { 'X-XSRF-TOKEN': leerCsrf() },
          credentials: 'same-origin',
        });
      } catch {
        return false;
      }
      if (!r.ok) return false;
      sesionHuesped.olvidar();
      if (!vigente.current) return true;
      setHaySesion(false);
      setYo(null);
      return true;
    },
  };
}

function leerCsrf(): string {
  const c = document.cookie.split('; ').find((f) => f.startsWith('XSRF-TOKEN='));
  return c ? decodeURIComponent(c.split('=').slice(1).join('=')) : '';
}

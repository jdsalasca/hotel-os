import { useEffect, useRef, useState } from 'react';
import { urlApi } from './cliente';
import { crearEstadoSesion } from './sesion';

type DatosAdmin = { email: string; nombre?: string };

async function pedirAdmin(): Promise<DatosAdmin | null> {
  const r = await fetch(urlApi('/api/admin/sesion'), { credentials: 'same-origin' });
  if (!r.ok) return null;
  const cuerpo = (await r.json()) as { email?: string; nombre?: string };
  return { email: cuerpo.email ?? '', nombre: cuerpo.nombre ?? '' };
}

/** Una sola pregunta para todos los montados: ver `sesion.ts`. */
const sesionAdmin = crearEstadoSesion(pedirAdmin);

/**
 * Sesión administrativa. El backend usa sesión con cookie HttpOnly y CSRF por cookie: aquí solo
 * se sabe si hay sesión. Nunca se guarda el token en localStorage ni en memoria accesible.
 */
export function useSesion() {
  const [estado, setEstado] = useState<{ haySesion: boolean; email: string; nombre: string } | null>(
    null,
  );
  const vigente = useRef(true);

  function fijar(e: { haySesion: boolean; datos: DatosAdmin | null }) {
    if (!vigente.current) return;
    setEstado({ haySesion: e.haySesion, email: e.datos?.email ?? '', nombre: e.datos?.nombre ?? '' });
  }

  useEffect(() => {
    vigente.current = true;
    void sesionAdmin.leer().then(fijar);
    return () => {
      vigente.current = false;
    };
  }, []);

  async function comprobar(): Promise<void> {
    sesionAdmin.olvidar();
    fijar(await sesionAdmin.leer());
  }

  return {
    haySesion: estado === null ? null : estado.haySesion,
    email: estado?.email ?? '',
    nombre: estado?.nombre ?? '',
    comprobar,
    async entrar(email: string, clave: string): Promise<'autenticado' | 'cambio_requerido'> {
      const r = await fetch(urlApi('/api/admin/login'), {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-XSRF-TOKEN': leerCsrf(),
        },
        credentials: 'same-origin',
        body: JSON.stringify({ email, password: clave }),
      });
      const cuerpo = await r.json().catch(() => ({}));
      if (!r.ok) throw new Error(cuerpo.error ?? 'Credenciales inválidas');
      if (cuerpo.estado === 'cambio_requerido') return 'cambio_requerido';
      sesionAdmin.olvidar();
      await comprobar();
      return 'autenticado';
    },
    async cambiarClave(email: string, actual: string, nueva: string) {
      const r = await fetch(urlApi('/api/admin/password'), {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-XSRF-TOKEN': leerCsrf(),
        },
        credentials: 'same-origin',
        body: JSON.stringify({ email, actual, nueva }),
      });
      const cuerpo = await r.json().catch(() => ({}));
      if (!r.ok) throw new Error(cuerpo.error ?? 'No se pudo cambiar la contraseña');
    },
    /**
     * Cierra en el servidor y solo entonces limpia lo local. Avisa true con la
     * confirmación; con 403/500 o sin red avisa false y la sesión se conserva:
     * declarar el cierre sin confirmarlo dejaba la cookie válida y al personal
     * creyendo que había salido.
     */
    async salir(): Promise<boolean> {
      const csrf = leerCsrf();
      let r: Response;
      try {
        r = await fetch(urlApi('/api/admin/logout'), {
          method: 'POST',
          headers: { 'X-XSRF-TOKEN': csrf },
          credentials: 'same-origin',
        });
      } catch {
        return false;
      }
      if (!r.ok) return false;
      sesionAdmin.olvidar();
      if (vigente.current) setEstado({ haySesion: false, email: '', nombre: '' });
      return true;
    },
  };
}

function leerCsrf(): string {
  const c = document.cookie.split('; ').find((f) => f.startsWith('XSRF-TOKEN='));
  return c ? decodeURIComponent(c.split('=').slice(1).join('=')) : '';
}

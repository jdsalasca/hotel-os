import { useEffect, useRef, useState } from 'react';
import { urlApi } from './cliente';
import { crearEstadoSesion } from './sesion';

type DatosAdmin = { email: string };

async function pedirAdmin(): Promise<DatosAdmin | null> {
  const r = await fetch(urlApi('/api/admin/sesion'), { credentials: 'same-origin' });
  if (!r.ok) return null;
  const cuerpo = (await r.json()) as { email?: string };
  return { email: cuerpo.email ?? '' };
}

/** Una sola pregunta para todos los montados: ver `sesion.ts`. */
const sesionAdmin = crearEstadoSesion(pedirAdmin);

/**
 * Sesión administrativa. El backend usa sesión con cookie HttpOnly y CSRF por cookie: aquí solo
 * se sabe si hay sesión. Nunca se guarda el token en localStorage ni en memoria accesible.
 */
export function useSesion() {
  const [estado, setEstado] = useState<{ haySesion: boolean; email: string } | null>(null);
  const vigente = useRef(true);

  useEffect(() => {
    vigente.current = true;
    void sesionAdmin.leer().then((e) => {
      if (vigente.current) setEstado({ haySesion: e.haySesion, email: e.datos?.email ?? '' });
    });
    return () => {
      vigente.current = false;
    };
  }, []);

  async function comprobar(): Promise<void> {
    sesionAdmin.olvidar();
    const e = await sesionAdmin.leer();
    if (vigente.current) setEstado({ haySesion: e.haySesion, email: e.datos?.email ?? '' });
  }

  return {
    haySesion: estado === null ? null : estado.haySesion,
    email: estado?.email ?? '',
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
    async salir() {
      const csrf = leerCsrf();
      await fetch(urlApi('/api/admin/logout'), {
        method: 'POST',
        headers: { 'X-XSRF-TOKEN': csrf },
        credentials: 'same-origin',
      });
      sesionAdmin.olvidar();
      if (vigente.current) setEstado({ haySesion: false, email: '' });
    },
  };
}

function leerCsrf(): string {
  const c = document.cookie.split('; ').find((f) => f.startsWith('XSRF-TOKEN='));
  return c ? decodeURIComponent(c.split('=').slice(1).join('=')) : '';
}

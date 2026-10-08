/**
 * Estado de sesión compartido entre todos los hooks montados.
 *
 * Cada página preguntaba por su cuenta y la cabecera otras dos veces: varias peticiones por
 * navegación para la misma pregunta. Aquí las lecturas a la vez comparten el vuelo y listo:
 * sin caché con TTL, porque una sesión cacheada miente (cierra en otra pestaña y el panel
 * seguiría saludando). `olvidar` es para login/logout: lo que venga en camino se descarta y
 * lo siguiente vuelve a preguntar.
 */

export type EstadoSesion<T> = { haySesion: boolean; datos: T | null };

export function crearEstadoSesion<T>(pedir: () => Promise<T | null>) {
  let vuelo: Promise<EstadoSesion<T>> | null = null;

  function leer(): Promise<EstadoSesion<T>> {
    if (!vuelo) {
      vuelo = pedir().then(
        (datos) => ({ haySesion: datos !== null, datos }),
        () => ({ haySesion: false, datos: null }),
      );
      void vuelo.finally(() => {
        vuelo = null;
      });
    }
    return vuelo;
  }

  function olvidar(): void {
    vuelo = null;
  }

  return { leer, olvidar };
}

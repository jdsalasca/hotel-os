import { useEffect, useRef, useState } from 'react';

/**
 * Estados de carga y error sin librería: la app es pequeña y esto es todo lo que necesita.
 *
 * Con una garantía: cada `ejecutar` lleva un número de petición y solo la última pinta.
 * Si el huésped busca B antes de que vuelva A, la A tardía se ignora en vez de
 * sobreescribir lo de B. `abortar()` invalida lo que venga en camino sin mensaje de error,
 * y al desmontar no se toca estado.
 */
export function usePeticion<T>() {
  const [datos, setDatos] = useState<T | null>(null);
  const [cargando, setCargando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const peticion = useRef(0);
  const montada = useRef(true);

  useEffect(() => {
    montada.current = true;
    return () => {
      montada.current = false;
      peticion.current++;
    };
  }, []);

  function vigente(id: number): boolean {
    return montada.current && peticion.current === id;
  }

  async function ejecutar(tarea: () => Promise<T>): Promise<T | null> {
    const mia = ++peticion.current;
    setCargando(true);
    setError(null);
    try {
      const resultado = await tarea();
      if (!vigente(mia)) return null;
      setDatos(resultado);
      return resultado;
    } catch (e) {
      if (!vigente(mia)) return null;
      // Una cancelación deliberada no es un error que mostrar (ver cliente.ts: viaja por nombre).
      if ((e as { name?: unknown } | null)?.name === 'AbortError') return null;
      setError(e instanceof Error ? e.message : 'Error inesperado');
      return null;
    } finally {
      if (vigente(mia)) setCargando(false);
    }
  }

  function abortar(): void {
    peticion.current++;
    if (montada.current) setCargando(false);
  }

  return { datos, cargando, error, ejecutar, abortar };
}

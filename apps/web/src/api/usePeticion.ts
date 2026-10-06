import { useState } from 'react';

/** Estados de carga y error sin librería: la app es pequeña y esto es todo lo que necesita. */
export function usePeticion<T>() {
  const [datos, setDatos] = useState<T | null>(null);
  const [cargando, setCargando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function ejecutar(tarea: () => Promise<T>): Promise<T | null> {
    setCargando(true);
    setError(null);
    try {
      const resultado = await tarea();
      setDatos(resultado);
      return resultado;
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Error inesperado');
      return null;
    } finally {
      setCargando(false);
    }
  }

  return { datos, cargando, error, ejecutar };
}
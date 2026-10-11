import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/cliente';
import { Aviso } from './Estado';

export type PuntoPreparacion = { clave: string; titulo: string; hecho: boolean; url: string | null };

/**
 * Lo que le falta al hotel para estar a punto, con enlace a donde se hace. Si todo está
 * hecho no se muestra nada: un panel completo no necesita una lista vacía recordándolo.
 */
export function ListaPreparacion({ puntos: fijos }: { puntos?: PuntoPreparacion[] }) {
  const [puntos, setPuntos] = useState<PuntoPreparacion[] | null>(fijos ?? null);
  const [error, setError] = useState(false);
  const [cargando, setCargando] = useState(fijos === undefined);
  const [intento, setIntento] = useState(0);

  useEffect(() => {
    if (fijos !== undefined) return;
    let vigente = true;
    setCargando(true);
    void api
      .get<{ items: PuntoPreparacion[] }>('/api/admin/preparacion')
      .then((datos) => {
        if (!vigente) return;
        setPuntos(datos.items);
        setError(false);
      })
      .catch(() => {
        if (!vigente) return;
        // No confundir un fallo del servidor con un checklist completado.
        setPuntos(null);
        setError(true);
      })
      .finally(() => {
        if (vigente) setCargando(false);
      });
    return () => {
      vigente = false;
    };
  }, [fijos, intento]);

  if (error) {
    return (
      <section className="tarjeta pila mb-e6" aria-label="Pon tu hotel a punto">
        <Aviso tono="error" titulo="No se pudo cargar la preparación del hotel">
          <p>No pudimos comprobar si quedan pasos pendientes. Inténtalo de nuevo.</p>
          <button
            className="boton boton--primario"
            type="button"
            disabled={cargando}
            onClick={() => {
              setCargando(true);
              setIntento((anterior) => anterior + 1);
            }}
          >
            {cargando ? 'Reintentando…' : 'Reintentar'}
          </button>
        </Aviso>
      </section>
    );
  }

  if (puntos === null || cargando) return null;
  const pendientes = puntos.filter((p) => !p.hecho);
  if (pendientes.length === 0) return null;

  return (
    <section className="tarjeta pila mb-e6" aria-label="Pon tu hotel a punto">
      <h2 className="t-lg mb-0">Pon tu hotel a punto ({pendientes.length} pendientes)</h2>
      <ul className="lista-marcada">
        {pendientes.map((p) => (
          <li key={p.clave}>
            {p.url ? <Link to={p.url}>{p.titulo}</Link> : <span>{p.titulo}</span>}
          </li>
        ))}
      </ul>
    </section>
  );
}

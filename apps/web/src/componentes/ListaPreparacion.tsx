import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/cliente';

export type PuntoPreparacion = { clave: string; titulo: string; hecho: boolean; url: string };

/**
 * Lo que le falta al hotel para estar a punto, con enlace a donde se hace. Si todo está
 * hecho no se muestra nada: un panel completo no necesita una lista vacía recordándolo.
 */
export function ListaPreparacion({ puntos: fijos }: { puntos?: PuntoPreparacion[] }) {
  const [puntos, setPuntos] = useState<PuntoPreparacion[] | null>(fijos ?? null);

  useEffect(() => {
    if (fijos) return;
    void api
      .get<{ items: PuntoPreparacion[] }>('/api/admin/preparacion')
      .then((datos) => setPuntos(datos.items))
      .catch(() => setPuntos([]));
  }, [fijos]);

  if (puntos === null) return null;
  const pendientes = puntos.filter((p) => !p.hecho);
  if (pendientes.length === 0) return null;

  return (
    <section className="tarjeta pila mb-e6" aria-label="Pon tu hotel a punto">
      <h2 className="t-lg mb-0">Pon tu hotel a punto ({pendientes.length} pendientes)</h2>
      <ul className="lista-marcada">
        {pendientes.map((p) => (
          <li key={p.clave}>
            <Link to={p.url}>{p.titulo}</Link>
          </li>
        ))}
      </ul>
    </section>
  );
}

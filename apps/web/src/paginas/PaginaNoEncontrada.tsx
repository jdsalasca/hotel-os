import { Link } from 'react-router-dom';
import { Vacio } from '../componentes/Estado';

/**
 * Comodín del router: enlace roto, marcador viejo o dedo mal puesto. Antes era un
 * hueco mudo entre cabecera y pie; ahora dice dónde estás y ofrece dos salidas.
 */
export function PaginaNoEncontrada() {
  return (
    <main id="contenido" className="centrado">
      <section className="seccion">
        <Vacio
          titulo="Esa página no existe"
          detalle="Revisa la dirección o sigue por aquí."
        />
        <p className="mt-e4">
          <Link className="boton boton--primario" to="/">
            Volver al inicio
          </Link>{' '}
          <Link className="boton boton--secundario" to="/consulta">
            Consultar mi reserva
          </Link>
        </p>
      </section>
    </main>
  );
}

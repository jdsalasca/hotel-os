import { Link } from 'react-router-dom';

/**
 * Condiciones de la reserva directa.
 *
 * Reflejan el flujo real: la solicitud queda pendiente de confirmación del hotel, el precio
 * mostrado se congela al confirmar (y si cambió, se avisa y se reconfirma), y la consulta es
 * con código y correo. Nada que la web no haga.
 */
export function PaginaTerminos() {
  return (
    <main id="contenido" className="centrado">
      <section className="seccion">
        <h1 className="seccion__titulo">Términos de la reserva</h1>
        <p className="seccion__intro">
          Las reglas de reservar directamente con el hotel, en lenguaje normal.
        </p>

        <h2 className="t-lg">Tu solicitud queda pendiente</h2>
        <p>
          Al confirmar no queda nada reservado en firme: tu solicitud llega al hotel como{' '}
          <strong>pendiente de confirmación</strong> y el personal la revisa. Te escribirán al
          correo que dejaste.
        </p>

        <h2 className="t-lg">El precio que ves es el precio que confirmas</h2>
        <p>
          Los importes son los que el hotel tiene configurados, sin cargos ocultos en esta web. Si
          una tarifa cambia entre que la ves y que confirmas, te mostramos el importe nuevo y te
          pedimos que lo aceptes de nuevo: nada se reserva con un precio que no hayas visto.
        </p>

        <h2 className="t-lg">Tu código es tu llave</h2>
        <p>
          Cada reserva tiene un código. Con él y tu correo puedes consultarla cuando quieras
          en <Link to="/consulta">Consultar reserva</Link>. Si entraste con Google, también la
          verás en <Link to="/mis-reservas">Mis reservas</Link> sin necesidad del código.
        </p>

        <h2 className="t-lg">Cambios y cancelaciones</h2>
        <p>
          Si tus planes cambian, avisa al hotel lo antes posible con tu código a mano: una
          habitación liberada a tiempo es una habitación que se puede volver a vender. Las
          condiciones concretas (plazos, cargos) te las confirma el hotel al aceptar tu solicitud.
        </p>

        <h2 className="t-lg">Sin pagos en esta web</h2>
        <p>
          Aquí no se cobra nada ni se piden tarjetas. Si el hotel te pide un abono o el pago, será
          por los canales que te indique al confirmar, nunca en un formulario de esta página.
        </p>

        <h2 className="t-lg">Uso correcto</h2>
        <p>
          Usa datos verdaderos y no reserves habitaciones que no vayas a usar: cada reserva
          bloquea fechas reales para otros huéspedes.
        </p>
      </section>
    </main>
  );
}

import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, urlApi } from '../api/cliente';
import { useSesionHuesped } from '../api/useSesionHuesped';
import { Aviso, Cargando, Etiqueta, MensajeError, Vacio } from '../componentes/Estado';

type Reserva = {
  codigo: string;
  llegada: string;
  salida: string;
  huespedes: number;
  estado: string;
  creado_en: string;
  total_cents: number | null;
  moneda: string | null;
};

function monto(cents: number | null, moneda: string | null): string | null {
  if (cents === null || moneda === null) return null;
  return new Intl.NumberFormat('es-CO', { style: 'currency', currency: moneda }).format(cents / 100);
}

/**
 * Las reservas del huésped que entró con Google. El backend las filtra por su usuario, así que
 * aquí no hay código ni correo: la sesión ya dice de quién son.
 */
export function PaginaMisReservas() {
  const sesion = useSesionHuesped();
  const [reservas, setReservas] = useState<Reserva[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (sesion.haySesion === false) return;
    api
      .get<{ reservas: Reserva[] }>('/api/mis-reservas')
      .then((datos) => setReservas(datos.reservas))
      .catch((e: Error) => setError(e.message));
  }, [sesion.haySesion]);

  if (sesion.haySesion === null) return <Cargando texto="Comprobando sesión" />;

  if (sesion.haySesion === false) {
    return (
      <main id="contenido" className="centrado">
        <section className="seccion ancho-acceso">
          <h1 className="seccion__titulo">Mis reservas</h1>
          <Aviso tono="aviso" titulo="Entra con Google">
            Con tu cuenta puedes ver tus reservas sin acordarte del código. También puedes
            consultar cualquier reserva con el código y el correo con el que la hiciste.
          </Aviso>
          <a
            className="boton boton--primario boton--bloque mt-e3"
            href={urlApi('/oauth2/authorization/google-huesped')}
          >
            Entrar con Google
          </a>
          <p className="mt-e3">
            <Link to="/consulta">Consultar una reserva sin entrar</Link>
          </p>
        </section>
      </main>
    );
  }

  return (
    <main id="contenido" className="centrado">
      <section className="seccion">
        <div className="pila mb-e4">
          <h1 className="seccion__titulo mb-0">Mis reservas</h1>
          <p className="seccion__intro mb-0">
            Hola, {sesion.nombre || sesion.email}. Aquí están las reservas de esta cuenta.
          </p>
        </div>

        <div className="pila gap-e2 mb-e4">
          <a className="boton boton--secundario boton--chico" href={urlApi('/oauth2/authorization/google-huesped')}>
            Vincular otra cuenta
          </a>
          <button className="boton boton--fantasma boton--chico" type="button" onClick={() => void sesion.salir()}>
            Salir
          </button>
        </div>

        {error ? <MensajeError texto={error} /> : null}
        {reservas === null && !error ? <Cargando /> : null}

        {reservas && reservas.length === 0 ? (
          <Vacio
            titulo="Todavía no has reservado"
            detalle="Cuando reserves con esta cuenta, la reserva aparecerá aquí sin necesidad de guardar el código."
          />
        ) : null}

        {reservas && reservas.length > 0 ? (
          <table className="tabla">
            <caption>Reservas de {sesion.email}</caption>
            <thead>
              <tr>
                <th scope="col">Código</th>
                <th scope="col">Llegada</th>
                <th scope="col">Salida</th>
                <th scope="col">Huéspedes</th>
                <th scope="col">Total acordado</th>
                <th scope="col">Estado</th>
              </tr>
            </thead>
            <tbody>
              {reservas.map((r) => (
                <tr key={r.codigo}>
                  <td data-label="Código">
                    <code>{r.codigo}</code>
                  </td>
                  <td className="cifra" data-label="Llegada">{r.llegada}</td>
                  <td className="cifra" data-label="Salida">{r.salida}</td>
                  <td className="cifra" data-label="Huéspedes">{r.huespedes}</td>
                  <td className="cifra" data-label="Total">{monto(r.total_cents, r.moneda) ?? 'Sin precio'}</td>
                  <td data-label="Estado">
                    <Etiqueta tono={r.estado === 'CONFIRMADA' ? 'exito' : r.estado === 'CANCELADA' ? 'error' : 'aviso'}>
                      {r.estado}
                    </Etiqueta>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : null}

        <p className="mt-e6">
          <Link to="/">Reservar otra vez</Link>
        </p>
      </section>
    </main>
  );
}
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/cliente';
import { fechaCorta, monto } from '../api/formato';
import { useSesion } from '../api/useSesion';
import { Aviso, Cargando, Etiqueta, MensajeError, Vacio } from '../componentes/Estado';

type Reserva = {
  codigo: string;
  email: string;
  nombre: string;
  llegada: string;
  salida: string;
  noches: number;
  huespedes: number;
  estado: string;
  origen: string;
  creadoEn: string;
};

type Detalle = {
  reserva: Reserva & { totalCents: number | null; moneda: string | null; plan: string | null };
  habitacion: { codigo: string; nombre: string; tipo: string } | null;
  historial: { estado_ant: string | null; estado_nuevo: string; actor: string; en: string }[];
};

const ESTADOS: { valor: string; texto: string }[] = [
  { valor: 'CONFIRMADA', texto: 'Confirmar' },
  { valor: 'CANCELADA', texto: 'Cancelar' },
  { valor: 'RECHAZADA', texto: 'Rechazar' },
];

export function PaginaAdminReservas() {
  const sesion = useSesion();
  const [reservas, setReservas] = useState<Reserva[] | null>(null);
  const [detalle, setDetalle] = useState<Detalle | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [cargando, setCargando] = useState(true);

  async function cargar() {
    setCargando(true);
    setError(null);
    try {
      setReservas(await api.get<Reserva[]>('/api/admin/reservas?limit=100'));
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudieron cargar las reservas');
    } finally {
      setCargando(false);
    }
  }

  useEffect(() => {
    void cargar();
  }, []);

  async function abrir(codigo: string) {
    setError(null);
    try {
      // El comprobante trae lo mismo que el detalle más habitación y total acordado.
      setDetalle(await api.get<Detalle>(`/api/admin/reservas/${codigo}/comprobante`));
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo abrir la reserva');
    }
  }

  async function cambiar(codigo: string, estado: string) {
    setError(null);
    try {
      await api.post(`/api/admin/reservas/${codigo}/estado`, { estado, actor: 'panel' });
      await cargar();
      await abrir(codigo);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo cambiar el estado');
    }
  }

  if (sesion.haySesion === false) return <RequiereSesion />;
  if (sesion.haySesion === null) return <Cargando texto="Comprobando sesión" />;

  return (
    <main id="contenido" className="centrado">
      <section className="seccion">
        <div className="pila mb-e4">
          <h1 className="seccion__titulo mb-0">Reservas</h1>
          <p className="seccion__intro mb-0">
            Cada reserva muestra su origen y su historial de cambios de estado.
          </p>
        </div>

        {error ? <MensajeError texto={error} /> : null}
        {cargando ? <Cargando /> : null}

        {!cargando && reservas && reservas.length === 0 ? (
          <Vacio titulo="Todavía no hay reservas" detalle="Aparecerán aquí las que lleguen por la web o por los canales." />
        ) : null}

        {reservas && reservas.length > 0 ? (
          <div className="tabla-envoltura">
            <table className="tabla">
              <caption>Reservas del hotel</caption>
              <thead>
                <tr>
                  <th scope="col">Código</th>
                  <th scope="col">Huésped</th>
                  <th scope="col">Fechas</th>
                  <th scope="col">Huéspedes</th>
                  <th scope="col">Origen</th>
                  <th scope="col">Estado</th>
                  <th scope="col"><span className="visually-hidden">Acciones</span></th>
                </tr>
              </thead>
              <tbody>
                {reservas.map((r) => (
                  <tr key={r.codigo}>
                    <td className="cifra" data-label="Código">{r.codigo}</td>
                    <td data-label="Huésped">{r.nombre || r.email}</td>
                    <td className="cifra" data-label="Fechas">
                      {fechaCorta(r.llegada)} → {fechaCorta(r.salida)}
                      <br />
                      <span className="campo__ayuda">{r.noches} noches</span>
                    </td>
                    <td className="cifra" data-label="Huéspedes">{r.huespedes}</td>
                    <td data-label="Origen"><Etiqueta tono="neutra">{r.origen}</Etiqueta></td>
                    <td data-label="Estado">
                      <Etiqueta tono={r.estado === 'CONFIRMADA' ? 'exito' : r.estado === 'CANCELADA' ? 'error' : 'aviso'}>
                        {r.estado}
                      </Etiqueta>
                    </td>
                    <td data-label="Acciones">
                      <div className="pila gap-e1">
                        {ESTADOS.filter((e) => e.valor !== r.estado).map((e) => (
                          <button
                            key={e.valor}
                            className={`boton boton--chico ${e.valor === 'CONFIRMADA' ? 'boton--primario' : 'boton--secundario'}`}
                            onClick={() => cambiar(r.codigo, e.valor)}
                            aria-label={`${e.texto} la reserva ${r.codigo}`}
                          >
                            {e.texto}
                          </button>
                        ))}
                        <button className="boton boton--chico boton--fantasma" onClick={() => abrir(r.codigo)}>
                          Ver detalle
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}

        {detalle ? (
          <aside className="tarjeta pila mt-e6" aria-labelledby="titulo-detalle">
            <h2 id="titulo-detalle" className="mb-0">{detalle.reserva.codigo}</h2>
            <p className="sin-margen">
              {detalle.reserva.nombre || detalle.reserva.email} · {fechaCorta(detalle.reserva.llegada)} →{' '}
              {fechaCorta(detalle.reserva.salida)}
            </p>
            <p className="sin-margen">
              {detalle.habitacion ? (
                <>Habitación {detalle.habitacion.codigo}{detalle.habitacion.tipo ? ` · ${detalle.habitacion.tipo}` : ''} · </>
              ) : null}
              {detalle.reserva.totalCents !== null && detalle.reserva.moneda ? (
                <>Total acordado: {monto(detalle.reserva.totalCents, detalle.reserva.moneda)}</>
              ) : (
                'Sin precio acordado: se reservó sin tarifa configurada'
              )}
            </p>
            <h3 className="t-base mb-0">Historial</h3>
            <ol className="pila gap-e1 lista-marcada">
              {detalle.historial.map((h, i) => (
                <li key={i} className="campo__ayuda">
                  {h.estado_ant ? `${h.estado_ant} → ` : 'creada como '}
                  <strong>{h.estado_nuevo}</strong> por {h.actor} el {h.en.slice(0, 16).replace('T', ' ')}
                </li>
              ))}
            </ol>
            <button className="boton boton--secundario boton--chico" onClick={() => setDetalle(null)}>
              Cerrar detalle
            </button>
          </aside>
        ) : null}
      </section>
    </main>
  );
}

function RequiereSesion() {
  return (
    <main id="contenido" className="centrado">
      <section className="seccion">
        <Aviso tono="aviso" titulo="Sesión requerida">
          <p>Esta pantalla es para el personal del hotel.</p>
          <Link className="boton boton--primario" to="/admin/entrar">
            Iniciar sesión
          </Link>
        </Aviso>
      </section>
    </main>
  );
}
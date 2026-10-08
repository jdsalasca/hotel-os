import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/cliente';
import { useSesion } from '../api/useSesion';
import { Cargando, Etiqueta, MensajeError, PuertaAdmin, Vacio } from '../componentes/Estado';

type Accion = {
  actor: string;
  metodo: string;
  ruta: string;
  estado: number;
  en: string;
};

/**
 * La ruta guardada es técnica. Sin traducirla esto sería un volcado de log que nadie del hotel
 * entiende. El orden importa: los prefijos más específicos van primero porque se comparan con
 * startsWith.
 */
const ACCIONES: [string, string][] = [
  ['/api/admin/integraciones/mapeos', 'tocó los mapeos de canales'],
  ['/api/admin/integraciones/', 'sincronizó un canal'],
  ['/api/admin/reservas/', 'cambió el estado de una reserva'],
  ['/api/admin/tipos', 'creó un tipo de habitación'],
  ['/api/admin/habitaciones/', 'modificó una habitación'],
  ['/api/admin/planes', 'creó un plan tarifario'],
  ['/api/admin/tarifas', 'fijó precios o disponibilidad'],
  ['/api/admin/bloqueos', 'registró un bloqueo'],
  ['/api/admin/hotel-config', 'cambió los datos del hotel'],
  ['/api/admin/correo/prueba', 'envió un correo de prueba'],
  ['/api/admin/indicadores', 'registró datos de indicadores'],
  ['/api/admin/login', 'inició sesión'],
  ['/api/admin/logout', 'cerró sesión'],
  ['/api/admin/init', 'creó el primer administrador'],
];

function describir(ruta: string): string {
  const encontrada = ACCIONES.find(([prefijo]) => ruta.startsWith(prefijo));
  return encontrada ? (encontrada[1] ?? ruta) : ruta;
}

/** /api/admin/reservas/H-ABC/estado -> H-ABC. El penúltimo segmento, no el último. */
function codigoReserva(ruta: string): string {
  const partes = ruta.split('/');
  return partes.length > 1 ? (partes[partes.length - 2] ?? ruta) : ruta;
}

/**
 * Quién tocó el panel y qué le pasó. El rastro se escribe solo en el servidor, así que esta
 * pantalla no puede mostrar acciones que nadie hizo.
 */
export function PaginaAdminAuditoria() {
  const sesion = useSesion();
  const [acciones, setAcciones] = useState<Accion[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api
      .get<Accion[]>('/api/admin/auditoria?limite=100')
      .then(setAcciones)
      .catch((e: Error) => setError(e.message));
  }, []);

  if (sesion.haySesion === false) {
    return <PuertaAdmin>Inicia sesión para ver el rastro de acciones.</PuertaAdmin>;
  }
  if (sesion.haySesion === null) return <Cargando texto="Comprobando sesión" />;

  return (
    <main id="contenido" className="centrado">
      <section className="seccion">
        <div className="pila mb-e4">
          <h1 className="seccion__titulo mb-0">Actividad del panel</h1>
          <p className="seccion__intro mb-0">
            Quién cambió qué, y si la operación le salió bien. El usuario sale de la sesión del
            navegador, no de lo que declare el formulario.
          </p>
        </div>

        {error ? <MensajeError texto={error} /> : null}
        {acciones === null && !error ? <Cargando /> : null}

        {acciones && acciones.length === 0 ? (
          <Vacio
            titulo="Todavía no hay actividad"
            detalle="En cuanto el panel cree un tipo de habitación, fije un precio o confirme una reserva, aparecerá aquí."
          />
        ) : null}

        {acciones && acciones.length > 0 ? (
          <table className="tabla">
            <caption>Últimas {acciones.length} acciones administrativas</caption>
            <thead>
              <tr>
                <th scope="col">Cuándo</th>
                <th scope="col">Quién</th>
                <th scope="col">Qué</th>
                <th scope="col">Resultado</th>
              </tr>
            </thead>
            <tbody>
              {acciones.map((a, i) => (
                <tr key={`${a.en}-${i}`}>
                  <td className="cifra">{a.en.slice(0, 16).replace('T', ' ')}</td>
                  <td>{a.actor}</td>
                  <td>
                    {describir(a.ruta)}
                    {a.ruta.startsWith('/api/admin/reservas/') ? (
                      <span className="campo__ayuda"> · {codigoReserva(a.ruta)}</span>
                    ) : null}
                  </td>
                  <td>
                    <Etiqueta tono={a.estado < 400 ? 'exito' : 'error'}>
                      {a.estado < 400 ? `Correcto (${a.estado})` : `Falló (${a.estado})`}
                    </Etiqueta>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : null}

        <p className="mt-e6">
          <Link to="/admin/reservas">Volver a las reservas</Link>
        </p>
      </section>
    </main>
  );
}
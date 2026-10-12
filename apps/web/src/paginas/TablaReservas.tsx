import { fechaCorta } from '../api/formato';
import { Etiqueta } from '../componentes/Estado';

export type Estado = 'PENDIENTE' | 'CONFIRMADA' | 'CANCELADA' | 'RECHAZADA' | 'NO_PRESENTADA';

export type Reserva = {
  codigo: string;
  email: string;
  nombre: string;
  llegada: string;
  salida: string;
  noches: number;
  huespedes: number;
  estado: Estado;
  /** Estados que el servidor acepta desde aquí. La lista la manda el dominio, no el panel. */
  siguientes: Estado[];
  origen: string;
  creadoEn: string;
};

export const TEXTO_ESTADO: Record<Estado, string> = {
  PENDIENTE: 'Pendiente',
  CONFIRMADA: 'Confirmada',
  CANCELADA: 'Cancelada',
  RECHAZADA: 'Rechazada',
  NO_PRESENTADA: 'No se presentó',
};

const ESTADOS: { valor: Estado; texto: string }[] = [
  { valor: 'CONFIRMADA', texto: 'Confirmar' },
  { valor: 'CANCELADA', texto: 'Cancelar' },
  { valor: 'RECHAZADA', texto: 'Rechazar' },
  { valor: 'NO_PRESENTADA', texto: 'No se presentó' },
];

function accionesPosibles(reserva: Reserva) {
  return ESTADOS.filter((estado) => reserva.siguientes.includes(estado.valor));
}

function tonoEstado(estado: Estado): 'exito' | 'error' | 'aviso' {
  if (estado === 'CONFIRMADA') return 'exito';
  if (estado === 'CANCELADA' || estado === 'RECHAZADA' || estado === 'NO_PRESENTADA') return 'error';
  return 'aviso';
}

type Props = {
  reservas: Reserva[];
  detalleAbierto: boolean;
  mensajesNuevos: Record<string, number>;
  onCambiar: (codigo: string, estado: Estado) => void;
  onVerDetalle: (codigo: string) => void;
};

export function TablaReservas({
  reservas,
  detalleAbierto,
  mensajesNuevos,
  onCambiar,
  onVerDetalle,
}: Props) {
  return (
    <div className={detalleAbierto ? 'tabla-envoltura no-imprimir' : 'tabla-envoltura'}>
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
          {reservas.map((reservaFila) => {
            const acciones = accionesPosibles(reservaFila);
            const mensajes = mensajesNuevos[reservaFila.codigo] ?? 0;

            return (
              <tr key={reservaFila.codigo}>
                <td className="cifra" data-label="Código">{reservaFila.codigo}</td>
                <td data-label="Huésped">{reservaFila.nombre || reservaFila.email}</td>
                <td className="cifra" data-label="Fechas">
                  {fechaCorta(reservaFila.llegada)} → {fechaCorta(reservaFila.salida)}
                  <br />
                  <span className="campo__ayuda">{reservaFila.noches} noches</span>
                </td>
                <td className="cifra" data-label="Huéspedes">{reservaFila.huespedes}</td>
                <td data-label="Origen"><Etiqueta tono="neutra">{reservaFila.origen}</Etiqueta></td>
                <td data-label="Estado">
                  <Etiqueta tono={tonoEstado(reservaFila.estado)}>
                    {TEXTO_ESTADO[reservaFila.estado]}
                  </Etiqueta>
                </td>
                <td data-label="Acciones">
                  <div className="pila gap-e1">
                    {acciones.map((accion) => (
                      <button
                        key={accion.valor}
                        className={`boton boton--chico ${accion.valor === 'CONFIRMADA' ? 'boton--primario' : 'boton--secundario'}`}
                        onClick={() => onCambiar(reservaFila.codigo, accion.valor)}
                        aria-label={`${accion.texto} la reserva ${reservaFila.codigo}`}
                      >
                        {accion.texto}
                      </button>
                    ))}
                    {acciones.length === 0 ? <span className="t-sm tenue">Sin acciones</span> : null}
                    <button
                      className="boton boton--chico boton--fantasma"
                      onClick={() => onVerDetalle(reservaFila.codigo)}
                    >
                      Ver detalle
                      {mensajes > 0 ? (
                        <span
                          className="insignia"
                          role="status"
                          aria-label={`${mensajes} mensajes sin leer`}
                        >
                          {mensajes} sin leer
                        </span>
                      ) : null}
                    </button>
                  </div>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

import { useEffect, useRef, useState } from 'react';
import { api } from '../api/cliente';
import { hoyIso, monto } from '../api/formato';
import { useActualizacionPeriodicaVisible } from '../api/useActualizacionPeriodicaVisible';
import { useSesion } from '../api/useSesion';
import { Aviso, Cargando, Etiqueta, MensajeError, PuertaAdmin, Vacio } from '../componentes/Estado';

type Movimiento = {
  codigo: string;
  email: string;
  nombre: string;
  huespedes: number;
  habitacion: string;
  /** Saldo: solo viene si la reserva se hizo con tarifa. Sin él, no se muestra importe. */
  abonadoCents?: number;
  pendienteCents?: number;
  moneda?: string;
};

/**
 * Saldo junto al movimiento para consultar el pago sin abrir la reserva.
 * Sin total acordado no hay una cifra fiable que mostrar.
 */
function saldo(m: Movimiento) {
  if (m.pendienteCents === undefined || m.abonadoCents === undefined || !m.moneda) return null;
  if (m.pendienteCents <= 0) return { pagada: true, texto: 'PAGADA' };
  return {
    pagada: false,
    texto: `Pendiente ${monto(m.pendienteCents, m.moneda)}`,
  };
}

type ParteDia = {
  fecha: string;
  llegadas: Movimiento[];
  salidas: Movimiento[];
  enCasa: Movimiento[];
};

/**
 * Parte del día para la recepción: quién llega y quién se va, sin SQL.
 *
 * Es la primera pantalla que mira el turno de la mañana: con la fecha de hoy por defecto y las
 * habitaciones de cada movimiento, no hace falta recorrer la lista completa de reservas.
 */
export function PaginaAdminHoy() {
  const sesion = useSesion();
  const [fecha, setFecha] = useState(hoyIso());
  const [parte, setParte] = useState<ParteDia | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [falloActualizacion, setFalloActualizacion] = useState(false);
  const [cargando, setCargando] = useState(true);
  const peticionActual = useRef(0);
  const sondeoActual = useRef<number | null>(null);

  async function leer(enSegundoPlano: boolean) {
    if (!fecha || (enSegundoPlano && (sondeoActual.current !== null || cargando))) return;
    const id = ++peticionActual.current;
    const fechaConsultada = fecha;
    if (enSegundoPlano) {
      sondeoActual.current = id;
    } else {
      setCargando(true);
      setError(null);
      setFalloActualizacion(false);
    }
    try {
      const respuesta = await api.get<ParteDia>(`/api/admin/ocupacion/dia?fecha=${fechaConsultada}`);
      if (peticionActual.current === id) {
        setParte(respuesta);
        setFalloActualizacion(false);
      }
    } catch (e) {
      if (enSegundoPlano && peticionActual.current === id) {
        setFalloActualizacion(true);
      }
      // Un fallo de fondo no borra la información que recepción ya está consultando.
      if (!enSegundoPlano && peticionActual.current === id) {
        setParte(null);
        setError(e instanceof Error ? e.message : 'No se pudo cargar el parte');
      }
    } finally {
      if (sondeoActual.current === id) sondeoActual.current = null;
      if (!enSegundoPlano && peticionActual.current === id) setCargando(false);
    }
  }

  useEffect(() => {
    void leer(false);
    return () => {
      // Una respuesta de la fecha anterior no puede pisar el parte seleccionado ahora.
      peticionActual.current += 1;
      sondeoActual.current = null;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fecha]);

  // El parte permanece abierto durante el turno; se refresca cada 30 s y se pausa con la pestaña oculta.
  useActualizacionPeriodicaVisible(() => leer(true));

  if (sesion.haySesion === false) {
    return (
      <main id="contenido" className="centrado">
        <section className="seccion">
          <PuertaAdmin>Esta pantalla es para el personal del hotel.</PuertaAdmin>
        </section>
      </main>
    );
  }
  if (sesion.haySesion === null) return <Cargando texto="Comprobando sesión" />;

  return (
    <main id="contenido" className="centrado">
      <section className="seccion">
        <h1 className="seccion__titulo">Hoy en el hotel</h1>
        <p className="seccion__intro">
          Quién llega y quién se va. Solo cuentan las reservas vigentes: una cancelada no llega
          ni se va.
        </p>

        <div className="campo ancho-campo">
          <label className="campo__etiqueta" htmlFor="parte-fecha">Fecha del parte</label>
          <input
            id="parte-fecha"
            type="date"
            required
            value={fecha}
            onChange={(e) => setFecha(e.target.value)}
          />
        </div>

        {error ? <MensajeError texto={error} /> : null}
        {falloActualizacion ? (
          <Aviso tono="aviso" titulo="No pudimos actualizar el parte">
            Los datos pueden estar desactualizados.
          </Aviso>
        ) : null}
        {cargando ? <Cargando texto="Cargando el parte" /> : null}

        {!cargando && !error && parte ? (
          <>
            <Movimientos titulo={`Llegadas (${parte.llegadas.length})`} movimientos={parte.llegadas} vacio="Nadie llega este día." />
            <Movimientos titulo={`Salidas (${parte.salidas.length})`} movimientos={parte.salidas} vacio="Nadie se va este día." />
            <Movimientos titulo={`En casa (${parte.enCasa.length})`} movimientos={parte.enCasa} vacio="El hotel duerme vacío esta noche." />
          </>
        ) : null}
      </section>
    </main>
  );
}

function Movimientos({ titulo, movimientos, vacio }: { titulo: string; movimientos: Movimiento[]; vacio: string }) {
  return (
    <section aria-label={titulo} className="mt-e4">
      <h2 className="t-lg">{titulo}</h2>
      {movimientos.length === 0 ? (
        <Vacio titulo="Sin movimientos" detalle={vacio} />
      ) : (
        <ul className="lista-marcada">
          {movimientos.map((m) => {
            const cuenta = saldo(m);
            return (
              <li key={m.codigo}>
                <strong>{m.nombre || m.email}</strong> · Habitación {m.habitacion} ·{' '}
                {m.huespedes} {m.huespedes === 1 ? 'huésped' : 'huéspedes'} ·{' '}
                <span className="cifra">{m.codigo}</span>
                {cuenta ? (
                  <>
                    {' · '}
                    <Etiqueta tono={cuenta.pagada ? 'exito' : 'aviso'}>{cuenta.texto}</Etiqueta>
                  </>
                ) : null}
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}

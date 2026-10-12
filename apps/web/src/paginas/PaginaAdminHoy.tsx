import { useEffect, useState } from 'react';
import { api } from '../api/cliente';
import { hoyIso } from '../api/formato';
import { useActualizacionPeriodicaVisible } from '../api/useActualizacionPeriodicaVisible';
import { useSesion } from '../api/useSesion';
import { Cargando, MensajeError, PuertaAdmin, Vacio } from '../componentes/Estado';

type Movimiento = {
  codigo: string;
  email: string;
  nombre: string;
  huespedes: number;
  habitacion: string;
};

type ParteDia = {
  fecha: string;
  llegadas: Movimiento[];
  salidas: Movimiento[];
  enCasa: Movimiento[];
};

/**
 * Parte del día para la recepción: quién llega y quién se va, sin SQL.
 *
 * Es la primera pantalla que mira el turno de la mañana y se queda abierta toda la jornada,
 * así que se refresca sola: una reserva que entra por la web aparece sin recargar. Con la
 * pestaña oculta el refresco se para, y un fallo del sondeo no borra ni pisa lo que ya se ve.
 */
export function PaginaAdminHoy() {
  const sesion = useSesion();
  const [fecha, setFecha] = useState(hoyIso());
  const [parte, setParte] = useState<ParteDia | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [cargando, setCargando] = useState(true);
  /** Datos de fondo: refrescan el parte sin esconderlo ni hacer parpadear el cargando. */
  const [silencio, setSilencio] = useState(false);

  async function leer(enSegundoPlano: boolean) {
    if (!fecha) return;
    if (enSegundoPlano) setSilencio(true);
    else {
      setCargando(true);
      setError(null);
    }
    try {
      const r = await api.get<ParteDia>(`/api/admin/ocupacion/dia?fecha=${fecha}`);
      setParte(r);
    } catch (e) {
      // En segundo plano no se pisa lo que ya se ve: un fallo del sondeo no borra el parte.
      if (!enSegundoPlano) {
        setParte(null);
        setError(e instanceof Error ? e.message : 'No se pudo cargar el parte');
      }
    } finally {
      if (enSegundoPlano) setSilencio(false);
      else setCargando(false);
    }
  }

  useEffect(() => {
    void leer(false);
    // El parte es de una fecha: al cambiarla se recarga entero.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fecha]);

  // La recepción deja esta pantalla abierta todo el turno. Una reserva que entra por la web
  // tiene que aparecer sola, no al recargar. El hook ya pausa con la pestaña oculta.
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
        {cargando && !silencio ? <Cargando texto="Cargando el parte" /> : null}

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
          {movimientos.map((m) => (
            <li key={m.codigo}>
              <strong>{m.nombre || m.email}</strong> · Habitación {m.habitacion} ·{' '}
              {m.huespedes} {m.huespedes === 1 ? 'huésped' : 'huéspedes'} ·{' '}
              <span className="cifra">{m.codigo}</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

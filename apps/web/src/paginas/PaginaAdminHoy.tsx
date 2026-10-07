import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/cliente';
import { hoyIso } from '../api/formato';
import { useSesion } from '../api/useSesion';
import { Aviso, Cargando, MensajeError, Vacio } from '../componentes/Estado';

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
  const [cargando, setCargando] = useState(true);

  useEffect(() => {
    if (!fecha) return;
    let vigente = true;
    setCargando(true);
    setError(null);
    api
      .get<ParteDia>(`/api/admin/ocupacion/dia?fecha=${fecha}`)
      .then((r) => {
        if (vigente) setParte(r);
      })
      .catch((e: Error) => {
        if (vigente) {
          setParte(null);
          setError(e.message);
        }
      })
      .finally(() => {
        if (vigente) setCargando(false);
      });
    return () => {
      vigente = false;
    };
  }, [fecha]);

  if (sesion.haySesion === false) {
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
        {cargando ? <Cargando texto="Cargando el parte" /> : null}

        {!cargando && !error && parte ? (
          <>
            <Movimientos titulo={`Llegadas (${parte.llegadas.length})`} movimientos={parte.llegadas} vacio="Nadie llega este día." />
            <Movimientos titulo={`Salidas (${parte.salidas.length})`} movimientos={parte.salidas} vacio="Nadie se va este día." />
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

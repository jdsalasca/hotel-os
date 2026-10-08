import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/cliente';
import { fechaCorta, monto } from '../api/formato';
import { useSesionHuesped } from '../api/useSesionHuesped';
import { useSesion } from '../api/useSesion';
import { Aviso, Etiqueta } from '../componentes/Estado';

type Comprobante = {
  reserva: {
    codigo: string;
    email: string;
    nombre: string;
    llegada: string;
    salida: string;
    noches: number;
    huespedes: number;
    estado: string;
    origen: string;
    totalCents: number | null;
    moneda: string | null;
    plan: string | null;
    abonadoCents: number | null;
    pendienteCents: number | null;
  };
  habitacion: { codigo: string; nombre: string; tipo: string } | null;
  hotel: Record<string, string>;
  historial: { estado_ant: string | null; estado_nuevo: string; actor: string; en: string }[];
};

/** Paso 6 del flujo público: consultar una reserva con código y correo. */
export function PaginaConsulta() {
  const huesped = useSesionHuesped();
  const panel = useSesion();
  const [codigo, setCodigo] = useState('');
  const [email, setEmail] = useState('');
  /** Con sesión, el correo viene puesto para no teclearlo: manda el huésped y, si no,
   * el panel. Solo rellena el vacío: lo escrito a mano (p. ej. la reserva de otra
   * persona) no se pisa cuando la sesión termina de resolverse. */
  const correoSesion = huesped.email || panel.email;
  useEffect(() => {
    if (correoSesion && !email) setEmail(correoSesion);
  }, [correoSesion, email]);
  const [comprobante, setComprobante] = useState<Comprobante | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [buscando, setBuscando] = useState(false);
  const [confirmando, setConfirmando] = useState(false);
  const [cancelando, setCancelando] = useState(false);
  const [cancelada, setCancelada] = useState(false);

  async function consultar(evento: React.FormEvent) {
    evento.preventDefault();
    setError(null);
    setComprobante(null);
    setConfirmando(false);
    setCancelada(false);
    setBuscando(true);
    try {
      const consulta = new URLSearchParams({ email });
      // El comprobante trae lo mismo que la consulta más habitación, total e historial.
      const r = await api.get<Comprobante>(`/api/reservas/${codigo.trim()}/comprobante?${consulta}`);
      setComprobante(r);
    } catch {
      setError('No encontramos una reserva con ese código y ese correo.');
    } finally {
      setBuscando(false);
    }
  }

  /** Cancelar en dos pasos: el primero avisa, el segundo ejecuta. Sin vuelta atrás fingida. */
  async function cancelar() {
    if (!comprobante) return;
    if (!confirmando) {
      setConfirmando(true);
      return;
    }
    setCancelando(true);
    setError(null);
    try {
      await api.post(`/api/reservas/${comprobante.reserva.codigo}/cancelar`, { email });
      setComprobante({ ...comprobante, reserva: { ...comprobante.reserva, estado: 'CANCELADA' } });
      setCancelada(true);
      setConfirmando(false);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo cancelar la reserva');
      setConfirmando(false);
    } finally {
      setCancelando(false);
    }
  }

  return (
    <main id="contenido" className="centrado">
      <section className="seccion">
        <h1 className="seccion__titulo">Consultar una reserva</h1>
        <p className="seccion__intro">
          Necesitas el código que te dimos al reservar y el mismo correo con el que lo hiciste.
        </p>

        <form
          onSubmit={consultar}
          noValidate
          className={comprobante ? 'ancho-formulario no-imprimir' : 'ancho-formulario'}
        >
          <div className="pila">
            <div className="campo">
              <label className="campo__etiqueta" htmlFor="codigo">Código de reserva</label>
              <input
                id="codigo"
                type="text"
                required
                placeholder="H-XXXXXXXX"
                className="cifra"
                value={codigo}
                onChange={(e) => setCodigo(e.target.value)}
              />
            </div>
            <div className="campo">
              <label className="campo__etiqueta" htmlFor="email-consulta">Correo electrónico</label>
              <input
                id="email-consulta"
                type="email"
                required
                autoComplete="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />
              {correoSesion && email === correoSesion ? (
                <p className="campo__ayuda">
                  Usamos el correo de tu sesión; cámbialo si consultas otra reserva.
                </p>
              ) : null}
            </div>
            {error ? <Aviso tono="error">{error}</Aviso> : null}
            <button className="boton boton--primario" type="submit" disabled={buscando || !codigo || !email}>
              {buscando ? 'Buscando…' : 'Consultar reserva'}
            </button>
          </div>
        </form>

        {comprobante ? (
          <div className="tarjeta pila mt-e6 comprobante">
            <div className="pila gap-e2">
              <p className="campo__ayuda sin-margen">
                {comprobante.hotel.nombre?.trim() ? comprobante.hotel.nombre : 'Comprobante de reserva'}
              </p>
              <h2 className="sin-margen">{comprobante.reserva.codigo}</h2>
              <Etiqueta tono={comprobante.reserva.estado === 'CONFIRMADA' ? 'exito' : 'neutra'}>
                {comprobante.reserva.estado === 'PENDIENTE' ? 'Pendiente de confirmación' : comprobante.reserva.estado}
              </Etiqueta>
            </div>
            <dl className="pila gap-e1">
              <div>
                <dt className="campo__etiqueta">Huésped</dt>
                <dd className="sin-margen">{comprobante.reserva.nombre || comprobante.reserva.email}</dd>
              </div>
              {comprobante.habitacion ? (
                <div>
                  <dt className="campo__etiqueta">Habitación</dt>
                  <dd className="sin-margen">
                    {comprobante.habitacion.codigo}
                    {comprobante.habitacion.tipo ? ` · ${comprobante.habitacion.tipo}` : ''}
                  </dd>
                </div>
              ) : null}
              <div>
                <dt className="campo__etiqueta">Fechas</dt>
                <dd className="cifra sin-margen">
                  {fechaCorta(comprobante.reserva.llegada)} → {fechaCorta(comprobante.reserva.salida)} ({comprobante.reserva.noches}{' '}
                  {comprobante.reserva.noches === 1 ? 'noche' : 'noches'})
                </dd>
              </div>
              <div>
                <dt className="campo__etiqueta">Total acordado</dt>
                <dd className="sin-margen">
                  {comprobante.reserva.totalCents !== null && comprobante.reserva.moneda ? (
                    <span className="cifra">
                      {monto(comprobante.reserva.totalCents, comprobante.reserva.moneda)}
                      {comprobante.reserva.plan ? ` · ${comprobante.reserva.plan}` : ''}
                    </span>
                  ) : (
                    'Pendiente de tarifar: el hotel aún no había fijado el precio al reservar.'
                  )}
                </dd>
              </div>
              {comprobante.reserva.abonadoCents !== null && comprobante.reserva.moneda ? (
                <div>
                  <dt className="campo__etiqueta">Cuenta</dt>
                  <dd className="sin-margen">
                    <span className="cifra">
                      Abonado: {monto(comprobante.reserva.abonadoCents, comprobante.reserva.moneda)} ·{' '}
                      Pendiente: {monto(comprobante.reserva.pendienteCents ?? 0, comprobante.reserva.moneda)}
                    </span>
                  </dd>
                </div>
              ) : null}
              <div>
                <dt className="campo__etiqueta">Estado</dt>
                <dd className="sin-margen">
                  Reserva creada desde {comprobante.reserva.origen} · {comprobante.historial.length}{' '}
                  {comprobante.historial.length === 1 ? 'movimiento registrado' : 'movimientos registrados'}
                </dd>
              </div>
            </dl>
            <button className="boton boton--secundario boton--chico no-estirar no-imprimir" type="button" onClick={() => window.print()}>
              Imprimir comprobante
            </button>
            {cancelada ? (
              <Aviso tono="exito" titulo="Reserva cancelada">
                <p>La habitación vuelve a estar disponible. Si cambias de idea, haz una reserva nueva.</p>
              </Aviso>
            ) : (comprobante.reserva.estado === 'PENDIENTE' || comprobante.reserva.estado === 'CONFIRMADA') ? (
              <>
                {confirmando ? (
                  <Aviso tono="aviso" titulo="¿Seguro que la cancelas?">
                    <p>Se liberan tus fechas y tendrás que reservar de nuevo si cambias de idea.</p>
                  </Aviso>
                ) : null}
                <button
                  className={`boton ${confirmando ? 'boton--peligro' : 'boton--fantasma'} boton--chico no-estirar no-imprimir`}
                  type="button"
                  onClick={() => void cancelar()}
                  disabled={cancelando}
                >
                  {cancelando ? 'Cancelando…' : confirmando ? 'Sí, cancelar mi reserva' : 'Cancelar esta reserva'}
                </button>
              </>
            ) : null}
          </div>
        ) : null}

        <p className="mt-e6">
          <Link to="/">Volver al inicio</Link>
        </p>
      </section>
    </main>
  );
}
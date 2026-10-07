import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/cliente';
import { fechaCorta, monto } from '../api/formato';
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
  };
  habitacion: { codigo: string; nombre: string; tipo: string } | null;
  hotel: Record<string, string>;
  historial: { estado_ant: string | null; estado_nuevo: string; actor: string; en: string }[];
};

/** Paso 6 del flujo público: consultar una reserva con código y correo. */
export function PaginaConsulta() {
  const [codigo, setCodigo] = useState('');
  const [email, setEmail] = useState('');
  const [comprobante, setComprobante] = useState<Comprobante | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [buscando, setBuscando] = useState(false);

  async function consultar(evento: React.FormEvent) {
    evento.preventDefault();
    setError(null);
    setComprobante(null);
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

  return (
    <main id="contenido" className="centrado">
      <section className="seccion">
        <h1 className="seccion__titulo">Consultar una reserva</h1>
        <p className="seccion__intro">
          Necesitas el código que te dimos al reservar y el mismo correo con el que lo hiciste.
        </p>

        <form onSubmit={consultar} noValidate className="ancho-formulario">
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
          </div>
        ) : null}

        <p className="mt-e6">
          <Link to="/">Volver al inicio</Link>
        </p>
      </section>
    </main>
  );
}
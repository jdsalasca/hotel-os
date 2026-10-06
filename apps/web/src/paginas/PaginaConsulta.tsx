import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/cliente';
import { fechaCorta } from '../api/formato';
import { Aviso, Etiqueta } from '../componentes/Estado';

type Reserva = {
  codigo: string;
  email: string;
  nombre: string;
  llegada: string;
  salida: string;
  huespedes: number;
  estado: string;
  origen: string;
};

/** Paso 6 del flujo público: consultar una reserva con código y correo. */
export function PaginaConsulta() {
  const [codigo, setCodigo] = useState('');
  const [email, setEmail] = useState('');
  const [reserva, setReserva] = useState<Reserva | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [buscando, setBuscando] = useState(false);

  async function consultar(evento: React.FormEvent) {
    evento.preventDefault();
    setError(null);
    setReserva(null);
    setBuscando(true);
    try {
      const consulta = new URLSearchParams({ email });
      const r = await api.get<Reserva>(`/api/reservas/${codigo.trim()}?${consulta}`);
      setReserva(r);
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

        <form onSubmit={consultar} noValidate style={{ maxWidth: '34rem' }}>
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

        {reserva ? (
          <div className="tarjeta pila" style={{ marginTop: '2rem' }}>
            <div className="pila" style={{ gap: '0.5rem' }}>
              <h2 style={{ margin: 0 }}>{reserva.codigo}</h2>
              <Etiqueta tono={reserva.estado === 'CONFIRMADA' ? 'exito' : 'neutra'}>
                {reserva.estado === 'PENDIENTE' ? 'Pendiente de confirmación' : reserva.estado}
              </Etiqueta>
            </div>
            <p className="cifra" style={{ margin: 0 }}>
              {fechaCorta(reserva.llegada)} → {fechaCorta(reserva.salida)} · {reserva.huespedes}{' '}
              {reserva.huespedes === 1 ? 'huésped' : 'huéspedes'}
            </p>
            <p className="campo__ayuda" style={{ margin: 0 }}>Reserva creada desde {reserva.origen}</p>
          </div>
        ) : null}

        <p style={{ marginTop: '2rem' }}>
          <Link to="/">Volver al inicio</Link>
        </p>
      </section>
    </main>
  );
}
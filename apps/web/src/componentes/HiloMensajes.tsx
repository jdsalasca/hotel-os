import { useEffect, useState, type FormEvent } from 'react';
import { Aviso } from './Estado';

export type Mensaje = { id: number; autor: string; texto: string; en: string; visto: boolean };

/**
 * Hilo de conversación de una reserva, igual en la web del huésped y en el panel. Quien
 * escribe se ve a la derecha; el otro lado, a la izquierda. Sin websockets: se relee al
 * abrir y con el botón, que en un hotel pequeño es suficiente y no deja conexiones colgadas.
 */
export function HiloMensajes({
  titulo,
  ladoPropio,
  cargar,
  enviar,
}: {
  titulo: string;
  ladoPropio: 'HUESPED' | 'HOTEL';
  cargar: () => Promise<{ mensajes: Mensaje[] }>;
  enviar: (texto: string) => Promise<void>;
}) {
  const [mensajes, setMensajes] = useState<Mensaje[] | null>(null);
  const [texto, setTexto] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  async function releer() {
    try {
      const datos = await cargar();
      setMensajes(datos.mensajes);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo leer la conversación');
    }
  }

  useEffect(() => {
    void releer();
    // El hilo es por reserva: si cambia el código, el padre monta otro componente.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function mandar(evento: FormEvent) {
    evento.preventDefault();
    const limpio = texto.trim();
    if (!limpio || enviando) return;
    setError(null);
    setEnviando(true);
    try {
      await enviar(limpio);
      setTexto('');
      await releer();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo enviar el mensaje');
    } finally {
      setEnviando(false);
    }
  }

  return (
    <div className="hilo">
      <div className="pila pila--fila gap-e2">
        <h3 className="t-lg mb-0">{titulo}</h3>
        <button className="boton boton--fantasma boton--chico" type="button" onClick={() => void releer()}>
          Actualizar
        </button>
      </div>
      {error ? <Aviso tono="error">{error}</Aviso> : null}
      {mensajes === null && !error ? <p className="cargando">Cargando conversación…</p> : null}
      {mensajes !== null && mensajes.length === 0 ? (
        <p className="campo__ayuda sin-margen">
          Sin mensajes todavía: escribe el primero y te responden por aquí.
        </p>
      ) : null}
      {mensajes !== null && mensajes.length > 0 ? (
        <ul className="hilo__lista" aria-live="polite">
          {mensajes.map((m) => (
            <li
              key={m.id}
              className={`hilo__mensaje${m.autor === ladoPropio ? ' hilo__mensaje--propio' : ''}`}
            >
              {m.texto}
              <span className="hilo__meta">
                {m.autor === ladoPropio ? 'Tú' : m.autor === 'HOTEL' ? 'Hotel' : 'Huésped'} ·{' '}
                {m.en.slice(0, 16).replace('T', ' ')}
              </span>
            </li>
          ))}
        </ul>
      ) : null}
      <form className="hilo__envio" onSubmit={(e) => void mandar(e)}>
        <div className="campo">
          <label className="solo-lectores" htmlFor={`hilo-texto-${titulo}`}>
            Escribe tu mensaje
          </label>
          <input
            id={`hilo-texto-${titulo}`}
            maxLength={1000}
            placeholder="Escribe tu mensaje…"
            autoComplete="off"
            value={texto}
            onChange={(e) => setTexto(e.target.value)}
          />
        </div>
        <button className="boton boton--primario" type="submit" disabled={enviando || !texto.trim()}>
          {enviando ? '…' : 'Enviar'}
        </button>
      </form>
    </div>
  );
}

import { useEffect, useLayoutEffect, useRef, useState, type FormEvent } from 'react';
import { useActualizacionPeriodicaVisible } from '../api/useActualizacionPeriodicaVisible';
import { Aviso } from './Estado';

export type Mensaje = { id: number; autor: string; texto: string; en: string; visto: boolean };
export type Hilo = { mensajes: Mensaje[]; total?: number; hay_mas?: boolean };

const TOLERANCIA_FINAL_SCROLL = 24;

/**
 * Hilo de conversación de una reserva, igual en la web del huésped y en el panel. Quien
 * escribe se ve a la derecha; el otro lado, a la izquierda.
 *
 * Sin websockets ni conexiones colgadas: se relee al abrir, con el botón y por sondeo
 * mientras la pestaña se ve. Un chat que hay que refrescar a mano se siente roto —el hotel
 * responde y el huésped no se entera hasta que pulsa—, pero sondear diez conversaciones
 * abiertas en segundo plano es gastar peticiones para nadie: con la pestaña oculta el sondeo
 * se para, y al volver a verla se relee.
 */
export function HiloMensajes({
  titulo,
  ladoPropio,
  cargar,
  enviar,
}: {
  titulo: string;
  ladoPropio: 'HUESPED' | 'HOTEL';
  cargar: (antesDe?: number) => Promise<Hilo>;
  enviar: (texto: string) => Promise<void>;
}) {
  const [mensajes, setMensajes] = useState<Mensaje[] | null>(null);
  const [total, setTotal] = useState<number | null>(null);
  const [hayMas, setHayMas] = useState(false);
  const [cargandoMas, setCargandoMas] = useState(false);
  const [texto, setTexto] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  /** Mensajes que llegaron mientras el usuario estaba leyendo arriba, sin bajar al final. */
  const [nuevosSinVer, setNuevosSinVer] = useState(0);
  const listaRef = useRef<HTMLUListElement>(null);
  const irAlFinalRef = useRef(false);
  const anclaScrollRef = useRef<{ altura: number; scrollTop: number } | null>(null);
  /** Última cantidad de mensajes que el usuario llegó a ver: de ahí salen los "sin ver". */
  const ultimoVistoRef = useRef(0);
  /** Una sola lectura en vuelo: si el sondeo pisa al usuario enviando, se descarta. */
  const leyendoRef = useRef(false);

  async function releer(forzarFinal = false) {
    const lista = listaRef.current;
    const estabaAlFinal = lista === null ||
      lista.scrollHeight - lista.clientHeight - lista.scrollTop <= TOLERANCIA_FINAL_SCROLL;
    // Lo que el usuario ya ha visto: si llegan más, se cuentan para avisar al que leía arriba.
    const vistos = ultimoVistoRef.current;
    if (leyendoRef.current) return;
    leyendoRef.current = true;
    try {
      const datos = await cargar();
      anclaScrollRef.current = null;
      irAlFinalRef.current = forzarFinal || estabaAlFinal;
      setMensajes([...datos.mensajes]);
      setTotal(datos.total ?? datos.mensajes.length);
      setHayMas(datos.hay_mas ?? false);
      if (forzarFinal || estabaAlFinal) {
        // Estar al final cuenta: lo nuevo ya está a la vista y avisar sería ruido. La marca
        // se mueve aquí para que el siguiente sondeo no cobre de nuevo lo mismo.
        ultimoVistoRef.current = datos.mensajes.length;
        setNuevosSinVer(0);
      } else {
        // Acumula hasta que el usuario baje: si se recalcula sobre lo que hay en pantalla,
        // el segundo sondeo borra un aviso que nunca nadie ha mirado.
        // Acumula hasta que el usuario baje: si se recalcula sobre lo que hay en pantalla,
        // el segundo sondeo borra un aviso que nunca nadie ha mirado.
        setNuevosSinVer(Math.max(vistos, datos.mensajes.length) - vistos);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo leer la conversación');
    } finally {
      leyendoRef.current = false;
    }
  }

  async function cargarAnteriores() {
    const primero = mensajes?.[0];
    if (primero === undefined || cargandoMas) return;
    setError(null);
    setCargandoMas(true);
    try {
      const datos = await cargar(primero.id);
      const lista = listaRef.current;
      anclaScrollRef.current = lista
        ? { altura: lista.scrollHeight, scrollTop: lista.scrollTop }
        : null;
      irAlFinalRef.current = false;
      setMensajes([...datos.mensajes, ...(mensajes ?? [])]);
      setHayMas(datos.hay_mas ?? false);
    } catch (e) {
      anclaScrollRef.current = null;
      setError(e instanceof Error ? e.message : 'No se pudo leer la conversación');
    } finally {
      setCargandoMas(false);
    }
  }

  useEffect(() => {
    void releer();
    // El hilo es por reserva: si cambia el código, el padre monta otro componente.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Sondeo mientras la pestaña se ve. El hook ya pausa con la pestaña oculta y relee al
  // volver a verla; se reutiliza porque el panel sondea igual, y un solo sitio donde vive
  // el sondeo es uno menos que mantener.
  useActualizacionPeriodicaVisible(() => void releer());

  useLayoutEffect(() => {
    const lista = listaRef.current;
    if (!lista || mensajes === null) return;

    if (anclaScrollRef.current) {
      const { altura, scrollTop } = anclaScrollRef.current;
      lista.scrollTop = scrollTop + lista.scrollHeight - altura;
      anclaScrollRef.current = null;
      return;
    }

    if (irAlFinalRef.current) {
      lista.scrollTop = Math.max(0, lista.scrollHeight - lista.clientHeight);
      irAlFinalRef.current = false;
    }
  }, [mensajes]);

  async function mandar(evento: FormEvent) {
    evento.preventDefault();
    const limpio = texto.trim();
    if (!limpio || enviando) return;
    setError(null);
    setEnviando(true);
    try {
      await enviar(limpio);
      setTexto('');
      await releer(true);
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
      {mensajes !== null && total !== null && total > mensajes.length ? (
        <p className="campo__ayuda sin-margen">
          Mostrando los últimos {mensajes.length} de {total} mensajes.
        </p>
      ) : null}
      {mensajes !== null && mensajes.length === 0 ? (
        <p className="campo__ayuda sin-margen">
          Sin mensajes todavía: escribe el primero y te responden por aquí.
        </p>
      ) : null}
      {mensajes !== null && mensajes.length > 0 && hayMas ? (
        <button
          className="boton boton--fantasma boton--chico"
          type="button"
          disabled={cargandoMas}
          onClick={() => void cargarAnteriores()}
        >
          {cargandoMas ? 'Cargando…' : 'Cargar anteriores'}
        </button>
      ) : null}
      {mensajes !== null && nuevosSinVer > 0 ? (
        <button
          className="boton boton--fantasma boton--chico"
          type="button"
          onClick={() => {
            irAlFinalRef.current = true;
            // Bajar al final es reconocer los mensajes: la marca se pone al día aquí.
            ultimoVistoRef.current = mensajes?.length ?? 0;
            setNuevosSinVer(0);
            // Array nueva para que el efecto de maquetación vuelva a correr y baje al final.
            setMensajes((actuales) => [...(actuales ?? [])]);
          }}
        >
          {nuevosSinVer === 1 ? '1 mensaje nuevo' : `${nuevosSinVer} mensajes nuevos`}
        </button>
      ) : null}
      {mensajes !== null && mensajes.length > 0 ? (
        <ul ref={listaRef} className="hilo__lista" aria-live="polite">
          {mensajes.map((m) => (
            <li
              key={m.id}
              className={`hilo__mensaje${m.autor === ladoPropio ? ' hilo__mensaje--propio' : ''}`}
            >
              {m.texto}
              <span className="hilo__meta">
                {m.autor === ladoPropio ? 'Tú' : m.autor === 'HOTEL' ? 'Hotel' : 'Huésped'} ·{' '}
                {m.en.slice(0, 16).replace('T', ' ')}
                {m.autor === ladoPropio ? (
                  <span className="hilo__lectura">
                    {m.visto
                      ? `Visto por ${ladoPropio === 'HOTEL' ? 'el huésped' : 'el hotel'}`
                      : 'Enviado · pendiente de lectura'}
                  </span>
                ) : null}
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

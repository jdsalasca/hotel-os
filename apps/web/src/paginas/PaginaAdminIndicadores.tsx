import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, urlApi } from '../api/cliente';
import { useSesion } from '../api/useSesion';
import { Aviso, Cargando, Etiqueta, MensajeError } from '../componentes/Estado';

type Fila = {
  clave: string;
  fase: number;
  nombre: string;
  definicion: string;
  formula: string;
  fuente: string;
  periodo: string;
  unidad: string;
  resultado: number | null;
  numerador: number | null;
  denominador: number | null;
  tieneResultado: boolean;
  datosFaltantes: string | null;
  lineaBase: string | null;
  meta: string | null;
  responsable: string | null;
  nota: string | null;
};

type Informe = {
  periodo: string;
  indicadores: Fila[];
  reservasPorCanal: Record<string, number>;
  actividades: { tipo: string; descripcion: string; fecha: string; tieneDatosDeOrigen: boolean }[];
};

const FASES = ['1 · Implementación y migración', '2 · Adopción y difusión', '3 · Operación estabilizada'];

/**
 * Informe de indicadores. Un indicador sin datos se muestra como "Sin datos" con el motivo, nunca
 * como 0: esa distinción es el motivo de existir del módulo.
 */
export function PaginaAdminIndicadores() {
  const sesion = useSesion();
  const [periodo, setPeriodo] = useState(mesActual());
  const [informe, setInforme] = useState<Informe | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [cargando, setCargando] = useState(true);
  /** Al guardar una referencia se relee el informe para mostrar lo guardado de verdad. */
  const [version, setVersion] = useState(0);

  useEffect(() => {
    setCargando(true);
    api
      .get<Informe>(`/api/admin/indicadores?periodo=${periodo}`)
      .then(setInforme)
      .catch((e: Error) => setError(e.message))
      .finally(() => setCargando(false));
  }, [periodo, version]);

  if (sesion.haySesion === false) return <Aviso tono="aviso" titulo="Sesión requerida">Inicia sesión para ver el informe.</Aviso>;
  if (sesion.haySesion === null) return <Cargando texto="Comprobando sesión" />;

  return (
    <main id="contenido" className="centrado">
      <section className="seccion">
        <div className="pila no-imprimir mb-e4">
          <h1 className="seccion__titulo mb-0">Indicadores</h1>
          <div className="campos">
            <div className="campo">
              <label className="campo__etiqueta" htmlFor="periodo">Periodo</label>
              <input
                id="periodo"
                type="month"
                className="cifra"
                value={periodo}
                onChange={(e) => setPeriodo(e.target.value)}
              />
            </div>
          </div>
          <a className="boton boton--secundario boton--chico" href={`${urlApi(`/api/admin/indicadores.csv?periodo=${periodo}`)}`}>
            Descargar CSV
          </a>
        </div>

        {error ? <MensajeError texto={error} /> : null}
        {cargando ? <Cargando /> : null}

        {informe
          ? FASES.map((titulo, indice) => {
              const filas = informe.indicadores.filter((i) => i.fase === indice + 1);
              if (filas.length === 0) return null;
              return (
                <section key={titulo} className="mb-e6">
                  <h2 className="t-xl">{titulo}</h2>
                  <div className="rejilla">
                    {filas.map((fila) => (
                      <article
                        key={fila.clave}
                        className={`tarjeta-indicador${fila.tieneResultado ? '' : ' tarjeta-indicador--faltante'}`}
                      >
                        <h3 className="tarjeta-indicador__nombre">{fila.nombre}</h3>
                        {fila.tieneResultado ? (
                          <>
                            <p className="tarjeta-indicador__valor">
                              {fila.unidad === 'porcentaje' ? `${fila.resultado}%` : fila.resultado}
                              {fila.unidad === 'conteo' ? '' : ''}
                            </p>
                            {fila.numerador !== null ? (
                              <p className="campo__ayuda">
                                {fila.numerador}
                                {fila.denominador !== null ? ` / ${fila.denominador}` : ''}
                              </p>
                            ) : null}
                          </>
                        ) : (
                          <p className="tarjeta-indicador__valor">Sin datos</p>
                        )}

                        <p className="campo__ayuda">{fila.definicion}</p>
                        <p className="tarjeta-indicador__formula">Fórmula: {fila.formula}</p>
                        <p className="campo__ayuda">
                          Fuente: {fila.fuente} · Periodo: {fila.periodo} · Unidad: {fila.unidad}
                        </p>

                        {!fila.tieneResultado && fila.datosFaltantes ? (
                          <p className="campo__ayuda">Motivo: {fila.datosFaltantes}</p>
                        ) : null}

                        <div className="pila gap-e1">
                          <Etiqueta tono={fila.tieneResultado ? 'exito' : 'neutra'}>
                            {fila.tieneResultado ? 'Calculado' : 'Sin datos'}
                          </Etiqueta>
                          <Etiqueta tono="neutra">
                            Línea base: {fila.lineaBase ?? 'no definida'}
                          </Etiqueta>
                          <Etiqueta tono="neutra">Meta: {fila.meta ?? 'no definida'}</Etiqueta>
                          <Etiqueta tono="neutra">
                            Responsable: {fila.responsable ?? 'sin asignar'}
                          </Etiqueta>
                        </div>
                        <EditorReferencia
                          key={`${fila.clave}|${fila.lineaBase ?? ''}|${fila.meta ?? ''}|${fila.responsable ?? ''}`}
                          fila={fila}
                          alGuardar={() => setVersion((v) => v + 1)}
                        />
                      </article>
                    ))}
                  </div>
                </section>
              );
            })
          : null}

        {informe ? (
          <section>
            <h2 className="t-xl">Reservas por canal</h2>
            {Object.keys(informe.reservasPorCanal).length === 0 ? (
              <p className="campo__ayuda">Sin reservas en el periodo.</p>
            ) : (
              <table className="tabla">
                <caption>Reservas por canal en el periodo</caption>
                <thead>
                  <tr>
                    <th scope="col">Canal</th>
                    <th scope="col">Reservas</th>
                  </tr>
                </thead>
                <tbody>
                  {Object.entries(informe.reservasPorCanal).map(([canal, n]) => (
                    <tr key={canal}>
                      <td>{canal}</td>
                      <td className="cifra">{n}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}

            <h2 className="t-xl mt-e5">Actividades de adopción</h2>
            {informe.actividades.length === 0 ? (
              <p className="campo__ayuda">
                Sin actividades confirmadas. No se atribuyen ventas a ninguna campaña.
              </p>
            ) : (
              <ul className="pila gap-e2">
                {informe.actividades.map((a, i) => (
                  <li key={i}>
                    <strong>{a.tipo}</strong> · {a.descripcion} ({a.fecha}) —{' '}
                    {a.tieneDatosDeOrigen
                      ? 'con datos de origen medibles'
                      : 'sin datos de origen: no se atribuyen ventas'}
                  </li>
                ))}
              </ul>
            )}
          </section>
        ) : null}

        <p className="mt-e6">
          <Link to="/admin/reservas">Volver a las reservas</Link>
        </p>
      </section>
    </main>
  );
}

function mesActual(): string {
  return new Date().toISOString().slice(0, 7);
}

/** Línea base, meta y responsable por indicador: lo que el hotel fija con datos propios.
 * Los campos viajan prellenados con lo guardado, así solo cambia lo que se edita
 * (vaciar uno lo devuelve a "no definida"). */
function EditorReferencia({ fila, alGuardar }: { fila: Fila; alGuardar: () => void }) {
  const [base, setBase] = useState(fila.lineaBase ?? '');
  const [meta, setMeta] = useState(fila.meta ?? '');
  const [responsable, setResponsable] = useState(fila.responsable ?? '');
  const [aviso, setAviso] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);

  async function guardar() {
    setAviso(null);
    setGuardando(true);
    try {
      await api.post(`/api/admin/indicadores/definiciones/${fila.clave}/referencia`, {
        lineaBase: base,
        meta,
        responsable,
      });
      alGuardar();
    } catch (e) {
      setAviso(e instanceof Error ? e.message : 'No se pudo guardar la referencia');
    } finally {
      setGuardando(false);
    }
  }

  return (
    <details className="referencia">
      <summary className="referencia__resumen">Definir línea base, meta y responsable</summary>
      <div className="campo">
        <label className="campo__etiqueta" htmlFor={`base-${fila.clave}`}>Línea base</label>
        <input
          id={`base-${fila.clave}`}
          value={base}
          maxLength={200}
          placeholder="p. ej. 60% en temporada baja"
          onChange={(e) => setBase(e.target.value)}
        />
      </div>
      <div className="campo">
        <label className="campo__etiqueta" htmlFor={`meta-${fila.clave}`}>Meta</label>
        <input
          id={`meta-${fila.clave}`}
          value={meta}
          maxLength={200}
          placeholder="p. ej. 75%"
          onChange={(e) => setMeta(e.target.value)}
        />
      </div>
      <div className="campo">
        <label className="campo__etiqueta" htmlFor={`resp-${fila.clave}`}>Responsable</label>
        <input
          id={`resp-${fila.clave}`}
          value={responsable}
          maxLength={200}
          placeholder="p. ej. Gerencia"
          onChange={(e) => setResponsable(e.target.value)}
        />
      </div>
      {aviso ? <p className="campo__error">{aviso}</p> : null}
      <button
        className="boton boton--secundario boton--chico"
        type="button"
        disabled={guardando}
        onClick={() => void guardar()}
      >
        {guardando ? 'Guardando…' : 'Guardar referencia'}
      </button>
    </details>
  );
}
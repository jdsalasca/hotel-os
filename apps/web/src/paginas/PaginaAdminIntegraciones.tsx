import { useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/cliente';
import { useSesion } from '../api/useSesion';
import { Aviso, Cargando, Etiqueta, MensajeError } from '../componentes/Estado';

type MapeoCanal = {
  id: number;
  canal: string;
  roomId: number | null;
  roomCodigo: string | null;
  roomNombre: string | null;
  roomTypeId: number | null;
  tipoNombre: string | null;
  ratePlanId: number | null;
  planNombre: string | null;
  planMoneda: string | null;
  externalId: string;
};
type EstadoCanal = {
  canal: string;
  estado: string;
  entorno: string | null;
  identificadores: Record<string, string | null>;
  variablesRequeridas: string[];
  variablesFaltantes: string[];
  requisitosPendientes: string[];
  ultimaSync: string | null;
  ultimaSyncResultado: string | null;
  ultimasSincronizaciones?: { operacion: string; exitosa: number; detalle: string; en: string }[];
  mapeos?: MapeoCanal[];
};
type Habitacion = { id: number; codigo: string; nombre: string };
type TipoHabitacion = { id: number; nombre: string };
type PlanTarifario = { id: number; nombre: string; moneda: string };

const TONO: Record<string, 'exito' | 'error' | 'aviso' | 'info' | 'neutra'> = {
  CONECTADO: 'exito',
  SANDBOX: 'info',
  ACCESO_PENDIENTE: 'aviso',
  NO_CONFIGURADO: 'neutra',
  ERROR: 'error',
  DESCONECTADO: 'neutra',
};

/**
 * Estado real de cada canal. Llenar los campos no conecta nada: el estado solo cambia tras una
 * llamada autorizada, y con sandbox el techo es SANDBOX.
 */
export function PaginaAdminIntegraciones() {
  const sesion = useSesion();
  const [panel, setPanel] = useState<Record<string, EstadoCanal> | null>(null);
  const [habitaciones, setHabitaciones] = useState<Habitacion[]>([]);
  const [tipos, setTipos] = useState<TipoHabitacion[]>([]);
  const [planes, setPlanes] = useState<PlanTarifario[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [cargando, setCargando] = useState(true);
  const [sincronizando, setSincronizando] = useState<string | null>(null);
  const [mapeoPorConfirmar, setMapeoPorConfirmar] = useState<number | null>(null);
  const [eliminandoMapeo, setEliminandoMapeo] = useState<number | null>(null);

  async function cargar() {
    setCargando(true);
    setError(null);
    try {
      const [estado, habs, tiposCargados, planesCargados] = await Promise.all([
        api.get<Record<string, EstadoCanal>>('/api/admin/integraciones'),
        api.get<Habitacion[]>('/api/admin/habitaciones'),
        api.get<TipoHabitacion[]>('/api/admin/tipos'),
        api.get<PlanTarifario[]>('/api/admin/planes'),
      ]);
      setPanel(estado);
      setHabitaciones(habs);
      setTipos(tiposCargados);
      setPlanes(planesCargados);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo cargar el estado de los canales');
    } finally {
      setCargando(false);
    }
  }

  useEffect(() => {
    void cargar();
  }, []);

  async function sincronizar(canal: string) {
    setSincronizando(canal);
    setError(null);
    try {
      await api.post(`/api/admin/integraciones/${canal}/sincronizar`);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'La sincronización no pudo completarse');
    } finally {
      setSincronizando(null);
      await cargar();
    }
  }

  async function eliminarMapeo(id: number) {
    setEliminandoMapeo(id);
    setMapeoPorConfirmar(null);
    setError(null);
    try {
      await api.post(`/api/admin/integraciones/mapeos/${id}/eliminar`);
      await cargar();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'El mapeo no pudo eliminarse');
    } finally {
      setEliminandoMapeo(null);
    }
  }

  if (sesion.haySesion === false) return <Aviso tono="aviso" titulo="Sesión requerida">Inicia sesión para ver las integraciones.</Aviso>;
  if (sesion.haySesion === null) return <Cargando texto="Comprobando sesión" />;

  return (
    <main id="contenido" className="centrado">
      <section className="seccion">
        <h1 className="seccion__titulo">Integraciones con canales</h1>
        <p className="seccion__intro">
          El estado refleja lo que respondió el proveedor. Con <strong>sandbox</strong> el máximo es
          SANDBOX: una integración nunca aparece conectada solo por tener los datos escritos.
        </p>

        {error ? <MensajeError texto={error} /> : null}
        {cargando ? <Cargando /> : null}

        <div className="pila gap-e5">
          {panel &&
            Object.values(panel).map((canal) => (
              <article className="tarjeta pila" key={canal.canal}>
                <div className="pila gap-e2">
                  <div className="pila pila--fila gap-e2">
                    <h2 className="sin-margen t-xl">{nombreCanal(canal.canal)}</h2>
                    <Etiqueta tono={TONO[canal.estado] ?? 'neutra'}>{traducirEstado(canal.estado)}</Etiqueta>
                    {canal.entorno ? <Etiqueta tono="neutra">entorno {canal.entorno}</Etiqueta> : null}
                  </div>

                  <p className="campo__ayuda sin-margen">
                    Última sincronización: {canal.ultimaSync ?? 'sin registrar'}
                    {canal.ultimaSyncResultado ? ` — ${canal.ultimaSyncResultado}` : ''}
                  </p>
                </div>

                {canal.variablesFaltantes.length > 0 ? (
                  <Aviso tono="aviso" titulo="Faltan credenciales">
                    <ul className="sin-margen lista-marcada">
                      {canal.variablesFaltantes.map((v) => (
                        <li key={v}>
                          <code>{v}</code>
                        </li>
                      ))}
                    </ul>
                  </Aviso>
                ) : null}

                {canal.requisitosPendientes.length > 0 ? (
                  <div className="pila gap-e1">
                    <h3 className="t-sm mb-0">Requisitos pendientes</h3>
                    <ul className="campo__ayuda sin-margen lista-marcada">
                      {canal.requisitosPendientes.map((r) => (
                        <li key={r}>{r}</li>
                      ))}
                    </ul>
                  </div>
                ) : null}

                <div className="pila gap-e2">
                  <h3 className="t-sm mb-0">Mapeos declarados</h3>
                  {canal.mapeos && canal.mapeos.length > 0 ? (
                    <ul className="pila gap-e2 sin-margen lista-marcada">
                      {canal.mapeos.map((mapeo) => (
                        <li key={mapeo.id}>
                          <div>
                            <strong><code>{mapeo.externalId}</code></strong>
                            <p className="campo__ayuda sin-margen">{describirMapeo(mapeo)}</p>
                          </div>
                          {mapeoPorConfirmar === mapeo.id ? (
                            <div className="pila pila--fila gap-e1">
                              <button
                                type="button"
                                className="boton boton--peligro boton--chico"
                                disabled={eliminandoMapeo === mapeo.id}
                                onClick={() => void eliminarMapeo(mapeo.id)}
                              >
                                {eliminandoMapeo === mapeo.id ? 'Eliminando…' : 'Confirmar eliminación'}
                              </button>
                              <button
                                type="button"
                                className="boton boton--secundario boton--chico"
                                onClick={() => setMapeoPorConfirmar(null)}
                              >
                                Cancelar
                              </button>
                            </div>
                          ) : (
                            <button
                              type="button"
                              className="boton boton--secundario boton--chico"
                              onClick={() => setMapeoPorConfirmar(mapeo.id)}
                            >
                              Eliminar
                            </button>
                          )}
                        </li>
                      ))}
                    </ul>
                  ) : (
                    <p className="campo__ayuda sin-margen">
                      Sin mapeos de habitaciones: nada de este inventario se publica en el canal.
                    </p>
                  )}
                  <FormularioMapeo
                    canal={canal.canal}
                    habitaciones={habitaciones}
                    tipos={tipos}
                    planes={planes}
                    onCreado={() => cargar()}
                  />
                </div>

                <div className="pila gap-e2">
                  {canal.ultimasSincronizaciones && canal.ultimasSincronizaciones.length > 0 ? (
                    <>
                      <h3 className="t-sm mb-0">Intentos recientes</h3>
                      <ul className="campo__ayuda sin-margen lista-marcada">
                        {canal.ultimasSincronizaciones.map((s, i) => (
                          <li key={i}>
                            {s.en.slice(0, 16).replace('T', ' ')} — {s.exitosa ? 'correcto' : 'fallido'}: {s.detalle}
                          </li>
                        ))}
                      </ul>
                    </>
                  ) : null}

                  <button
                    className="boton boton--secundario boton--chico"
                    onClick={() => sincronizar(canal.canal)}
                    disabled={sincronizando === canal.canal}
                  >
                    {sincronizando === canal.canal ? 'Sincronizando…' : 'Reintentar sincronización'}
                  </button>
                </div>
              </article>
            ))}
        </div>

        <p className="mt-e6">
          <Link to="/admin/reservas">Volver a las reservas</Link>
        </p>
      </section>
    </main>
  );
}

function describirMapeo(mapeo: MapeoCanal): string {
  const recurso = mapeo.roomId !== null
    ? `Habitación ${mapeo.roomCodigo ?? mapeo.roomId}${mapeo.roomNombre ? ` · ${mapeo.roomNombre}` : ''}`
    : `Tipo ${mapeo.tipoNombre ?? mapeo.roomTypeId}`;
  const plan = mapeo.ratePlanId !== null
    ? ` · plan ${mapeo.planNombre ?? mapeo.ratePlanId}${mapeo.planMoneda ? ` (${mapeo.planMoneda})` : ''}`
    : '';
  return `${recurso}${plan}`;
}

function FormularioMapeo({
  canal,
  habitaciones,
  tipos,
  planes,
  onCreado,
}: {
  canal: string;
  habitaciones: Habitacion[];
  tipos: TipoHabitacion[];
  planes: PlanTarifario[];
  onCreado: () => Promise<void>;
}) {
  const [alcance, setAlcance] = useState<'habitacion' | 'tipo'>('habitacion');
  const [recurso, setRecurso] = useState('');
  const [planId, setPlanId] = useState('');
  const [externo, setExterno] = useState('');
  const [errorFormulario, setErrorFormulario] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  const recursos = alcance === 'habitacion' ? habitaciones : tipos;

  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    setErrorFormulario(null);
    if (!recurso) {
      setErrorFormulario('Selecciona el recurso local que reconoce el proveedor.');
      return;
    }
    if (!externo.trim()) {
      setErrorFormulario('Escribe el identificador externo que usa el proveedor.');
      return;
    }
    setEnviando(true);
    try {
      await api.post('/api/admin/integraciones/mapeos', {
        canal,
        roomId: alcance === 'habitacion' ? Number(recurso) : null,
        roomTypeId: alcance === 'tipo' ? Number(recurso) : null,
        ratePlanId: planId ? Number(planId) : null,
        externalId: externo.trim(),
      });
      setRecurso('');
      setPlanId('');
      setExterno('');
      await onCreado();
    } catch (e) {
      setErrorFormulario(e instanceof Error ? e.message : 'El mapeo no pudo registrarse');
    } finally {
      setEnviando(false);
    }
  }

  return (
    <form className="pila gap-e2" aria-label={`Nuevo mapeo en ${nombreCanal(canal)}`} onSubmit={(e) => void enviar(e)}>
      <h4 className="t-sm mb-0">Nuevo mapeo</h4>
      <p className="campo__ayuda sin-margen">
        El identificador externo lo da el proveedor. Aquí solo se declara el enlace; no se publica nada.
      </p>
      <div className="campos">
        <div className="campo">
          <label className="campo__etiqueta" htmlFor={`mapeo-alcance-${canal}`}>Recurso local</label>
          <select
            id={`mapeo-alcance-${canal}`}
            value={alcance}
            onChange={(e) => {
              setAlcance(e.target.value as 'habitacion' | 'tipo');
              setRecurso('');
            }}
          >
            <option value="habitacion">Habitación</option>
            <option value="tipo">Tipo de habitación</option>
          </select>
        </div>
        <div className="campo">
          <label className="campo__etiqueta" htmlFor={`mapeo-recurso-${canal}`}>
            {alcance === 'habitacion' ? 'Habitación' : 'Tipo'}
          </label>
          <select
            id={`mapeo-recurso-${canal}`}
            value={recurso}
            onChange={(e) => setRecurso(e.target.value)}
          >
            <option value="">Selecciona un recurso</option>
            {recursos.map((item) => (
              <option key={item.id} value={item.id}>
                {'codigo' in item ? `${item.codigo} · ${item.nombre || 'sin nombre'}` : item.nombre}
              </option>
            ))}
          </select>
        </div>
      </div>
      <div className="campos">
        <div className="campo">
          <label className="campo__etiqueta" htmlFor={`mapeo-plan-${canal}`}>Plan tarifario (opcional)</label>
          <select id={`mapeo-plan-${canal}`} value={planId} onChange={(e) => setPlanId(e.target.value)}>
            <option value="">Sin plan específico</option>
            {planes.map((plan) => (
              <option key={plan.id} value={plan.id}>
                {plan.nombre} ({plan.moneda})
              </option>
            ))}
          </select>
        </div>
        <div className="campo">
          <label className="campo__etiqueta" htmlFor={`mapeo-externo-${canal}`}>Identificador externo</label>
          <input
            id={`mapeo-externo-${canal}`}
            value={externo}
            maxLength={120}
            onChange={(e) => setExterno(e.target.value)}
            placeholder="p. ej. 123456"
          />
        </div>
      </div>
      {errorFormulario ? <p className="campo__error" role="alert">{errorFormulario}</p> : null}
      <button className="boton boton--primario boton--chico no-estirar" type="submit" disabled={enviando}>
        {enviando ? 'Registrando…' : 'Registrar mapeo'}
      </button>
    </form>
  );
}

function nombreCanal(codigo: string): string {
  return { BOOKING: 'Booking.com', DESPEGAR: 'Despegar', AIRBNB: 'Airbnb' }[codigo] ?? codigo;
}

function traducirEstado(estado: string): string {
  return {
    NO_CONFIGURADO: 'No configurado',
    ACCESO_PENDIENTE: 'Acceso pendiente',
    SANDBOX: 'Sandbox',
    CONECTADO: 'Conectado',
    ERROR: 'Error',
    DESCONECTADO: 'Desconectado',
  }[estado] ?? estado;
}
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/cliente';
import { useSesion } from '../api/useSesion';
import { Aviso, Cargando, Etiqueta, MensajeError } from '../componentes/Estado';

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
  mapeos?: { room_id: number | null; external_id: string }[];
};

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
  const [error, setError] = useState<string | null>(null);
  const [cargando, setCargando] = useState(true);
  const [sincronizando, setSincronizando] = useState<string | null>(null);

  async function cargar() {
    setCargando(true);
    setError(null);
    try {
      setPanel(await api.get<Record<string, EstadoCanal>>('/api/admin/integraciones'));
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

                {canal.mapeos && canal.mapeos.length > 0 ? (
                  <p className="campo__ayuda">
                    {canal.mapeos.length} mapeo(s) configurado(s) con el canal.
                  </p>
                ) : (
                  <p className="campo__ayuda">
                    Sin mapeos de habitaciones: nada de este inventario se publica en el canal.
                  </p>
                )}

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
import { useEffect, useState } from 'react';
import { Aviso, Cargando, Etiqueta } from './Estado';
import { enlaceWhatsApp } from '../api/formato';

export type ComprobantePropioDatos = {
  reserva: {
    codigo: string;
    llegada: string;
    salida: string;
    huespedes: number;
    estado: string;
    totalCents: number | null;
    moneda: string | null;
    plan: string | null;
    abonadoCents: number | null;
    pendienteCents: number | null;
  };
  habitacion: { codigo: string; nombre: string; tipo: string } | null;
  hotel: { nombre?: string | null; contacto_telefono?: string | null };
};

function monto(cents: number | null, moneda: string | null): string | null {
  if (cents === null || moneda === null) return null;
  return new Intl.NumberFormat('es-CO', { style: 'currency', currency: moneda }).format(cents / 100);
}

/**
 * Comprobante de una reserva propia, sin código ni correo a la vista: el padre ya sabe el
 * código de la fila y la sesión dice de quién es. Muestra habitación, fechas y saldo.
 */
export function ComprobantePropio({
  codigo,
  cargar,
}: {
  codigo: string;
  cargar: (codigo: string) => Promise<ComprobantePropioDatos>;
}) {
  const [datos, setDatos] = useState<ComprobantePropioDatos | null>(null);
  const [error, setError] = useState(false);

  useEffect(() => {
    cargar(codigo)
      .then(setDatos)
      .catch(() => setError(true));
  }, [codigo, cargar]);

  if (error) return <Aviso tono="error">No se pudo leer el comprobante</Aviso>;
  if (datos === null) return <Cargando texto="Leyendo comprobante" />;

  const r = datos.reserva;
  const pagada =
    r.totalCents !== null && r.moneda !== null && (r.pendienteCents ?? r.totalCents) <= 0;
  const wa = enlaceWhatsApp(
    datos.hotel.contacto_telefono ?? '',
    `Hola, soy ${r.codigo}: quisiera confirmar mi reserva del ${r.llegada} al ${r.salida}.`,
  );

  return (
    <>
    <dl className="pila gap-e1">
      {datos.hotel.nombre?.trim() ? (
        <>
          <dt className="campo__etiqueta">Hotel</dt>
          <dd className="sin-margen">{datos.hotel.nombre}</dd>
        </>
      ) : null}
      <dt className="campo__etiqueta">Habitación</dt>
      <dd className="sin-margen">
        {datos.habitacion
          ? `${datos.habitacion.nombre || datos.habitacion.codigo}${datos.habitacion.tipo ? ` · ${datos.habitacion.tipo}` : ''}`
          : 'Por asignar'}
      </dd>
      <dt className="campo__etiqueta">Fechas</dt>
      <dd className="sin-margen">
        {r.llegada} → {r.salida} · {r.huespedes} {r.huespedes === 1 ? 'huésped' : 'huéspedes'}
      </dd>
      <dt className="campo__etiqueta">Total acordado</dt>
      <dd className="sin-margen">
        {monto(r.totalCents, r.moneda) ?? 'Sin precio'}
        {r.plan ? ` · ${r.plan}` : ''}
      </dd>
      <dt className="campo__etiqueta">Saldo</dt>
      <dd className="sin-margen">
        {pagada ? (
          <Etiqueta tono="exito">PAGADA</Etiqueta>
        ) : (
          <>
            Abonado: {monto(r.abonadoCents ?? 0, r.moneda) ?? '—'} · Pendiente:{' '}
            {monto(r.pendienteCents, r.moneda) ?? '—'}
          </>
        )}
      </dd>
    </dl>
    <p className="sin-margen no-imprimir">
      <button className="boton boton--secundario boton--chico" type="button" onClick={() => window.print()}>
        Imprimir
      </button>
      {wa ? (
        <>
          {' '}
          <a className="boton boton--fantasma boton--chico" href={wa} target="_blank" rel="noreferrer">
            Compartir por WhatsApp
          </a>
        </>
      ) : null}
    </p>
    </>
  );
}

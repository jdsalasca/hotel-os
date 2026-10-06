import type { ReactNode } from 'react';

/** Estados compartidos: carga, error y vacío. Usarlos bien evita pantallas a medias. */

export function Cargando({ texto = 'Cargando' }: { texto?: string }) {
  return (
    <p className="cargando" role="status" aria-live="polite">
      <span aria-hidden="true">◌</span> {texto}…
    </p>
  );
}

export function MensajeError({ texto }: { texto: string }) {
  return (
    <div className="aviso aviso--error" role="alert">
      <strong>No se pudo completar la operación.</strong>
      <span>{texto}</span>
    </div>
  );
}

export function Vacio({ titulo, detalle }: { titulo: string; detalle?: string }) {
  return (
    <div className="vacio">
      <p className="vacio__titulo">{titulo}</p>
      {detalle ? <p>{detalle}</p> : null}
    </div>
  );
}

export function Aviso({
  tono = 'info',
  titulo,
  children,
}: {
  tono?: 'info' | 'exito' | 'error' | 'aviso';
  titulo?: string;
  children: ReactNode;
}) {
  return (
    <div className={`aviso aviso--${tono}`} role={tono === 'error' ? 'alert' : 'status'}>
      {titulo ? <strong>{titulo}</strong> : null}
      <div>{children}</div>
    </div>
  );
}

export function Etiqueta({
  tono = 'neutra',
  children,
}: {
  tono?: 'exito' | 'error' | 'aviso' | 'info' | 'neutra';
  children: ReactNode;
}) {
  return <span className={`etiqueta etiqueta--${tono}`}>{children}</span>;
}

/**
 * Hueco reservado para una fotografía del hotel. Sin imágenes remotas: el espacio queda listo
 * para la foto que el hotel autorice, y no hay enlaces que se rompan.
 */
export function HuecoImagen({
  alto = 'medio',
  texto = 'Espacio para fotografía autorizada del hotel',
  src,
  alt,
}: {
  alto?: 'bajo' | 'medio' | 'ancho';
  texto?: string;
  src?: string;
  alt?: string;
}) {
  const clase = alto === 'ancho' ? 'hueco-imagen hueco-imagen--ancho'
    : alto === 'bajo' ? 'hueco-imagen hueco-imagen--bajo' : 'hueco-imagen';
  if (src) {
    return (
      <div className={clase}>
        <img src={src} alt={alt ?? texto} />
      </div>
    );
  }
  return (
    <div className={clase} role="img" aria-label={texto}>
      {texto}
    </div>
  );
}
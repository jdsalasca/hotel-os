import { useState } from 'react';

/**
 * El código de reserva y un botón para copiarlo.
 *
 * Es la única constancia que se lleva quien reserva sin cuenta: si cierra la pestaña, no
 * tiene dónde mirar. Y sin copiar hay que anotarlo a mano, con un H-0/l mal transcrito que
 * ya no sirve para nada.
 *
 * Si el navegador bloquea el portapapeles (permiso denegado, contexto sin HTTPS) se cae al
 * plano de siempre: seleccionar el texto, que siempre funciona. Se dice lo que pasó en vez
 * de fingir que se copió.
 */
export function CodigoReserva({ codigo }: { codigo: string }) {
  const [estado, setEstado] = useState<'inactivo' | 'copiado' | 'seleccionado'>('inactivo');

  function seleccionar() {
    const nodo = document.getElementById('codigo-reserva');
    const rango = document.createRange();
    rango.selectNodeContents(nodo!);
    const seleccion = window.getSelection();
    seleccion?.removeAllRanges();
    seleccion?.addRange(rango);
    setEstado('seleccionado');
  }

  async function copiar() {
    try {
      await navigator.clipboard.writeText(codigo);
      setEstado('copiado');
    } catch {
      seleccionar();
    }
  }

  return (
    <div className="codigo-reserva">
      <span className="campo__etiqueta" id="etiqueta-codigo">
        Tu código de reserva
      </span>
      <div className="codigo-reserva__fila">
        <span className="codigo-reserva__texto" id="codigo-reserva">
          {codigo}
        </span>
        <button
          type="button"
          className="boton boton--fantasma boton--chico codigo-reserva__boton"
          onClick={() => void copiar()}
        >
          Copiar código
        </button>
      </div>
      {/* role="status" para que un lector de pantalla anuncie el cambio sin sacar el foco. */}
      <span className="codigo-reserva__aviso" role="status" aria-live="polite">
        {estado === 'copiado'
          ? 'Código copiado. Pégalo donde lo necesites.'
          : estado === 'seleccionado'
            ? 'No se pudo copiar; el código quedó seleccionado, cópialo con Ctrl+C.'
            : ''}
      </span>
    </div>
  );
}

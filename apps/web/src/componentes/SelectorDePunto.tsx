import { useState } from 'react';

/**
 * Elegir un punto sobre el mapa en vez de teclear `5.635, -73.525`.
 *
 * El punto marcado ES la fuente de verdad: los botones lo mueven y el visor se recentra en él
 * en cada pulsación, así que lo que se guarda nunca se desincroniza de lo que se ve. El visor
 * trae sus propios controles de zoom y arrastre, pero si quien configura los usa la vista se
 * desvía; la siguiente pulsación de un botón la vuelve a recentrar en el punto real.
 *
 * Compartido a propósito: el hotel y cada lugar turístico se ubican igual, y tener dos
 * copias de esto es tener que arreglar dos veces el mismo fallo.
 */
export function SelectorDePunto({
  punto,
  onElegir,
  titulo,
  ayuda,
}: {
  punto: { latitud: number; longitud: number };
  onElegir: (lat: number, lng: number) => void;
  titulo: string;
  ayuda: string;
}) {
  const [paso, setPaso] = useState(0.02);
  const src = `https://www.openstreetmap.org/export/embed.html?bbox=${
    punto.longitud - paso
  }%2C${punto.latitud - paso * 0.66}%2C${punto.longitud + paso}%2C${
    punto.latitud + paso * 0.66
  }&layer=mapnik&marker=${encodeURIComponent(`${punto.latitud},${punto.longitud}`)}`;

  return (
    <div className="tarjeta pila">
      <h2 className="t-base mb-0">{titulo}</h2>
      <p className="campo__ayuda sin-margen">{ayuda}</p>
      <div className="pila gap-e1">
        <iframe
          className="mapa__marco"
          title={`Mapa para elegir el punto: ${titulo.toLowerCase()}`}
          loading="lazy"
          src={src}
        />
        <div className="acciones-lugar">
          <button
            className="boton boton--fantasma boton--chico"
            type="button"
            onClick={() => onElegir(Number((punto.latitud - paso / 4).toFixed(5)), punto.longitud)}
          >
            Sur
          </button>
          <button
            className="boton boton--fantasma boton--chico"
            type="button"
            onClick={() => onElegir(Number((punto.latitud + paso / 4).toFixed(5)), punto.longitud)}
          >
            Norte
          </button>
          <button
            className="boton boton--fantasma boton--chico"
            type="button"
            onClick={() => onElegir(punto.latitud, Number((punto.longitud - paso / 4).toFixed(5)))}
          >
            Oeste
          </button>
          <button
            className="boton boton--fantasma boton--chico"
            type="button"
            onClick={() => onElegir(punto.latitud, Number((punto.longitud + paso / 4).toFixed(5)))}
          >
            Este
          </button>
          <button
            className="boton boton--fantasma boton--chico"
            type="button"
            onClick={() => setPaso((p) => Number((p / 2).toFixed(5)))}
          >
            Acercar
          </button>
          <button
            className="boton boton--secundario boton--chico"
            type="button"
            onClick={() => onElegir(punto.latitud, punto.longitud)}
          >
            Usar este punto
          </button>
        </div>
      </div>
    </div>
  );
}

import { createServer } from 'node:http';

// API local para revisar la interfaz R249. Solo usa datos ficticios .test.
let configuracion = {
  nombre: 'Hotel de prueba R249',
  contacto_email: 'reservas@hotel.test',
  contacto_telefono: '',
  direccion: '',
  hora_entrada: '',
  hora_salida: '',
  politica_cancelacion: '',
  zona_horaria: '',
  moneda: 'COP',
  latitud: '',
  longitud: '',
};

function json(respuesta, estado, datos) {
  respuesta.writeHead(estado, { 'Content-Type': 'application/json; charset=utf-8' });
  respuesta.end(JSON.stringify(datos));
}

createServer((solicitud, respuesta) => {
  const ruta = new URL(solicitud.url ?? '/', 'http://localhost').pathname;

  if (solicitud.method === 'GET' && ruta === '/api/admin/sesion') {
    json(respuesta, 200, { email: 'admin@hotel.test', nombre: 'Administración de prueba' });
    return;
  }

  if (solicitud.method === 'GET' && ruta === '/api/yo') {
    json(respuesta, 401, { error: 'sin sesión de huésped de prueba' });
    return;
  }

  if (solicitud.method === 'GET' && ruta === '/api/admin/hotel-config') {
    json(respuesta, 200, configuracion);
    return;
  }

  if (solicitud.method === 'POST' && ruta === '/api/admin/hotel-config') {
    let cuerpo = '';
    solicitud.setEncoding('utf8');
    solicitud.on('data', (fragmento) => { cuerpo += fragmento; });
    solicitud.on('end', () => {
      try {
        configuracion = { ...configuracion, ...JSON.parse(cuerpo) };
        json(respuesta, 200, configuracion);
      } catch {
        json(respuesta, 400, { error: 'JSON de prueba inválido' });
      }
    });
    return;
  }

  if (solicitud.method === 'GET' && ruta === '/api/hotel') {
    json(respuesta, 200, configuracion);
    return;
  }

  json(respuesta, 404, { error: 'Ruta no simulada por la prueba visual R249' });
}).listen(8918, '127.0.0.1', () => {
  console.log('API sintética R249 disponible en 127.0.0.1:8918');
});
